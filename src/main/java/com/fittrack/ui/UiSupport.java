package com.fittrack.ui;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

/**
 * Shared page chrome so every screen uses the same surface and spacing.
 */
public final class UiSupport {

    private UiSupport() {
    }

    public static Label errorLabel() {
        Label label = new Label();
        label.setWrapText(true);
        label.getStyleClass().add("error-label");
        label.setManaged(false);
        label.setVisible(false);
        return label;
    }

    public static void showError(Label label, String message) {
        label.setText(message);
        label.setVisible(true);
        label.setManaged(true);
    }

    public static void clearError(Label label) {
        label.setText("");
        label.setVisible(false);
        label.setManaged(false);
    }

    /** Full page with title — used by top-level screens. */
    public static VBox page(String titleText, Node... children) {
        Theme.ensureFonts();
        VBox box = new VBox(12);
        box.getStyleClass().add("page");
        box.getChildren().add(Theme.title(titleText));
        box.getChildren().addAll(children);
        return box;
    }

    /** Nested body inside a parent page (e.g. Nutrition tabs) — same spacing, no second card. */
    public static VBox embed(Node... children) {
        Theme.ensureFonts();
        VBox box = new VBox(12);
        box.getStyleClass().add("page-embed");
        box.getChildren().addAll(children);
        return box;
    }

    public static ScrollPane scroll(Node content) {
        ScrollPane scroll = new ScrollPane(content);
        Theme.styleScroll(scroll);
        return scroll;
    }

    public static double parsePositiveDouble(String raw, String fieldName) {
        try {
            double value = Double.parseDouble(raw.trim());
            if (value <= 0) {
                throw new NumberFormatException();
            }
            return value;
        } catch (Exception e) {
            throw new IllegalArgumentException("Enter a valid " + fieldName);
        }
    }
}
