package com.fittrack.app;

import com.fittrack.ui.ShellController;
import com.fittrack.ui.common.Theme;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * JavaFX entry point. Boots config + SQLite migrations, then shows the app shell.
 */
public final class FitTrackApp extends Application {

    @Override
    public void init() {
        AppContext.initialize();
    }

    @Override
    public void start(Stage stage) {
        Theme.ensureFonts();
        ShellController shell = new ShellController();
        Scene scene = new Scene(shell.getRoot(), 1024, 700);
        Theme.apply(scene);
        stage.setTitle(AppContext.config().get("app.name", "Fitness Tracking System"));
        stage.setMinWidth(880);
        stage.setMinHeight(560);
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        AppContext.shutdown();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
