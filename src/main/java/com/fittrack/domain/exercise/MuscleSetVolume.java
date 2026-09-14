package com.fittrack.domain.exercise;

import com.fittrack.domain.common.MuscleGroup;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Weekly / plan muscle set volume: primary muscle = 1.0 set, secondary = 0.5 set.
 */
public final class MuscleSetVolume {

    public static final double PRIMARY_WEIGHT = 1.0;
    public static final double SECONDARY_WEIGHT = 0.5;

    private MuscleSetVolume() {
    }

    public static void addExerciseSets(Map<MuscleGroup, Double> totals, Exercise exercise, double sets) {
        if (exercise == null || sets <= 0) {
            return;
        }
        for (MuscleGroup muscle : exercise.getPrimaryMuscles()) {
            totals.merge(muscle, sets * PRIMARY_WEIGHT, Double::sum);
        }
        for (MuscleGroup muscle : exercise.getSecondaryMuscles()) {
            if (exercise.getPrimaryMuscles().contains(muscle)) {
                continue;
            }
            totals.merge(muscle, sets * SECONDARY_WEIGHT, Double::sum);
        }
    }

    public static Map<MuscleGroup, Double> emptyTotals() {
        return new EnumMap<>(MuscleGroup.class);
    }

    public static Map<MuscleGroup, Double> sortedNonZero(Map<MuscleGroup, Double> totals) {
        List<Map.Entry<MuscleGroup, Double>> entries = totals.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue() > 1e-9)
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .toList();
        Map<MuscleGroup, Double> ordered = new LinkedHashMap<>();
        for (Map.Entry<MuscleGroup, Double> entry : entries) {
            ordered.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(ordered);
    }

    public static String formatLine(Map<MuscleGroup, Double> totals) {
        Map<MuscleGroup, Double> sorted = sortedNonZero(totals);
        if (sorted.isEmpty()) {
            return "No muscle volume yet.";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<MuscleGroup, Double> entry : sorted.entrySet()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(entry.getKey().name()).append(": ")
                    .append("%.1f sets".formatted(entry.getValue()));
        }
        return sb.toString();
    }
}
