package com.franklouw.b1248.ui;

import javafx.application.Application;

/**
 * Entry point for a non-modular (fat-jar / jpackage) launch. Kept separate from
 * {@link BadgeApp} because JavaFX's {@code Application.launch()} fails its runtime
 * check when invoked from an {@code Application} subclass on the classpath.
 */
public final class Launcher {

    public static void main(String[] args) {
        Application.launch(BadgeApp.class, args);
    }
}
