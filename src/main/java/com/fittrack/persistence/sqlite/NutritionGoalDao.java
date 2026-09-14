package com.fittrack.persistence.sqlite;

import com.fittrack.domain.common.DataAccessException;
import com.fittrack.domain.nutrition.NutritionGoal;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.Optional;

public final class NutritionGoalDao {

    private final Database database;

    public NutritionGoalDao(Database database) {
        this.database = database;
    }

    public Optional<NutritionGoal> findByUserId(long userId) {
        String sql = """
                SELECT user_id, kcal, protein_g, carbs_g, fat_g, fiber_g, effective_from
                FROM nutrition_goals
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
            throw new DataAccessException("Failed to load nutrition goal", e);
        }
    }

    public NutritionGoal upsert(NutritionGoal goal) {
        String sql = """
                INSERT INTO nutrition_goals(user_id, kcal, protein_g, carbs_g, fat_g, fiber_g, effective_from)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(user_id) DO UPDATE SET
                    kcal = excluded.kcal,
                    protein_g = excluded.protein_g,
                    carbs_g = excluded.carbs_g,
                    fat_g = excluded.fat_g,
                    fiber_g = excluded.fiber_g,
                    effective_from = excluded.effective_from
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, goal.getUserId());
            ps.setDouble(2, goal.getKcal());
            ps.setDouble(3, goal.getProteinG());
            ps.setDouble(4, goal.getCarbsG());
            ps.setDouble(5, goal.getFatG());
            if (goal.getFiberG() == null) {
                ps.setNull(6, Types.REAL);
            } else {
                ps.setDouble(6, goal.getFiberG());
            }
            ps.setString(7, goal.getEffectiveFrom().toString());
            ps.executeUpdate();
            return goal;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to save nutrition goal", e);
        }
    }

    private static NutritionGoal map(ResultSet rs) throws SQLException {
        NutritionGoal goal = new NutritionGoal();
        goal.setUserId(rs.getLong("user_id"));
        goal.setKcal(rs.getDouble("kcal"));
        goal.setProteinG(rs.getDouble("protein_g"));
        goal.setCarbsG(rs.getDouble("carbs_g"));
        goal.setFatG(rs.getDouble("fat_g"));
        double fiber = rs.getDouble("fiber_g");
        goal.setFiberG(rs.wasNull() ? null : fiber);
        goal.setEffectiveFrom(LocalDate.parse(rs.getString("effective_from")));
        return goal;
    }
}
