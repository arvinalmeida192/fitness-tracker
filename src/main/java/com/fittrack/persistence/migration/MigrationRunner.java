package com.fittrack.persistence.migration;

import com.fittrack.persistence.sqlite.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Applies numbered SQL migrations in order. Tracks version in {@code app_meta}.
 */
public final class MigrationRunner {

    private final Database database;

    public MigrationRunner(Database database) {
        this.database = database;
    }

    public void migrate() {
        Connection conn = database.getConnection();
        try {
            ensureMetaTable(conn);
            int current = readSchemaVersion(conn);
            List<Migration> pending = migrations().stream()
                    .filter(m -> m.version() > current)
                    .sorted(Comparator.comparingInt(Migration::version))
                    .toList();

            for (Migration migration : pending) {
                conn.setAutoCommit(false);
                try (Statement st = conn.createStatement()) {
                    st.executeUpdate(migration.sql());
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO app_meta(key, value) VALUES('schema_version', ?) "
                                    + "ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
                        ps.setString(1, Integer.toString(migration.version()));
                        ps.executeUpdate();
                    }
                    conn.commit();
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Database migration failed", e);
        }
    }

    public int currentVersion() {
        try {
            ensureMetaTable(database.getConnection());
            return readSchemaVersion(database.getConnection());
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot read schema version", e);
        }
    }

    private static void ensureMetaTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS app_meta (
                        key   TEXT PRIMARY KEY NOT NULL,
                        value TEXT NOT NULL
                    )
                    """);
        }
    }

    private static int readSchemaVersion(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT value FROM app_meta WHERE key = 'schema_version'")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return 0;
                }
                return Integer.parseInt(rs.getString(1));
            }
        }
    }

    private static List<Migration> migrations() {
        List<Migration> list = new ArrayList<>();
        list.add(new Migration(1, V1_SCHEMA));
        list.add(new Migration(2, V2_EXERCISE_MUSCLES));
        list.add(new Migration(3, V3_DROP_DIET_TEMPLATES));
        return List.copyOf(list);
    }

    private record Migration(int version, String sql) {
    }

    /** Drop unused diet template tables (feature removed from the app). */
    private static final String V3_DROP_DIET_TEMPLATES = """
            DROP TABLE IF EXISTS diet_plan_entries;
            DROP TABLE IF EXISTS diet_plans;
            """;

    private static final String V2_EXERCISE_MUSCLES = """
            ALTER TABLE exercises ADD COLUMN primary_muscles TEXT;
            ALTER TABLE exercises ADD COLUMN secondary_muscles TEXT NOT NULL DEFAULT '';
            ALTER TABLE exercises ADD COLUMN external_id TEXT;
            UPDATE exercises
            SET primary_muscles = muscle_group
            WHERE primary_muscles IS NULL OR TRIM(primary_muscles) = '';
            """;

    /** Initial application schema. */
    private static final String V1_SCHEMA = """
            CREATE TABLE IF NOT EXISTS users (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                username      TEXT    NOT NULL UNIQUE COLLATE NOCASE,
                password_hash TEXT    NOT NULL,
                salt          TEXT    NOT NULL,
                created_at    TEXT    NOT NULL
            );

            CREATE TABLE IF NOT EXISTS profiles (
                user_id              INTEGER PRIMARY KEY,
                full_name            TEXT    NOT NULL,
                date_of_birth        TEXT    NOT NULL,
                sex                  TEXT    NOT NULL,
                height_cm            REAL    NOT NULL,
                activity_level       TEXT    NOT NULL,
                preferred_1rm_formula TEXT   NOT NULL DEFAULT 'EPLEY',
                units                TEXT    NOT NULL DEFAULT 'METRIC',
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            );

            CREATE TABLE IF NOT EXISTS weight_entries (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id     INTEGER NOT NULL,
                weight_kg   REAL    NOT NULL,
                measured_at TEXT    NOT NULL,
                note        TEXT,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            );
            CREATE INDEX IF NOT EXISTS idx_weight_entries_user_time
                ON weight_entries(user_id, measured_at);

            CREATE TABLE IF NOT EXISTS exercises (
                id               INTEGER PRIMARY KEY AUTOINCREMENT,
                name             TEXT    NOT NULL,
                muscle_group     TEXT    NOT NULL,
                equipment        TEXT,
                instructions     TEXT,
                default_rest_sec INTEGER NOT NULL DEFAULT 90
            );
            CREATE INDEX IF NOT EXISTS idx_exercises_name ON exercises(name);

            CREATE TABLE IF NOT EXISTS workout_plans (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id     INTEGER NOT NULL,
                name        TEXT    NOT NULL,
                description TEXT,
                archived    INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            );

            CREATE TABLE IF NOT EXISTS plan_items (
                id             INTEGER PRIMARY KEY AUTOINCREMENT,
                plan_id        INTEGER NOT NULL,
                exercise_id    INTEGER NOT NULL,
                order_index    INTEGER NOT NULL,
                target_sets    INTEGER NOT NULL,
                reps_min       INTEGER NOT NULL,
                reps_max       INTEGER NOT NULL,
                target_weight  REAL,
                rest_sec       INTEGER,
                notes          TEXT,
                FOREIGN KEY (plan_id) REFERENCES workout_plans(id) ON DELETE CASCADE,
                FOREIGN KEY (exercise_id) REFERENCES exercises(id)
            );

            CREATE TABLE IF NOT EXISTS workout_sessions (
                id         INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id    INTEGER NOT NULL,
                plan_id    INTEGER,
                started_at TEXT    NOT NULL,
                ended_at   TEXT,
                notes      TEXT,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                FOREIGN KEY (plan_id) REFERENCES workout_plans(id) ON DELETE SET NULL
            );

            CREATE TABLE IF NOT EXISTS logged_sets (
                id                     INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id             INTEGER NOT NULL,
                exercise_id            INTEGER NOT NULL,
                exercise_name_snapshot TEXT    NOT NULL,
                set_index              INTEGER NOT NULL,
                weight_kg              REAL    NOT NULL,
                reps                   INTEGER NOT NULL,
                rpe                    REAL,
                completed              INTEGER NOT NULL DEFAULT 1,
                logged_at              TEXT    NOT NULL,
                epley_1rm              REAL,
                brzycki_1rm            REAL,
                lombardi_1rm           REAL,
                FOREIGN KEY (session_id) REFERENCES workout_sessions(id) ON DELETE CASCADE,
                FOREIGN KEY (exercise_id) REFERENCES exercises(id)
            );
            CREATE INDEX IF NOT EXISTS idx_logged_sets_exercise
                ON logged_sets(exercise_id);

            CREATE TABLE IF NOT EXISTS ingredients (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                fdc_id      INTEGER UNIQUE,
                name        TEXT    NOT NULL,
                brand       TEXT,
                per100_kcal REAL    NOT NULL,
                protein_g   REAL    NOT NULL,
                carbs_g     REAL    NOT NULL,
                fat_g       REAL    NOT NULL,
                fiber_g     REAL    NOT NULL DEFAULT 0,
                source      TEXT    NOT NULL,
                fetched_at  TEXT    NOT NULL
            );
            CREATE INDEX IF NOT EXISTS idx_ingredients_name ON ingredients(name);

            CREATE TABLE IF NOT EXISTS dishes (
                id              INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id         INTEGER NOT NULL,
                name            TEXT    NOT NULL,
                notes           TEXT,
                total_weight_g  REAL    NOT NULL,
                yield_weight_g  REAL,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            );

            CREATE TABLE IF NOT EXISTS dish_items (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                dish_id       INTEGER NOT NULL,
                ingredient_id INTEGER NOT NULL,
                amount_g      REAL    NOT NULL,
                FOREIGN KEY (dish_id) REFERENCES dishes(id) ON DELETE CASCADE,
                FOREIGN KEY (ingredient_id) REFERENCES ingredients(id)
            );

            CREATE TABLE IF NOT EXISTS meal_logs (
                id             INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id        INTEGER NOT NULL,
                eaten_at       TEXT    NOT NULL,
                meal_type      TEXT    NOT NULL,
                dish_id        INTEGER,
                portion_g      REAL    NOT NULL,
                scale_factor   REAL    NOT NULL,
                kcal           REAL    NOT NULL,
                protein_g      REAL    NOT NULL,
                carbs_g        REAL    NOT NULL,
                fat_g          REAL    NOT NULL,
                fiber_g        REAL    NOT NULL DEFAULT 0,
                notes          TEXT,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                FOREIGN KEY (dish_id) REFERENCES dishes(id) ON DELETE SET NULL
            );
            CREATE INDEX IF NOT EXISTS idx_meal_logs_user_time
                ON meal_logs(user_id, eaten_at);

            CREATE TABLE IF NOT EXISTS nutrition_goals (
                user_id        INTEGER PRIMARY KEY,
                kcal           REAL    NOT NULL,
                protein_g      REAL    NOT NULL,
                carbs_g        REAL    NOT NULL,
                fat_g          REAL    NOT NULL,
                fiber_g        REAL,
                effective_from TEXT    NOT NULL,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            );

            CREATE TABLE IF NOT EXISTS personal_records (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id       INTEGER NOT NULL,
                exercise_id   INTEGER NOT NULL,
                pr_type       TEXT    NOT NULL,
                reps          INTEGER,
                weight_kg     REAL,
                estimated_1rm REAL,
                formula       TEXT,
                achieved_at   TEXT    NOT NULL,
                logged_set_id INTEGER,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                FOREIGN KEY (exercise_id) REFERENCES exercises(id),
                FOREIGN KEY (logged_set_id) REFERENCES logged_sets(id) ON DELETE SET NULL
            );

            CREATE TABLE IF NOT EXISTS body_goals (
                user_id           INTEGER PRIMARY KEY,
                target_weight_kg  REAL    NOT NULL,
                target_date       TEXT,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            );
            """;
}
