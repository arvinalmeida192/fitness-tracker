package com.fittrack.ui.plans;

import com.fittrack.app.AppContext;
import com.fittrack.domain.common.MuscleGroup;
import com.fittrack.domain.common.ValidationException;
import com.fittrack.domain.exercise.Exercise;
import com.fittrack.domain.workout.PlanItem;
import com.fittrack.domain.workout.WorkoutPlan;
import com.fittrack.service.MuscleVolumeService;
import com.fittrack.service.WorkoutPlanService;
import com.fittrack.ui.common.SearchableComboBox;
import com.fittrack.ui.common.Theme;
import com.fittrack.ui.common.UiSupport;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

public final class PlanEditorView {

    private final WorkoutPlan plan;
    private final Runnable onBack;
    private final WorkoutPlanService plans = AppContext.workoutPlanService();
    private final MuscleVolumeService muscleVolume = AppContext.muscleVolumeService();
    private final ObservableList<PlanItem> items = FXCollections.observableArrayList();
    private final TableView<PlanItem> table = new TableView<>(items);
    private final Label error = UiSupport.errorLabel();
    private final Label ok = new Label();
    private final Label volumeLabel = new Label();
    private final Label volumeHint = new Label(
            "Muscle volume for this plan: primary muscle = 1.0 set, secondary = 0.5 set.");

    private final TextField nameField = new TextField();
    private final TextArea descriptionArea = new TextArea();

    private final SearchableComboBox<Exercise> exercisePicker =
            new SearchableComboBox<>(Exercise::getName);
    private final ComboBox<MuscleGroup> muscleFilter = new ComboBox<>();
    private final TextField setsField = new TextField("3");
    private final TextField repsMinField = new TextField("8");
    private final TextField repsMaxField = new TextField("12");
    private final TextField weightField = new TextField();
    private final TextField restField = new TextField();
    private final TextField notesField = new TextField();

    public PlanEditorView(WorkoutPlan plan, Runnable onBack) {
        this.plan = plan;
        this.onBack = onBack;
    }

