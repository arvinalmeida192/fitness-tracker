package com.fittrack.persistence.sqlite;

import com.fittrack.domain.common.DataAccessException;
import com.fittrack.domain.common.OneRepMaxFormula;
import com.fittrack.domain.common.PersonalRecordType;
import com.fittrack.domain.progress.PersonalRecord;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class PersonalRecordDao {

    private final Database database;

    public PersonalRecordDao(Database database) {
        this.database = database;
    }

    public PersonalRecord insert(PersonalRecord record) {
        String sql = """
                INSERT INTO personal_records(
                    user_id, exercise_id, pr_type, reps, weight_kg,
                    estimated_1rm, formula, achieved_at, logged_set_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, record.getUserId());
            ps.setLong(2, record.getExerciseId());
            ps.setString(3, record.getPrType().name());
            if (record.getReps() == null) {
                ps.setNull(4, Types.INTEGER);
            } else {
                ps.setInt(4, record.getReps());
            }
            setNullableDouble(ps, 5, record.getWeightKg());
            setNullableDouble(ps, 6, record.getEstimated1rm());
            if (record.getFormula() == null) {
                ps.setNull(7, Types.VARCHAR);
            } else {
                ps.setString(7, record.getFormula().name());
            }
            ps.setString(8, record.getAchievedAt().toString());
            if (record.getLoggedSetId() == null) {
                ps.setNull(9, Types.INTEGER);
            } else {
                ps.setLong(9, record.getLoggedSetId());
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    record.setId(keys.getLong(1));
                }
            }
            return record;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to save personal record", e);
        }
    }

    public List<PersonalRecord> findByUser(long userId) {
        String sql = """
                SELECT pr.id, pr.user_id, pr.exercise_id, e.name AS exercise_name,
                       pr.pr_type, pr.reps, pr.weight_kg, pr.estimated_1rm, pr.formula,
                       pr.achieved_at, pr.logged_set_id
                FROM personal_records pr
                JOIN exercises e ON e.id = pr.exercise_id
                WHERE pr.user_id = ?
                ORDER BY pr.achieved_at DESC, pr.id DESC
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                List<PersonalRecord> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(map(rs));
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to list personal records", e);
        }
    }

    public Optional<PersonalRecord> findBestHeaviestAtReps(long userId, long exerciseId, int reps) {
        String sql = """
                SELECT pr.id, pr.user_id, pr.exercise_id, e.name AS exercise_name,
                       pr.pr_type, pr.reps, pr.weight_kg, pr.estimated_1rm, pr.formula,
                       pr.achieved_at, pr.logged_set_id
                FROM personal_records pr
                JOIN exercises e ON e.id = pr.exercise_id
                WHERE pr.user_id = ?
                  AND pr.exercise_id = ?
                  AND pr.pr_type = ?
                  AND pr.reps = ?
                ORDER BY pr.weight_kg DESC, pr.achieved_at DESC
                LIMIT 1
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, exerciseId);
            ps.setString(3, PersonalRecordType.HEAVIEST_AT_REPS.name());
            ps.setInt(4, reps);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load heaviest-at-reps PR", e);
        }
    }

    public Optional<PersonalRecord> findBestEstimated1rm(long userId, long exerciseId) {
        String sql = """
                SELECT pr.id, pr.user_id, pr.exercise_id, e.name AS exercise_name,
                       pr.pr_type, pr.reps, pr.weight_kg, pr.estimated_1rm, pr.formula,
                       pr.achieved_at, pr.logged_set_id
                FROM personal_records pr
                JOIN exercises e ON e.id = pr.exercise_id
                WHERE pr.user_id = ?
                  AND pr.exercise_id = ?
                  AND pr.pr_type = ?
                ORDER BY pr.estimated_1rm DESC, pr.achieved_at DESC
                LIMIT 1
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, exerciseId);
            ps.setString(3, PersonalRecordType.ESTIMATED_1RM.name());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load estimated-1RM PR", e);
        }
    }

    private static PersonalRecord map(ResultSet rs) throws SQLException {
        PersonalRecord record = new PersonalRecord();
        record.setId(rs.getLong("id"));
        record.setUserId(rs.getLong("user_id"));
        record.setExerciseId(rs.getLong("exercise_id"));
        record.setExerciseName(rs.getString("exercise_name"));
        record.setPrType(PersonalRecordType.valueOf(rs.getString("pr_type")));
        int reps = rs.getInt("reps");
        record.setReps(rs.wasNull() ? null : reps);
        record.setWeightKg(getNullableDouble(rs, "weight_kg"));
        record.setEstimated1rm(getNullableDouble(rs, "estimated_1rm"));
        String formula = rs.getString("formula");
        record.setFormula(formula == null ? null : OneRepMaxFormula.valueOf(formula));
        record.setAchievedAt(Instant.parse(rs.getString("achieved_at")));
        long setId = rs.getLong("logged_set_id");
        record.setLoggedSetId(rs.wasNull() ? null : setId);
        return record;
    }

    private static void setNullableDouble(PreparedStatement ps, int index, Double value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.REAL);
        } else {
            ps.setDouble(index, value);
        }
    }

    private static Double getNullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }
}
