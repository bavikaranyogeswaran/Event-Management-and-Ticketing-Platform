package com.ticketing.ticket;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.ticketing.shared.config.AppProperties;

/**
 * Draws the QR a staff member scans. It carries the validation token and nothing else,
 * so the token never travels inside a URL that a browser or proxy would keep.
 */
@Component
class TicketQrRenderer {

    // middling correction: survives a scuffed phone screen without inflating the pattern
    private static final ErrorCorrectionLevel CORRECTION = ErrorCorrectionLevel.M;
    private static final String FORMAT = "PNG";

    private final TicketTokenFactory tokenFactory;
    private final int pixels;
    private final int margin;

    TicketQrRenderer(TicketTokenFactory tokenFactory, AppProperties properties) {
        AppProperties.Ticket config = properties.ticket();
        this.tokenFactory = tokenFactory;
        this.pixels = config.qrPixels();
        this.margin = config.qrMargin();
    }

    byte[] renderPng(UUID ticketId) {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, CORRECTION);
        hints.put(EncodeHintType.MARGIN, margin);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        try {
            BitMatrix matrix = new QRCodeWriter()
                    .encode(tokenFactory.rawToken(ticketId), BarcodeFormat.QR_CODE, pixels, pixels, hints);
            return toPng(matrix);
        } catch (WriterException | IOException e) {
            throw new IllegalStateException("Could not render the ticket QR", e);
        }
    }

    private byte[] toPng(BitMatrix matrix) throws IOException {
        BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < matrix.getWidth(); x++) {
            for (int y = 0; y < matrix.getHeight(); y++) {
                image.setRGB(x, y, matrix.get(x, y) ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, FORMAT, out);
        return out.toByteArray();
    }
}
