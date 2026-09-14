package com.fittrack.ui.live;

import com.fittrack.app.AppContext;
import com.fittrack.domain.common.OneRepMaxFormula;
import com.fittrack.domain.common.PersonalRecordType;
import com.fittrack.domain.common.ValidationException;
import com.fittrack.domain.progress.PersonalRecord;
import com.fittrack.domain.workout.LoggedSet;
import com.fittrack.domain.workout.PlanItem;
import com.fittrack.service.ActiveWorkout;
import com.fittrack.service.LogSetResult;
import com.fittrack.service.OneRepMaxService;
import com.fittrack.service.WorkoutSessionService;
import com.fittrack.ui.common.Theme;
import com.fittrack.ui.common.UiSupport;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.Optional;

public final class LiveWorkoutView {

    private final Runnable onFinished;
    private final WorkoutSessionService sessions = AppContext.workoutSessionService();
    private final OneRepMaxService oneRm = AppContext.oneRepMaxService();

    private final Label title = new Label();
    private final Label exerciseLabel = new Label();
    private final Label setLabel = new Label();
    private final Label targetLabel = new Label();
    private final Label previousLabel = new Label();
    private final Label timerLabel = new Label("Rest: —");
    private final Label statusLabel = new Label();
    private final Label error = UiSupport.errorLabel();

    private final TextField weightField = new TextField();
    private final TextField repsField = new TextField();
    private final TextField rpeField = new TextField();
    private final TableView<LoggedSet> logTable = new TableView<>();

    private Timeline restTimeline;
    private int restSecondsLeft;

    public LiveWorkoutView(Runnable onFinished) {
        this.onFinished = onFinished;
    }

    public VBox getRoot() {
        ActiveWorkout workout = sessions.current()
                .orElseThrow(() -> new IllegalStateException("No active workout"));

        title.getStyleClass().add("page-title");
        title.setText(workout.getPlanName());
        exerciseLabel.getStyleClass().add("exercise-title");
        setLabel.getStyleClass().add("body-text");
        targetLabel.getStyleClass().add("muted");
        previousLabel.getStyleClass().add("muted");
        timerLabel.getStyleClass().add("timer");
        statusLabel.getStyleClass().add("success");

        weightField.setPromptText("kg");
        repsField.setPromptText("reps");
        rpeField.setPromptText("RPE (opt)");
        weightField.setPrefWidth(90);
        repsField.setPrefWidth(70);
        rpeField.setPrefWidth(80);

        buildLogTable();

        Button logSet = new Button("Log set");
        logSet.setDefaultButton(true);
        Theme.primary(logSet);
        logSet.setOnAction(e -> onLogSet());

        Button skip = new Button("Skip exercise");
        skip.setOnAction(e -> onSkip());

        Button skipRest = new Button("Skip rest");
        skipRest.setOnAction(e -> stopRestTimer());

        Button finish = new Button("Finish workout");
        finish.setOnAction(e -> onFinish());

        GridPane inputs = new GridPane();
        inputs.setHgap(10);
        inputs.setVgap(8);
        inputs.addRow(0, new Label("Weight"), weightField, new Label("Reps"), repsField, new Label("RPE"), rpeField);

        HBox actions = new HBox(10, logSet, skip, skipRest, finish);

        VBox root = UiSupport.page(
                "Live workout",
                title,
                exerciseLabel,
                setLabel,
                targetLabel,
                previousLabel,
                timerLabel,
                inputs,
                actions,
                error,
                statusLabel,
                new Label("Sets this session"),
                logTable
        );
        VBox.setVgrow(logTable, Priority.ALWAYS);
        refreshUi();
        return root;
    }

