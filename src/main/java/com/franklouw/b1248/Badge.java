package com.franklouw.b1248;

import java.util.ArrayList;
import java.util.List;

/**
 * The editable badge model: eight message slots, some of which may be empty.
 */
public final class Badge {

    public static final int SLOT_COUNT = 8;

    private final BadgeMessage[] slots = new BadgeMessage[SLOT_COUNT];

    public void set(int slot, BadgeMessage message) {
        checkSlot(slot);
        slots[slot] = message;
    }

    public BadgeMessage get(int slot) {
        checkSlot(slot);
        return slots[slot];
    }

    public void clear(int slot) {
        checkSlot(slot);
        slots[slot] = null;
    }

    public void clearAll() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            slots[i] = null;
        }
    }

    public boolean isEmpty(int slot) {
        checkSlot(slot);
        return slots[slot] == null;
    }

    /** Snapshot of all 8 slots (may contain nulls). */
    public BadgeMessage[] slots() {
        return slots.clone();
    }

    /** Total byte-column width across all non-empty slots. */
    public int totalColumns() {
        int total = 0;
        for (BadgeMessage m : slots) {
            if (m != null) {
                total += m.cols();
            }
        }
        return total;
    }

    /** Whether the total width is within the device's 256-byte write limit. */
    public boolean isWithinLimit() {
        return totalColumns() <= BadgeWriter.MAX_TOTAL_COLUMNS;
    }

    /** Non-empty messages in slot order. */
    public List<BadgeMessage> nonEmptyMessages() {
        List<BadgeMessage> out = new ArrayList<>();
        for (BadgeMessage m : slots) {
            if (m != null) {
                out.add(m);
            }
        }
        return out;
    }

    public boolean isEmpty() {
        return nonEmptyMessages().isEmpty();
    }

    private static void checkSlot(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            throw new IndexOutOfBoundsException("slot must be 0.." + (SLOT_COUNT - 1) + ", got " + slot);
        }
    }
}
