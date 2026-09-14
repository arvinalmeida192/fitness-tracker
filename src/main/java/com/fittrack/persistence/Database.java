package com.fittrack.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/**
 * SQLite connection holder. One writer-friendly connection for the desktop app.
 */
public final class Database implements AutoCloseable {

    private final Path dbPath;
    private final Connection connection;

    private Database(Path dbPath, Connection connection) {
        this.dbPath = dbPath;
        this.connection = connection;
    }

    public static Database open(String path) {
        Objects.requireNonNull(path, "path");
        Path dbPath = Path.of(path).toAbsolutePath().normalize();
        try {
            Path parent = dbPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String url = "jdbc:sqlite:" + dbPath;
            Connection connection = DriverManager.getConnection(url);
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
                st.execute("PRAGMA journal_mode = WAL");
            }
            return new Database(dbPath, connection);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create database directory for " + dbPath, e);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot open SQLite database at " + dbPath, e);
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public Path getDbPath() {
        return dbPath;
    }

    @Override
    public void close() {
        try {
            if (!connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to close database", e);
        }
    }
}
