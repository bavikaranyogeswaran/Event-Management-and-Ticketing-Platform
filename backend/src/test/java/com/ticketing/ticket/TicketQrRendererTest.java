package com.ticketing.ticket;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.ticketing.AbstractIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/** Reads the rendered image back, so the QR is checked by decoding rather than by inspection. */
class TicketQrRendererTest extends AbstractIntegrationTest {

    private static final UUID TICKET = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID OTHER_TICKET = UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Autowired
    TicketQrRenderer renderer;
    @Autowired
    TicketTokenFactory tokenFactory;

    private String decode(byte[] png) throws IOException, NotFoundException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(
                new RGBLuminanceSource(width, height, pixels)));
        return new MultiFormatReader().decode(bitmap).getText();
    }

    @Test
    void theOutputIsARealPngImage() throws Exception {
        byte[] png = renderer.renderPng(TICKET);

        assertThat(png).isNotEmpty();
        // PNG magic number, so the bytes really are an image and not an error page
        assertThat(new byte[] { png[0], png[1], png[2], png[3] })
                .isEqualTo(new byte[] { (byte) 0x89, 'P', 'N', 'G' });
        assertThat(ImageIO.read(new ByteArrayInputStream(png))).isNotNull();
    }

    @Test
    void theImageScansBackToTheTicketsValidationToken() throws Exception {
        String scanned = decode(renderer.renderPng(TICKET));

        // exactly what a staff scanner will send, and what check-in will hash to find the ticket
        assertThat(scanned).isEqualTo(tokenFactory.rawToken(TICKET));
    }

    @Test
    void theScannedValueHashesToWhatIsStoredOnTheTicket() throws Exception {
        String scanned = decode(renderer.renderPng(TICKET));

        assertThat(tokenFactory.tokenHash(TICKET))
                .isEqualTo(new com.ticketing.shared.security.TokenService().hash(scanned));
    }

    @Test
    void differentTicketsScanToDifferentValues() throws Exception {
        assertThat(decode(renderer.renderPng(TICKET)))
                .isNotEqualTo(decode(renderer.renderPng(OTHER_TICKET)));
    }

    @Test
    void theSameTicketAlwaysRendersTheSameCode() throws Exception {
        // a QR can be reopened weeks later and must still be the same ticket
        assertThat(decode(renderer.renderPng(TICKET)))
                .isEqualTo(decode(renderer.renderPng(TICKET)));
    }

    @Test
    void theImageUsesTheConfiguredSize() throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(renderer.renderPng(TICKET)));

        assertThat(image.getWidth()).isEqualTo(320);
        assertThat(image.getHeight()).isEqualTo(320);
    }
}
