/*
 * Copyright 2017 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.aeron.cluster;

import io.aeron.*;
import io.aeron.cluster.codecs.*;
import io.aeron.logbuffer.BufferClaim;
import io.aeron.logbuffer.ControlledFragmentHandler;
import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.collections.ArrayListUtil;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.*;

import java.util.ArrayList;
import java.util.Iterator;

import static io.aeron.cluster.ClusterSession.State.*;
import static io.aeron.driver.status.SystemCounterDescriptor.SYSTEM_COUNTER_TYPE_ID;

class SequencerAgent implements Agent
{
    private static final int MAX_SEND_ATTEMPTS = 3;
    private static final int TIMER_POLL_LIMIT = 10;
    private static final int FRAGMENT_POLL_LIMIT = 10;

    private long nextSessionId = 1;
    private final long sessionTimeoutMs;
    private final ConsensusModule.Context ctx;
    private final Aeron aeron;
    private final AgentInvoker aeronClientInvoker;
    private final EpochClock epochClock;
    private final CachedEpochClock cachedEpochClock = new CachedEpochClock();
    private final TimerService timerService;
    private final IngressAdapter ingressAdapter;
    private final BufferClaim bufferClaim = new BufferClaim();
    private final LogAppender logAppender;
    private final Counter messageIndex;
    private final Long2ObjectHashMap<ClusterSession> clusterSessionByIdMap = new Long2ObjectHashMap<>();
    private final ArrayList<ClusterSession> pendingClusterSessions = new ArrayList<>();
    private final ArrayList<ClusterSession> rejectedClusterSessions = new ArrayList<>();
    private final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    private final SessionEventEncoder sessionEventEncoder = new SessionEventEncoder();

    // TODO: last message correlation id per session counter

    SequencerAgent(final ConsensusModule.Context ctx)
    {
        this.ctx = ctx;
        this.aeron = ctx.aeron();
        this.epochClock = ctx.epochClock();
        this.sessionTimeoutMs = ctx.sessionTimeoutNs() / 1000;

        aeronClientInvoker = ctx.ownsAeronClient() ? aeron.conductorAgentInvoker() : null;

        messageIndex = aeron.addCounter(SYSTEM_COUNTER_TYPE_ID, "Log message index");

        final Subscription ingressSubscription = aeron.addSubscription(ctx.ingressChannel(), ctx.ingressStreamId());
        ingressAdapter = new IngressAdapter(this, ingressSubscription, FRAGMENT_POLL_LIMIT);

        final Publication logPublication = aeron.addExclusivePublication(ctx.logChannel(), ctx.logStreamId());
        logAppender = new LogAppender(logPublication);

        final Subscription timerSubscription = aeron.addSubscription(ctx.timerChannel(), ctx.timerStreamId());
        timerService = new TimerService(
            TIMER_POLL_LIMIT, FRAGMENT_POLL_LIMIT, this, timerSubscription, cachedEpochClock);
    }

    public void onClose()
    {
        if (!ctx.ownsAeronClient())
        {
            for (final ClusterSession session : clusterSessionByIdMap.values())
            {
                session.close();
            }

            CloseHelper.close(ingressAdapter);
            CloseHelper.close(timerService);
            CloseHelper.close(logAppender);
        }
    }

    public int doWork()
    {
        int workCount = 0;

        final long nowMs = epochClock.time();
        cachedEpochClock.update(nowMs);

        if (null != aeronClientInvoker)
        {
            workCount += aeronClientInvoker.invoke();
        }

        workCount += processPendingSessions(pendingClusterSessions, nowMs);
        workCount += ingressAdapter.poll();
        workCount += timerService.poll(nowMs);
        workCount += checkSessions(clusterSessionByIdMap, nowMs);

        processRejectedSessions(rejectedClusterSessions, nowMs);

        return workCount;
    }

    public String roleName()
    {
        return "sequencer";
    }

    public void onSessionConnect(final long correlationId, final int responseStreamId, final String responseChannel)
    {
        final Publication publication = aeron.addPublication(responseChannel, responseStreamId);
        final long sessionId = nextSessionId++;
        final ClusterSession session = new ClusterSession(sessionId, publication);
        session.lastActivity(cachedEpochClock.time(), correlationId);

        if (pendingClusterSessions.size() + clusterSessionByIdMap.size() < ctx.maxConcurrentSessions())
        {
            pendingClusterSessions.add(session);
        }
        else
        {
            rejectedClusterSessions.add(session);
        }
    }

    public void onSessionClose(final long clusterSessionId)
    {
        final ClusterSession session = clusterSessionByIdMap.get(clusterSessionId);
        if (null != session)
        {
            session.close();
            if (appendCloseSession(session, CloseReason.USER_ACTION, cachedEpochClock.time()))
            {
                clusterSessionByIdMap.remove(clusterSessionId);
            }
        }
    }

    public ControlledFragmentAssembler.Action onSessionMessage(
        final DirectBuffer buffer,
        final int offset,
        final int length,
        final long clusterSessionId,
        final long correlationId)
    {
        final long nowMs = cachedEpochClock.time();
        final ClusterSession session = clusterSessionByIdMap.get(clusterSessionId);
        if (null == session || (session.state() == TIMED_OUT || session.state() == CLOSED))
        {
            return ControlledFragmentHandler.Action.CONTINUE;
        }

        if (session.state() == OPEN && logAppender.appendMessage(buffer, offset, length, nowMs))
        {
            messageIndex.incrementOrdered();
            session.lastActivity(nowMs, correlationId);

            return ControlledFragmentHandler.Action.CONTINUE;
        }

        return ControlledFragmentHandler.Action.ABORT;
    }

    public void onKeepAlive(final long correlationId, final long clusterSessionId)
    {
        final ClusterSession session = clusterSessionByIdMap.get(clusterSessionId);
        if (null != session)
        {
            session.lastActivity(cachedEpochClock.time(), correlationId);
        }
    }

    public boolean onExpireTimer(final long correlationId, final long nowMs)
    {
        if (logAppender.appendTimerEvent(correlationId, nowMs))
        {
            messageIndex.incrementOrdered();

            return true;
        }

        return false;
    }

    private int processPendingSessions(final ArrayList<ClusterSession> pendingSessions, final long nowMs)
    {
        int workCount = 0;

        for (int lastIndex = pendingSessions.size() - 1, i = lastIndex; i >= 0; i--)
        {
            final ClusterSession session = pendingSessions.get(i);

            if (sendSessionEvent(session, EventCode.OK, ""))
            {
                ArrayListUtil.fastUnorderedRemove(pendingSessions, i, lastIndex);
                lastIndex--;

                session.timeOfLastActivityMs(nowMs);
                session.state(CONNECTED);
                clusterSessionByIdMap.put(session.id(), session);

                appendConnectedSession(session, nowMs);

                workCount += 1;
            }
            else if (nowMs > (session.timeOfLastActivityMs() + sessionTimeoutMs))
            {
                ArrayListUtil.fastUnorderedRemove(pendingSessions, i, lastIndex);
                lastIndex--;

                session.close();
            }
        }

        return workCount;
    }

    private void processRejectedSessions(final ArrayList<ClusterSession> rejectedSessions, final long nowMs)
    {
        for (int lastIndex = rejectedSessions.size() - 1, i = lastIndex; i >= 0; i--)
        {
            final ClusterSession session = rejectedSessions.get(i);

            if (sendSessionEvent(session, EventCode.ERROR, "Active session limit exceeded") ||
                nowMs > (session.timeOfLastActivityMs() + sessionTimeoutMs))
            {
                ArrayListUtil.fastUnorderedRemove(rejectedSessions, i, lastIndex);
                lastIndex--;

                session.close();
            }
        }
    }

    private int checkSessions(final Long2ObjectHashMap<ClusterSession> sessionByIdMap, final long nowMs)
    {
        int workCount = 0;

        final Iterator<ClusterSession> iter = sessionByIdMap.values().iterator();
        while (iter.hasNext())
        {
            final ClusterSession session = iter.next();

            final ClusterSession.State state = session.state();
            if (nowMs > (session.timeOfLastActivityMs() + sessionTimeoutMs))
            {
                switch (state)
                {
                    case OPEN:
                        sendSessionEvent(session, EventCode.ERROR, "Timeout due to inactivity");
                        if (appendCloseSession(session, CloseReason.TIMEOUT, nowMs))
                        {
                            iter.remove();
                            workCount += 1;
                        }
                        else
                        {
                            session.state(TIMED_OUT);
                        }
                        break;

                    case TIMED_OUT:
                    case CLOSED:
                        final CloseReason reason = state == TIMED_OUT ? CloseReason.TIMEOUT : CloseReason.USER_ACTION;
                        if (appendCloseSession(session, reason, nowMs))
                        {
                            iter.remove();
                            workCount += 1;
                        }
                        break;

                    default:
                        session.close();
                        iter.remove();
                }
            }
            else if (state == CONNECTED)
            {
                if (appendConnectedSession(session, nowMs))
                {
                    workCount += 1;
                }
            }
        }

        return workCount;
    }

    private boolean appendConnectedSession(final ClusterSession session, final long nowMs)
    {
        if (logAppender.appendConnectedSession(session, nowMs))
        {
            session.state(ClusterSession.State.OPEN);
            messageIndex.incrementOrdered();

            return true;
        }

        return false;
    }

    private boolean appendCloseSession(final ClusterSession session, final CloseReason closeReason, final long nowMs)
    {
        if (logAppender.appendClosedSession(session, closeReason, nowMs))
        {
            messageIndex.incrementOrdered();
            session.close();

            return true;
        }

        return false;
    }

    private boolean sendSessionEvent(final ClusterSession session, final EventCode code, final String detail)
    {
        final Publication publication = session.responsePublication();
        final int length = MessageHeaderEncoder.ENCODED_LENGTH +
            SessionEventEncoder.BLOCK_LENGTH +
            SessionEventEncoder.detailHeaderLength() +
            detail.length();

        int attempts = MAX_SEND_ATTEMPTS;
        do
        {
            if (publication.tryClaim(length, bufferClaim) > 0)
            {
                sessionEventEncoder
                    .wrapAndApplyHeader(bufferClaim.buffer(), bufferClaim.offset(), messageHeaderEncoder)
                    .clusterSessionId(session.id())
                    .correlationId(session.lastCorrelationId())
                    .code(code)
                    .detail(detail);

                bufferClaim.commit();

                return true;
            }
        }
        while (--attempts > 0);

        return false;
    }
}