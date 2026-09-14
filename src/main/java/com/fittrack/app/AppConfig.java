package com.fittrack.app;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

/**
 * Loads classpath defaults, optional local override file, then environment variables.
 */
public final class AppConfig {

    private static final String CLASSPATH_CONFIG = "/config.properties";
    private static final String LOCAL_CONFIG_FILE = "config.local.properties";

    private final Properties properties;

    private AppConfig(Properties properties) {
        this.properties = properties;
    }

    public static AppConfig load() {
        Properties props = new Properties();
        try (InputStream in = AppConfig.class.getResourceAsStream(CLASSPATH_CONFIG)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource: " + CLASSPATH_CONFIG);
            }
            props.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + CLASSPATH_CONFIG, e);
        }

        Path local = Path.of(LOCAL_CONFIG_FILE);
        if (Files.isRegularFile(local)) {
            try (InputStream in = Files.newInputStream(local)) {
                props.load(in);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load " + LOCAL_CONFIG_FILE, e);
            }
        }

        overlayEnv(props, "USDA_API_KEY", "usda.api.key");
        overlayEnv(props, "FITTRACK_DB_PATH", "db.path");

        return new AppConfig(props);
    }

    public String get(String key) {
        return properties.getProperty(key);
    }

    public String get(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public synchronized void set(String key, String value) {
        if (value == null) {
            properties.remove(key);
        } else {
            properties.setProperty(key, value);
        }
    }

    /**
     * Persists selected overrides (e.g. API key) into {@code config.local.properties}.
     */
    public synchronized void saveLocalOverride(String key, String value) {
        set(key, value);
        Path local = Path.of(LOCAL_CONFIG_FILE);
        Properties localProps = new Properties();
        if (Files.isRegularFile(local)) {
            try (InputStream in = Files.newInputStream(local)) {
                localProps.load(in);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read " + LOCAL_CONFIG_FILE, e);
            }
        }
        if (value == null || value.isBlank()) {
            localProps.remove(key);
        } else {
            localProps.setProperty(key, value);
        }
        try (var out = Files.newOutputStream(local)) {
            localProps.store(out, "FitTrack local overrides (not committed)");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write " + LOCAL_CONFIG_FILE, e);
        }
    }

    private static void overlayEnv(Properties props, String envName, String propertyKey) {
        String env = System.getenv(envName);
        if (env != null && !env.isBlank()) {
            props.setProperty(propertyKey, env.trim());
        }
    }

    @Override
    public String toString() {
        return "AppConfig{keys=" + properties.size() + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AppConfig that)) {
            return false;
        }
        return Objects.equals(properties, that.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(properties);
    }
}
