package com.franklouw.b1248;

import org.hid4java.HidDevice;
import org.hid4java.HidManager;
import org.hid4java.HidServices;
import org.hid4java.HidServicesSpecification;

/**
 * Device/protocol layer for the B1248 (0483:5750) USB-HID LED badge.
 * Implements the validated write protocol from SPEC.md §3.
 */
public class BadgeWriter {

    public static final int VID = 0x0483;
    public static final int PID = 0x5750;
    public static final int HEIGHT = 11;
    public static final int REPORT_SIZE = 64;
    private static final int SLOTS = 8;

    /**
     * The device's write buffer is 256 bytes, so the total width across all messages
     * is capped at {@code 256 / HEIGHT = 23} byte-columns (184 px). Exceeding this
     * drops or corrupts the trailing messages (see SPEC.md §9). A full-width 44-px
     * message is 6 byte-columns, so this allows 3 full-width messages.
     */
    public static final int MAX_TOTAL_COLUMNS = 256 / HEIGHT; // 23

    private final HidServices services;
    private HidDevice device;

    public BadgeWriter() {
        HidServicesSpecification spec = new HidServicesSpecification();
        spec.setAutoShutdown(true);
        services = HidManager.getHidServices(spec);
        services.start();
    }

    public boolean open() {
        if (device != null) {
            return true; // already open
        }
        HidDevice d = services.getHidDevice(VID, PID, null);
        if (d == null) {
            return false;
        }
        if (!d.open()) {
            return false;
        }
        device = d;
        return true;
    }

    public boolean isOpen() {
        return device != null;
    }

    /** Close the device handle (keeps the HID services running so it can be reopened). */
    public void close() {
        if (device != null) {
            device.close();
            device = null;
        }
    }

    /** Close the device and shut down the HID services. */
    public void shutdown() {
        close();
        services.shutdown();
    }

    /**
     * Write the given messages (contiguous, from slot 0). Convenience for
     * {@link #write(Badge)}.
     */
    public void writeMessages(BadgeMessage... messages) {
        Badge badge = new Badge();
        for (int i = 0; i < messages.length; i++) {
            badge.set(i, messages[i]);
        }
        write(badge);
    }

    /**
     * Write a badge configuration to the device. Empty slots are allowed. The total
     * width across all messages must not exceed {@link #MAX_TOTAL_COLUMNS} byte-columns.
     */
    public void write(Badge badge) {
        if (!isOpen()) {
            throw new IllegalStateException("device is not open; call open() first");
        }
        int total = badge.totalColumns();
        if (total > MAX_TOTAL_COLUMNS) {
            throw new IllegalArgumentException(
                    "total content of " + total + " byte-columns exceeds the device limit of "
                            + MAX_TOTAL_COLUMNS + " (" + (MAX_TOTAL_COLUMNS * 8) + " px)");
        }

        BadgeMessage[] slots = badge.slots();

        writeReport(handshakeReport());
        sleep(500);
        writeReport(headerReport(slots));
        sleep(500);

        int[] offsets = new int[SLOTS];
        int running = 0;
        for (int i = 0; i < SLOTS; i++) {
            offsets[i] = running;
            if (slots[i] != null) {
                running += slots[i].cols();
            }
        }

        for (int row = 0; row < HEIGHT; row++) {
            byte[] line = new byte[REPORT_SIZE];
            for (int i = 0; i < SLOTS; i++) {
                BadgeMessage m = slots[i];
                if (m == null) {
                    continue;
                }
                for (int col = 0; col < m.cols(); col++) {
                    line[offsets[i] + col] = m.bitmap()[col * HEIGHT + row];
                }
            }
            writeReport(line);
            sleep(100);
        }

        writeReport(new byte[REPORT_SIZE]); // dummy line
        sleep(500);
    }

    private void writeReport(byte[] payload) {
        if (payload.length != REPORT_SIZE) {
            throw new IllegalArgumentException("payload must be " + REPORT_SIZE + " bytes");
        }
        device.write(payload, REPORT_SIZE, (byte) 0x00);
    }

    private static byte[] handshakeReport() {
        byte[] hello = new byte[REPORT_SIZE];
        System.arraycopy("Hello".getBytes(), 0, hello, 0, 5);
        return hello;
    }

    private static byte[] headerReport(BadgeMessage[] messages) {
        byte[] header = new byte[REPORT_SIZE];
        int offset = 0;
        for (int i = 0; i < SLOTS; i++) {
            BadgeMessage m = i < messages.length ? messages[i] : null;
            if (m != null) {
                int e = (m.frame() ? 0x80 : 0)
                        | (((m.speed() - 1) & 0x07) << 4)
                        | (m.blink() ? 0x08 : 0)
                        | (m.effect() & 0x07);
                header[1 + i] = (byte) e;
            }
            int base = 10 + i * 4;
            header[base] = 0x08;
            header[base + 1] = (byte) offset;
            header[base + 3] = (byte) (m != null ? m.cols() : 0);
            offset += m != null ? m.cols() : 0;
        }
        return header;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
