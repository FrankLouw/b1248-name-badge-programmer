package com.franklouw.b1248;

/**
 * A single badge message: its bitmap plus display attributes.
 *
 * @param bitmap column-major bitmap: {@code bitmap[col * HEIGHT + row]}, MSB = leftmost pixel, 1 = lit
 * @param cols   number of 8-pixel byte-columns in {@code bitmap}
 * @param effect effect id (0..7, 4 = freeze)
 * @param speed  scrolling speed (1..8)
 * @param blink  blink on/off
 * @param frame  border/frame on/off
 */
public record BadgeMessage(byte[] bitmap, int cols, int effect, int speed, boolean blink, boolean frame) {
}
