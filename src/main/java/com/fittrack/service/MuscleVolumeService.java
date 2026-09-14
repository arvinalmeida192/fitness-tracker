package com.fittrack.service;

import com.fittrack.domain.MuscleGroup;
import com.fittrack.domain.Exercise;
import com.fittrack.domain.MuscleSetVolume;
import com.fittrack.domain.LoggedSet;
import com.fittrack.domain.PlanItem;
import com.fittrack.domain.WorkoutPlan;
import com.fittrack.persistence.ExerciseDao;
import com.fittrack.persistence.WorkoutSessionDao;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Planned and completed muscle set volume (primary = 1.0, secondary = 0.5).
 */
public final class MuscleVolumeService {

    private final ExerciseDao exerciseDao;
    private final WorkoutSessionDao workoutSessionDao;
    private final UserSession userSession;

    public MuscleVolumeService(
            ExerciseDao exerciseDao,
            WorkoutSessionDao workoutSessionDao,
            UserSession userSession
    ) {
        this.exerciseDao = exerciseDao;
        this.workoutSessionDao = workoutSessionDao;
        this.userSession = userSession;
    }

    private Map<MuscleGroup, Double> plannedVolume(WorkoutPlan plan) {
        Map<MuscleGroup, Double> totals = MuscleSetVolume.emptyTotals();
        if (plan == null || plan.getItems() == null) {
            return MuscleSetVolume.sortedNonZero(totals);
        }
        Map<Long, Exercise> cache = new HashMap<>();
        for (PlanItem item : plan.getItems()) {
            Exercise exercise = cache.computeIfAbsent(item.getExerciseId(), id ->
                    exerciseDao.findById(id).orElse(null));
            if (exercise == null) {
                continue;
            }
            MuscleSetVolume.addExerciseSets(totals, exercise, item.getTargetSets());
        }
        return MuscleSetVolume.sortedNonZero(totals);
    }

    public Map<MuscleGroup, Double> plannedVolume(List<PlanItem> items) {
        WorkoutPlan stub = new WorkoutPlan();
        stub.setItems(items);
        return plannedVolume(stub);
    }

    /** Completed sets from Monday of the current week through now (system default zone). */
    private Map<MuscleGroup, Double> weekCompletedVolume() {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Instant from = weekStart.atStartOfDay(zone).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(zone).toInstant();
        return completedVolumeBetween(from, to);
    }

    private Map<MuscleGroup, Double> completedVolumeBetween(Instant fromInclusive, Instant toExclusive) {
        Map<MuscleGroup, Double> totals = MuscleSetVolume.emptyTotals();
        long userId = userSession.requireUser().getId();
        List<LoggedSet> sets = workoutSessionDao.findCompletedSetsBetween(userId, fromInclusive, toExclusive);
        Map<Long, Exercise> cache = new HashMap<>();
        for (LoggedSet set : sets) {
            Exercise exercise = cache.computeIfAbsent(set.getExerciseId(), id ->
                    exerciseDao.findById(id).orElse(null));
            if (exercise == null) {
                // Deleted exercise: attribute whole set to OTHER as primary
                Exercise fallback = new Exercise();
                fallback.setName(set.getExerciseNameSnapshot());
                fallback.setPrimaryMuscles(List.of(MuscleGroup.OTHER));
                MuscleSetVolume.addExerciseSets(totals, fallback, 1);
                continue;
            }
            MuscleSetVolume.addExerciseSets(totals, exercise, 1);
        }
        return MuscleSetVolume.sortedNonZero(totals);
    }

    public String formatWeekCompleted() {
        Map<MuscleGroup, Double> volume = weekCompletedVolume();
        if (volume.isEmpty()) {
            return "No completed sets this week yet.";
        }
        return MuscleSetVolume.formatLine(volume);
    }
}
