package com.fittrack.ui;

import com.fittrack.app.AppContext;
import com.fittrack.persistence.migration.MigrationRunner;
import com.fittrack.ui.auth.LoginView;
import com.fittrack.ui.auth.RegisterView;
import com.fittrack.ui.common.Theme;
import com.fittrack.ui.home.DashboardView;
import com.fittrack.ui.library.ExerciseLibraryView;
import com.fittrack.ui.nutrition.NutritionHubView;
import com.fittrack.ui.plans.PlanListView;
import com.fittrack.ui.profile.ProfileView;
import com.fittrack.ui.progress.ProgressView;
import com.fittrack.ui.settings.SettingsView;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * App shell: auth gate + side navigation for logged-in users.
 */
public final class ShellController {

    private final BorderPane root = new BorderPane();
    private final StackPane content = new StackPane();
    private final Label statusLabel = new Label();
    private final Label userLabel = new Label();
    private final VBox sideNav = new VBox();
    private final Button homeNav = navButton("Home", this::showHome);
    private Button activeNav;

    public ShellController() {
        Theme.ensureFonts();
        root.getStyleClass().add("app-shell");
        content.getStyleClass().add("content-host");
        root.setCenter(content);
        root.setBottom(buildStatusBar());
        buildSideNav();
        showAuthGate();
    }

    public BorderPane getRoot() {
        return root;
    }

    private void showAuthGate() {
        root.setLeft(null);
        activeNav = null;
        refreshStatus();
        showLogin();
    }

    private void enterApp() {
        root.setLeft(sideNav);
        refreshStatus();
        showHome();
    }

    private void showLogin() {
        setContent(centerAuth(new LoginView(this::enterApp, this::showRegister).getRoot()));
    }

    private void showRegister() {
        setContent(centerAuth(new RegisterView(this::enterApp, this::showLogin).getRoot()));
    }

    private void buildSideNav() {
        Label brand = new Label(AppContext.config().get("app.name", "FitTrack"));
        brand.getStyleClass().add("side-brand");
        brand.setWrapText(true);

        Button profile = navButton("Profile", () -> setContent(new ProfileView().getRoot()));
        Button library = navButton("Exercises", () -> setContent(new ExerciseLibraryView().getRoot()));
        Button plans = navButton("Plans", this::showPlans);
        Button nutrition = navButton("Nutrition", () -> setContent(new NutritionHubView().getRoot()));
        Button progress = navButton("Progress", () -> setContent(new ProgressView().getRoot()));
        Button settings = navButton("Settings", () -> setContent(new SettingsView(this::showAuthGate).getRoot()));

        Button logout = new Button("Log out");
        logout.getStyleClass().addAll("nav", "nav-logout");
        logout.setMaxWidth(Double.MAX_VALUE);
        logout.setAlignment(Pos.CENTER_LEFT);
        logout.setOnAction(e -> {
            AppContext.workoutSessionService().current().ifPresent(w -> {
                if (!w.isFinished()) {
                    try {
                        AppContext.workoutSessionService().finish("Ended on logout");
                    } catch (Exception ignored) {
                        // ignore
                    }
                }
            });
            AppContext.authService().logout();
            showAuthGate();
        });

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        userLabel.getStyleClass().setAll("nav-user");
        userLabel.setWrapText(true);

        sideNav.getChildren().setAll(
                brand,
                homeNav, profile, library, plans, nutrition, progress, settings,
                spacer,
                userLabel,
                logout
        );
        sideNav.getStyleClass().add("side-nav");
    }

    private Node buildStatusBar() {
        statusLabel.getStyleClass().add("status-label");
        HBox bar = new HBox(statusLabel);
        bar.getStyleClass().add("status-bar");
        refreshStatus();
        return bar;
    }

    private void refreshStatus() {
        String dbPath = AppContext.database().getDbPath().toString();
        int version = new MigrationRunner(AppContext.database()).currentVersion();
        String user = AppContext.session().isLoggedIn()
                ? AppContext.session().requireUser().getUsername()
                : "(not logged in)";
        statusLabel.setText("DB: " + dbPath + "  |  schema v" + version + "  |  user: " + user);
        if (AppContext.session().isLoggedIn()) {
            userLabel.setText(AppContext.session().requireUser().getUsername());
        } else {
            userLabel.setText("");
        }
    }

    private Button navButton(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("nav");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setOnAction(e -> {
            setActiveNav(button);
            action.run();
        });
        return button;
    }

    private void setActiveNav(Button button) {
        if (activeNav != null) {
            activeNav.getStyleClass().remove("nav-active");
        }
        activeNav = button;
        if (!button.getStyleClass().contains("nav-active")) {
            button.getStyleClass().add("nav-active");
        }
    }

    private void showPlans() {
        PlanListView list = new PlanListView(swap -> setContent(swap.node()));
        setContent(list.getRoot());
    }

    private void showHome() {
        refreshStatus();
        setActiveNav(homeNav);
        setContent(new DashboardView().getRoot());
    }

    private void setContent(Node node) {
        content.getChildren().setAll(node);
    }

    private static Node centerAuth(Node node) {
        StackPane pad = new StackPane(node);
        pad.getStyleClass().add("auth-host");
        return pad;
    }
}
