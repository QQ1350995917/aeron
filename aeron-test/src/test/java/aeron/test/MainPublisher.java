package aeron.test;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.samples.SampleConfiguration;
import java.nio.ByteBuffer;
import org.agrona.BufferUtil;
import org.agrona.CloseHelper;
import org.agrona.concurrent.UnsafeBuffer;

import java.util.concurrent.TimeUnit;

/**
 * aeron.test@aeron
 *
 * TODO what you want to do?
 *
 * date 2019-05-10 16:09
 *
 * @author DingPengwei[dingpengwei@eversec.cn]
 * @version 1.0.0
 * @since DistributionVersion
 */
public class MainPublisher {
//  static final MediaDriver driver = MediaDriver.launchEmbedded();
//  static final Aeron.Context context = new Aeron.Context();
//  static final Aeron aeron = Aeron.connect(context.aeronDirectoryName(driver.aeronDirectoryName()));
//  static final Publication publication = aeron
//    .addPublication("aeron:udp?endpoint=localhost:40123", 10);

  private Aeron aeron;
  private Publication publication;

  public MainPublisher(Aeron aeron) {
    this.aeron = aeron;
    this.publication = aeron.addPublication("aeron:udp?endpoint=localhost:40123", 10);
  }

  public void send(){
    for (int i=1;i<10000001;i++) {
      UnsafeBuffer BUFFER = new UnsafeBuffer(ByteBuffer.allocateDirect(256));
      final String message = i + "";
      BUFFER.putBytes(0, message.getBytes());
      long resultingPosition = publication.offer(BUFFER, 0, message.getBytes().length);
    }
  }

  public static void main(final String[] args) throws Exception {

//    UnsafeBuffer BUFFER = new UnsafeBuffer(ByteBuffer.allocateDirect(256));
//    final String message = "Hello World!";
//    BUFFER.putBytes(0, message.getBytes());
//
//    long resultingPosition = publication.offer(BUFFER, 0, message.getBytes().length);
//
//    System.out.println("pub " + resultingPosition);

//    CloseHelper.quietClose(driver);
  }
}
