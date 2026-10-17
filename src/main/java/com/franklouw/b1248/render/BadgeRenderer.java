package com.franklouw.b1248.render;

import com.franklouw.b1248.BadgeWriter;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/**
 * Renders text and images into the B1248 bitmap format (see SPEC.md §3.4).
 * Text is drawn with the bundled DejaVu Sans font at 11 px line height, with
 * anti-aliasing off so the result is a crisp 1-bit image.
 */
public final class BadgeRenderer {

    private static final int HEIGHT = BadgeWriter.HEIGHT;
    private static final String FONT_RESOURCE = "/fonts/DejaVuSans.ttf";

    private final Font font;

    public BadgeRenderer() {
        this.font = loadFont();
    }

    private static Font loadFont() {
        try (InputStream in = BadgeRenderer.class.getResourceAsStream(FONT_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("bundled font not found: " + FONT_RESOURCE);
            }
            return Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (IOException | java.awt.FontFormatException e) {
            throw new IllegalStateException("failed to load font " + FONT_RESOURCE, e);
        }
    }

    /** Render a line of text to a bitmap. Text may be wider than the 44-px display. */
    public Bitmap renderText(String text) {
        Font sized = deriveForLineHeight(HEIGHT);

        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = probe.createGraphics();
        FontMetrics fm = g.getFontMetrics(sized);
        int width = Math.max(1, fm.stringWidth(text));
        int ascent = fm.getAscent();
        g.dispose();

        BufferedImage img = new BufferedImage(width, HEIGHT, BufferedImage.TYPE_BYTE_GRAY);
        g = img.createGraphics();
        g.setFont(sized);
        g.setColor(Color.WHITE);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.drawString(text, 0, ascent);
        g.dispose();

        return encode(img);
    }

    /** Render an image (any color model), scaled to 11 px height (aspect preserved). */
    public Bitmap renderImage(BufferedImage image) {
        int dstW = Math.max(1, Math.round((float) image.getWidth() * HEIGHT / image.getHeight()));
        BufferedImage scaled = new BufferedImage(dstW, HEIGHT, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, 0, 0, dstW, HEIGHT, null);
        g.dispose();
        return encode(scaled);
    }

    /** Derive the font size (in points) whose ascent+descent equals {@code targetHeight} px. */
    private Font deriveForLineHeight(float targetHeight) {
        float referenceSize = 100f;
        Font reference = font.deriveFont(referenceSize);
        float lineHeight = lineHeight(reference);
        return font.deriveFont(referenceSize * targetHeight / lineHeight);
    }

    private static float lineHeight(Font f) {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = img.createGraphics();
        FontMetrics fm = g.getFontMetrics(f);
        g.dispose();
        return fm.getAscent() + fm.getDescent();
    }

    /** Convert a grayscale image (11 px tall) to a column-major, MSB-first bitmap. */
    private static Bitmap encode(BufferedImage img) {
        if (img.getHeight() != HEIGHT) {
            throw new IllegalArgumentException("image must be " + HEIGHT + " px tall, got " + img.getHeight());
        }
        int width = img.getWidth();
        int cols = (width + 7) / 8;
        byte[] data = new byte[cols * HEIGHT];
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < width; x++) {
                if ((img.getRGB(x, y) & 0xFF) >= 128) {
                    data[(x / 8) * HEIGHT + y] |= (byte) (0x80 >> (x % 8));
                }
            }
        }
        return new Bitmap(data, cols, width);
    }
}
