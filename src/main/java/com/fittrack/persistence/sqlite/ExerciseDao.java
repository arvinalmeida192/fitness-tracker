package com.fittrack.persistence.sqlite;

import com.fittrack.domain.common.DataAccessException;
import com.fittrack.domain.common.MuscleGroup;
import com.fittrack.domain.exercise.Exercise;
import com.fittrack.domain.exercise.FreeExerciseMuscleMapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ExerciseDao {

    private final Database database;

    public ExerciseDao(Database database) {
        this.database = database;
    }

    public long count() {
        try (PreparedStatement ps = database.getConnection().prepareStatement("SELECT COUNT(*) FROM exercises");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count exercises", e);
        }
    }

    public Exercise insert(Exercise exercise) {
        String sql = """
                INSERT INTO exercises(
                    name, muscle_group, equipment, instructions, default_rest_sec,
                    primary_muscles, secondary_muscles, external_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, exercise);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    exercise.setId(keys.getLong(1));
                }
            }
            return exercise;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to insert exercise", e);
        }
    }

    public void update(Exercise exercise) {
        if (exercise.getId() == null) {
            throw new DataAccessException("Cannot update exercise without id");
        }
        String sql = """
                UPDATE exercises
                SET name = ?, muscle_group = ?, equipment = ?, instructions = ?, default_rest_sec = ?,
                    primary_muscles = ?, secondary_muscles = ?, external_id = ?
                WHERE id = ?
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            bind(ps, exercise);
            ps.setLong(9, exercise.getId());
            if (ps.executeUpdate() == 0) {
                throw new DataAccessException("Exercise not found: " + exercise.getId());
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update exercise", e);
        }
    }

    public void delete(long id) {
        try (PreparedStatement ps = database.getConnection().prepareStatement("DELETE FROM exercises WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to delete exercise (it may be used by a plan)", e);
        }
    }

    public Optional<Exercise> findById(long id) {
        String sql = """
                SELECT id, name, muscle_group, equipment, instructions, default_rest_sec,
                       primary_muscles, secondary_muscles, external_id
                FROM exercises WHERE id = ?
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
            throw new DataAccessException("Failed to load exercise", e);
        }
    }

    public List<Exercise> findAll() {
        String sql = """
                SELECT id, name, muscle_group, equipment, instructions, default_rest_sec,
                       primary_muscles, secondary_muscles, external_id
                FROM exercises
                ORDER BY name COLLATE NOCASE
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Exercise> list = new ArrayList<>();
            while (rs.next()) {
                list.add(map(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to list exercises", e);
        }
    }

    public void insertAll(List<Exercise> exercises) {
        ConnectionTxn.run(database, () -> {
            for (Exercise exercise : exercises) {
                insert(exercise);
            }
        });
    }

    private static void bind(PreparedStatement ps, Exercise exercise) throws SQLException {
        ps.setString(1, exercise.getName());
        ps.setString(2, exercise.getMuscleGroup().name());
        ps.setString(3, exercise.getEquipment());
        ps.setString(4, exercise.getInstructions());
        ps.setInt(5, exercise.getDefaultRestSec());
        ps.setString(6, FreeExerciseMuscleMapper.encode(exercise.getPrimaryMuscles()));
        ps.setString(7, FreeExerciseMuscleMapper.encode(exercise.getSecondaryMuscles()));
        if (exercise.getExternalId() == null || exercise.getExternalId().isBlank()) {
            ps.setNull(8, Types.VARCHAR);
        } else {
            ps.setString(8, exercise.getExternalId());
        }
    }

    private static Exercise map(ResultSet rs) throws SQLException {
        Exercise exercise = new Exercise();
        exercise.setId(rs.getLong("id"));
        exercise.setName(rs.getString("name"));
        MuscleGroup legacy = MuscleGroup.valueOf(rs.getString("muscle_group"));
        String primaryCsv = rs.getString("primary_muscles");
        List<MuscleGroup> primary = FreeExerciseMuscleMapper.decode(primaryCsv);
        if (primary.isEmpty()) {
            primary = List.of(legacy);
        }
        exercise.setPrimaryMuscles(primary);
        exercise.setSecondaryMuscles(FreeExerciseMuscleMapper.decode(rs.getString("secondary_muscles")));
        exercise.setEquipment(rs.getString("equipment"));
        exercise.setInstructions(rs.getString("instructions"));
        exercise.setDefaultRestSec(rs.getInt("default_rest_sec"));
        exercise.setExternalId(rs.getString("external_id"));
        return exercise;
    }
}
