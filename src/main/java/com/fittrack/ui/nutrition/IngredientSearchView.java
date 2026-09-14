package com.fittrack.ui.nutrition;

import com.fittrack.app.AppContext;
import com.fittrack.domain.common.Macros;
import com.fittrack.domain.common.NutritionApiException;
import com.fittrack.domain.common.OfflineDataException;
import com.fittrack.domain.common.ValidationException;
import com.fittrack.domain.nutrition.Ingredient;
import com.fittrack.service.NutritionService;
import com.fittrack.ui.common.UiSupport;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

public final class IngredientSearchView {

    private final NutritionService nutrition = AppContext.nutritionService();

    private final TextField queryField = new TextField();
    private final PasswordField apiKeyField = new PasswordField();
    private final Label keyHint = new Label();
    private final Label status = new Label();
    private final Label detail = new Label();
    private final Label error = UiSupport.errorLabel();
    private final TableView<NutritionService.SearchRow> table = new TableView<>();

    /** Body only — used inside {@link NutritionHubView}. */
    public VBox getRoot() {
        VBox box = UiSupport.embed(bodyNodes());
        VBox.setVgrow(table, Priority.ALWAYS);
        return box;
    }

    private javafx.scene.Node[] bodyNodes() {
        status.setWrapText(true);
        status.getStyleClass().add("body-text");
        detail.setWrapText(true);
        detail.getStyleClass().add("body-text");
        keyHint.getStyleClass().add("muted");
        refreshKeyHint();

        queryField.setPromptText("Search ingredients (e.g. chicken breast)");
        queryField.textProperty().addListener((obs, old, neo) -> nutrition.searchDebounced(neo, 400, outcome ->
                Platform.runLater(() -> applyOutcome(outcome))));

        Button searchNow = new Button("Search");
        searchNow.setDefaultButton(true);
        searchNow.setOnAction(e -> nutrition.searchDebounced(queryField.getText(), 0, outcome ->
                Platform.runLater(() -> applyOutcome(outcome))));

        Button cacheSelected = new Button("Cache / refresh macros");
        cacheSelected.setOnAction(e -> cacheSelected());

        apiKeyField.setPromptText("USDA API key");
        apiKeyField.setPrefWidth(220);
        Button saveKey = new Button("Save key");
        saveKey.setOnAction(e -> saveKey());

        if (table.getColumns().isEmpty()) {
            buildTable();
        }
        showCachedBrowse();

        HBox searchRow = new HBox(8, queryField, searchNow, cacheSelected);
        HBox.setHgrow(queryField, Priority.ALWAYS);
        HBox keyRow = new HBox(8, apiKeyField, saveKey, keyHint);

        return new javafx.scene.Node[]{
                new Label("Search USDA FoodData Central; results are cached in SQLite for offline use."),
                keyRow,
                searchRow,
                status,
                error,
                table,
                detail
        };
    }

    private void showCachedBrowse() {
        List<NutritionService.SearchRow> rows = new ArrayList<>();
        for (Ingredient ingredient : nutrition.listCached(50)) {
            rows.add(rowFromCached(ingredient));
        }
        table.setItems(FXCollections.observableArrayList(rows));
        if (rows.isEmpty()) {
            status.setText("Cache is empty — search online once to store foods locally.");
        } else {
            status.setText("Showing " + rows.size() + " cached ingredients. Type to search USDA + cache.");
            table.getSelectionModel().selectFirst();
        }
    }

    private NutritionService.SearchRow rowFromCached(Ingredient ingredient) {
        return new NutritionService.SearchRow(
                ingredient.getFdcId(),
                ingredient.getId(),
                ingredient.getName(),
                ingredient.getBrand(),
                "Cache",
                ingredient
        );
    }

