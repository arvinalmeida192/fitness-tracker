package com.fittrack.ui.nutrition;

import com.fittrack.app.AppContext;
import com.fittrack.domain.common.Macros;
import com.fittrack.domain.common.MealType;
import com.fittrack.domain.common.ValidationException;
import com.fittrack.domain.nutrition.Dish;
import com.fittrack.domain.nutrition.Ingredient;
import com.fittrack.domain.nutrition.MealLog;
import com.fittrack.service.DishService;
import com.fittrack.service.MealService;
import com.fittrack.service.NutritionService;
import com.fittrack.ui.common.SearchableComboBox;
import com.fittrack.ui.common.UiSupport;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
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

public final class MealDiaryView {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    private final MealService meals = AppContext.mealService();
    private final DishService dishes = AppContext.dishService();
    private final NutritionService nutrition = AppContext.nutritionService();

    private final TableView<MealLog> table = new TableView<>();
    private final Label totals = new Label();
    private final Label error = UiSupport.errorLabel();

    private final SearchableComboBox<Dish> dishPick = new SearchableComboBox<>(Dish::getName);
    private final SearchableComboBox<Ingredient> ingredientPick =
            new SearchableComboBox<>(Ingredient::getName);
    private final ComboBox<MealType> mealTypePick = new ComboBox<>();
    private final TextField portionField = new TextField();
    private final TextField adHocGrams = new TextField();
    private final TextField notesField = new TextField();

    public VBox getRoot() {
        mealTypePick.setItems(FXCollections.observableArrayList(MealType.values()));
        mealTypePick.getSelectionModel().select(MealType.LUNCH);
        dishPick.setSearchPrompt("Search dishes…");
        dishPick.setComboPrefWidth(220);
        dishPick.setItems(dishes.list());
        dishPick.selectFirstIfAny();
        ingredientPick.setSearchPrompt("Search ingredients…");
        ingredientPick.setComboPrefWidth(220);
        ingredientPick.setItems(nutrition.listCached(200));
        ingredientPick.selectFirstIfAny();
        portionField.setPromptText("portion g");
        portionField.setPrefWidth(100);
        adHocGrams.setPromptText("grams");
        adHocGrams.setPrefWidth(90);
        notesField.setPromptText("notes (optional)");

        buildTable();
        refresh();

        Button logDish = new Button("Log dish portion");
        logDish.setOnAction(e -> logDish());
        Button logIngredient = new Button("Log ingredient");
        logIngredient.setOnAction(e -> logIngredient());
        Button delete = new Button("Delete selected");
        delete.setOnAction(e -> deleteSelected());
        Button refresh = new Button("Refresh");
        refresh.setOnAction(e -> refresh());

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);
        form.addRow(0, new Label("Meal type"), mealTypePick, new Label("Notes"), notesField);
        form.add(new Label("Dish"), 0, 1);
        form.add(dishPick.asInlineRow(), 1, 1, 3, 1);
        form.add(new Label("Portion g"), 4, 1);
        form.add(portionField, 5, 1);
        form.add(logDish, 6, 1);
        form.add(new Label("Ingredient"), 0, 2);
        form.add(ingredientPick.asInlineRow(), 1, 2, 3, 1);
        form.add(new Label("Grams"), 4, 2);
        form.add(adHocGrams, 5, 2);
        form.add(logIngredient, 6, 2);

        HBox actions = new HBox(8, delete, refresh);

        VBox root = new VBox(12,
                new Label("Today's meals — portion macros = batch × (portionG / basisWeight). Example: 373 g of a 1000 g batch → 37.3%."),
                form,
                actions,
                error,
                totals,
                table
        );
        root.setPadding(new Insets(8, 24, 24, 24));
        VBox.setVgrow(table, Priority.ALWAYS);
        return root;
    }

    private void buildTable() {
        TableColumn<MealLog, String> time = new TableColumn<>("Time");
        time.setPrefWidth(70);
        time.setCellValueFactory(c -> new SimpleStringProperty(TIME.format(c.getValue().getEatenAt())));

        TableColumn<MealLog, String> type = new TableColumn<>("Type");
        type.setPrefWidth(90);
        type.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getMealType().name()));

        TableColumn<MealLog, String> what = new TableColumn<>("What");
        what.setPrefWidth(200);
        what.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().displayLabel()));

        TableColumn<MealLog, String> portion = new TableColumn<>("g");
        portion.setPrefWidth(70);
        portion.setCellValueFactory(c -> new SimpleStringProperty("%.0f".formatted(c.getValue().getPortionG())));

        TableColumn<MealLog, String> macros = new TableColumn<>("Macros");
        macros.setPrefWidth(220);
        macros.setCellValueFactory(c -> new SimpleStringProperty(fmt(c.getValue().getMacros())));

        table.getColumns().addAll(List.of(time, type, what, portion, macros));
        table.setPrefHeight(260);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No meals logged today"));
    }

    private void refresh() {
        List<MealLog> logs = meals.todaysMeals();
        table.setItems(FXCollections.observableArrayList(logs));
        Macros total = meals.todaysTotals();
        totals.setText("Today total: " + fmt(total) + "  ·  " + logs.size() + " entries");
        dishPick.setItems(dishes.list());
        ingredientPick.setItems(nutrition.listCached(200));
    }

    private void logDish() {
        UiSupport.clearError(error);
        try {
            Dish dish = dishPick.getValue();
            if (dish == null) {
                throw new ValidationException("Select a dish");
            }
            double portion = UiSupport.parsePositiveDouble(portionField.getText(), "portion (g)");
            meals.logDish(dish.getId(), portion, mealTypePick.getValue(), notesField.getText());
            portionField.clear();
            refresh();
        } catch (ValidationException | IllegalArgumentException ex) {
            UiSupport.showError(error, ex.getMessage());
        }
    }

    private void logIngredient() {
        UiSupport.clearError(error);
        try {
            Ingredient ingredient = ingredientPick.getValue();
            if (ingredient == null) {
                throw new ValidationException("Select a cached ingredient");
            }
            double grams = UiSupport.parsePositiveDouble(adHocGrams.getText(), "grams");
            meals.logIngredient(ingredient.getId(), grams, mealTypePick.getValue(), notesField.getText());
            adHocGrams.clear();
            refresh();
        } catch (ValidationException | IllegalArgumentException ex) {
            UiSupport.showError(error, ex.getMessage());
        }
    }

    private void deleteSelected() {
        UiSupport.clearError(error);
        MealLog selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiSupport.showError(error, "Select a meal to delete");
            return;
        }
        meals.delete(selected.getId());
        refresh();
    }

    private static String fmt(Macros m) {
        return "%.0f kcal · P %.1fg · C %.1fg · F %.1fg".formatted(
                m.getKcal(), m.getProteinG(), m.getCarbsG(), m.getFatG());
    }
}
