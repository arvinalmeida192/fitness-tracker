package com.fittrack.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Loads the bundled free-exercise-db JSON catalog into {@link Exercise} domain objects.
 */
public final class FreeExerciseDbLoader {

    private static final String RESOURCE = "/free-exercise-db.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FreeExerciseDbLoader() {
    }

    public static List<Exercise> loadBundled() {
        try (InputStream in = FreeExerciseDbLoader.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource: " + RESOURCE);
            }
            JsonNode root = MAPPER.readTree(in);
            if (!root.isArray()) {
                throw new IllegalStateException("free-exercise-db.json must be a JSON array");
            }
            List<Exercise> exercises = new ArrayList<>();
            for (JsonNode node : root) {
                exercises.add(mapNode(node));
            }
            return exercises;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load free-exercise-db catalog", e);
        }
    }

    private static Exercise mapNode(JsonNode node) {
        Exercise exercise = new Exercise();
        exercise.setName(text(node, "name"));
        exercise.setExternalId(text(node, "id"));
        exercise.setEquipment(capitalizeEquipment(text(node, "equipment")));
        exercise.setInstructions(joinInstructions(node.get("instructions")));
        exercise.setDefaultRestSec(defaultRestFor(node));

        List<String> primaryLabels = stringList(node.get("primaryMuscles"));
        List<String> secondaryLabels = stringList(node.get("secondaryMuscles"));
        List<MuscleGroup> primary = new ArrayList<>(FreeExerciseMuscleMapper.mapAll(primaryLabels));
        List<MuscleGroup> secondary = new ArrayList<>(FreeExerciseMuscleMapper.mapAll(secondaryLabels));
        secondary.removeIf(primary::contains);
        if (primary.isEmpty()) {
            primary = new ArrayList<>(List.of(MuscleGroup.OTHER));
        }
        exercise.setPrimaryMuscles(primary);
        exercise.setSecondaryMuscles(secondary);
        return exercise;
    }

    private static int defaultRestFor(JsonNode node) {
        String mechanic = text(node, "mechanic");
        String category = text(node, "category");
        if ("cardio".equalsIgnoreCase(category)) {
            return 0;
        }
        if ("compound".equalsIgnoreCase(mechanic)) {
            return 150;
        }
        if ("isolation".equalsIgnoreCase(mechanic)) {
            return 75;
        }
        return 90;
    }

    private static String joinInstructions(JsonNode instructions) {
        if (instructions == null || !instructions.isArray() || instructions.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (JsonNode step : instructions) {
            if (!step.isTextual()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(i++).append(". ").append(step.asText().trim());
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static List<String> stringList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return values;
        }
        for (Iterator<JsonNode> it = node.elements(); it.hasNext(); ) {
            JsonNode child = it.next();
            if (child.isTextual()) {
                values.add(child.asText());
            }
        }
        return values;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isTextual()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private static String capitalizeEquipment(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if ("body only".equalsIgnoreCase(raw)) {
            return "Bodyweight";
        }
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }
}
