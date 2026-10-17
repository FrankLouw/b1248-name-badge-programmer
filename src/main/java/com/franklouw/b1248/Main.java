package com.franklouw.b1248;

/**
 * Proof-of-functionality: writes three full-width messages (the maximum the device
 * supports per write; see {@link BadgeWriter#MAX_TOTAL_COLUMNS}).
 */
public class Main {

    private static final int WIDTH = 44;
    private static final int COLS = (WIDTH + 7) / 8; // 6

    public static void main(String[] args) {
        BadgeWriter writer = new BadgeWriter();
        if (!writer.open()) {
            System.err.println("No B1248 badge (0483:5750) found.");
            System.exit(1);
        }

        BadgeMessage border = pattern("border");
        BadgeMessage leftHalf = pattern("left");
        BadgeMessage rightHalf = pattern("right");

        System.out.println("Device opened; writing 3 full-width messages...");
        writer.writeMessages(border, leftHalf, rightHalf);
        writer.close();
        System.out.println("Done. Cycle through M1-M3 on the badge.");
    }

    /** Build a message from a named test pattern (freeze effect). */
    private static BadgeMessage pattern(String name) {
        byte[] bitmap = new byte[COLS * BadgeWriter.HEIGHT];
        for (int y = 0; y < BadgeWriter.HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                boolean lit = switch (name) {
                    case "leftcol" -> x == 0;
                    case "rightcol" -> x == WIDTH - 1;
                    case "toprow" -> y == 0;
                    case "bottomrow" -> y == BadgeWriter.HEIGHT - 1;
                    case "border" -> y == 0 || y == BadgeWriter.HEIGHT - 1 || x == 0 || x == WIDTH - 1;
                    case "left" -> x < WIDTH / 2;
                    case "right" -> x >= WIDTH / 2;
                    case "top" -> y < BadgeWriter.HEIGHT / 2;
                    case "bottom" -> y >= BadgeWriter.HEIGHT / 2;
                    default -> false;
                };
                if (lit) {
                    bitmap[(x / 8) * BadgeWriter.HEIGHT + y] |= (byte) (0x80 >> (x % 8));
                }
            }
        }
        return new BadgeMessage(bitmap, COLS, 4 /* freeze */, 1, false, false);
    }
}
