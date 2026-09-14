package com.fittrack.persistence.sqlite;

import com.fittrack.domain.common.DataAccessException;
import com.fittrack.domain.common.Macros;
import com.fittrack.domain.nutrition.Dish;
import com.fittrack.domain.nutrition.DishItem;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class DishDao {

    private final Database database;

    public DishDao(Database database) {
        this.database = database;
    }

    public Dish insert(Dish dish) {
        String sql = """
                INSERT INTO dishes(user_id, name, notes, total_weight_g, yield_weight_g)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, dish.getUserId());
            ps.setString(2, dish.getName());
            ps.setString(3, dish.getNotes());
            ps.setDouble(4, dish.getTotalWeightG());
            if (dish.getYieldWeightG() == null) {
                ps.setNull(5, Types.REAL);
            } else {
                ps.setDouble(5, dish.getYieldWeightG());
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    dish.setId(keys.getLong(1));
                }
            }
            return dish;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to create dish", e);
        }
    }

    public void updateMeta(Dish dish) {
        String sql = """
                UPDATE dishes SET name = ?, notes = ?, total_weight_g = ?, yield_weight_g = ?
                WHERE id = ? AND user_id = ?
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, dish.getName());
            ps.setString(2, dish.getNotes());
            ps.setDouble(3, dish.getTotalWeightG());
            if (dish.getYieldWeightG() == null) {
                ps.setNull(4, Types.REAL);
            } else {
                ps.setDouble(4, dish.getYieldWeightG());
            }
            ps.setLong(5, dish.getId());
            ps.setLong(6, dish.getUserId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update dish", e);
        }
    }

    public void replaceItems(long dishId, List<DishItem> items) {
        try (PreparedStatement del = database.getConnection().prepareStatement(
                "DELETE FROM dish_items WHERE dish_id = ?")) {
            del.setLong(1, dishId);
            del.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to clear dish items", e);
        }
        String sql = "INSERT INTO dish_items(dish_id, ingredient_id, amount_g) VALUES (?, ?, ?)";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            for (DishItem item : items) {
                ps.setLong(1, dishId);
                ps.setLong(2, item.getIngredientId());
                ps.setDouble(3, item.getAmountG());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to save dish items", e);
        }
    }

    public void delete(long dishId, long userId) {
        String sql = "DELETE FROM dishes WHERE id = ? AND user_id = ?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, dishId);
            ps.setLong(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to delete dish", e);
        }
    }

    public List<Dish> listByUser(long userId) {
        String sql = """
                SELECT id, user_id, name, notes, total_weight_g, yield_weight_g
                FROM dishes WHERE user_id = ?
                ORDER BY name COLLATE NOCASE
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Dish> dishes = new ArrayList<>();
                while (rs.next()) {
                    dishes.add(mapDish(rs));
                }
                return dishes;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to list dishes", e);
        }
    }

    public Optional<Dish> findById(long dishId, long userId) {
        String sql = """
                SELECT id, user_id, name, notes, total_weight_g, yield_weight_g
                FROM dishes WHERE id = ? AND user_id = ?
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, dishId);
            ps.setLong(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                Dish dish = mapDish(rs);
                dish.setItems(findItems(dishId));
                return Optional.of(dish);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load dish", e);
        }
    }

    public List<DishItem> findItems(long dishId) {
        String sql = """
                SELECT di.id, di.dish_id, di.ingredient_id, di.amount_g,
                       i.name AS ingredient_name,
                       i.per100_kcal, i.protein_g, i.carbs_g, i.fat_g, i.fiber_g
                FROM dish_items di
                JOIN ingredients i ON i.id = di.ingredient_id
                WHERE di.dish_id = ?
                ORDER BY di.id ASC
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, dishId);
            try (ResultSet rs = ps.executeQuery()) {
                List<DishItem> items = new ArrayList<>();
                while (rs.next()) {
                    DishItem item = new DishItem();
                    item.setId(rs.getLong("id"));
                    item.setDishId(rs.getLong("dish_id"));
                    item.setIngredientId(rs.getLong("ingredient_id"));
                    item.setIngredientName(rs.getString("ingredient_name"));
                    item.setAmountG(rs.getDouble("amount_g"));
                    item.setPer100g(new Macros(
                            rs.getDouble("per100_kcal"),
                            rs.getDouble("protein_g"),
                            rs.getDouble("carbs_g"),
                            rs.getDouble("fat_g"),
                            rs.getDouble("fiber_g")
                    ));
                    items.add(item);
                }
                return items;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load dish items", e);
        }
    }

    private static Dish mapDish(ResultSet rs) throws SQLException {
        Dish dish = new Dish();
        dish.setId(rs.getLong("id"));
        dish.setUserId(rs.getLong("user_id"));
        dish.setName(rs.getString("name"));
        dish.setNotes(rs.getString("notes"));
        dish.setTotalWeightG(rs.getDouble("total_weight_g"));
        double yield = rs.getDouble("yield_weight_g");
        dish.setYieldWeightG(rs.wasNull() ? null : yield);
        return dish;
    }
}
