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
        Scene scene = new Scene(shell.getRoot(), 1100, 720);
        Theme.apply(scene);
        stage.setTitle(AppContext.config().get("app.name", "Fitness Tracking System"));
        stage.setMinWidth(960);
        stage.setMinHeight(600);
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
