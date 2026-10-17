package com.franklouw.b1248.ui;

import com.franklouw.b1248.BadgeWriter;
import com.franklouw.b1248.render.Bitmap;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * Scaled preview of a badge bitmap. When the message is wider than the 44-px display,
 * the visible window is outlined in red.
 */
public final class BadgePreview extends Canvas {

    private static final int SCALE = 8;
    private static final int HEIGHT = BadgeWriter.HEIGHT;
    private static final int DISPLAY_WIDTH = 44;
    private static final int MAX_PREVIEW_WIDTH = BadgeWriter.MAX_TOTAL_COLUMNS * 8;

    private Bitmap bitmap;

    public BadgePreview() {
        super(DISPLAY_WIDTH * SCALE, HEIGHT * SCALE);
        setBitmap(null);
    }

    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
        int width = bitmap == null
                ? DISPLAY_WIDTH
                : Math.max(DISPLAY_WIDTH, Math.min(bitmap.widthPx(), MAX_PREVIEW_WIDTH));
        setWidth(width * SCALE);
        setHeight(HEIGHT * SCALE);
        redraw();
    }

    private void redraw() {
        GraphicsContext g = getGraphicsContext2D();
        g.setFill(Color.BLACK);
        g.fillRect(0, 0, getWidth(), getHeight());

        if (bitmap != null) {
            g.setFill(new Color(1.0, 0.55, 0.0, 1.0)); // LED orange
            for (int y = 0; y < HEIGHT; y++) {
                for (int x = 0; x < bitmap.widthPx(); x++) {
                    if (bitmap.isSet(x, y)) {
                        g.fillRect(x * SCALE, y * SCALE, SCALE, SCALE);
                    }
                }
            }
        }

        if (bitmap != null && bitmap.widthPx() > DISPLAY_WIDTH) {
            g.setStroke(Color.RED);
            g.setLineWidth(2);
            g.strokeRect(1, 1, DISPLAY_WIDTH * SCALE - 2, HEIGHT * SCALE - 2);
        }
    }
}
