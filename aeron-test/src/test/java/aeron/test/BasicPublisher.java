package aeron.test;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.driver.MediaDriver;
import io.aeron.samples.SampleConfiguration;
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
public class BasicPublisher {

  private static final int STREAM_ID = SampleConfiguration.STREAM_ID;
  private static final String CHANNEL = SampleConfiguration.CHANNEL;
  private static final long NUMBER_OF_MESSAGES = SampleConfiguration.NUMBER_OF_MESSAGES;
  private static final long LINGER_TIMEOUT_MS = SampleConfiguration.LINGER_TIMEOUT_MS;
  private static final boolean EMBEDDED_MEDIA_DRIVER = SampleConfiguration.EMBEDDED_MEDIA_DRIVER;
  private static final UnsafeBuffer BUFFER = new UnsafeBuffer(
      BufferUtil.allocateDirectAligned(256, 64));

  public void start(MediaDriver driver) throws Exception {
    System.out.println("Publishing to " + CHANNEL + " on stream Id " + STREAM_ID);

    // If configured to do so, create an embedded media driver within this application rather
    // than relying on an external one.

    final Aeron.Context ctx = new Aeron.Context();
    if (EMBEDDED_MEDIA_DRIVER) {
      ctx.aeronDirectoryName(driver.aeronDirectoryName());
    }

    // Connect a new Aeron instance to the media driver and create a publication on
    // the given channel and stream ID.
    // The Aeron and Publication classes implement "AutoCloseable" and will automatically
    // clean up resources when this try block is finished
    try (Aeron aeron = Aeron.connect(ctx);
        Publication publication = aeron.addPublication(CHANNEL, STREAM_ID)) {
      for (long i = 0; i < NUMBER_OF_MESSAGES; i++) {
        final String message = "Hello World! " + i;
        final byte[] messageBytes = message.getBytes();
        BUFFER.putBytes(0, messageBytes);

        System.out.print("Offering " + i + "/" + NUMBER_OF_MESSAGES + " - ");

        final long result = publication.offer(BUFFER, 0, messageBytes.length);

        if (result < 0L) {
          if (result == Publication.BACK_PRESSURED) {
            System.out.println("Offer failed due to back pressure");
          } else if (result == Publication.NOT_CONNECTED) {
            System.out.println("Offer failed because publisher is not connected to subscriber");
          } else if (result == Publication.ADMIN_ACTION) {
            System.out.println("Offer failed because of an administration action in the system");
          } else if (result == Publication.CLOSED) {
            System.out.println("Offer failed publication is closed");
            break;
          } else if (result == Publication.MAX_POSITION_EXCEEDED) {
            System.out.println("Offer failed due to publication reaching max position");
            break;
          } else {
            System.out.println("Offer failed due to unknown reason");
          }
        } else {
          System.out.println("yay!");
        }

        if (!publication.isConnected()) {
          System.out.println("No active subscribers detected");
        }

        Thread.sleep(TimeUnit.SECONDS.toMillis(1));
      }

      System.out.println("Done sending.");

      if (LINGER_TIMEOUT_MS > 0) {
        System.out.println("Lingering for " + LINGER_TIMEOUT_MS + " milliseconds...");
        Thread.sleep(LINGER_TIMEOUT_MS);
      }
    }
  }

  public static void main(final String[] args) throws Exception {
    System.out.println("Publishing to " + CHANNEL + " on stream Id " + STREAM_ID);

    // If configured to do so, create an embedded media driver within this application rather
    // than relying on an external one.
    final MediaDriver driver = EMBEDDED_MEDIA_DRIVER ? MediaDriver.launchEmbedded() : null;

    final Aeron.Context ctx = new Aeron.Context();
    if (EMBEDDED_MEDIA_DRIVER) {
      ctx.aeronDirectoryName(driver.aeronDirectoryName());
    }

    // Connect a new Aeron instance to the media driver and create a publication on
    // the given channel and stream ID.
    // The Aeron and Publication classes implement "AutoCloseable" and will automatically
    // clean up resources when this try block is finished
    try (Aeron aeron = Aeron.connect(ctx);
        Publication publication = aeron.addPublication(CHANNEL, STREAM_ID)) {
      for (long i = 0; i < NUMBER_OF_MESSAGES; i++) {
        final String message = "Hello World! " + i;
        final byte[] messageBytes = message.getBytes();
        BUFFER.putBytes(0, messageBytes);

        System.out.print("Offering " + i + "/" + NUMBER_OF_MESSAGES + " - ");

        final long result = publication.offer(BUFFER, 0, messageBytes.length);

        if (result < 0L) {
          if (result == Publication.BACK_PRESSURED) {
            System.out.println("Offer failed due to back pressure");
          } else if (result == Publication.NOT_CONNECTED) {
            System.out.println("Offer failed because publisher is not connected to subscriber");
          } else if (result == Publication.ADMIN_ACTION) {
            System.out.println("Offer failed because of an administration action in the system");
          } else if (result == Publication.CLOSED) {
            System.out.println("Offer failed publication is closed");
            break;
          } else if (result == Publication.MAX_POSITION_EXCEEDED) {
            System.out.println("Offer failed due to publication reaching max position");
            break;
          } else {
            System.out.println("Offer failed due to unknown reason");
          }
        } else {
          System.out.println("yay!");
        }

        if (!publication.isConnected()) {
          System.out.println("No active subscribers detected");
        }

        Thread.sleep(TimeUnit.SECONDS.toMillis(1));
      }

      System.out.println("Done sending.");

      if (LINGER_TIMEOUT_MS > 0) {
        System.out.println("Lingering for " + LINGER_TIMEOUT_MS + " milliseconds...");
        Thread.sleep(LINGER_TIMEOUT_MS);
      }
    }

    CloseHelper.quietClose(driver);
  }
}
