package com.fittrack.ui.plans;

import com.fittrack.app.AppContext;
import com.fittrack.domain.common.ValidationException;
import com.fittrack.domain.workout.WorkoutPlan;
import com.fittrack.service.WorkoutPlanService;
import com.fittrack.service.WorkoutSessionService;
import com.fittrack.ui.common.UiSupport;
import com.fittrack.ui.live.LiveWorkoutView;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Optional;
import java.util.function.Consumer;

public final class PlanListView {

    private final Consumer<NodeSwap> navigator;
    private final WorkoutPlanService plans = AppContext.workoutPlanService();
    private final WorkoutSessionService sessions = AppContext.workoutSessionService();
    private final TableView<WorkoutPlan> table = new TableView<>();
    private final CheckBox showArchived = new CheckBox("Show archived");
    private final Label error = UiSupport.errorLabel();
    private final TextField nameField = new TextField();

    public record NodeSwap(javafx.scene.Node node) {
    }

    public PlanListView(Consumer<NodeSwap> navigator) {
        this.navigator = navigator;
    }

    public VBox getRoot() {
        buildTable();
        nameField.setPromptText("New plan name");

        Button create = new Button("Create plan");
        create.setDefaultButton(true);
        create.setOnAction(e -> createPlan());

        Button edit = new Button("Edit");
        edit.setOnAction(e -> openSelected());

        Button start = new Button("Start workout");
        start.setOnAction(e -> startSelected());

        Button archive = new Button("Archive / Restore");
        archive.setOnAction(e -> toggleArchive());

        Button delete = new Button("Delete");
        delete.setOnAction(e -> deleteSelected());

        showArchived.selectedProperty().addListener((o, a, b) -> refresh());

        table.setRowFactory(tv -> {
            var row = new javafx.scene.control.TableRow<WorkoutPlan>();
            row.setOnMouseClicked(ev -> {
                if (ev.getClickCount() == 2 && !row.isEmpty()) {
                    openEditor(row.getItem());
                }
            });
            return row;
        });

        HBox createRow = new HBox(8, nameField, create);
        HBox.setHgrow(nameField, Priority.ALWAYS);
        HBox actions = new HBox(8, start, edit, archive, delete, showArchived);

        VBox root = UiSupport.page(
                "Workout plans",
                new Label("Start a workout from a plan, or double-click to edit exercises."),
                createRow,
                actions,
                error,
                table
        );
        root.setPadding(new Insets(20));
        VBox.setVgrow(table, Priority.ALWAYS);
        refresh();
        return root;
    }

    private void buildTable() {
        TableColumn<WorkoutPlan, String> name = new TableColumn<>("Name");
        name.setPrefWidth(220);
        name.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));

        TableColumn<WorkoutPlan, String> items = new TableColumn<>("Exercises");
        items.setPrefWidth(90);
        items.setCellValueFactory(c -> new SimpleStringProperty(Integer.toString(c.getValue().itemCount())));

        TableColumn<WorkoutPlan, String> desc = new TableColumn<>("Description");
        desc.setPrefWidth(280);
        desc.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getDescription() == null ? "" : c.getValue().getDescription()));

        TableColumn<WorkoutPlan, String> status = new TableColumn<>("Status");
        status.setPrefWidth(90);
        status.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().isArchived() ? "Archived" : "Active"));

        table.getColumns().add(name);
        table.getColumns().add(items);
        table.getColumns().add(desc);
        table.getColumns().add(status);
        table.setPrefHeight(320);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private void refresh() {
        table.setItems(FXCollections.observableArrayList(plans.listPlans(showArchived.isSelected())));
    }

    private void createPlan() {
        UiSupport.clearError(error);
        try {
            WorkoutPlan plan = plans.create(nameField.getText(), null);
            nameField.clear();
            openEditor(plan);
        } catch (ValidationException ex) {
            UiSupport.showError(error, ex.getMessage());
        } catch (Exception ex) {
            UiSupport.showError(error, "Could not create plan: " + ex.getMessage());
        }
    }

    private void openSelected() {
        WorkoutPlan selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiSupport.showError(error, "Select a plan first");
            return;
        }
        openEditor(selected);
    }

    private void openEditor(WorkoutPlan plan) {
        WorkoutPlan fresh = plans.getPlan(plan.getId());
        navigator.accept(new NodeSwap(new PlanEditorView(fresh, () ->
                navigator.accept(new NodeSwap(new PlanListView(navigator).getRoot()))
        ).getRoot()));
    }

    private void startSelected() {
        UiSupport.clearError(error);
        WorkoutPlan selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiSupport.showError(error, "Select a plan to start");
            return;
        }
        if (selected.isArchived()) {
            UiSupport.showError(error, "Restore the plan before starting it");
            return;
        }
        try {
            sessions.startFromPlan(selected.getId());
            navigator.accept(new NodeSwap(new LiveWorkoutView(() ->
                    navigator.accept(new NodeSwap(new PlanListView(navigator).getRoot()))
            ).getRoot()));
        } catch (ValidationException ex) {
            UiSupport.showError(error, ex.getMessage());
        } catch (Exception ex) {
            UiSupport.showError(error, "Could not start workout: " + ex.getMessage());
        }
    }

    private void toggleArchive() {
        UiSupport.clearError(error);
        WorkoutPlan selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiSupport.showError(error, "Select a plan");
            return;
        }
        try {
            plans.setArchived(selected.getId(), !selected.isArchived());
            refresh();
        } catch (Exception ex) {
            UiSupport.showError(error, ex.getMessage());
        }
    }

    private void deleteSelected() {
        UiSupport.clearError(error);
        WorkoutPlan selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiSupport.showError(error, "Select a plan to delete");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete \"" + selected.getName() + "\" permanently?",
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText(null);
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }
        try {
            plans.delete(selected.getId());
            refresh();
        } catch (Exception ex) {
            UiSupport.showError(error, "Delete failed: " + ex.getMessage());
        }
    }
}
