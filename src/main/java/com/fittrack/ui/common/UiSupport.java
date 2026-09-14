package com.fittrack.ui.common;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

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

    public static VBox page(String titleText, javafx.scene.Node... children) {
        Theme.ensureFonts();
        Label title = Theme.title(titleText);
        VBox box = new VBox(12);
        box.getStyleClass().add("page");
        box.setPadding(new Insets(24));
        box.getChildren().add(title);
        box.getChildren().addAll(children);
        return box;
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
