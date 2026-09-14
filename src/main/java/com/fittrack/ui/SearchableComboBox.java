package com.fittrack.ui;

import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * ComboBox plus a search field that filters a large item list as you type.
 * Caps dropdown size and debounces typing so ~800+ catalogs stay responsive.
 */
public final class SearchableComboBox<T> {

    private static final int MAX_VISIBLE = 80;
    private static final Duration DEBOUNCE = Duration.millis(120);

    private final List<T> master = new ArrayList<>();
    private final ObservableList<T> visible = FXCollections.observableArrayList();
    private final ComboBox<T> combo = new ComboBox<>(visible);
    private final TextField searchField = new TextField();
    private final Label matchLabel = new Label();
    private final Function<T, String> labeler;
    private final PauseTransition debounce = new PauseTransition(DEBOUNCE);
    private Predicate<T> extraFilter = item -> true;

    public SearchableComboBox(Function<T, String> labeler) {
        this.labeler = Objects.requireNonNull(labeler, "labeler");
        combo.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(T object) {
                return object == null ? "" : labeler.apply(object);
            }

            @Override
            public T fromString(String string) {
                return null;
            }
        });
        combo.setVisibleRowCount(12);
        searchField.setPromptText("Search…");
        debounce.setOnFinished(e -> refreshNow());
        searchField.textProperty().addListener((o, a, b) -> debounce.playFromStart());
        matchLabel.getStyleClass().add("micro");
    }

    public void setItems(List<T> items) {
        master.clear();
        if (items != null) {
            master.addAll(items);
            master.sort(Comparator.comparing(labeler, String.CASE_INSENSITIVE_ORDER));
        }
        refreshNow();
    }

    public void setExtraFilter(Predicate<T> filter) {
        this.extraFilter = filter == null ? item -> true : filter;
        refreshNow();
    }

    public void setSearchPrompt(String prompt) {
        searchField.setPromptText(prompt);
    }

    public void setComboPrefWidth(double width) {
        combo.setPrefWidth(width);
    }

    public ComboBox<T> combo() {
        return combo;
    }

    public TextField searchField() {
        return searchField;
    }

    public T getValue() {
        return combo.getValue();
    }

    public void selectFirstIfAny() {
        if (!visible.isEmpty()) {
            combo.getSelectionModel().selectFirst();
        } else {
            combo.getSelectionModel().clearSelection();
        }
    }

    public VBox asLabeledBlock(String comboLabel) {
        HBox searchRow = new HBox(8, searchField, matchLabel);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchField.setMaxWidth(Double.MAX_VALUE);

        Label label = new Label(comboLabel);
        HBox comboRow = new HBox(8, label, combo);
        comboRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(combo, Priority.ALWAYS);
        combo.setMaxWidth(Double.MAX_VALUE);

        return new VBox(6, searchRow, comboRow);
    }

    public HBox asInlineRow() {
        HBox row = new HBox(8, searchField, combo, matchLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchField.setPrefWidth(160);
        return row;
    }

    private void refreshNow() {
        T previous = combo.getValue();
        String needle = searchField.getText() == null
                ? ""
                : searchField.getText().trim().toLowerCase(Locale.ROOT);

        List<T> capped = new ArrayList<>(Math.min(MAX_VISIBLE + 1, master.size()));
        int totalMatches = 0;
        for (T item : master) {
            if (!extraFilter.test(item)) {
                continue;
            }
            if (!needle.isEmpty()) {
                String label = labeler.apply(item);
                if (label == null || !label.toLowerCase(Locale.ROOT).contains(needle)) {
                    continue;
                }
            }
            totalMatches++;
            if (capped.size() < MAX_VISIBLE) {
                capped.add(item);
            }
        }

        if (previous != null && !capped.contains(previous) && matchesFilters(previous, needle)) {
            if (capped.size() >= MAX_VISIBLE) {
                capped.set(MAX_VISIBLE - 1, previous);
            } else {
                capped.add(previous);
            }
        }

        visible.setAll(capped);
        if (totalMatches > capped.size()) {
            matchLabel.setText(totalMatches + " matches · showing " + capped.size() + " — type to narrow");
        } else {
            matchLabel.setText(totalMatches + " match" + (totalMatches == 1 ? "" : "es"));
        }

        if (previous != null && capped.contains(previous)) {
            combo.getSelectionModel().select(previous);
        } else if (!capped.isEmpty()) {
            combo.getSelectionModel().selectFirst();
        } else {
            combo.getSelectionModel().clearSelection();
        }
    }

    private boolean matchesFilters(T item, String needle) {
        if (!extraFilter.test(item)) {
            return false;
        }
        if (needle.isEmpty()) {
            return true;
        }
        String label = labeler.apply(item);
        return label != null && label.toLowerCase(Locale.ROOT).contains(needle);
    }
}
