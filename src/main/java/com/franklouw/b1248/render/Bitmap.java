package com.franklouw.b1248.render;

import com.franklouw.b1248.BadgeWriter;

/**
 * A rendered badge image in the B1248 bitmap format: column-major, 11 bytes per
 * 8-pixel byte-column, MSB = leftmost pixel, 1 = lit.
 */
public final class Bitmap {

    private final byte[] data;
    private final int cols;
    private final int widthPx;

    public Bitmap(byte[] data, int cols, int widthPx) {
        this.data = data;
        this.cols = cols;
        this.widthPx = widthPx;
    }

    /** Column-major bitmap: {@code data[col * HEIGHT + row]}. */
    public byte[] data() {
        return data;
    }

    /** Number of 8-pixel byte-columns (this is the message "length"). */
    public int cols() {
        return cols;
    }

    /** Pixel width (may be less than {@code cols * 8}; the tail bits are padding). */
    public int widthPx() {
        return widthPx;
    }

    /** Whether the pixel at (x, y) is lit. */
    public boolean isSet(int x, int y) {
        if (x < 0 || x >= widthPx || y < 0 || y >= BadgeWriter.HEIGHT) {
            return false;
        }
        return (data[(x / 8) * BadgeWriter.HEIGHT + y] & (0x80 >> (x % 8))) != 0;
    }
}
