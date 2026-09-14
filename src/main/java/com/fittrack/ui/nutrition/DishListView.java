package com.fittrack.ui.nutrition;

import com.fittrack.app.AppContext;
import com.fittrack.domain.common.ValidationException;
import com.fittrack.domain.nutrition.Dish;
import com.fittrack.service.DishService;
import com.fittrack.ui.common.UiSupport;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Optional;
import java.util.function.Consumer;

public final class DishListView {

    private final Consumer<Node> navigator;
    private final DishService dishes = AppContext.dishService();
    private final TableView<Dish> table = new TableView<>();
    private final TextField nameField = new TextField();
    private final Label error = UiSupport.errorLabel();

    public DishListView(Consumer<Node> navigator) {
        this.navigator = navigator;
    }

    public VBox getRoot() {
        buildTable();
        nameField.setPromptText("New dish name");

        Button create = new Button("Create dish");
        create.setDefaultButton(true);
        create.setOnAction(e -> createDish());

        Button edit = new Button("Edit");
        edit.setOnAction(e -> openSelected());

        Button delete = new Button("Delete");
        delete.setOnAction(e -> deleteSelected());

        table.setRowFactory(tv -> {
            var row = new javafx.scene.control.TableRow<Dish>();
            row.setOnMouseClicked(ev -> {
                if (ev.getClickCount() == 2 && !row.isEmpty()) {
                    openEditor(row.getItem());
                }
            });
            return row;
        });

        HBox createRow = new HBox(8, nameField, create);
        HBox.setHgrow(nameField, Priority.ALWAYS);
        HBox actions = new HBox(8, edit, delete);

        VBox root = UiSupport.embed(
                new Label("Build recipes from cached ingredients. Portion math uses batch weight (or cooked yield)."),
                createRow,
                actions,
                error,
                table
        );
        VBox.setVgrow(table, Priority.ALWAYS);
        refresh();
        return root;
    }

    private void buildTable() {
        TableColumn<Dish, String> name = new TableColumn<>("Name");
        name.setPrefWidth(220);
        name.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));

        TableColumn<Dish, String> weight = new TableColumn<>("Batch g");
        weight.setPrefWidth(90);
        weight.setCellValueFactory(c -> new SimpleStringProperty("%.0f".formatted(c.getValue().getTotalWeightG())));

        TableColumn<Dish, String> yield = new TableColumn<>("Yield g");
        yield.setPrefWidth(90);
        yield.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getYieldWeightG() == null ? "—" : "%.0f".formatted(c.getValue().getYieldWeightG())));

        table.getColumns().addAll(java.util.List.of(name, weight, yield));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(280);
        table.setPlaceholder(new Label("No dishes yet — create one after caching ingredients"));
    }

    private void refresh() {
        table.setItems(FXCollections.observableArrayList(dishes.list()));
    }

    private void createDish() {
        UiSupport.clearError(error);
        try {
            Dish dish = dishes.create(nameField.getText(), null);
            nameField.clear();
            openEditor(dish);
        } catch (ValidationException ex) {
            UiSupport.showError(error, ex.getMessage());
        }
    }

    private void openSelected() {
        Dish selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiSupport.showError(error, "Select a dish");
            return;
        }
        openEditor(selected);
    }

    private void openEditor(Dish dish) {
        navigator.accept(new DishEditorView(dish.getId(), () -> navigator.accept(getRoot())).getRoot());
    }

    private void deleteSelected() {
        UiSupport.clearError(error);
        Dish selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiSupport.showError(error, "Select a dish");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete dish \"" + selected.getName() + "\"?",
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText(null);
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }
        try {
            dishes.delete(selected.getId());
            refresh();
        } catch (Exception ex) {
            UiSupport.showError(error, ex.getMessage());
        }
    }
}
