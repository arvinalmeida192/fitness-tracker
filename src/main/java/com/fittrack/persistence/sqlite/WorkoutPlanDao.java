package com.fittrack.persistence.sqlite;

import com.fittrack.domain.common.DataAccessException;
import com.fittrack.domain.workout.PlanItem;
import com.fittrack.domain.workout.WorkoutPlan;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class WorkoutPlanDao {

    private final Database database;

    public WorkoutPlanDao(Database database) {
        this.database = database;
    }

    public List<WorkoutPlan> findByUser(long userId, boolean includeArchived) {
        String sql = """
                SELECT id, user_id, name, description, archived
                FROM workout_plans
                WHERE user_id = ?
                """ + (includeArchived ? "" : " AND archived = 0") + """
                 ORDER BY archived ASC, name COLLATE NOCASE
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                List<WorkoutPlan> plans = new ArrayList<>();
                while (rs.next()) {
                    plans.add(mapPlan(rs));
                }
                return plans;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to list workout plans", e);
        }
    }

    public Optional<WorkoutPlan> findById(long id) {
        String sql = """
                SELECT id, user_id, name, description, archived
                FROM workout_plans WHERE id = ?
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapPlan(rs));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load workout plan", e);
        }
    }

    public WorkoutPlan insert(WorkoutPlan plan) {
        String sql = """
                INSERT INTO workout_plans(user_id, name, description, archived)
                VALUES (?, ?, ?, ?)
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, plan.getUserId());
            ps.setString(2, plan.getName());
            ps.setString(3, plan.getDescription());
            ps.setInt(4, plan.isArchived() ? 1 : 0);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    plan.setId(keys.getLong(1));
                }
            }
            return plan;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to create workout plan", e);
        }
    }

    public void updateMeta(WorkoutPlan plan) {
        String sql = """
                UPDATE workout_plans
                SET name = ?, description = ?, archived = ?
                WHERE id = ? AND user_id = ?
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, plan.getName());
            ps.setString(2, plan.getDescription());
            ps.setInt(3, plan.isArchived() ? 1 : 0);
            ps.setLong(4, plan.getId());
            ps.setLong(5, plan.getUserId());
            if (ps.executeUpdate() == 0) {
                throw new DataAccessException("Workout plan not found");
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update workout plan", e);
        }
    }

    public void delete(long planId) {
        try (PreparedStatement ps = database.getConnection().prepareStatement(
                "DELETE FROM workout_plans WHERE id = ?")) {
            ps.setLong(1, planId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to delete workout plan", e);
        }
    }

    public List<PlanItem> findItems(long planId) {
        String sql = """
                SELECT pi.id, pi.plan_id, pi.exercise_id, e.name AS exercise_name,
                       pi.order_index, pi.target_sets, pi.reps_min, pi.reps_max,
                       pi.target_weight, pi.rest_sec, pi.notes
                FROM plan_items pi
                JOIN exercises e ON e.id = pi.exercise_id
                WHERE pi.plan_id = ?
                ORDER BY pi.order_index ASC, pi.id ASC
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, planId);
            try (ResultSet rs = ps.executeQuery()) {
                List<PlanItem> items = new ArrayList<>();
                while (rs.next()) {
                    items.add(mapItem(rs));
                }
                return items;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load plan items", e);
        }
    }

    public void replaceItems(long planId, List<PlanItem> items) {
        ConnectionTxn.run(database, () -> {
            try (PreparedStatement del = database.getConnection().prepareStatement(
                    "DELETE FROM plan_items WHERE plan_id = ?")) {
                del.setLong(1, planId);
                del.executeUpdate();
            } catch (SQLException e) {
                throw new DataAccessException("Failed to clear plan items", e);
            }

            String sql = """
                    INSERT INTO plan_items(plan_id, exercise_id, order_index, target_sets,
                                           reps_min, reps_max, target_weight, rest_sec, notes)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
                int order = 0;
                for (PlanItem item : items) {
                    ps.setLong(1, planId);
                    ps.setLong(2, item.getExerciseId());
                    ps.setInt(3, order++);
                    ps.setInt(4, item.getTargetSets());
                    ps.setInt(5, item.getRepsMin());
                    ps.setInt(6, item.getRepsMax());
                    if (item.getTargetWeight() == null) {
                        ps.setNull(7, Types.REAL);
                    } else {
                        ps.setDouble(7, item.getTargetWeight());
                    }
                    if (item.getRestSec() == null) {
                        ps.setNull(8, Types.INTEGER);
                    } else {
                        ps.setInt(8, item.getRestSec());
                    }
                    ps.setString(9, item.getNotes());
                    ps.addBatch();
                }
                ps.executeBatch();
            } catch (SQLException e) {
                throw new DataAccessException("Failed to save plan items", e);
            }
        });
    }

    private static WorkoutPlan mapPlan(ResultSet rs) throws SQLException {
        return new WorkoutPlan(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getInt("archived") == 1
        );
    }

    private static PlanItem mapItem(ResultSet rs) throws SQLException {
        PlanItem item = new PlanItem();
        item.setId(rs.getLong("id"));
        item.setPlanId(rs.getLong("plan_id"));
        item.setExerciseId(rs.getLong("exercise_id"));
        item.setExerciseName(rs.getString("exercise_name"));
        item.setOrderIndex(rs.getInt("order_index"));
        item.setTargetSets(rs.getInt("target_sets"));
        item.setRepsMin(rs.getInt("reps_min"));
        item.setRepsMax(rs.getInt("reps_max"));
        double weight = rs.getDouble("target_weight");
        item.setTargetWeight(rs.wasNull() ? null : weight);
        int rest = rs.getInt("rest_sec");
        item.setRestSec(rs.wasNull() ? null : rest);
        item.setNotes(rs.getString("notes"));
        return item;
    }
}
