package com.fittrack.ui;

import com.fittrack.app.AppContext;
import com.fittrack.domain.OneRepMaxFormula;
import com.fittrack.domain.PersonalRecordType;
import com.fittrack.domain.ValidationException;
import com.fittrack.domain.PersonalRecord;
import com.fittrack.domain.BodyGoal;
import com.fittrack.domain.WeightEntry;
import com.fittrack.domain.LoggedSet;
import com.fittrack.persistence.WorkoutSessionDao;
import com.fittrack.service.DashboardService;
import com.fittrack.service.MuscleVolumeService;
import com.fittrack.service.NutritionGoalService;
import com.fittrack.service.OneRepMaxService;
import com.fittrack.service.ProgressService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public final class ProgressView {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final ProgressService progress = AppContext.progressService();
    private final OneRepMaxService oneRm = AppContext.oneRepMaxService();
    private final DashboardService dashboard = AppContext.dashboardService();
    private final NutritionGoalService nutritionGoals = AppContext.nutritionGoalService();
    private final MuscleVolumeService muscleVolume = AppContext.muscleVolumeService();

    private final TableView<PersonalRecord> prTable = new TableView<>();
    private final TableView<LoggedSet> historyTable = new TableView<>();
    private final TableView<WeightEntry> weightLogTable = new TableView<>();
    private final SearchableComboBox<WorkoutSessionDao.ExerciseSummary> exercisePick =
            new SearchableComboBox<>(WorkoutSessionDao.ExerciseSummary::name);
    private final Label formulaLabel = new Label();
    private final Label historyHint = new Label();
    private final Label bodyStatus = new Label();
    private final Label bodyError = UiSupport.errorLabel();
    private final TextField targetWeight = new TextField();
    private final DatePicker targetDate = new DatePicker();
    private final VBox adherenceBox = new VBox(6);
    private final Label weekMuscleLabel = new Label();
    private final Label weekMuscleHint = new Label(
            "Completed sets this week (Mon–today): primary muscle = 1.0, secondary = 0.5.");


    public ScrollPane getRoot() {
        OneRepMaxFormula preferred = progress.preferredFormula();
        formulaLabel.setText("Preferred estimate: " + preferred.name()
                + " (change under Profile). Estimates with reps > 10 are less reliable.");
        formulaLabel.setWrapText(true);
        formulaLabel.getStyleClass().add("muted");
        historyHint.getStyleClass().add("muted");
        bodyStatus.getStyleClass().add("success");

        buildPrTable();
        buildHistoryTable();
        buildWeightLogTable();

        List<WorkoutSessionDao.ExerciseSummary> exercises = progress.exercisesWithLoggedSets();
        exercisePick.setSearchPrompt("Search logged exercises…");
        exercisePick.setComboPrefWidth(280);
        exercisePick.setItems(exercises);
        exercisePick.combo().setPromptText("Exercise for 1RM history");
        exercisePick.combo().setOnAction(e -> loadHistory());

        if (!exercises.isEmpty()) {
            exercisePick.selectFirstIfAny();
            loadHistory();
        } else {
            historyHint.setText("Log weighted sets in a workout to see 1RM history here.");
        }

        prTable.setItems(FXCollections.observableArrayList(progress.listRecords()));
        refreshWeightLog();

        HBox historyHeader = exercisePick.asInlineRow();
        historyHeader.setPadding(new Insets(4, 0, 4, 0));
        historyHeader.setAlignment(Pos.CENTER_LEFT);

        Label bodyTitle = new Label("Body goal & weight");
        bodyTitle.getStyleClass().add("section-title");
        Label weightLogTitle = new Label("Weight & BMI log");
        weightLogTitle.getStyleClass().add("section-title");
        Label muscleTitle = new Label("Weekly muscle sets (completed)");
        muscleTitle.getStyleClass().add("section-title");
        weekMuscleHint.setWrapText(true);
        weekMuscleHint.getStyleClass().add("hint");
        weekMuscleLabel.setWrapText(true);
        weekMuscleLabel.getStyleClass().add("body-text");
        weekMuscleLabel.setText(muscleVolume.formatWeekCompleted());

        Label nutritionTitle = new Label("Nutrition adherence (last 7 days)");
        nutritionTitle.getStyleClass().add("section-title");
        Label prTitle = new Label("Personal records");
        prTitle.getStyleClass().add("section-title");
        Label histTitle = new Label("Estimated 1RM history");
        histTitle.getStyleClass().add("section-title");

        loadBodyGoalForm();
        refreshAdherence();

        VBox page = UiSupport.page(
                "Progress",
                formulaLabel,
                muscleTitle,
                weekMuscleHint,
                weekMuscleLabel,
                bodyTitle,
                buildBodyGoalForm(),
                bodyError,
                bodyStatus,
                weightLogTitle,
                weightLogTable,
                nutritionTitle,
                adherenceBox,
                prTitle,
                prTable,
                histTitle,
                historyHeader,
                historyHint,
                historyTable
        );
        VBox.setVgrow(prTable, Priority.ALWAYS);
        VBox.setVgrow(historyTable, Priority.ALWAYS);
        VBox.setVgrow(weightLogTable, Priority.ALWAYS);
        return UiSupport.scroll(page);
    }

    private VBox buildBodyGoalForm() {
        targetWeight.setPromptText("Target kg");
        targetWeight.setPrefWidth(100);
        Button save = new Button("Save body goal");
        save.setOnAction(e -> {
            UiSupport.clearError(bodyError);
            bodyStatus.setText("");
            try {
                double kg = UiSupport.parsePositiveDouble(targetWeight.getText(), "target weight (kg)");
                dashboard.saveBodyGoal(kg, targetDate.getValue());
                bodyStatus.setText("Body goal saved.");
            } catch (ValidationException | IllegalArgumentException ex) {
                UiSupport.showError(bodyError, ex.getMessage());
            }
        });
        Button clear = new Button("Clear");
        clear.setOnAction(e -> {
            dashboard.clearBodyGoal();
            targetWeight.clear();
            targetDate.setValue(null);
            bodyStatus.setText("Body goal cleared.");
        });

        DashboardService.WeightTrend trend = dashboard.weightTrend();
        Label trendLabel = new Label(formatTrend(trend));
        trendLabel.setWrapText(true);
        trendLabel.getStyleClass().add("body-text");

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);
        form.addRow(0, new Label("Target weight (kg)"), targetWeight);
        form.addRow(1, new Label("Target date (optional)"), targetDate);

        return new VBox(8, trendLabel, form, new HBox(8, save, clear));
    }

    private void loadBodyGoalForm() {
        Optional<BodyGoal> goal = dashboard.bodyGoal();
        if (goal.isPresent()) {
            targetWeight.setText("%.1f".formatted(goal.get().getTargetWeightKg()));
            targetDate.setValue(goal.get().getTargetDate());
        }
    }

    private void buildWeightLogTable() {
        TableColumn<WeightEntry, String> when = new TableColumn<>("When");
        when.setPrefWidth(140);
        when.setCellValueFactory(c -> new SimpleStringProperty(WHEN.format(c.getValue().getMeasuredAt())));

        TableColumn<WeightEntry, String> kg = new TableColumn<>("kg");
        kg.setPrefWidth(80);
        kg.setCellValueFactory(c -> new SimpleStringProperty("%.2f".formatted(c.getValue().getWeightKg())));

        TableColumn<WeightEntry, String> bmi = new TableColumn<>("BMI");
        bmi.setPrefWidth(80);
        bmi.setCellValueFactory(c -> {
            Double value = bmiFor(c.getValue());
            return new SimpleStringProperty(value == null ? "—" : "%.1f".formatted(value));
        });

        TableColumn<WeightEntry, String> note = new TableColumn<>("Note");
        note.setPrefWidth(220);
        note.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getNote() == null ? "" : c.getValue().getNote()));

        weightLogTable.getColumns().addAll(List.of(when, kg, bmi, note));
        weightLogTable.setPrefHeight(200);
        weightLogTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        weightLogTable.setPlaceholder(new Label("No weight logs yet — log weight on Profile."));
    }

    private void refreshWeightLog() {
        List<WeightEntry> entries = dashboard.weightTrend().chronological();
        List<WeightEntry> newestFirst = new java.util.ArrayList<>(entries);
        java.util.Collections.reverse(newestFirst);
        weightLogTable.setItems(FXCollections.observableArrayList(newestFirst));
    }

    private Double bmiFor(WeightEntry entry) {
        for (DashboardService.BmiPoint point : dashboard.weightTrend().bmiPoints()) {
            if (point.day().equals(entry.getMeasuredAt().atZone(ZoneId.systemDefault()).toLocalDate())) {
                return point.bmi();
            }
        }
        return null;
    }

    private void refreshAdherence() {
        adherenceBox.getChildren().clear();
        List<NutritionGoalService.DayProgress> history = nutritionGoals.adherenceHistory(7);
        double rate = nutritionGoals.calorieAdherenceRate(7);
        Label summary = new Label(history.stream().anyMatch(d -> d.goal() != null)
                ? "Days within ±10%% of calorie goal: %.0f%%".formatted(rate * 100)
                : "Set nutrition goals under Nutrition → Goals to track adherence.");
        summary.getStyleClass().add("body-text");
        adherenceBox.getChildren().add(summary);

        for (NutritionGoalService.DayProgress day : history) {
            if (day.goal() == null) {
                Label row = new Label(day.day() + "  —  no goal · %.0f kcal logged"
                        .formatted(day.consumed().getKcal()));
                row.getStyleClass().add("hint");
                adherenceBox.getChildren().add(row);
                continue;
            }
            adherenceBox.getChildren().add(
                    NutritionGoalsView.macroBar(
                            day.day().toString(),
                            day.consumed().getKcal(),
                            day.goal().getKcal(),
                            "kcal"
                    )
            );
        }
    }

    private void loadHistory() {
        WorkoutSessionDao.ExerciseSummary selected = exercisePick.getValue();
        if (selected == null) {
            historyTable.getItems().clear();
            return;
        }
        List<LoggedSet> sets = progress.oneRmHistory(selected.exerciseId());
        historyTable.setItems(FXCollections.observableArrayList(sets));
        if (sets.isEmpty()) {
            historyHint.setText("No weighted sets with estimates for this exercise yet.");
        } else {
            historyHint.setText(sets.size() + " sets · preferred column highlighted in value text");
        }
    }

    private void buildPrTable() {
        TableColumn<PersonalRecord, String> when = new TableColumn<>("When");
        when.setPrefWidth(130);
        when.setCellValueFactory(c -> new SimpleStringProperty(WHEN.format(c.getValue().getAchievedAt())));

        TableColumn<PersonalRecord, String> exercise = new TableColumn<>("Exercise");
        exercise.setPrefWidth(160);
        exercise.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getExerciseName()));

        TableColumn<PersonalRecord, String> type = new TableColumn<>("Type");
        type.setPrefWidth(140);
        type.setCellValueFactory(c -> new SimpleStringProperty(labelType(c.getValue().getPrType())));

        TableColumn<PersonalRecord, String> detail = new TableColumn<>("Detail");
        detail.setPrefWidth(220);
        detail.setCellValueFactory(c -> new SimpleStringProperty(detailOf(c.getValue())));

        prTable.getColumns().addAll(List.of(when, exercise, type, detail));
        prTable.setPrefHeight(220);
        prTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        prTable.setPlaceholder(new Label("No PRs yet — log a strong set in a live workout."));
    }

    private void buildHistoryTable() {
        OneRepMaxFormula preferred = progress.preferredFormula();

        TableColumn<LoggedSet, String> when = new TableColumn<>("When");
        when.setPrefWidth(130);
        when.setCellValueFactory(c -> new SimpleStringProperty(WHEN.format(c.getValue().getLoggedAt())));

        TableColumn<LoggedSet, String> load = new TableColumn<>("Set");
        load.setPrefWidth(100);
        load.setCellValueFactory(c -> new SimpleStringProperty(
                "%.1f × %d".formatted(c.getValue().getWeightKg(), c.getValue().getReps())));

        TableColumn<LoggedSet, String> epley = new TableColumn<>("Epley");
        epley.setPrefWidth(80);
        epley.setCellValueFactory(c -> new SimpleStringProperty(fmt1rm(c.getValue().getEpley1rm(),
                preferred == OneRepMaxFormula.EPLEY, c.getValue().getReps())));

        TableColumn<LoggedSet, String> brzycki = new TableColumn<>("Brzycki");
        brzycki.setPrefWidth(80);
        brzycki.setCellValueFactory(c -> new SimpleStringProperty(fmt1rm(c.getValue().getBrzycki1rm(),
                preferred == OneRepMaxFormula.BRZYCKI, c.getValue().getReps())));

        TableColumn<LoggedSet, String> lombardi = new TableColumn<>("Lombardi");
        lombardi.setPrefWidth(80);
        lombardi.setCellValueFactory(c -> new SimpleStringProperty(fmt1rm(c.getValue().getLombardi1rm(),
                preferred == OneRepMaxFormula.LOMBARDI, c.getValue().getReps())));

        historyTable.getColumns().addAll(List.of(when, load, epley, brzycki, lombardi));
        historyTable.setPrefHeight(200);
        historyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        historyTable.setPlaceholder(new Label("Pick an exercise"));
    }

    private String fmt1rm(Double value, boolean preferred, int reps) {
        if (value == null) {
            return "—";
        }
        String text = "%.1f".formatted(value);
        if (preferred) {
            text = "★ " + text;
        }
        if (oneRm.estimateLessReliable(reps)) {
            text += " ?";
        }
        return text;
    }

    private static String labelType(PersonalRecordType type) {
        return switch (type) {
            case HEAVIEST_AT_REPS -> "Heaviest @ reps";
            case ESTIMATED_1RM -> "Est. 1RM";
            case VOLUME -> "Volume";
        };
    }

    private static String detailOf(PersonalRecord record) {
        if (record.getPrType() == PersonalRecordType.HEAVIEST_AT_REPS) {
            return "%.1f kg × %d".formatted(record.getWeightKg(), record.getReps())
                    + (record.getEstimated1rm() == null ? "" :
                    "  (est 1RM %.1f %s)".formatted(record.getEstimated1rm(),
                            record.getFormula() == null ? "" : record.getFormula().name()));
        }
        if (record.getPrType() == PersonalRecordType.ESTIMATED_1RM) {
            return "%.1f kg est. (%s from %.1f × %d)".formatted(
                    record.getEstimated1rm(),
                    record.getFormula() == null ? "?" : record.getFormula().name(),
                    record.getWeightKg(),
                    record.getReps());
        }
        return "—";
    }

    private static String formatTrend(DashboardService.WeightTrend trend) {
        if (trend.latest() == null) {
            return "No weight logged yet.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Latest: %.1f kg".formatted(trend.latest().getWeightKg()));
        if (trend.deltaKg() != null) {
            sb.append("  ·  Δ %.1f kg vs previous".formatted(trend.deltaKg()));
        }
        return sb.toString();
    }
}
