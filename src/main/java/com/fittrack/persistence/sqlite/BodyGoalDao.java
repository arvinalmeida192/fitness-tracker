package com.fittrack.persistence.sqlite;

import com.fittrack.domain.common.DataAccessException;
import com.fittrack.domain.user.BodyGoal;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.Optional;

public final class BodyGoalDao {

    private final Database database;

    public BodyGoalDao(Database database) {
        this.database = database;
    }

    public Optional<BodyGoal> findByUserId(long userId) {
        String sql = """
                SELECT user_id, target_weight_kg, target_date
                FROM body_goals
                WHERE user_id = ?
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load body goal", e);
        }
    }

    public BodyGoal upsert(BodyGoal goal) {
        String sql = """
                INSERT INTO body_goals(user_id, target_weight_kg, target_date)
                VALUES (?, ?, ?)
                ON CONFLICT(user_id) DO UPDATE SET
                    target_weight_kg = excluded.target_weight_kg,
                    target_date = excluded.target_date
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, goal.getUserId());
            ps.setDouble(2, goal.getTargetWeightKg());
            if (goal.getTargetDate() == null) {
                ps.setNull(3, Types.VARCHAR);
            } else {
                ps.setString(3, goal.getTargetDate().toString());
            }
            ps.executeUpdate();
            return goal;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to save body goal", e);
        }
    }

    public void delete(long userId) {
        String sql = "DELETE FROM body_goals WHERE user_id = ?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to clear body goal", e);
        }
    }

    private static BodyGoal map(ResultSet rs) throws SQLException {
        BodyGoal goal = new BodyGoal();
        goal.setUserId(rs.getLong("user_id"));
        goal.setTargetWeightKg(rs.getDouble("target_weight_kg"));
        String date = rs.getString("target_date");
        goal.setTargetDate(date == null || date.isBlank() ? null : LocalDate.parse(date));
        return goal;
    }
}
