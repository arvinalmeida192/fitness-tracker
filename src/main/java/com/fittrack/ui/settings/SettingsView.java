package com.fittrack.ui.settings;

import com.fittrack.app.AppContext;
import com.fittrack.domain.common.ValidationException;
import com.fittrack.domain.progress.PersonalRecord;
import com.fittrack.ui.common.Theme;
import com.fittrack.ui.common.UiSupport;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public final class SettingsView {

    private final Runnable onRestoredLogout;
    private final Label status = new Label();
    private final Label error = UiSupport.errorLabel();
    private final PasswordField apiKeyField = new PasswordField();
    private final Label keyHint = new Label();
    private final Label prBufferLabel = new Label();

    public SettingsView(Runnable onRestoredLogout) {
        this.onRestoredLogout = onRestoredLogout;
    }

    public ScrollPane getRoot() {
        status.setWrapText(true);
        status.getStyleClass().add("success");
        keyHint.getStyleClass().add("hint");
        prBufferLabel.setWrapText(true);
        prBufferLabel.getStyleClass().add("hint");
        refreshKeyHint();
        refreshPrBuffer();

        Label dbLabel = new Label("Database: " + AppContext.database().getDbPath());
        dbLabel.setWrapText(true);
        dbLabel.getStyleClass().add("hint");

        apiKeyField.setPromptText("USDA API key");
        apiKeyField.setPrefWidth(280);
        Button saveKey = new Button("Save API key");
        saveKey.setOnAction(e -> saveApiKey());

        Button exportDb = new Button("Export SQLite backup…");
        exportDb.setOnAction(e -> exportSqlite());
        Button restoreDb = new Button("Restore SQLite backup…");
        restoreDb.setOnAction(e -> restoreSqlite());

        Button exportJson = new Button("Export user JSON…");
        exportJson.setOnAction(e -> exportJson());
        Button importJson = new Button("Import user JSON…");
        importJson.setOnAction(e -> importJson());

        Label apiTitle = new Label("USDA API");
        apiTitle.getStyleClass().add("section-title");
        Label backupTitle = new Label("Backup & restore");
        backupTitle.getStyleClass().add("section-title");
        Label prTitle = new Label("Recent PR notifications");
        prTitle.getStyleClass().add("section-title");

        Label backupHint = new Label(
                "SQLite backup copies the whole database (best for a fresh restore). "
                        + "JSON exports the logged-in user's profile, weights, goals, dishes, and recent meals.");
        backupHint.setWrapText(true);
        backupHint.getStyleClass().add("hint");

        VBox page = UiSupport.page(
                "Settings",
                dbLabel,
                new Separator(),
                apiTitle,
                keyHint,
                new HBox(8, apiKeyField, saveKey),
                new Separator(),
                backupTitle,
                backupHint,
                new HBox(8, exportDb, restoreDb),
                new HBox(8, exportJson, importJson),
                new Separator(),
                prTitle,
                prBufferLabel,
                error,
                status
        );
        page.setPadding(new Insets(20));

        ScrollPane scroll = new ScrollPane(page);
        Theme.styleScroll(scroll);
        return scroll;
    }

    private void saveApiKey() {
        UiSupport.clearError(error);
        status.setText("");
        try {
            String key = apiKeyField.getText() == null ? "" : apiKeyField.getText().trim();
            if (key.isBlank()) {
                throw new ValidationException("Enter an API key");
            }
            AppContext.config().saveLocalOverride("usda.api.key", key);
            apiKeyField.clear();
            refreshKeyHint();
            status.setText("API key saved to config.local.properties");
        } catch (ValidationException ex) {
            UiSupport.showError(error, ex.getMessage());
        } catch (Exception ex) {
            UiSupport.showError(error, "Could not save key: " + ex.getMessage());
        }
    }

    private void exportSqlite() {
        Path dest = chooseSave("fittrack-backup-" + today() + ".db", "SQLite DB", "*.db");
        if (dest == null) {
            return;
        }
        runBusy(AppContext.backupService()::exportSqliteAsync, dest, "SQLite backup written:\n");
    }

    private void restoreSqlite() {
        Path source = chooseOpen("SQLite DB", "*.db");
        if (source == null) {
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Restore database");
        confirm.setHeaderText("Replace the current database?");
        confirm.setContentText("This overwrites local data with the backup. You will need to log in again.");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }
        UiSupport.clearError(error);
        status.setText("Restoring…");
        try {
            AppContext.restoreDatabaseFrom(source);
            status.setText("Database restored from " + source.toAbsolutePath());
            if (onRestoredLogout != null) {
                onRestoredLogout.run();
            }
        } catch (Exception ex) {
            UiSupport.showError(error, "Restore failed: " + ex.getMessage());
            status.setText("");
        }
    }

    private void exportJson() {
        Path dest = chooseSave("fittrack-user-" + today() + ".json", "JSON", "*.json");
        if (dest == null) {
            return;
        }
        runBusy(AppContext.backupService()::exportUserJsonAsync, dest, "JSON export written:\n");
    }

    private void importJson() {
        Path source = chooseOpen("JSON", "*.json");
        if (source == null) {
            return;
        }
        UiSupport.clearError(error);
        status.setText("Importing…");
        AppContext.backupService().importUserJsonAsync(
                source,
                summary -> Platform.runLater(() -> {
                    status.setText(summary);
                    refreshPrBuffer();
                }),
                ex -> Platform.runLater(() -> {
                    status.setText("");
                    UiSupport.showError(error, "Import failed: " + ex.getMessage());
                })
        );
    }

    private void runBusy(
            TriConsumer<Path, Consumer<String>, Consumer<Exception>> action,
            Path path,
            String successPrefix
    ) {
        UiSupport.clearError(error);
        status.setText("Working…");
        action.accept(
                path,
                written -> Platform.runLater(() -> status.setText(successPrefix + written)),
                ex -> Platform.runLater(() -> {
                    status.setText("");
                    UiSupport.showError(error, ex.getMessage());
                })
        );
    }

    private void refreshKeyHint() {
        String key = AppContext.config().get("usda.api.key", "");
        if (key == null || key.isBlank()) {
            keyHint.setText("No API key configured.");
        } else if ("DEMO_KEY".equals(key)) {
            keyHint.setText("Using DEMO_KEY (rate-limited). Override here or via USDA_API_KEY.");
        } else {
            keyHint.setText("API key on file (hidden). Saving replaces config.local.properties.");
        }
    }

    private void refreshPrBuffer() {
        List<PersonalRecord> recent = AppContext.progressService().notifications().snapshot();
        if (recent.isEmpty()) {
            prBufferLabel.setText("No new PRs this session. Log a stronger set in a live workout.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        int n = Math.min(5, recent.size());
        for (int i = 0; i < n; i++) {
            PersonalRecord pr = recent.get(i);
            if (i > 0) {
                sb.append('\n');
            }
            sb.append("• ").append(pr.getExerciseName()).append(" — ").append(pr.getPrType().name());
        }
        prBufferLabel.setText(sb.toString());
    }

    private Path chooseSave(String initial, String typeLabel, String extension) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save " + typeLabel);
        chooser.setInitialFileName(initial);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(typeLabel, extension));
        var file = chooser.showSaveDialog(null);
        return file == null ? null : file.toPath();
    }

    private Path chooseOpen(String typeLabel, String extension) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open " + typeLabel);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(typeLabel, extension));
        var file = chooser.showOpenDialog(null);
        return file == null ? null : file.toPath();
    }

    private static String today() {
        return LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    @FunctionalInterface
    private interface TriConsumer<A, B, C> {
        void accept(A a, B b, C c);
    }
}
