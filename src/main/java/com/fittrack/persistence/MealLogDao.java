package com.fittrack.persistence;

import com.fittrack.domain.DataAccessException;
import com.fittrack.domain.Macros;
import com.fittrack.domain.MealType;
import com.fittrack.domain.MealLog;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class MealLogDao {

    private final Database database;

    public MealLogDao(Database database) {
        this.database = database;
    }

    public MealLog insert(MealLog log) {
        String sql = """
                INSERT INTO meal_logs(
                    user_id, eaten_at, meal_type, dish_id, portion_g, scale_factor,
                    kcal, protein_g, carbs_g, fat_g, fiber_g, notes
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, log.getUserId());
            ps.setString(2, log.getEatenAt().toString());
            ps.setString(3, log.getMealType().name());
            if (log.getDishId() == null) {
                ps.setNull(4, Types.INTEGER);
            } else {
                ps.setLong(4, log.getDishId());
            }
            ps.setDouble(5, log.getPortionG());
            ps.setDouble(6, log.getScaleFactor());
            Macros m = log.getMacros();
            ps.setDouble(7, m.getKcal());
            ps.setDouble(8, m.getProteinG());
            ps.setDouble(9, m.getCarbsG());
            ps.setDouble(10, m.getFatG());
            ps.setDouble(11, m.getFiberG());
            ps.setString(12, log.getNotes());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    log.setId(keys.getLong(1));
                }
            }
            return log;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to log meal", e);
        }
    }

    public void delete(long id, long userId) {
        String sql = "DELETE FROM meal_logs WHERE id = ? AND user_id = ?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.setLong(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to delete meal log", e);
        }
    }

    public List<MealLog> findForUserBetween(long userId, Instant fromInclusive, Instant toExclusive) {
        String sql = """
                SELECT ml.id, ml.user_id, ml.eaten_at, ml.meal_type, ml.dish_id, ml.portion_g, ml.scale_factor,
                       ml.kcal, ml.protein_g, ml.carbs_g, ml.fat_g, ml.fiber_g, ml.notes,
                       d.name AS dish_name
                FROM meal_logs ml
                LEFT JOIN dishes d ON d.id = ml.dish_id
                WHERE ml.user_id = ?
                  AND ml.eaten_at >= ?
                  AND ml.eaten_at < ?
                ORDER BY ml.eaten_at ASC, ml.id ASC
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, fromInclusive.toString());
            ps.setString(3, toExclusive.toString());
            try (ResultSet rs = ps.executeQuery()) {
                List<MealLog> logs = new ArrayList<>();
                while (rs.next()) {
                    logs.add(map(rs));
                }
                return logs;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to list meal logs", e);
        }
    }

    private static MealLog map(ResultSet rs) throws SQLException {
        MealLog log = new MealLog();
        log.setId(rs.getLong("id"));
        log.setUserId(rs.getLong("user_id"));
        log.setEatenAt(Instant.parse(rs.getString("eaten_at")));
        log.setMealType(MealType.valueOf(rs.getString("meal_type")));
        long dishId = rs.getLong("dish_id");
        log.setDishId(rs.wasNull() ? null : dishId);
        String dishName = rs.getString("dish_name");
        log.setDishName(dishName);
        log.setPortionG(rs.getDouble("portion_g"));
        log.setScaleFactor(rs.getDouble("scale_factor"));
        log.setMacros(new Macros(
                rs.getDouble("kcal"),
                rs.getDouble("protein_g"),
                rs.getDouble("carbs_g"),
                rs.getDouble("fat_g"),
                rs.getDouble("fiber_g")
        ));
        log.setNotes(rs.getString("notes"));
        return log;
    }
}
