package com.fittrack.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Nutrition section with sub-screens: ingredients, dishes, meals, goals.
 */
public final class NutritionHubView {

    private final StackPane content = new StackPane();
    private final List<Button> tabs = new ArrayList<>();

    public VBox getRoot() {
        Button ingredients = tab("Ingredients", this::showIngredients);
        Button dishes = tab("Dishes", this::showDishes);
        Button meals = tab("Meals", this::showMeals);
        Button goals = tab("Goals", this::showGoals);

        HBox tabBar = new HBox(4, ingredients, dishes, meals, goals);
        tabBar.setAlignment(Pos.CENTER_LEFT);

        VBox page = UiSupport.page("Nutrition", tabBar, content);
        VBox.setVgrow(content, Priority.ALWAYS);
        showIngredients();
        return page;
    }

    private Button tab(String label, Runnable action) {
        Button button = new Button(label);
        button.getStyleClass().add("tab");
        tabs.add(button);
        button.setOnAction(e -> action.run());
        return button;
    }

    private void setActiveTab(Button button) {
        for (Button tab : tabs) {
            tab.getStyleClass().remove("tab-active");
        }
        button.getStyleClass().add("tab-active");
    }

    private void showIngredients() {
        setActiveTab(tabs.get(0));
        setContent(new IngredientSearchView().getRoot());
    }

    private void showDishes() {
        setActiveTab(tabs.get(1));
        DishListView list = new DishListView(this::setContent);
        setContent(list.getRoot());
    }

    private void showMeals() {
        setActiveTab(tabs.get(2));
        setContent(new MealDiaryView().getRoot());
    }

    private void showGoals() {
        setActiveTab(tabs.get(3));
        setContent(new NutritionGoalsView().getRoot());
    }

    private void setContent(Node node) {
        content.getChildren().setAll(node);
    }
}
