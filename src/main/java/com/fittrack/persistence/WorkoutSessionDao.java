package com.fittrack.persistence;

import com.fittrack.domain.DataAccessException;
import com.fittrack.domain.LoggedSet;
import com.fittrack.domain.WorkoutSession;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class WorkoutSessionDao {

    private final Database database;

    public WorkoutSessionDao(Database database) {
        this.database = database;
    }

    public WorkoutSession insert(WorkoutSession session) {
        String sql = """
                INSERT INTO workout_sessions(user_id, plan_id, started_at, ended_at, notes)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, session.getUserId());
            if (session.getPlanId() == null) {
                ps.setNull(2, Types.INTEGER);
            } else {
                ps.setLong(2, session.getPlanId());
            }
            ps.setString(3, session.getStartedAt().toString());
            ps.setNull(4, Types.VARCHAR);
            ps.setString(5, session.getNotes());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    session.setId(keys.getLong(1));
                }
            }
            return session;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to start workout session", e);
        }
    }

    public void finish(long sessionId, Instant endedAt, String notes) {
        String sql = """
                UPDATE workout_sessions
                SET ended_at = ?, notes = ?
                WHERE id = ? AND ended_at IS NULL
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, endedAt.toString());
            ps.setString(2, notes);
            ps.setLong(3, sessionId);
            if (ps.executeUpdate() == 0) {
                throw new DataAccessException("Open workout session not found");
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to finish workout session", e);
        }
    }

    public LoggedSet insertSet(LoggedSet set) {
        String sql = """
                INSERT INTO logged_sets(
                    session_id, exercise_id, exercise_name_snapshot, set_index,
                    weight_kg, reps, rpe, completed, logged_at,
                    epley_1rm, brzycki_1rm, lombardi_1rm
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, set.getSessionId());
            ps.setLong(2, set.getExerciseId());
            ps.setString(3, set.getExerciseNameSnapshot());
            ps.setInt(4, set.getSetIndex());
            ps.setDouble(5, set.getWeightKg());
            ps.setInt(6, set.getReps());
            if (set.getRpe() == null) {
                ps.setNull(7, Types.REAL);
            } else {
                ps.setDouble(7, set.getRpe());
            }
            ps.setInt(8, set.isCompleted() ? 1 : 0);
            ps.setString(9, set.getLoggedAt().toString());
            setNullableDouble(ps, 10, set.getEpley1rm());
            setNullableDouble(ps, 11, set.getBrzycki1rm());
            setNullableDouble(ps, 12, set.getLombardi1rm());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    set.setId(keys.getLong(1));
                }
            }
            return set;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to log set", e);
        }
    }

    public List<LoggedSet> findSetsBySession(long sessionId) {
        String sql = """
                SELECT id, session_id, exercise_id, exercise_name_snapshot, set_index,
                       weight_kg, reps, rpe, completed, logged_at,
                       epley_1rm, brzycki_1rm, lombardi_1rm
                FROM logged_sets
                WHERE session_id = ?
                ORDER BY logged_at ASC, id ASC
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                List<LoggedSet> sets = new ArrayList<>();
                while (rs.next()) {
                    sets.add(mapSet(rs));
                }
                return sets;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to list logged sets", e);
        }
    }

    public List<LoggedSet> findSetsWithOneRm(long userId, long exerciseId) {
        String sql = """
                SELECT ls.id, ls.session_id, ls.exercise_id, ls.exercise_name_snapshot, ls.set_index,
                       ls.weight_kg, ls.reps, ls.rpe, ls.completed, ls.logged_at,
                       ls.epley_1rm, ls.brzycki_1rm, ls.lombardi_1rm
                FROM logged_sets ls
                JOIN workout_sessions ws ON ws.id = ls.session_id
                WHERE ws.user_id = ?
                  AND ls.exercise_id = ?
                  AND ls.completed = 1
                  AND ls.weight_kg > 0
                  AND (ls.epley_1rm IS NOT NULL OR ls.brzycki_1rm IS NOT NULL OR ls.lombardi_1rm IS NOT NULL)
                ORDER BY ls.logged_at ASC, ls.id ASC
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, exerciseId);
            try (ResultSet rs = ps.executeQuery()) {
                List<LoggedSet> sets = new ArrayList<>();
                while (rs.next()) {
                    sets.add(mapSet(rs));
                }
                return sets;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load 1RM history", e);
        }
    }

    public List<LoggedSet> findCompletedSetsBetween(long userId, Instant fromInclusive, Instant toExclusive) {
        String sql = """
                SELECT ls.id, ls.session_id, ls.exercise_id, ls.exercise_name_snapshot, ls.set_index,
                       ls.weight_kg, ls.reps, ls.rpe, ls.completed, ls.logged_at,
                       ls.epley_1rm, ls.brzycki_1rm, ls.lombardi_1rm
                FROM logged_sets ls
                JOIN workout_sessions ws ON ws.id = ls.session_id
                WHERE ws.user_id = ?
                  AND ls.completed = 1
                  AND ls.logged_at >= ?
                  AND ls.logged_at < ?
                ORDER BY ls.logged_at ASC, ls.id ASC
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, fromInclusive.toString());
            ps.setString(3, toExclusive.toString());
            try (ResultSet rs = ps.executeQuery()) {
                List<LoggedSet> sets = new ArrayList<>();
                while (rs.next()) {
                    sets.add(mapSet(rs));
                }
                return sets;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to list completed sets", e);
        }
    }

    public List<ExerciseSummary> findExercisesWithSets(long userId) {
        String sql = """
                SELECT DISTINCT e.id, e.name
                FROM exercises e
                JOIN logged_sets ls ON ls.exercise_id = e.id
                JOIN workout_sessions ws ON ws.id = ls.session_id
                WHERE ws.user_id = ?
                  AND ls.completed = 1
                ORDER BY e.name COLLATE NOCASE
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                List<ExerciseSummary> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(new ExerciseSummary(rs.getLong("id"), rs.getString("name")));
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to list exercises with sets", e);
        }
    }

    public record ExerciseSummary(long exerciseId, String name) {
        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Latest completed set for this user+exercise from any finished or open session.
     */
    public Optional<LoggedSet> findPreviousPerformance(long userId, long exerciseId, Long excludeSessionId) {
        String sql = """
                SELECT ls.id, ls.session_id, ls.exercise_id, ls.exercise_name_snapshot, ls.set_index,
                       ls.weight_kg, ls.reps, ls.rpe, ls.completed, ls.logged_at,
                       ls.epley_1rm, ls.brzycki_1rm, ls.lombardi_1rm
                FROM logged_sets ls
                JOIN workout_sessions ws ON ws.id = ls.session_id
                WHERE ws.user_id = ?
                  AND ls.exercise_id = ?
                  AND ls.completed = 1
                  AND (? IS NULL OR ls.session_id <> ?)
                ORDER BY ls.logged_at DESC, ls.id DESC
                LIMIT 1
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, exerciseId);
            if (excludeSessionId == null) {
                ps.setNull(3, Types.INTEGER);
                ps.setNull(4, Types.INTEGER);
            } else {
                ps.setLong(3, excludeSessionId);
                ps.setLong(4, excludeSessionId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapSet(rs));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load previous performance", e);
        }
    }

    private static void setNullableDouble(PreparedStatement ps, int index, Double value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.REAL);
        } else {
            ps.setDouble(index, value);
        }
    }

    private static LoggedSet mapSet(ResultSet rs) throws SQLException {
        LoggedSet set = new LoggedSet();
        set.setId(rs.getLong("id"));
        set.setSessionId(rs.getLong("session_id"));
        set.setExerciseId(rs.getLong("exercise_id"));
        set.setExerciseNameSnapshot(rs.getString("exercise_name_snapshot"));
        set.setSetIndex(rs.getInt("set_index"));
        set.setWeightKg(rs.getDouble("weight_kg"));
        set.setReps(rs.getInt("reps"));
        set.setRpe(getNullableDouble(rs, "rpe"));
        set.setCompleted(rs.getInt("completed") == 1);
        set.setLoggedAt(Instant.parse(rs.getString("logged_at")));
        set.setEpley1rm(getNullableDouble(rs, "epley_1rm"));
        set.setBrzycki1rm(getNullableDouble(rs, "brzycki_1rm"));
        set.setLombardi1rm(getNullableDouble(rs, "lombardi_1rm"));
        return set;
    }

    private static Double getNullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }
}
