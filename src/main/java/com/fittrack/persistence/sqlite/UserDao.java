package com.fittrack.persistence.sqlite;

import com.fittrack.domain.common.DataAccessException;
import com.fittrack.domain.user.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Optional;

public final class UserDao {

    private final Database database;

    public UserDao(Database database) {
        this.database = database;
    }

    public User insert(User user) {
        String sql = """
                INSERT INTO users(username, password_hash, salt, created_at)
                VALUES (?, ?, ?, ?)
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPasswordHash());
            ps.setString(3, user.getSalt());
            ps.setString(4, user.getCreatedAt().toString());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    user.setId(keys.getLong(1));
                }
            }
            return user;
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("unique")) {
                throw new DataAccessException("Username already exists: " + user.getUsername(), e);
            }
            throw new DataAccessException("Failed to insert user", e);
        }
    }

    public Optional<User> findByUsername(String username) {
        String sql = "SELECT id, username, password_hash, salt, created_at FROM users WHERE username = ? COLLATE NOCASE";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find user", e);
        }
    }

    private static User map(ResultSet rs) throws SQLException {
        return new User(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getString("salt"),
                Instant.parse(rs.getString("created_at"))
        );
    }

    public Connection connection() {
        return database.getConnection();
    }
}
