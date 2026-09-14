package com.fittrack.ui.common;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.text.Font;

/**
 * Loads bundled fonts and applies the FitTrack stylesheet.
 */
public final class Theme {

    private static boolean fontsLoaded;

    private Theme() {
    }

    public static void apply(Scene scene) {
        ensureFonts();
        String css = Theme.class.getResource("/css/fittrack.css").toExternalForm();
        if (!scene.getStylesheets().contains(css)) {
            scene.getStylesheets().add(css);
        }
        Parent root = scene.getRoot();
        if (root != null && !root.getStyleClass().contains("app-shell")) {
            root.getStyleClass().add("app-shell");
        }
    }

    public static void ensureFonts() {
        if (fontsLoaded) {
            return;
        }
        Font.loadFont(Theme.class.getResourceAsStream("/fonts/Manrope-Regular.ttf"), 13);
        Font.loadFont(Theme.class.getResourceAsStream("/fonts/Manrope-SemiBold.ttf"), 13);
        Font.loadFont(Theme.class.getResourceAsStream("/fonts/Manrope-Bold.ttf"), 13);
        fontsLoaded = true;
    }

    public static Label title(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("page-title");
        return label;
    }

    public static Label section(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }

    public static Label muted(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("muted");
        label.setWrapText(true);
        return label;
    }

    public static Label hint(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("hint");
        label.setWrapText(true);
        return label;
    }

    public static void primary(Button button) {
        if (!button.getStyleClass().contains("primary")) {
            button.getStyleClass().add("primary");
        }
    }

    public static void styleScroll(ScrollPane scroll) {
        scroll.getStyleClass().add("scroll-plain");
        scroll.setFitToWidth(true);
    }

    public static void setBarTone(ProgressBar bar, String toneClass) {
        bar.getStyleClass().removeAll("bar-ok", "bar-warn", "bar-over");
        if (toneClass != null && !toneClass.isBlank()) {
            bar.getStyleClass().add(toneClass);
        }
    }
}
