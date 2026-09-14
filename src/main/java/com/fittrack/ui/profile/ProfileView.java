package com.fittrack.ui.profile;

import com.fittrack.app.AppContext;
import com.fittrack.domain.common.OneRepMaxFormula;
import com.fittrack.domain.common.Sex;
import com.fittrack.domain.common.Units;
import com.fittrack.domain.common.ValidationException;
import com.fittrack.domain.user.Profile;
import com.fittrack.domain.user.WeightEntry;
import com.fittrack.service.ProfileService;
import com.fittrack.ui.common.UiSupport;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class ProfileView {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    public VBox getRoot() {
        ProfileService profiles = AppContext.profileService();
        ProfileService.BodySnapshot snap = profiles.snapshot();
        Profile profile = snap.profile();

        TextField fullName = new TextField(profile.getFullName());
        fullName.setPromptText("Full name");
        DatePicker dob = new DatePicker(profile.getDateOfBirth());
        ComboBox<Sex> sex = new ComboBox<>(FXCollections.observableArrayList(Sex.values()));
        sex.getSelectionModel().select(profile.getSex());
        TextField height = new TextField(Double.toString(profile.getHeightCm()));
        height.setPromptText("Height (cm)");
        ComboBox<OneRepMaxFormula> formula = new ComboBox<>(FXCollections.observableArrayList(OneRepMaxFormula.values()));
        formula.getSelectionModel().select(profile.getPreferred1rmFormula());
        ComboBox<Units> units = new ComboBox<>(FXCollections.observableArrayList(Units.values()));
        units.getSelectionModel().select(profile.getUnits());

        Label metrics = new Label(formatMetrics(snap));
        metrics.setWrapText(true);
        metrics.getStyleClass().add("body-text");

        Label error = UiSupport.errorLabel();
        Label ok = new Label();
        ok.getStyleClass().add("success");

        Button save = new Button("Save profile");
        save.setOnAction(e -> {
            UiSupport.clearError(error);
            ok.setText("");
            try {
                double heightCm = UiSupport.parsePositiveDouble(height.getText(), "height (cm)");
                Profile updated = profiles.updateProfile(
                        fullName.getText(),
                        dob.getValue(),
                        sex.getValue(),
                        heightCm,
                        formula.getValue(),
                        units.getValue()
                );
                ok.setText("Profile saved. Age now: " + updated.getAge() + " years.");
                metrics.setText(formatMetrics(profiles.snapshot()));
            } catch (ValidationException | IllegalArgumentException ex) {
                UiSupport.showError(error, ex.getMessage());
            } catch (Exception ex) {
                UiSupport.showError(error, "Save failed: " + ex.getMessage());
            }
        });

        TextField newWeight = new TextField();
        newWeight.setPromptText("Weight (kg)");
        TextField note = new TextField();
        note.setPromptText("Note (optional)");
        TableView<WeightEntry> history = buildHistoryTable();
        history.setItems(FXCollections.observableArrayList(profiles.weightHistory()));
        history.setPrefHeight(220);

        Button logWeight = new Button("Log weight");
        logWeight.setOnAction(e -> {
            UiSupport.clearError(error);
            ok.setText("");
            try {
                double kg = UiSupport.parsePositiveDouble(newWeight.getText(), "weight (kg)");
                profiles.logWeight(kg, note.getText());
                history.setItems(FXCollections.observableArrayList(profiles.weightHistory()));
                metrics.setText(formatMetrics(profiles.snapshot()));
                newWeight.clear();
                note.clear();
                ok.setText("Weight logged. Metrics updated.");
            } catch (ValidationException | IllegalArgumentException ex) {
                UiSupport.showError(error, ex.getMessage());
            } catch (Exception ex) {
                UiSupport.showError(error, "Could not log weight: " + ex.getMessage());
            }
        });

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);
        int r = 0;
        form.addRow(r++, new Label("Username"), new Label(AppContext.session().requireUser().getUsername()));
        form.addRow(r++, new Label("Full name"), fullName);
        form.addRow(r++, new Label("Date of birth"), dob);
        form.addRow(r++, new Label("Sex"), sex);
        form.addRow(r++, new Label("Height (cm)"), height);
        form.addRow(r++, new Label("1RM formula"), formula);
        form.addRow(r++, new Label("Units"), units);

        Label metricsTitle = new Label("Live metrics (age from DOB)");
        metricsTitle.getStyleClass().add("section-title");

        Label weightTitle = new Label("Log new weight");
        weightTitle.getStyleClass().add("section-title");
        HBox weightRow = new HBox(8, newWeight, note, logWeight);

        Label historyTitle = new Label("Weight history");
        historyTitle.getStyleClass().add("section-title");

        VBox root = UiSupport.page(
                "Profile",
                form,
                save,
                error,
                ok,
                metricsTitle,
                metrics,
                weightTitle,
                weightRow,
                historyTitle,
                history
        );
        return root;
    }

    private static TableView<WeightEntry> buildHistoryTable() {
        TableView<WeightEntry> table = new TableView<>();
        TableColumn<WeightEntry, String> when = new TableColumn<>("When");
        when.setPrefWidth(160);
        when.setCellValueFactory(c -> new SimpleStringProperty(TIME_FMT.format(c.getValue().getMeasuredAt())));
        TableColumn<WeightEntry, String> kg = new TableColumn<>("kg");
        kg.setPrefWidth(80);
        kg.setCellValueFactory(c -> new SimpleStringProperty("%.2f".formatted(c.getValue().getWeightKg())));
        TableColumn<WeightEntry, String> noteCol = new TableColumn<>("Note");
        noteCol.setPrefWidth(220);
        noteCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getNote() == null ? "" : c.getValue().getNote()));
        table.getColumns().add(when);
        table.getColumns().add(kg);
        table.getColumns().add(noteCol);
        return table;
    }

    private static String formatMetrics(ProfileService.BodySnapshot snap) {
        Profile p = snap.profile();
        StringBuilder sb = new StringBuilder();
        sb.append("Age: ").append(p.getAge()).append(" years\n");
        if (snap.weightKg() == null) {
            sb.append("No weight logged yet.");
            return sb.toString();
        }
        sb.append("Current weight: ").append("%.2f kg".formatted(snap.weightKg())).append('\n');
        sb.append("BMI: ").append("%.1f".formatted(snap.bmi()))
                .append(" (").append(snap.bmiCategory()).append(")\n");
        sb.append("BMR: ").append("%.0f kcal/day".formatted(snap.bmr())).append('\n');
        sb.append("TDEE: ").append("%.0f kcal/day".formatted(snap.tdee()));
        return sb.toString();
    }
}
