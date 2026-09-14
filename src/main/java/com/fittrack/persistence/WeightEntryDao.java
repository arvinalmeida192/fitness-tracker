package com.fittrack.persistence;

import com.fittrack.domain.DataAccessException;
import com.fittrack.domain.WeightEntry;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class WeightEntryDao {

    private final Database database;

    public WeightEntryDao(Database database) {
        this.database = database;
    }

    public WeightEntry insert(WeightEntry entry) {
        String sql = """
                INSERT INTO weight_entries(user_id, weight_kg, measured_at, note)
                VALUES (?, ?, ?, ?)
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, entry.getUserId());
            ps.setDouble(2, entry.getWeightKg());
            ps.setString(3, entry.getMeasuredAt().toString());
            ps.setString(4, entry.getNote());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    entry.setId(keys.getLong(1));
                }
            }
            return entry;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to insert weight entry", e);
        }
    }

    public List<WeightEntry> findByUserId(long userId) {
        String sql = """
                SELECT id, user_id, weight_kg, measured_at, note
                FROM weight_entries
                WHERE user_id = ?
                ORDER BY measured_at DESC, id DESC
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                List<WeightEntry> entries = new ArrayList<>();
                while (rs.next()) {
                    entries.add(map(rs));
                }
                return entries;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to list weight entries", e);
        }
    }

    public Optional<WeightEntry> findLatest(long userId) {
        String sql = """
                SELECT id, user_id, weight_kg, measured_at, note
                FROM weight_entries
                WHERE user_id = ?
                ORDER BY measured_at DESC, id DESC
                LIMIT 1
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
            throw new DataAccessException("Failed to load latest weight", e);
        }
    }

    private static WeightEntry map(ResultSet rs) throws SQLException {
        return new WeightEntry(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getDouble("weight_kg"),
                Instant.parse(rs.getString("measured_at")),
                rs.getString("note")
        );
    }
}
