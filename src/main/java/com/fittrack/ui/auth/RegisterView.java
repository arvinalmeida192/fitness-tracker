package com.fittrack.ui.auth;

import com.fittrack.app.AppContext;
import com.fittrack.domain.common.Sex;
import com.fittrack.domain.common.ValidationException;
import com.fittrack.ui.common.Theme;
import com.fittrack.ui.common.UiSupport;
import com.fittrack.util.PasswordStrengthChecker;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDate;

public final class RegisterView {

    private final Runnable onSuccess;
    private final Runnable onGoLogin;

    public RegisterView(Runnable onSuccess, Runnable onGoLogin) {
        this.onSuccess = onSuccess;
        this.onGoLogin = onGoLogin;
    }

    public VBox getRoot() {
        TextField username = new TextField();
        username.setPromptText("Username");
        PasswordField password = new PasswordField();
        password.setPromptText("Password");
        Label strength = Theme.hint("Strength: —");
        password.textProperty().addListener((obs, oldV, newV) -> {
            PasswordStrengthChecker.Strength s = PasswordStrengthChecker.check(newV);
            strength.setText("Strength: " + s.name());
            strength.getStyleClass().removeAll("error-label", "success", "hint");
            strength.getStyleClass().add(switch (s) {
                case WEAK -> "error-label";
                case MODERATE -> "hint";
                case STRONG -> "success";
            });
        });

        TextField fullName = new TextField();
        fullName.setPromptText("Full name");
        DatePicker dob = new DatePicker(LocalDate.now().minusYears(20));
        ComboBox<Sex> sex = new ComboBox<>();
        sex.getItems().addAll(Sex.values());
        sex.getSelectionModel().select(Sex.MALE);
        TextField height = new TextField("175");
        height.setPromptText("Height (cm)");
        TextField weight = new TextField("70");
        weight.setPromptText("Weight (kg)");

        Label error = UiSupport.errorLabel();

        Button create = new Button("Create account");
        create.setDefaultButton(true);
        Theme.primary(create);
        create.setOnAction(e -> {
            UiSupport.clearError(error);
            try {
                double heightCm = UiSupport.parsePositiveDouble(height.getText(), "height (cm)");
                double weightKg = UiSupport.parsePositiveDouble(weight.getText(), "weight (kg)");
                AppContext.authService().register(
                        username.getText(),
                        password.getText(),
                        fullName.getText(),
                        dob.getValue(),
                        sex.getValue(),
                        heightCm,
                        weightKg
                );
                onSuccess.run();
            } catch (ValidationException | IllegalArgumentException ex) {
                UiSupport.showError(error, ex.getMessage());
            } catch (Exception ex) {
                UiSupport.showError(error, "Registration failed: " + ex.getMessage());
            }
        });

        Button back = new Button("Back to login");
        back.setOnAction(e -> onGoLogin.run());

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);
        int r = 0;
        form.addRow(r++, new Label("Username"), username);
        form.addRow(r++, new Label("Password"), password);
        form.add(strength, 1, r++);
        form.addRow(r++, new Label("Full name"), fullName);
        form.addRow(r++, new Label("Date of birth"), dob);
        form.addRow(r++, new Label("Sex"), sex);
        form.addRow(r++, new Label("Height (cm)"), height);
        form.addRow(r++, new Label("Weight (kg)"), weight);

        HBox actions = new HBox(10, create, back);
        VBox root = new VBox(14,
                Theme.title("Create account"),
                Theme.muted("Profile age is always calculated from date of birth."),
                form,
                error,
                actions
        );
        root.getStyleClass().add("auth-panel");
        root.setPadding(new Insets(28));
        root.setMaxWidth(560);
        return root;
    }
}
