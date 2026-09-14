package com.fittrack.ui.auth;

import com.fittrack.app.AppContext;
import com.fittrack.domain.common.AuthenticationException;
import com.fittrack.domain.common.ValidationException;
import com.fittrack.ui.common.Theme;
import com.fittrack.ui.common.UiSupport;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public final class LoginView {

    private final Runnable onSuccess;
    private final Runnable onGoRegister;

    public LoginView(Runnable onSuccess, Runnable onGoRegister) {
        this.onSuccess = onSuccess;
        this.onGoRegister = onGoRegister;
    }

    public VBox getRoot() {
        TextField username = new TextField();
        username.setPromptText("Username");
        PasswordField password = new PasswordField();
        password.setPromptText("Password");
        Label error = UiSupport.errorLabel();

        Button login = new Button("Log in");
        login.setDefaultButton(true);
        Theme.primary(login);
        login.setOnAction(e -> {
            UiSupport.clearError(error);
            try {
                AppContext.authService().login(username.getText(), password.getText());
                onSuccess.run();
            } catch (ValidationException | AuthenticationException ex) {
                UiSupport.showError(error, ex.getMessage());
            } catch (Exception ex) {
                UiSupport.showError(error, "Login failed: " + ex.getMessage());
            }
        });

        Button register = new Button("Create account");
        register.setOnAction(e -> onGoRegister.run());

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.addRow(0, new Label("Username"), username);
        form.addRow(1, new Label("Password"), password);

        HBox actions = new HBox(10, login, register);
        VBox root = UiSupport.page(
                "Log in",
                Theme.muted("Sign in to continue training and nutrition tracking."),
                form,
                error,
                actions
        );
        root.setMaxWidth(460);
        return root;
    }
}
