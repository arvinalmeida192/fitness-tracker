package com.fittrack.ui.nutrition;

import com.fittrack.ui.common.Theme;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
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

    public BorderPane getRoot() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("page-plain");

        Button ingredients = tab("Ingredients", this::showIngredients);
        Button dishes = tab("Dishes", this::showDishes);
        Button meals = tab("Meals", this::showMeals);
        Button goals = tab("Goals", this::showGoals);

        HBox tabBar = new HBox(4, ingredients, dishes, meals, goals);
        tabBar.setAlignment(Pos.CENTER_LEFT);

        VBox header = new VBox(12, Theme.title("Nutrition"), tabBar);
        header.setPadding(new Insets(20, 24, 8, 24));

        root.setTop(header);
        root.setCenter(content);
        showIngredients();
        return root;
    }

    private Button tab(String label, Runnable action) {
        Button button = new Button(label);
        button.getStyleClass().add("tab");
        tabs.add(button);
        button.setOnAction(e -> {
            for (Button tab : tabs) {
                tab.getStyleClass().remove("tab-active");
            }
            button.getStyleClass().add("tab-active");
            action.run();
        });
        return button;
    }

    private void showIngredients() {
        activateFirstMatching("Ingredients");
        setContent(new IngredientSearchView().getRootWithoutOuterTitle());
    }

    private void showDishes() {
        activateFirstMatching("Dishes");
        DishListView list = new DishListView(swap -> setContent(swap));
        setContent(list.getRoot());
    }

    private void showMeals() {
        activateFirstMatching("Meals");
        setContent(new MealDiaryView().getRoot());
    }

    private void showGoals() {
        activateFirstMatching("Goals");
        setContent(new NutritionGoalsView().getRoot());
    }

    private void activateFirstMatching(String label) {
        for (Button tab : tabs) {
            tab.getStyleClass().remove("tab-active");
            if (label.equals(tab.getText())) {
                tab.getStyleClass().add("tab-active");
            }
        }
    }

    private void setContent(Node node) {
        content.getChildren().setAll(node);
        VBox.setVgrow(content, Priority.ALWAYS);
    }
}
