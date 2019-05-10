package aeron.test;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import io.aeron.samples.SamplesUtil;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * aeron.test@aeron
 *
 * TODO what you want to do?
 *
 * date 2019-05-10 18:23
 *
 * @author DingPengwei[dingpengwei@eversec.cn]
 * @version 1.0.0
 * @since DistributionVersion
 */
public class MainTest {
  static final MediaDriver driver = MediaDriver.launchEmbedded();
  static final Aeron.Context context = new Aeron.Context();
  static final Aeron aeron = Aeron.connect(context.aeronDirectoryName(driver.aeronDirectoryName()));
  static final Subscription subscription = aeron
      .addSubscription("aeron:udp?endpoint=localhost:40123", 10);
  static final Publication publication = aeron
      .addPublication("aeron:udp?endpoint=localhost:40123", 10);

  public static void main(String[] args) {
    final FragmentHandler fragmentHandler = new FragmentHandler() {
      @Override
      public void onFragment(DirectBuffer buffer, int offset, int length, Header header) {
        final byte[] data = new byte[length];
        buffer.getBytes(offset, data);
        System.out.println(String.format(
            "message to stream %d from session %x (%d@%d) <<%s>>",
            header.streamId(), header.sessionId(), length, offset, new String(data)));
      }
    };

    IdleStrategy idleStrategy = new BackoffIdleStrategy(
        100, 10, TimeUnit.MICROSECONDS.toNanos(1), TimeUnit.MICROSECONDS.toNanos(100));

    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    UnsafeBuffer BUFFER = new UnsafeBuffer(ByteBuffer.allocateDirect(256));
    final String message = "Hello World!";
    BUFFER.putBytes(0, message.getBytes());

    long resultingPosition = publication.offer(BUFFER, 0, message.getBytes().length);

    System.out.println("pub " + resultingPosition);

    while (true) {
      final int fragmentsRead = subscription.poll(fragmentHandler, 10);
      idleStrategy.idle(fragmentsRead);
    }

  }
}
