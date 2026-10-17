package com.franklouw.b1248;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BadgeTest {

    private static BadgeMessage msg(int cols) {
        return new BadgeMessage(new byte[cols * 11], cols, 4, 1, false, false);
    }

    @Test
    void totalColumnsSumsNonEmptySlots() {
        Badge b = new Badge();
        b.set(0, msg(6));
        b.set(2, msg(6));
        b.set(5, msg(5));
        assertEquals(17, b.totalColumns());
        assertEquals(3, b.nonEmptyMessages().size());
    }

    @Test
    void withinLimit() {
        Badge b = new Badge();
        b.set(0, msg(6));
        b.set(1, msg(6));
        b.set(2, msg(6)); // 18 <= 23
        assertTrue(b.isWithinLimit());
        b.set(3, msg(6)); // 24 > 23
        assertFalse(b.isWithinLimit());
    }

    @Test
    void clearAndEmpty() {
        Badge b = new Badge();
        assertTrue(b.isEmpty());
        b.set(0, msg(6));
        assertFalse(b.isEmpty());
        b.clear(0);
        assertTrue(b.isEmpty());
    }
}
