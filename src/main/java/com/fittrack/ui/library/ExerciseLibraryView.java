package com.fittrack.ui.library;

import com.fittrack.app.AppContext;
import com.fittrack.domain.common.MuscleGroup;
import com.fittrack.domain.common.ValidationException;
import com.fittrack.domain.exercise.Exercise;
import com.fittrack.service.ExerciseService;
import com.fittrack.ui.common.UiSupport;
import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;
import java.util.Optional;

public final class ExerciseLibraryView {

    private final ExerciseService exercises = AppContext.exerciseService();
    private final TableView<Exercise> table = new TableView<>();
    private final Label summary = new Label();
    private final Label error = UiSupport.errorLabel();

    private final TextField nameField = new TextField();
    private final ComboBox<MuscleGroup> muscleBox = new ComboBox<>();
    private final TextField equipmentField = new TextField();
    private final TextField secondaryField = new TextField();
    private final TextField restField = new TextField("90");
    private final TextArea instructionsArea = new TextArea();
    private Long editingId;

    private final TextField searchField = new TextField();
    private final ComboBox<MuscleGroup> filterMuscle = new ComboBox<>();
    private final PauseTransition searchDebounce = new PauseTransition(Duration.millis(120));

    public VBox getRoot() {
        summary.getStyleClass().add("hint");
        buildTable();

        searchField.setPromptText("Search name or equipment");
        filterMuscle.getItems().add(null);
        filterMuscle.getItems().addAll(MuscleGroup.values());
        filterMuscle.setPromptText("All muscles");
        filterMuscle.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(MuscleGroup object) {
                return object == null ? "All muscles" : object.name();
            }

            @Override
            public MuscleGroup fromString(String string) {
                return null;
            }
        });

        searchDebounce.setOnFinished(e -> refreshTable());
        searchField.textProperty().addListener((o, a, b) -> searchDebounce.playFromStart());
        filterMuscle.valueProperty().addListener((o, a, b) -> refreshTable());

        Button clearFilter = new Button("Clear");
        clearFilter.setOnAction(e -> {
            searchDebounce.stop();
            searchField.clear();
            filterMuscle.getSelectionModel().clearSelection();
            refreshTable();
        });

        HBox filters = new HBox(8, searchField, filterMuscle, clearFilter);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        muscleBox.getItems().addAll(MuscleGroup.values());
        muscleBox.getSelectionModel().select(MuscleGroup.OTHER);
        muscleBox.setPromptText("Primary muscle");
        instructionsArea.setPrefRowCount(3);
        instructionsArea.setWrapText(true);
        instructionsArea.setPromptText("Instructions (optional)");
        nameField.setPromptText("Exercise name");
        equipmentField.setPromptText("Equipment (optional)");
        restField.setPromptText("Rest (sec)");
        secondaryField.setPromptText("Secondary muscles, e.g. TRICEPS, SHOULDERS");

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);
        form.addRow(0, new Label("Name"), nameField);
        form.addRow(1, new Label("Primary muscle"), muscleBox);
        form.addRow(2, new Label("Secondary (comma)"), secondaryField);
        form.addRow(3, new Label("Equipment"), equipmentField);
        form.addRow(4, new Label("Rest (sec)"), restField);
        form.addRow(5, new Label("Instructions"), instructionsArea);

        Label secondaryHint = new Label("Secondary example: TRICEPS, SHOULDERS (counts as 0.5 set each)");
        secondaryHint.getStyleClass().add("micro");

        Button save = new Button("Save");
        save.setDefaultButton(true);
        save.setOnAction(e -> saveExercise());

        Button newBtn = new Button("New");
        newBtn.setOnAction(e -> clearForm());

        Button delete = new Button("Delete");
        delete.setOnAction(e -> deleteSelected());

        HBox actions = new HBox(8, save, newBtn, delete);

        table.getSelectionModel().selectedItemProperty().addListener((obs, oldV, selected) -> {
            if (selected != null) {
                loadIntoForm(selected);
            }
        });

        VBox root = UiSupport.page(
                "Exercise library",
                summary,
                filters,
                table,
                new Label("Add / edit"),
                form,
                secondaryHint,
                error,
                actions
        );
        VBox.setVgrow(table, Priority.ALWAYS);
        refreshTable();
        return root;
    }

    private void buildTable() {
        TableColumn<Exercise, String> name = new TableColumn<>("Name");
        name.setPrefWidth(200);
        name.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));

        TableColumn<Exercise, String> muscle = new TableColumn<>("Primary");
        muscle.setPrefWidth(110);
        muscle.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getMuscleGroup().name()));

        TableColumn<Exercise, String> secondary = new TableColumn<>("Secondary");
        secondary.setPrefWidth(160);
        secondary.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().secondaryLabel()));

        TableColumn<Exercise, String> equipment = new TableColumn<>("Equipment");
        equipment.setPrefWidth(120);
        equipment.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getEquipment() == null ? "" : c.getValue().getEquipment()));

        TableColumn<Exercise, String> rest = new TableColumn<>("Rest");
        rest.setPrefWidth(70);
        rest.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDefaultRestSec() + "s"));

        table.getColumns().add(name);
        table.getColumns().add(muscle);
        table.getColumns().add(secondary);
        table.getColumns().add(equipment);
        table.getColumns().add(rest);
        table.setPrefHeight(280);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private void refreshTable() {
        List<Exercise> visible = exercises.search(searchField.getText(), filterMuscle.getValue());
        table.setItems(FXCollections.observableArrayList(visible));
        summary.setText(exercises.summaryLabel(visible));
    }

    private void loadIntoForm(Exercise exercise) {
        editingId = exercise.getId();
        nameField.setText(exercise.getName());
        muscleBox.getSelectionModel().select(exercise.getMuscleGroup());
        secondaryField.setText(exercise.secondaryLabel());
        equipmentField.setText(exercise.getEquipment() == null ? "" : exercise.getEquipment());
        restField.setText(Integer.toString(exercise.getDefaultRestSec()));
        instructionsArea.setText(exercise.getInstructions() == null ? "" : exercise.getInstructions());
        UiSupport.clearError(error);
    }

    private void clearForm() {
        editingId = null;
        table.getSelectionModel().clearSelection();
        nameField.clear();
        muscleBox.getSelectionModel().select(MuscleGroup.OTHER);
        secondaryField.clear();
        equipmentField.clear();
        restField.setText("90");
        instructionsArea.clear();
        UiSupport.clearError(error);
    }

    private void saveExercise() {
        UiSupport.clearError(error);
        try {
            int rest = Integer.parseInt(restField.getText().trim());
            var secondary = ExerciseService.parseMuscleList(secondaryField.getText());
            if (editingId == null) {
                exercises.create(
                        nameField.getText(),
                        muscleBox.getValue(),
                        equipmentField.getText(),
                        instructionsArea.getText(),
                        rest,
                        secondary
                );
            } else {
                exercises.update(
                        editingId,
                        nameField.getText(),
                        muscleBox.getValue(),
                        equipmentField.getText(),
                        instructionsArea.getText(),
                        rest,
                        secondary
                );
            }
            clearForm();
            refreshTable();
        } catch (NumberFormatException ex) {
            UiSupport.showError(error, "Rest seconds must be a whole number");
        } catch (ValidationException | IllegalArgumentException ex) {
            UiSupport.showError(error, ex.getMessage());
        } catch (Exception ex) {
            UiSupport.showError(error, "Save failed: " + ex.getMessage());
        }
    }

    private void deleteSelected() {
        UiSupport.clearError(error);
        Exercise selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiSupport.showError(error, "Select an exercise to delete");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete \"" + selected.getName() + "\"?",
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText(null);
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }
        try {
            exercises.delete(selected.getId());
            clearForm();
            refreshTable();
        } catch (Exception ex) {
            UiSupport.showError(error, "Delete failed: " + ex.getMessage());
        }
    }
}
