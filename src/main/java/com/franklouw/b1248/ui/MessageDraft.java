package com.franklouw.b1248.ui;

import java.awt.image.BufferedImage;

/**
 * UI-level draft of a message: its source (text or image) and display settings.
 * The source is kept here so it survives slot switching; the rendered bitmap is
 * produced on demand by {@code BadgeRenderer}.
 */
public final class MessageDraft {

    public String text = "";
    public BufferedImage image;
    public int effect = 4; // freeze
    public int speed = 4;
    public boolean blink;
    public boolean frame;

    public boolean hasContent() {
        return (text != null && !text.isBlank()) || image != null;
    }
}
