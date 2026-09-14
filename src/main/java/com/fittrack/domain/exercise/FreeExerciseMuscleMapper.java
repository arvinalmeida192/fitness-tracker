package com.fittrack.domain.exercise;

import com.fittrack.domain.common.MuscleGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Maps free-exercise-db muscle labels onto {@link MuscleGroup}.
 */
public final class FreeExerciseMuscleMapper {

    private static final Map<String, MuscleGroup> BY_LABEL = Map.ofEntries(
            Map.entry("abdominals", MuscleGroup.CORE),
            Map.entry("abductors", MuscleGroup.ABDUCTORS),
            Map.entry("adductors", MuscleGroup.ADDUCTORS),
            Map.entry("biceps", MuscleGroup.BICEPS),
            Map.entry("calves", MuscleGroup.CALVES),
            Map.entry("chest", MuscleGroup.CHEST),
            Map.entry("forearms", MuscleGroup.FOREARMS),
            Map.entry("glutes", MuscleGroup.GLUTES),
            Map.entry("hamstrings", MuscleGroup.HAMSTRINGS),
            Map.entry("lats", MuscleGroup.LATS),
            Map.entry("lower back", MuscleGroup.LOWER_BACK),
            Map.entry("middle back", MuscleGroup.MIDDLE_BACK),
            Map.entry("neck", MuscleGroup.NECK),
            Map.entry("quadriceps", MuscleGroup.QUADS),
            Map.entry("shoulders", MuscleGroup.SHOULDERS),
            Map.entry("traps", MuscleGroup.TRAPS),
            Map.entry("triceps", MuscleGroup.TRICEPS)
    );

    private FreeExerciseMuscleMapper() {
    }

    public static MuscleGroup mapOne(String label) {
        if (label == null || label.isBlank()) {
            return MuscleGroup.OTHER;
        }
        return BY_LABEL.getOrDefault(label.trim().toLowerCase(Locale.ROOT), MuscleGroup.OTHER);
    }

    public static List<MuscleGroup> mapAll(List<String> labels) {
        if (labels == null || labels.isEmpty()) {
            return List.of();
        }
        Set<MuscleGroup> unique = new LinkedHashSet<>();
        for (String label : labels) {
            unique.add(mapOne(label));
        }
        return List.copyOf(unique);
    }

    public static String encode(List<MuscleGroup> muscles) {
        if (muscles == null || muscles.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (MuscleGroup muscle : muscles) {
            if (muscle == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(muscle.name());
        }
        return sb.toString();
    }

    public static List<MuscleGroup> decode(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        List<MuscleGroup> list = new ArrayList<>();
        for (String part : csv.split(",")) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            try {
                list.add(MuscleGroup.valueOf(token));
            } catch (IllegalArgumentException ignored) {
                list.add(MuscleGroup.OTHER);
            }
        }
        return Collections.unmodifiableList(list);
    }
}
