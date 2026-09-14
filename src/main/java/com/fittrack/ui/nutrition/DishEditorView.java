package com.fittrack.ui.nutrition;

import com.fittrack.app.AppContext;
import com.fittrack.domain.common.Macros;
import com.fittrack.domain.common.ValidationException;
import com.fittrack.domain.nutrition.Dish;
import com.fittrack.domain.nutrition.DishItem;
import com.fittrack.domain.nutrition.Ingredient;
import com.fittrack.domain.nutrition.PortionScaler;
import com.fittrack.service.DishService;
import com.fittrack.service.NutritionService;
import com.fittrack.ui.common.SearchableComboBox;
import com.fittrack.ui.common.UiSupport;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

public final class DishEditorView {

    private final long dishId;
    private final Runnable onBack;
    private final DishService dishes = AppContext.dishService();
    private final NutritionService nutrition = AppContext.nutritionService();

    private final TextField nameField = new TextField();
    private final TextField notesField = new TextField();
    private final TextField yieldField = new TextField();
    private final TextField amountField = new TextField();
    private final TextField portionPreviewField = new TextField();
    private final SearchableComboBox<Ingredient> ingredientPick =
            new SearchableComboBox<>(Ingredient::getName);
    private final ObservableList<DishItem> items = FXCollections.observableArrayList();
    private final TableView<DishItem> table = new TableView<>(items);
    private final Label totals = new Label();
    private final Label preview = new Label();
    private final Label error = UiSupport.errorLabel();

    public DishEditorView(long dishId, Runnable onBack) {
        this.dishId = dishId;
        this.onBack = onBack;
    }

    public VBox getRoot() {
        Dish dish = dishes.get(dishId);
        nameField.setText(dish.getName());
        notesField.setText(dish.getNotes() == null ? "" : dish.getNotes());
        yieldField.setPromptText("optional cooked yield g");
        if (dish.getYieldWeightG() != null) {
            yieldField.setText(Double.toString(dish.getYieldWeightG()));
        }
        items.setAll(dish.getItems());
        ingredientPick.setSearchPrompt("Search cached ingredients…");
        ingredientPick.setComboPrefWidth(280);
        ingredientPick.setItems(nutrition.listCached(200));
        ingredientPick.selectFirstIfAny();
        amountField.setPromptText("g");
        amountField.setPrefWidth(80);
        portionPreviewField.setPromptText("portion g e.g. 373");
        portionPreviewField.setPrefWidth(120);

        buildTable();
        refreshTotals();

        Button add = new Button("Add ingredient");
        add.setOnAction(e -> addItem());
        Button remove = new Button("Remove");
        remove.setOnAction(e -> {
            DishItem selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                items.remove(selected);
                refreshTotals();
            }
        });
        Button save = new Button("Save dish");
        save.setDefaultButton(true);
        save.setOnAction(e -> saveDish());
        Button previewBtn = new Button("Preview portion");
        previewBtn.setOnAction(e -> previewPortion());
        Button back = new Button("Back");
        back.setOnAction(e -> onBack.run());

        GridPane meta = new GridPane();
        meta.setHgap(10);
        meta.setVgap(8);
        meta.addRow(0, new Label("Name"), nameField);
        meta.addRow(1, new Label("Notes"), notesField);
        meta.addRow(2, new Label("Yield g"), yieldField);

        HBox addRow = new HBox(8, ingredientPick.asInlineRow(), amountField, add, remove);
        HBox portionRow = new HBox(8, portionPreviewField, previewBtn);
        HBox actions = new HBox(8, save, back);

        VBox root = new VBox(12,
                new Label("Edit dish"),
                meta,
                new Label("Ingredients"),
                addRow,
                table,
                totals,
                portionRow,
                preview,
                error,
                actions
        );
        root.setPadding(new Insets(8, 24, 24, 24));
        VBox.setVgrow(table, Priority.ALWAYS);
        return root;
    }

    private void buildTable() {
        TableColumn<DishItem, String> name = new TableColumn<>("Ingredient");
        name.setPrefWidth(220);
        name.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getIngredientName()));

        TableColumn<DishItem, String> amount = new TableColumn<>("g");
        amount.setPrefWidth(70);
        amount.setCellValueFactory(c -> new SimpleStringProperty("%.1f".formatted(c.getValue().getAmountG())));

        TableColumn<DishItem, String> macros = new TableColumn<>("Line macros");
        macros.setPrefWidth(220);
        macros.setCellValueFactory(c -> new SimpleStringProperty(fmt(c.getValue().lineMacros())));

        table.getColumns().addAll(List.of(name, amount, macros));
        table.setPrefHeight(220);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private void addItem() {
        UiSupport.clearError(error);
        try {
            Ingredient ingredient = ingredientPick.getValue();
            if (ingredient == null) {
                throw new ValidationException("Pick a cached ingredient");
            }
            double grams = UiSupport.parsePositiveDouble(amountField.getText(), "amount (g)");
            DishItem item = new DishItem();
            item.setIngredientId(ingredient.getId());
            item.setIngredientName(ingredient.getName());
            item.setPer100g(ingredient.getPer100g());
            item.setAmountG(grams);
            items.add(item);
            amountField.clear();
            refreshTotals();
        } catch (ValidationException | IllegalArgumentException ex) {
            UiSupport.showError(error, ex.getMessage());
        }
    }

    private void saveDish() {
        UiSupport.clearError(error);
        try {
            Double yield = null;
            if (yieldField.getText() != null && !yieldField.getText().isBlank()) {
                yield = UiSupport.parsePositiveDouble(yieldField.getText(), "yield (g)");
            }
            dishes.updateMeta(dishId, nameField.getText(), notesField.getText(), yield);
            dishes.saveItems(dishId, new ArrayList<>(items));
            Dish saved = dishes.get(dishId);
            items.setAll(saved.getItems());
            refreshTotals();
            totals.setText(totals.getText() + "  ·  saved");
        } catch (ValidationException | IllegalArgumentException ex) {
            UiSupport.showError(error, ex.getMessage());
        }
    }

    private void previewPortion() {
        UiSupport.clearError(error);
        try {
            // Use in-memory items for live preview before save
            Dish draft = dishes.get(dishId);
            draft.setItems(new ArrayList<>(items));
            draft.setTotalWeightG(PortionScaler.sumAmounts(items));
            if (yieldField.getText() != null && !yieldField.getText().isBlank()) {
                draft.setYieldWeightG(UiSupport.parsePositiveDouble(yieldField.getText(), "yield (g)"));
            } else {
                draft.setYieldWeightG(null);
            }
            double portion = UiSupport.parsePositiveDouble(portionPreviewField.getText(), "portion (g)");
            Macros scaled = PortionScaler.macrosForPortion(draft, portion);
            double scale = PortionScaler.scaleFactor(draft, portion);
            preview.setText("Portion %.0fg → scale %.4f → %s".formatted(portion, scale, fmt(scaled)));
        } catch (ValidationException | IllegalArgumentException ex) {
            UiSupport.showError(error, ex.getMessage());
        }
    }

    private void refreshTotals() {
        Macros batch = PortionScaler.batchMacros(items);
        double totalG = PortionScaler.sumAmounts(items);
        totals.setText("Batch: %.0fg · %s".formatted(totalG, fmt(batch)));
    }

    private static String fmt(Macros m) {
        return "%.0f kcal · P %.1fg · C %.1fg · F %.1fg".formatted(
                m.getKcal(), m.getProteinG(), m.getCarbsG(), m.getFatG());
    }
}
