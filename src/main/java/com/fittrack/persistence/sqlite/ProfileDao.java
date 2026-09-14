package com.fittrack.persistence.sqlite;

import com.fittrack.domain.common.ActivityLevel;
import com.fittrack.domain.common.DataAccessException;
import com.fittrack.domain.common.OneRepMaxFormula;
import com.fittrack.domain.common.Sex;
import com.fittrack.domain.common.Units;
import com.fittrack.domain.user.Profile;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;

public final class ProfileDao {

    private final Database database;

    public ProfileDao(Database database) {
        this.database = database;
    }

    public void upsert(Profile profile) {
        String sql = """
                INSERT INTO profiles(user_id, full_name, date_of_birth, sex, height_cm,
                                     activity_level, preferred_1rm_formula, units)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(user_id) DO UPDATE SET
                    full_name = excluded.full_name,
                    date_of_birth = excluded.date_of_birth,
                    sex = excluded.sex,
                    height_cm = excluded.height_cm,
                    activity_level = excluded.activity_level,
                    preferred_1rm_formula = excluded.preferred_1rm_formula,
                    units = excluded.units
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, profile.getUserId());
            ps.setString(2, profile.getFullName());
            ps.setString(3, profile.getDateOfBirth().toString());
            ps.setString(4, profile.getSex().name());
            ps.setDouble(5, profile.getHeightCm());
            ps.setString(6, profile.getActivityLevel().name());
            ps.setString(7, profile.getPreferred1rmFormula().name());
            ps.setString(8, profile.getUnits().name());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to save profile", e);
        }
    }

    public Optional<Profile> findByUserId(long userId) {
        String sql = """
                SELECT user_id, full_name, date_of_birth, sex, height_cm,
                       activity_level, preferred_1rm_formula, units
                FROM profiles WHERE user_id = ?
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                Profile profile = new Profile();
                profile.setUserId(rs.getLong("user_id"));
                profile.setFullName(rs.getString("full_name"));
                profile.setDateOfBirth(LocalDate.parse(rs.getString("date_of_birth")));
                profile.setSex(Sex.valueOf(rs.getString("sex")));
                profile.setHeightCm(rs.getDouble("height_cm"));
                profile.setActivityLevel(ActivityLevel.valueOf(rs.getString("activity_level")));
                profile.setPreferred1rmFormula(OneRepMaxFormula.valueOf(rs.getString("preferred_1rm_formula")));
                profile.setUnits(Units.valueOf(rs.getString("units")));
                return Optional.of(profile);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load profile", e);
        }
    }
}