    public ScrollPane getRoot() {
        ok.getStyleClass().add("success");
        volumeHint.getStyleClass().add("hint");
        volumeHint.setWrapText(true);
        volumeLabel.getStyleClass().add("body-text");
        volumeLabel.setWrapText(true);
        nameField.setText(plan.getName());
        nameField.setPromptText("Plan name");
        descriptionArea.setText(plan.getDescription() == null ? "" : plan.getDescription());
        descriptionArea.setPromptText("Plan description (optional)");
        descriptionArea.setPrefRowCount(2);
        descriptionArea.setWrapText(true);

        setsField.setPromptText("Sets");
        repsMinField.setPromptText("Reps min");
        repsMaxField.setPromptText("Reps max");
        weightField.setPromptText("Target kg");
        restField.setPromptText("Rest sec");
        notesField.setPromptText("Notes (optional)");

        exercisePicker.setSearchPrompt("Search exercises by name…");
        exercisePicker.setComboPrefWidth(320);
        exercisePicker.setItems(AppContext.exerciseService().listAll());
        exercisePicker.selectFirstIfAny();

        muscleFilter.getItems().add(null);
        muscleFilter.getItems().addAll(MuscleGroup.values());
        muscleFilter.setPromptText("All muscles");
        muscleFilter.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(MuscleGroup object) {
                return object == null ? "All muscles" : object.name();
            }

            @Override
            public MuscleGroup fromString(String string) {
                return null;
            }
        });
        muscleFilter.valueProperty().addListener((o, a, muscle) -> {
            exercisePicker.setExtraFilter(ex -> muscle == null || ex.targetsMuscle(muscle));
        });

        items.setAll(plan.getItems());
        buildTable();
        refreshVolume();

        Button saveMeta = new Button("Save details");
        saveMeta.setOnAction(e -> saveDetails());

        Button addItem = new Button("Add exercise");
        addItem.setOnAction(e -> addItem());

        Button moveUp = new Button("Move up");
        moveUp.setOnAction(e -> moveSelected(-1));

        Button moveDown = new Button("Move down");
        moveDown.setOnAction(e -> moveSelected(1));

        Button remove = new Button("Remove");
        remove.setOnAction(e -> removeSelected());

        Button saveItems = new Button("Save exercises");
        saveItems.setDefaultButton(true);
        Theme.primary(saveItems);
        saveItems.setOnAction(e -> saveItems());

        Button back = new Button("Back to plans");
        back.setOnAction(e -> onBack.run());

        GridPane meta = new GridPane();
        meta.setHgap(10);
        meta.setVgap(8);
        meta.addRow(0, new Label("Name"), nameField);
        meta.addRow(1, new Label("Description"), descriptionArea);

        HBox muscleRow = new HBox(8, new Label("Muscle filter"), muscleFilter);
        muscleRow.setAlignment(Pos.CENTER_LEFT);
        VBox exercisePickBlock = exercisePicker.asLabeledBlock("Exercise");

        GridPane addForm = new GridPane();
        addForm.setHgap(8);
        addForm.setVgap(6);
        addForm.add(muscleRow, 0, 0, 6, 1);
        addForm.add(exercisePickBlock, 0, 1, 6, 1);
        addForm.addRow(2, new Label("Sets"), setsField, new Label("Reps min"), repsMinField, new Label("max"), repsMaxField);
        addForm.addRow(3, new Label("Target kg"), weightField, new Label("Rest sec"), restField);
        addForm.addRow(4, new Label("Notes"), notesField);

        HBox itemActions = new HBox(8, addItem, moveUp, moveDown, remove, saveItems);
        HBox topActions = new HBox(8, saveMeta, back);

        Label volumeTitle = new Label("Planned muscle sets");
        volumeTitle.getStyleClass().add("section-title");

        VBox page = UiSupport.page(
                "Edit plan",
                meta,
                topActions,
                new Label("Exercises (order = workout order)"),
                table,
                volumeTitle,
                volumeHint,
                volumeLabel,
                new Label("Add exercise"),
                addForm,
                itemActions,
                error,
                ok
        );
        VBox.setVgrow(table, Priority.ALWAYS);
        return UiSupport.scroll(page);
    }

    private void buildTable() {
        TableColumn<PlanItem, String> order = new TableColumn<>("#");
        order.setPrefWidth(40);
        order.setCellValueFactory(c -> new SimpleStringProperty(
                Integer.toString(items.indexOf(c.getValue()) + 1)));

        TableColumn<PlanItem, String> name = new TableColumn<>("Exercise");
        name.setPrefWidth(180);
        name.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getExerciseName()));

        TableColumn<PlanItem, String> sets = new TableColumn<>("Sets");
        sets.setPrefWidth(60);
        sets.setCellValueFactory(c -> new SimpleStringProperty(Integer.toString(c.getValue().getTargetSets())));

        TableColumn<PlanItem, String> reps = new TableColumn<>("Reps");
        reps.setPrefWidth(70);
        reps.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().repsLabel()));

        TableColumn<PlanItem, String> weight = new TableColumn<>("kg");
        weight.setPrefWidth(70);
        weight.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getTargetWeight() == null ? "" : "%.1f".formatted(c.getValue().getTargetWeight())));

        TableColumn<PlanItem, String> rest = new TableColumn<>("Rest");
        rest.setPrefWidth(70);
        rest.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getRestSec() == null ? "" : c.getValue().getRestSec() + "s"));

        table.getColumns().add(order);
        table.getColumns().add(name);
        table.getColumns().add(sets);
        table.getColumns().add(reps);
        table.getColumns().add(weight);
        table.getColumns().add(rest);
        table.setPrefHeight(240);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private void saveDetails() {
        clearMessages();
        try {
            WorkoutPlan updated = plans.updateMeta(plan.getId(), nameField.getText(), descriptionArea.getText());
            plan.setName(updated.getName());
            plan.setDescription(updated.getDescription());
            ok.setText("Plan details saved.");
        } catch (ValidationException ex) {
            UiSupport.showError(error, ex.getMessage());
        } catch (Exception ex) {
            UiSupport.showError(error, "Save failed: " + ex.getMessage());
        }
    }

    private void addItem() {
        clearMessages();
        try {
            Exercise exercise = exercisePicker.getValue();
            if (exercise == null) {
                throw new ValidationException("Search and choose an exercise");
            }
            PlanItem item = new PlanItem();
            item.setExerciseId(exercise.getId());
            item.setExerciseName(exercise.getName());
            item.setTargetSets(parseInt(setsField.getText(), "sets"));
            item.setRepsMin(parseInt(repsMinField.getText(), "reps min"));
            item.setRepsMax(parseInt(repsMaxField.getText(), "reps max"));
            item.setTargetWeight(parseOptionalDouble(weightField.getText(), "target weight"));
            Integer rest = parseOptionalInt(restField.getText(), "rest");
            item.setRestSec(rest == null ? exercise.getDefaultRestSec() : rest);
            item.setNotes(notesField.getText());
            items.add(item);
            table.refresh();
            refreshVolume();
            notesField.clear();
        } catch (ValidationException | IllegalArgumentException ex) {
            UiSupport.showError(error, ex.getMessage());
        }
    }

    private void moveSelected(int delta) {
        clearMessages();
        int index = table.getSelectionModel().getSelectedIndex();
        if (index < 0) {
            UiSupport.showError(error, "Select an exercise row");
            return;
        }
        int target = index + delta;
        if (target < 0 || target >= items.size()) {
            return;
        }
        PlanItem item = items.remove(index);
        items.add(target, item);
        table.getSelectionModel().select(target);
        table.refresh();
        refreshVolume();
    }

    private void removeSelected() {
        clearMessages();
        int index = table.getSelectionModel().getSelectedIndex();
        if (index < 0) {
            UiSupport.showError(error, "Select an exercise row");
            return;
        }
        items.remove(index);
        table.refresh();
        refreshVolume();
    }

    private void saveItems() {
        clearMessages();
        try {
            List<PlanItem> snapshot = new ArrayList<>(items);
            WorkoutPlan saved = plans.saveItems(plan.getId(), snapshot);
            items.setAll(saved.getItems());
            table.refresh();
            refreshVolume();
            ok.setText("Exercises saved (" + saved.itemCount() + ").");
        } catch (ValidationException ex) {
            UiSupport.showError(error, ex.getMessage());
        } catch (Exception ex) {
            UiSupport.showError(error, "Could not save exercises: " + ex.getMessage());
        }
    }

    private void refreshVolume() {
        volumeLabel.setText(com.fittrack.domain.exercise.MuscleSetVolume.formatLine(
                muscleVolume.plannedVolume(new ArrayList<>(items))));
    }

    private void clearMessages() {
        UiSupport.clearError(error);
        ok.setText("");
    }

    private static int parseInt(String raw, String field) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Enter a valid " + field);
        }
    }

    private static Integer parseOptionalInt(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return parseInt(raw, field);
    }

    private static Double parseOptionalDouble(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
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
}