    private void buildTable() {
        TableColumn<NutritionService.SearchRow, String> origin = new TableColumn<>("Source");
        origin.setPrefWidth(70);
        origin.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().origin()));

        TableColumn<NutritionService.SearchRow, String> name = new TableColumn<>("Name");
        name.setPrefWidth(280);
        name.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name()));

        TableColumn<NutritionService.SearchRow, String> brand = new TableColumn<>("Brand");
        brand.setPrefWidth(140);
        brand.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().brand() == null ? "" : c.getValue().brand()));

        TableColumn<NutritionService.SearchRow, String> macros = new TableColumn<>("Per 100 g");
        macros.setPrefWidth(220);
        macros.setCellValueFactory(c -> new SimpleStringProperty(macroSummary(c.getValue().ingredient())));

        table.getColumns().add(origin);
        table.getColumns().add(name);
        table.getColumns().add(brand);
        table.getColumns().add(macros);
        table.setPrefHeight(320);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getSelectionModel().selectedItemProperty().addListener((obs, o, neo) -> showDetail(neo));
        table.setPlaceholder(new Label("No results yet"));
    }

    private void applyOutcome(NutritionService.SearchOutcome outcome) {
        UiSupport.clearError(error);
        table.setItems(FXCollections.observableArrayList(outcome.rows()));
        status.setText(outcome.message() + (outcome.offline() ? "  [offline]" : ""));
        refreshKeyHint();
        if (!outcome.rows().isEmpty()) {
            table.getSelectionModel().selectFirst();
        } else {
            detail.setText("");
        }
    }

    private void cacheSelected() {
        UiSupport.clearError(error);
        NutritionService.SearchRow selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiSupport.showError(error, "Select a row first");
            return;
        }
        status.setText("Fetching macros…");
        nutrition.runAsync(() -> {
            try {
                Ingredient cached = nutrition.cacheFromHit(selected);
                Platform.runLater(() -> {
                    status.setText("Cached: " + cached.getName() + " (id " + cached.getId() + ")");
                    detail.setText(detailText(cached));
                    refreshKeyHint();
                    String q = queryField.getText();
                    if (q != null && q.trim().length() >= 2) {
                        nutrition.searchDebounced(q, 0, outcome ->
                                Platform.runLater(() -> applyOutcome(outcome)));
                    } else {
                        showCachedBrowse();
                    }
                });
            } catch (OfflineDataException | NutritionApiException | ValidationException ex) {
                Platform.runLater(() -> {
                    UiSupport.showError(error, ex.getMessage());
                    status.setText("Could not cache ingredient");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> UiSupport.showError(error, "Cache failed: " + ex.getMessage()));
            }
        });
    }

    private void saveKey() {
        UiSupport.clearError(error);
        try {
            nutrition.saveApiKey(apiKeyField.getText());
            status.setText("API key saved to config.local.properties");
            apiKeyField.clear();
            refreshKeyHint();
        } catch (ValidationException | IllegalStateException ex) {
            UiSupport.showError(error, ex.getMessage());
        }
    }

    private void refreshKeyHint() {
        keyHint.setText("API key: " + nutrition.apiKeyStatus()
                + "  ·  cached: " + nutrition.cachedCount());
    }

    private void showDetail(NutritionService.SearchRow row) {
        if (row == null) {
            detail.setText("");
            return;
        }
        if (row.ingredient() != null) {
            detail.setText(detailText(row.ingredient()));
        } else {
            detail.setText("USDA hit fdcId=" + row.fdcId()
                    + " — click “Cache / refresh macros” to download and store per-100g values.");
        }
    }

    private static String detailText(Ingredient ingredient) {
        Macros m = ingredient.getPer100g();
        return ingredient.getName()
                + (ingredient.getBrand() == null ? "" : " · " + ingredient.getBrand())
                + "\nPer 100 g: "
                + "%.0f kcal · P %.1fg · C %.1fg · F %.1fg · fiber %.1fg".formatted(
                m.getKcal(), m.getProteinG(), m.getCarbsG(), m.getFatG(), m.getFiberG())
                + (ingredient.getFdcId() == null ? "" : "\nFDC id: " + ingredient.getFdcId());
    }

    private static String macroSummary(Ingredient ingredient) {
        if (ingredient == null) {
            return "(not cached)";
        }
        Macros m = ingredient.getPer100g();
        return "%.0f kcal · P%.0f C%.0f F%.0f".formatted(
                m.getKcal(), m.getProteinG(), m.getCarbsG(), m.getFatG());
    }
}
