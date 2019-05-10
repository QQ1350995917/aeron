package aeron.test;


import io.aeron.Aeron;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import io.aeron.samples.SampleConfiguration;
import io.aeron.samples.SamplesUtil;
import java.util.concurrent.TimeUnit;
import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SigInt;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * aeron.test@aeron
 *
 * TODO what you want to do?
 *
 * date 2019-05-10 16:08
 *
 * @author DingPengwei[dingpengwei@eversec.cn]
 * @version 1.0.0
 * @since DistributionVersion
 */

public class MainSubscriber {

  //  static final MediaDriver driver = MediaDriver.launchEmbedded();
//  static final Aeron.Context context = new Aeron.Context();
//  static final Aeron aeron = Aeron.connect(context.aeronDirectoryName(driver.aeronDirectoryName()));
//  static final Subscription subscription = aeron
//      .addSubscription("aeron:udp?endpoint=localhost:40123", 10);
  private Aeron aeron;
  private Subscription subscription;

  static final FragmentHandler fragmentHandler = new FragmentHandler() {
    @Override
    public void onFragment(DirectBuffer buffer, int offset, int length, Header header) {
      final byte[] data = new byte[length];
      buffer.getBytes(offset, data);
      String s = new String(data);
      int i = Integer.parseInt(s);
      if (i % 1000000 == 0){
        System.out.println(i);
      }
    }
  };

  public MainSubscriber(Aeron aeron) {
    this.aeron = aeron;
    this.subscription = aeron.addSubscription("aeron:udp?endpoint=localhost:40123", 10);
  }

  public void start() {
    IdleStrategy idleStrategy = new BackoffIdleStrategy(
        100, 10, TimeUnit.MICROSECONDS.toNanos(1), TimeUnit.MICROSECONDS.toNanos(100));
    while (true) {
      final int fragmentsRead = subscription.poll(fragmentHandler, 10);
      idleStrategy.idle(fragmentsRead);
    }
  }

  public static void main(final String[] args) {
//    IdleStrategy idleStrategy = new BackoffIdleStrategy(
//        100, 10, TimeUnit.MICROSECONDS.toNanos(1), TimeUnit.MICROSECONDS.toNanos(100));
//    while (true) {
//      final int fragmentsRead = subscription.poll(fragmentHandler, 10);
//      idleStrategy.idle(fragmentsRead);
//    }
  }
}
