package aeron.test;

import io.aeron.Aeron;
import io.aeron.driver.MediaDriver;

/**
 * aeron.test@aeron
 *
 * TODO what you want to do?
 *
 * date 2019-05-10 19:02
 *
 * @author DingPengwei[dingpengwei@eversec.cn]
 * @version 1.0.0
 * @since DistributionVersion
 */
public class Main {
  static final MediaDriver driver = MediaDriver.launchEmbedded();
  static final Aeron.Context context = new Aeron.Context();
  static final Aeron aeron = Aeron.connect(context.aeronDirectoryName(driver.aeronDirectoryName()));

  public static void main(String[] args) {
    MainSubscriber mainSubscriber = new MainSubscriber(aeron);
    MainPublisher mainPublisher = new MainPublisher(aeron);

    new Thread(new Runnable() {
      @Override
      public void run() {
    mainSubscriber.start();

      }
    }).start();

    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    mainPublisher.send();
  }
}
