package com.franklouw.b1248.render;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BadgeRendererTest {

    private final BadgeRenderer renderer = new BadgeRenderer();

    @Test
    void renderTextIsNonEmpty() {
        Bitmap b = renderer.renderText("Hello");
        assertTrue(b.widthPx() > 0, "text should have width");
        assertTrue(b.cols() > 0, "text should have columns");
        assertEquals(b.cols() * 11, b.data().length, "bitmap sized cols x 11");
        assertTrue(hasLitPixel(b), "text should have at least one lit pixel");
    }

    @Test
    void renderTextWiderThanDisplay() {
        Bitmap b = renderer.renderText("This is a very long message that will scroll");
        assertTrue(b.widthPx() > 44, "long text should exceed the 44-px display");
    }

    @Test
    void renderFullWhiteImage() {
        BufferedImage img = solidImage(8, 11);
        Bitmap b = renderer.renderImage(img);
        assertEquals(1, b.cols());
        assertEquals(8, b.widthPx());
        for (int y = 0; y < 11; y++) {
            assertEquals((byte) 0xFF, b.data()[y], "all 8 pixels lit in row " + y);
        }
    }

    @Test
    void renderSinglePixelImage() {
        BufferedImage img = solidImage(1, 11);
        Bitmap b = renderer.renderImage(img);
        assertEquals(1, b.cols());
        assertEquals(1, b.widthPx());
        for (int y = 0; y < 11; y++) {
            assertEquals((byte) 0x80, b.data()[y], "MSB (leftmost) set in row " + y);
        }
    }

    @Test
    void renderImagePreservesAspectRatio() {
        BufferedImage img = solidImage(88, 11); // 8:1 aspect
        Bitmap b = renderer.renderImage(img);
        assertEquals(88, b.widthPx(), "88-px-wide image stays 88 px at 11 px height");
    }

    @Test
    void renderTextLooksLikeLetters() {
        Bitmap b = renderer.renderText("Hi");
        // Print for manual inspection when running with -Dtest output
        System.out.println("\n" + asciiArt(b));
        assertTrue(hasLitPixel(b));
    }

    private static boolean hasLitPixel(Bitmap b) {
        for (byte d : b.data()) {
            if (d != 0) {
                return true;
            }
        }
        return false;
    }

    private static BufferedImage solidImage(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }

    private static String asciiArt(Bitmap b) {
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < 11; y++) {
            for (int x = 0; x < b.widthPx(); x++) {
                sb.append(b.isSet(x, y) ? '#' : '.');
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
