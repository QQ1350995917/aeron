package aeron.test;

import static io.aeron.samples.SampleConfiguration.EMBEDDED_MEDIA_DRIVER;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.driver.MediaDriver;
import org.agrona.CloseHelper;

/**
 * aeron.test@aeron
 *
 * TODO what you want to do?
 *
 * date 2019-05-10 18:10
 *
 * @author DingPengwei[dingpengwei@eversec.cn]
 * @version 1.0.0
 * @since DistributionVersion
 */
public class Main {

  public static void main(String[] args) {
    final MediaDriver driver = EMBEDDED_MEDIA_DRIVER ? MediaDriver.launchEmbedded() : null;
    final Aeron.Context ctx = new Aeron.Context();
    if (EMBEDDED_MEDIA_DRIVER) {
      ctx.aeronDirectoryName(driver.aeronDirectoryName());
    }


    try {
      new BasicPublisher().start(driver);
    } catch (Exception e) {
      e.printStackTrace();
    }
    new BasicSubscriber().start(driver);
    CloseHelper.quietClose(driver);


  }
}
