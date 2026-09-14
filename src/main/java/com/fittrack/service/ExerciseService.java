package com.fittrack.service;

import com.fittrack.domain.MuscleGroup;
import com.fittrack.domain.ValidationException;
import com.fittrack.domain.Exercise;
import com.fittrack.domain.FreeExerciseDbLoader;
import com.fittrack.domain.FreeExerciseMuscleMapper;
import com.fittrack.persistence.Database;
import com.fittrack.persistence.ExerciseDao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class ExerciseService {

    private static final String META_CATALOG_KEY = "exercise_catalog";
    private static final String CATALOG_MARKER = "free-exercise-db-v1";

    private final Database database;
    private final ExerciseDao exerciseDao;
    private volatile List<Exercise> cache;

    public ExerciseService(Database database, ExerciseDao exerciseDao) {
        this.database = database;
        this.exerciseDao = exerciseDao;
    }

    /**
     * Seeds / merges the bundled free-exercise-db catalog (primary + secondary muscles).
     */
    public synchronized void ensureCatalog() {
        if (CATALOG_MARKER.equals(readMeta(META_CATALOG_KEY))) {
            if (exerciseDao.count() == 0) {
                importCatalog(true);
            }
            return;
        }
        importCatalog(exerciseDao.count() == 0);
        writeMeta(META_CATALOG_KEY, CATALOG_MARKER);
    }

    public List<Exercise> listAll() {
        return List.copyOf(cachedAll());
    }

    /**
     * Search by name / equipment; muscle filter matches primary or secondary targets.
     */
    public List<Exercise> search(String query, MuscleGroup muscleFilter) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Exercise> all = cachedAll();
        Set<Long> matchedIds = new LinkedHashSet<>();
        List<Exercise> matches = new ArrayList<>();

        for (Exercise exercise : all) {
            boolean muscleOk = muscleFilter == null || exercise.targetsMuscle(muscleFilter);
            boolean nameOk = needle.isEmpty()
                    || exercise.getName().toLowerCase(Locale.ROOT).contains(needle)
                    || (exercise.getEquipment() != null
                    && exercise.getEquipment().toLowerCase(Locale.ROOT).contains(needle));
            if (muscleOk && nameOk && matchedIds.add(exercise.getId())) {
                matches.add(exercise);
            }
        }

        matches.sort(Comparator.comparing(Exercise::getName, String.CASE_INSENSITIVE_ORDER));
        return matches;
    }

    private Map<MuscleGroup, List<Exercise>> groupByMuscle(List<Exercise> exercises) {
        Map<MuscleGroup, List<Exercise>> map = new EnumMap<>(MuscleGroup.class);
        for (MuscleGroup group : MuscleGroup.values()) {
            map.put(group, new ArrayList<>());
        }
        for (Exercise exercise : exercises) {
            map.get(exercise.getMuscleGroup()).add(exercise);
        }
        map.values().forEach(list -> list.sort(Comparator.comparing(Exercise::getName, String.CASE_INSENSITIVE_ORDER)));
        return map;
    }

    private Set<String> distinctEquipment(List<Exercise> exercises) {
        Set<String> equipment = new HashSet<>();
        for (Exercise exercise : exercises) {
            if (exercise.getEquipment() != null && !exercise.getEquipment().isBlank()) {
                equipment.add(exercise.getEquipment().trim());
            }
        }
        return Collections.unmodifiableSet(equipment);
    }

    public Exercise create(
            String name,
            MuscleGroup muscleGroup,
            String equipment,
            String instructions,
            int defaultRestSec,
            List<MuscleGroup> secondaryMuscles
    ) {
        Exercise exercise = buildValidated(
                null, name, muscleGroup, equipment, instructions, defaultRestSec, secondaryMuscles);
        ensureUniqueName(exercise.getName(), null);
        Exercise saved = exerciseDao.insert(exercise);
        invalidateCache();
        return saved;
    }

    public Exercise update(
            long id,
            String name,
            MuscleGroup muscleGroup,
            String equipment,
            String instructions,
            int defaultRestSec,
            List<MuscleGroup> secondaryMuscles
    ) {
        Exercise existing = exerciseDao.findById(id)
                .orElseThrow(() -> new ValidationException("Exercise not found"));
        Exercise exercise = buildValidated(
                existing.getId(), name, muscleGroup, equipment, instructions, defaultRestSec, secondaryMuscles);
        exercise.setExternalId(existing.getExternalId());
        ensureUniqueName(exercise.getName(), id);
        exerciseDao.update(exercise);
        invalidateCache();
        return exercise;
    }

    public void delete(long id) {
        if (exerciseDao.findById(id).isEmpty()) {
            throw new ValidationException("Exercise not found");
        }
        exerciseDao.delete(id);
        invalidateCache();
    }

    public String summaryLabel(List<Exercise> visible) {
        Map<MuscleGroup, List<Exercise>> grouped = groupByMuscle(visible);
        long nonEmptyGroups = grouped.values().stream().filter(list -> !list.isEmpty()).count();
        String equipmentSample = distinctEquipment(visible).stream()
                .sorted()
                .limit(4)
                .collect(Collectors.joining(", "));
        return visible.size() + " exercises across " + nonEmptyGroups + " muscle groups"
                + (equipmentSample.isEmpty() ? "" : " · equipment: " + equipmentSample);
    }

    private void importCatalog(boolean replaceEmptyOnlyPath) {
        List<Exercise> catalog = FreeExerciseDbLoader.loadBundled();
        if (replaceEmptyOnlyPath && exerciseDao.count() == 0) {
            exerciseDao.insertAll(catalog);
            invalidateCache();
            return;
        }
        Set<String> existingNames = new HashSet<>();
        for (Exercise exercise : exerciseDao.findAll()) {
            existingNames.add(exercise.getName().toLowerCase(Locale.ROOT));
        }
        List<Exercise> missing = new ArrayList<>();
        for (Exercise exercise : catalog) {
            if (!existingNames.contains(exercise.getName().toLowerCase(Locale.ROOT))) {
                missing.add(exercise);
            }
        }
        if (!missing.isEmpty()) {
            exerciseDao.insertAll(missing);
        }
        invalidateCache();
    }

    private List<Exercise> cachedAll() {
        List<Exercise> local = cache;
        if (local == null) {
            synchronized (this) {
                local = cache;
                if (local == null) {
                    local = List.copyOf(exerciseDao.findAll());
                    cache = local;
                }
            }
        }
        return local;
    }

    private void invalidateCache() {
        cache = null;
    }

    private String readMeta(String key) {
        String sql = "SELECT value FROM app_meta WHERE key = ?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return rs.getString(1);
            }
        } catch (SQLException e) {
            return null;
        }
    }

    private void writeMeta(String key, String value) {
        String sql = """
                INSERT INTO app_meta(key, value) VALUES(?, ?)
                ON CONFLICT(key) DO UPDATE SET value = excluded.value
                """;
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new ValidationException("Failed to record exercise catalog import");
        }
    }

    private void ensureUniqueName(String name, Long excludeId) {
        for (Exercise exercise : cachedAll()) {
            if (exercise.getName().equalsIgnoreCase(name)
                    && (excludeId == null || !excludeId.equals(exercise.getId()))) {
                throw new ValidationException("An exercise named \"" + name + "\" already exists");
            }
        }
    }

    private static Exercise buildValidated(
            Long id,
            String name,
            MuscleGroup muscleGroup,
            String equipment,
            String instructions,
            int defaultRestSec,
            List<MuscleGroup> secondaryMuscles
    ) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("Exercise name is required");
        }
        if (muscleGroup == null) {
            throw new ValidationException("Primary muscle is required");
        }
        if (defaultRestSec < 0 || defaultRestSec > 600) {
            throw new ValidationException("Default rest must be between 0 and 600 seconds");
        }
        Exercise exercise = new Exercise();
        exercise.setId(id);
        exercise.setName(name.trim());
        exercise.setPrimaryMuscles(List.of(muscleGroup));
        List<MuscleGroup> secondary = secondaryMuscles == null ? List.of() : secondaryMuscles;
        secondary = secondary.stream().filter(m -> m != null && m != muscleGroup).distinct().toList();
        exercise.setSecondaryMuscles(secondary);
        exercise.setEquipment(blankToNull(equipment));
        exercise.setInstructions(blankToNull(instructions));
        exercise.setDefaultRestSec(defaultRestSec);
        return exercise;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public static List<MuscleGroup> parseMuscleList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        StringBuilder normalized = new StringBuilder();
        for (String part : raw.split(",")) {
            String token = part.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
            if (token.isEmpty()) {
                continue;
            }
            if (normalized.length() > 0) {
                normalized.append(',');
            }
            normalized.append(token);
        }
        return FreeExerciseMuscleMapper.decode(normalized.toString());
    }
}
