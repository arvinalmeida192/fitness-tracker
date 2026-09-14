package com.fittrack.persistence.sqlite;

import com.fittrack.domain.common.DataAccessException;
import com.fittrack.domain.common.Macros;
import com.fittrack.domain.nutrition.Ingredient;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class IngredientDao {

    private final Database database;

    public IngredientDao(Database database) {
        this.database = database;
    }

    public Ingredient upsert(Ingredient ingredient) {
        if (ingredient.getFdcId() != null) {
            Optional<Ingredient> existing = findByFdcId(ingredient.getFdcId());
            if (existing.isPresent()) {
                ingredient.setId(existing.get().getId());
                return update(ingredient);
            }
        }
        return insert(ingredient);
    }

    public Ingredient insert(Ingredient ingredient) {
        String sql = """
                INSERT INTO ingredients(
                    fdc_id, name, brand, per100_kcal, protein_g, carbs_g, fat_g, fiber_g, source, fetched_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, ingredient);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    ingredient.setId(keys.getLong(1));
                }
            }
            return ingredient;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to insert ingredient", e);
        }
    }

    public Ingredient update(Ingredient ingredient) {
        String sql = """
                UPDATE ingredients SET
                    fdc_id = ?, name = ?, brand = ?, per100_kcal = ?, protein_g = ?,
                    carbs_g = ?, fat_g = ?, fiber_g = ?, source = ?, fetched_at = ?
                WHERE id = ?
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            bind(ps, ingredient);
            ps.setLong(11, ingredient.getId());
            ps.executeUpdate();
            return ingredient;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update ingredient", e);
        }
    }

    public Optional<Ingredient> findById(long id) {
        String sql = """
                SELECT id, fdc_id, name, brand, per100_kcal, protein_g, carbs_g, fat_g, fiber_g, source, fetched_at
                FROM ingredients WHERE id = ?
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load ingredient", e);
        }
    }

    public Optional<Ingredient> findByFdcId(int fdcId) {
        String sql = """
                SELECT id, fdc_id, name, brand, per100_kcal, protein_g, carbs_g, fat_g, fiber_g, source, fetched_at
                FROM ingredients WHERE fdc_id = ?
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setInt(1, fdcId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load ingredient by FDC id", e);
        }
    }

    public List<Ingredient> searchByName(String query, int limit) {
        String sql = """
                SELECT id, fdc_id, name, brand, per100_kcal, protein_g, carbs_g, fat_g, fiber_g, source, fetched_at
                FROM ingredients
                WHERE lower(name) LIKE ? OR lower(ifnull(brand, '')) LIKE ?
                ORDER BY name COLLATE NOCASE
                LIMIT ?
                """;
        String like = "%" + query.toLowerCase().trim() + "%";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, like);
            ps.setString(2, like);
            ps.setInt(3, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                List<Ingredient> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(map(rs));
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to search ingredients", e);
        }
    }

    public List<Ingredient> listAll(int limit) {
        String sql = """
                SELECT id, fdc_id, name, brand, per100_kcal, protein_g, carbs_g, fat_g, fiber_g, source, fetched_at
                FROM ingredients
                ORDER BY name COLLATE NOCASE
                LIMIT ?
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                List<Ingredient> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(map(rs));
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to list ingredients", e);
        }
    }

    public int count() {
        try (PreparedStatement ps = database.getConnection().prepareStatement("SELECT COUNT(*) FROM ingredients");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count ingredients", e);
        }
    }

    private static void bind(PreparedStatement ps, Ingredient ingredient) throws SQLException {
        if (ingredient.getFdcId() == null) {
            ps.setNull(1, Types.INTEGER);
        } else {
            ps.setInt(1, ingredient.getFdcId());
        }
        ps.setString(2, ingredient.getName());
        ps.setString(3, ingredient.getBrand());
        Macros m = ingredient.getPer100g();
        ps.setDouble(4, m.getKcal());
        ps.setDouble(5, m.getProteinG());
        ps.setDouble(6, m.getCarbsG());
        ps.setDouble(7, m.getFatG());
        ps.setDouble(8, m.getFiberG());
        ps.setString(9, ingredient.getSource());
        ps.setString(10, ingredient.getFetchedAt().toString());
    }

    private static Ingredient map(ResultSet rs) throws SQLException {
        Ingredient ingredient = new Ingredient();
        ingredient.setId(rs.getLong("id"));
        int fdc = rs.getInt("fdc_id");
        ingredient.setFdcId(rs.wasNull() ? null : fdc);
        ingredient.setName(rs.getString("name"));
        ingredient.setBrand(rs.getString("brand"));
        ingredient.setPer100g(new Macros(
                rs.getDouble("per100_kcal"),
                rs.getDouble("protein_g"),
                rs.getDouble("carbs_g"),
                rs.getDouble("fat_g"),
                rs.getDouble("fiber_g")
        ));
        ingredient.setSource(rs.getString("source"));
        ingredient.setFetchedAt(Instant.parse(rs.getString("fetched_at")));
        return ingredient;
    }
}
