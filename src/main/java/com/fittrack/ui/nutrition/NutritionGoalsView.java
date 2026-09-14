package com.fittrack.ui.nutrition;

import com.fittrack.app.AppContext;
import com.fittrack.domain.common.ValidationException;
import com.fittrack.domain.nutrition.NutritionGoal;
import com.fittrack.service.NutritionGoalService;
import com.fittrack.ui.common.Theme;
import com.fittrack.ui.common.UiSupport;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Optional;

/**
 * Edit daily nutrition targets and see today's consumed vs goal bars.
 */
public final class NutritionGoalsView {

    private final NutritionGoalService goals = AppContext.nutritionGoalService();

    private final TextField kcalField = new TextField();
    private final TextField proteinField = new TextField();
    private final TextField carbsField = new TextField();
    private final TextField fatField = new TextField();
    private final TextField fiberField = new TextField();
    private final Label status = new Label();
    private final Label error = UiSupport.errorLabel();
    private final VBox barsBox = new VBox(8);

    public VBox getRoot() {
        status.getStyleClass().add("success");
        barsBox.setPadding(new Insets(4, 0, 0, 0));

        Button save = new Button("Save goals");
        Theme.primary(save);
        save.setOnAction(e -> saveGoals());

        kcalField.setPromptText("Calories (kcal)");
        proteinField.setPromptText("Protein (g)");
        carbsField.setPromptText("Carbs (g)");
        fatField.setPromptText("Fat (g)");
        fiberField.setPromptText("Fiber (g, optional)");

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);
        form.addRow(0, new Label("Calories (kcal)"), kcalField);
        form.addRow(1, new Label("Protein (g)"), proteinField);
        form.addRow(2, new Label("Carbs (g)"), carbsField);
        form.addRow(3, new Label("Fat (g)"), fatField);
        form.addRow(4, new Label("Fiber (g, optional)"), fiberField);

        HBox actions = new HBox(8, save);

        Label todayTitle = Theme.section("Today");

        loadIntoForm();
        refreshBars();

        VBox root = new VBox(12,
                form,
                actions,
                error,
                status,
                todayTitle,
                barsBox
        );
        root.setPadding(new Insets(8, 24, 24, 24));
        return root;
    }

    private void loadIntoForm() {
        Optional<NutritionGoal> current = goals.current();
        if (current.isPresent()) {
            fillFields(current.get());
        } else {
            kcalField.clear();
            proteinField.clear();
            carbsField.clear();
            fatField.clear();
            fiberField.clear();
        }
    }

    private void fillFields(NutritionGoal goal) {
        kcalField.setText(trimNum(goal.getKcal()));
        proteinField.setText(trimNum(goal.getProteinG()));
        carbsField.setText(trimNum(goal.getCarbsG()));
        fatField.setText(trimNum(goal.getFatG()));
        fiberField.setText(goal.getFiberG() == null ? "" : trimNum(goal.getFiberG()));
    }

    private void saveGoals() {
        UiSupport.clearError(error);
        status.setText("");
        try {
            double kcal = UiSupport.parsePositiveDouble(kcalField.getText(), "calories");
            double protein = parseNonNeg(proteinField.getText(), "protein");
            double carbs = parseNonNeg(carbsField.getText(), "carbs");
            double fat = parseNonNeg(fatField.getText(), "fat");
            Double fiber = fiberField.getText() == null || fiberField.getText().isBlank()
                    ? null
                    : parseNonNeg(fiberField.getText(), "fiber");
            goals.save(kcal, protein, carbs, fat, fiber);
            status.setText("Goals saved.");
            refreshBars();
        } catch (ValidationException | IllegalArgumentException ex) {
            UiSupport.showError(error, ex.getMessage());
        }
    }

    private void refreshBars() {
        barsBox.getChildren().clear();
        NutritionGoalService.DayProgress day = goals.todayProgress();
        if (day.goal() == null) {
            barsBox.getChildren().add(Theme.muted(
                    "No nutrition goals yet. Enter targets above and save."));
            return;
        }
        barsBox.getChildren().addAll(
                macroBar("Calories", day.consumed().getKcal(), day.goal().getKcal(), "kcal"),
                macroBar("Protein", day.consumed().getProteinG(), day.goal().getProteinG(), "g"),
                macroBar("Carbs", day.consumed().getCarbsG(), day.goal().getCarbsG(), "g"),
                macroBar("Fat", day.consumed().getFatG(), day.goal().getFatG(), "g")
        );
        if (day.goal().getFiberG() != null && day.goal().getFiberG() > 0) {
            barsBox.getChildren().add(
                    macroBar("Fiber", day.consumed().getFiberG(), day.goal().getFiberG(), "g"));
        }
        Label remaining = new Label("Remaining today: %.0f kcal · %.0f g protein"
                .formatted(day.remainingKcal(), day.remainingProteinG()));
        remaining.getStyleClass().add("body-text");
        barsBox.getChildren().add(remaining);
    }

    public static VBox macroBar(String label, double consumed, double goal, String unit) {
        double fraction = goal <= 0 ? 0 : Math.min(1.0, consumed / goal);
        ProgressBar bar = new ProgressBar(fraction);
        bar.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(bar, Priority.ALWAYS);
        Label text = new Label("%s  %.0f / %.0f %s".formatted(label, consumed, goal, unit));
        text.getStyleClass().add("hint");
        if (consumed > goal + 1e-6) {
            Theme.setBarTone(bar, "bar-over");
            text.setText(text.getText() + "  (over)");
        } else if (fraction >= 0.9) {
            Theme.setBarTone(bar, "bar-ok");
        } else {
            Theme.setBarTone(bar, "bar-warn");
        }
        return new VBox(4, text, bar);
    }

    private static double parseNonNeg(String raw, String field) {
        try {
            double value = Double.parseDouble(raw.trim());
            if (value < 0) {
                throw new NumberFormatException();
            }
            return value;
        } catch (Exception e) {
            throw new IllegalArgumentException("Enter a valid " + field);
        }
    }

    private static String trimNum(double value) {
        if (Math.abs(value - Math.rint(value)) < 1e-6) {
            return Long.toString(Math.round(value));
        }
        return "%.1f".formatted(value);
    }
}