    private void buildLogTable() {
        TableColumn<LoggedSet, String> ex = new TableColumn<>("Exercise");
        ex.setPrefWidth(160);
        ex.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getExerciseNameSnapshot()));

        TableColumn<LoggedSet, String> set = new TableColumn<>("Set");
        set.setPrefWidth(50);
        set.setCellValueFactory(c -> new SimpleStringProperty(Integer.toString(c.getValue().getSetIndex())));

        TableColumn<LoggedSet, String> load = new TableColumn<>("kg");
        load.setPrefWidth(70);
        load.setCellValueFactory(c -> new SimpleStringProperty("%.1f".formatted(c.getValue().getWeightKg())));

        TableColumn<LoggedSet, String> reps = new TableColumn<>("Reps");
        reps.setPrefWidth(60);
        reps.setCellValueFactory(c -> new SimpleStringProperty(Integer.toString(c.getValue().getReps())));

        TableColumn<LoggedSet, String> rpe = new TableColumn<>("RPE");
        rpe.setPrefWidth(50);
        rpe.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getRpe() == null ? "" : "%.1f".formatted(c.getValue().getRpe())));

        TableColumn<LoggedSet, String> est = new TableColumn<>("Est 1RM");
        est.setPrefWidth(90);
        est.setCellValueFactory(c -> new SimpleStringProperty(formatPreferred1rm(c.getValue())));

        logTable.getColumns().add(ex);
        logTable.getColumns().add(set);
        logTable.getColumns().add(load);
        logTable.getColumns().add(reps);
        logTable.getColumns().add(rpe);
        logTable.getColumns().add(est);
        logTable.setPrefHeight(200);
        logTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private void refreshUi() {
        Optional<ActiveWorkout> maybe = sessions.current();
        if (maybe.isEmpty() || maybe.get().isFinished()) {
            exerciseLabel.setText("Workout complete");
            setLabel.setText("");
            targetLabel.setText("Press Finish workout to save and exit.");
            previousLabel.setText("");
            return;
        }

        ActiveWorkout workout = maybe.get();
        PlanItem item = workout.currentItem();
        exerciseLabel.setText(item.getExerciseName());
        setLabel.setText("Set " + workout.getNextSetNumber() + " of " + item.getTargetSets()
                + "  ·  exercise " + (workout.getItemIndex() + 1) + "/" + workout.getItems().size());

        String target = "Target: " + item.getTargetSets() + " × " + item.repsLabel() + " reps";
        if (item.getTargetWeight() != null) {
            target += " @ " + item.getTargetWeight() + " kg";
        }
        if (item.getRestSec() != null) {
            target += "  ·  rest " + item.getRestSec() + "s";
        }
        targetLabel.setText(target);

        Optional<LoggedSet> prev = sessions.previousPerformance();
        Optional<LoggedSet> same = sessions.lastSetThisExercise();
        StringBuilder prevText = new StringBuilder("Previous: ");
        if (same.isPresent()) {
            LoggedSet s = same.get();
            prevText.append("this session set ").append(s.getSetIndex()).append(" = ")
                    .append("%.1f".formatted(s.getWeightKg())).append(" × ").append(s.getReps());
            weightField.setText(Double.toString(s.getWeightKg()));
            repsField.setText(Integer.toString(s.getReps()));
        } else if (prev.isPresent()) {
            LoggedSet s = prev.get();
            prevText.append("last time ").append("%.1f".formatted(s.getWeightKg()))
                    .append(" × ").append(s.getReps());
            weightField.setText(Double.toString(s.getWeightKg()));
            if (item.getRepsMin() == item.getRepsMax()) {
                repsField.setText(Integer.toString(item.getRepsMin()));
            } else {
                repsField.setText(Integer.toString(item.getRepsMin()));
            }
        } else {
            prevText.append("none yet");
            if (item.getTargetWeight() != null) {
                weightField.setText(Double.toString(item.getTargetWeight()));
            } else {
                weightField.clear();
            }
            repsField.setText(Integer.toString(item.getRepsMin()));
        }
        previousLabel.setText(prevText.toString());

        logTable.setItems(FXCollections.observableArrayList(workout.getSession().getSets()));
    }

    private void onLogSet() {
        UiSupport.clearError(error);
        statusLabel.setText("");
        try {
            ActiveWorkout before = sessions.current().orElseThrow();
            PlanItem item = before.currentItem();
            int restSec = item != null && item.getRestSec() != null ? item.getRestSec() : 0;

            double weight = parseWeight(weightField.getText());
            int reps = Integer.parseInt(repsField.getText().trim());
            Double rpe = null;
            if (rpeField.getText() != null && !rpeField.getText().isBlank()) {
                rpe = Double.parseDouble(rpeField.getText().trim());
            }

            LogSetResult result = sessions.logSet(weight, reps, rpe);
            LoggedSet logged = result.set();
            statusLabel.setText(statusFor(logged, result.newRecords()));
            refreshUi();

            ActiveWorkout after = sessions.current().orElse(null);
            if (after != null && !after.isFinished() && restSec > 0) {
                startRestTimer(restSec);
            } else if (after != null && after.isFinished()) {
                stopRestTimer();
                statusLabel.setText(statusLabel.getText() + "  ·  All planned sets done — finish when ready.");
            }
        } catch (NumberFormatException ex) {
            UiSupport.showError(error, "Enter valid numbers for weight/reps/RPE");
        } catch (ValidationException | IllegalArgumentException ex) {
            UiSupport.showError(error, ex.getMessage());
        } catch (Exception ex) {
            UiSupport.showError(error, "Could not log set: " + ex.getMessage());
        }
    }

    private String statusFor(LoggedSet logged, java.util.List<PersonalRecord> newRecords) {
        StringBuilder sb = new StringBuilder("Logged set ")
                .append(logged.getSetIndex())
                .append(" · ")
                .append(logged.getExerciseNameSnapshot());
        String est = formatPreferred1rm(logged);
        if (!est.isBlank()) {
            sb.append(" · est 1RM ").append(est);
            if (oneRm.estimateLessReliable(logged.getReps())) {
                sb.append(" (less reliable >10 reps)");
            }
        }
        if (!newRecords.isEmpty()) {
            sb.append(" · NEW PR: ");
            for (int i = 0; i < newRecords.size(); i++) {
                if (i > 0) {
                    sb.append("; ");
                }
                sb.append(prSummary(newRecords.get(i)));
            }
        }
        return sb.toString();
    }

    private String formatPreferred1rm(LoggedSet set) {
        OneRepMaxFormula preferred = AppContext.progressService().preferredFormula();
        Double value = oneRm.preferredEstimate(set, preferred);
        if (value == null) {
            return "";
        }
        return "%.1f kg (%s)".formatted(value, preferred.name());
    }

    private static String prSummary(PersonalRecord record) {
        if (record.getPrType() == PersonalRecordType.HEAVIEST_AT_REPS) {
            return "heaviest %d-rep (%.1f kg)".formatted(record.getReps(), record.getWeightKg());
        }
        if (record.getPrType() == PersonalRecordType.ESTIMATED_1RM) {
            return "est 1RM %.1f kg".formatted(record.getEstimated1rm());
        }
        return record.getPrType().name();
    }

    private static double parseWeight(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException("Enter weight (0 for bodyweight)");
        }
        double weight = Double.parseDouble(raw.trim());
        if (weight < 0) {
            throw new ValidationException("Weight cannot be negative");
        }
        return weight;
    }

    private void onSkip() {
        UiSupport.clearError(error);
        try {
            stopRestTimer();
            sessions.skipExercise();
            statusLabel.setText("Skipped exercise.");
            refreshUi();
        } catch (ValidationException ex) {
            UiSupport.showError(error, ex.getMessage());
        }
    }

    private void onFinish() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "End this workout and save all logged sets?",
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText(null);
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }
        try {
            stopRestTimer();
            var done = sessions.finish(null);
            statusLabel.setText("Saved " + done.getSets().size() + " sets.");
            onFinished.run();
        } catch (Exception ex) {
            UiSupport.showError(error, "Could not finish: " + ex.getMessage());
        }
    }

    private void startRestTimer(int seconds) {
        stopRestTimer();
        restSecondsLeft = seconds;
        timerLabel.setText("Rest: " + restSecondsLeft + "s");
        restTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            restSecondsLeft--;
            if (restSecondsLeft <= 0) {
                timerLabel.setText("Rest: done");
                stopRestTimer();
            } else {
                timerLabel.setText("Rest: " + restSecondsLeft + "s");
            }
        }));
        restTimeline.setCycleCount(seconds);
        restTimeline.play();
    }

    private void stopRestTimer() {
        if (restTimeline != null) {
            restTimeline.stop();
            restTimeline = null;
        }
        timerLabel.setText("Rest: —");
    }
}
