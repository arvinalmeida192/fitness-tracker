package com.fittrack.service;

import com.fittrack.domain.progress.PersonalRecord;
import com.fittrack.domain.user.BodyGoal;
import com.fittrack.domain.user.WeightEntry;
import com.fittrack.persistence.sqlite.BodyGoalDao;
import com.fittrack.util.BodyMetrics;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Aggregates home/dashboard and body-progress data.
 */
public final class DashboardService {

    private final NutritionGoalService nutritionGoalService;
    private final ProfileService profileService;
    private final ProgressService progressService;
    private final WorkoutPlanService workoutPlanService;
    private final BodyGoalDao bodyGoalDao;
    private final UserSession userSession;

    public DashboardService(
            NutritionGoalService nutritionGoalService,
            ProfileService profileService,
            ProgressService progressService,
            WorkoutPlanService workoutPlanService,
            BodyGoalDao bodyGoalDao,
            UserSession userSession
    ) {
        this.nutritionGoalService = nutritionGoalService;
        this.profileService = profileService;
        this.progressService = progressService;
        this.workoutPlanService = workoutPlanService;
        this.bodyGoalDao = bodyGoalDao;
        this.userSession = userSession;
    }

    public HomeSnapshot home() {
        ProfileService.BodySnapshot body = profileService.snapshot();
        NutritionGoalService.DayProgress today = nutritionGoalService.todayProgress();
        List<PersonalRecord> recentPrs = progressService.listRecords().stream().limit(3).toList();
        int activePlans = workoutPlanService.listPlans(false).size();
        Optional<BodyGoal> bodyGoal = bodyGoal();
        WeightTrend trend = weightTrend();
        return new HomeSnapshot(body, today, recentPrs, activePlans, bodyGoal.orElse(null), trend);
    }

    public Optional<BodyGoal> bodyGoal() {
        return bodyGoalDao.findByUserId(userSession.requireUser().getId());
    }

    public BodyGoal saveBodyGoal(double targetWeightKg, LocalDate targetDate) {
        if (targetWeightKg < 30 || targetWeightKg > 400) {
            throw new com.fittrack.domain.common.ValidationException("Target weight must be between 30 and 400 kg");
        }
        if (targetDate != null && targetDate.isBefore(LocalDate.now(ZoneId.systemDefault()))) {
            throw new com.fittrack.domain.common.ValidationException("Target date cannot be in the past");
        }
        BodyGoal goal = new BodyGoal(userSession.requireUser().getId(), targetWeightKg, targetDate);
        return bodyGoalDao.upsert(goal);
    }

    public void clearBodyGoal() {
        bodyGoalDao.delete(userSession.requireUser().getId());
    }

    public WeightTrend weightTrend() {
        List<WeightEntry> history = profileService.weightHistory();
        if (history.isEmpty()) {
            return new WeightTrend(List.of(), null, null, null, null);
        }
        // history is newest-first; trend helpers want chronological
        List<WeightEntry> chronological = new ArrayList<>(history);
        chronological.sort(Comparator.comparing(WeightEntry::getMeasuredAt));

        WeightEntry latest = chronological.get(chronological.size() - 1);
        WeightEntry previous = chronological.size() >= 2
                ? chronological.get(chronological.size() - 2)
                : null;
        Double delta = previous == null ? null : latest.getWeightKg() - previous.getWeightKg();

        ProfileService.BodySnapshot snap = profileService.snapshot();
        List<BmiPoint> bmiPoints = new ArrayList<>();
        if (snap.profile() != null) {
            double height = snap.profile().getHeightCm();
            for (WeightEntry entry : chronological) {
                double bmi = BodyMetrics.bmi(entry.getWeightKg(), height);
                LocalDate day = entry.getMeasuredAt().atZone(ZoneId.systemDefault()).toLocalDate();
                bmiPoints.add(new BmiPoint(day, entry.getWeightKg(), bmi));
            }
        }
        return new WeightTrend(chronological, latest, previous, delta, bmiPoints);
    }

    public record HomeSnapshot(
            ProfileService.BodySnapshot body,
            NutritionGoalService.DayProgress today,
            List<PersonalRecord> recentPrs,
            int activePlanCount,
            BodyGoal bodyGoal,
            WeightTrend weightTrend
    ) {
    }

    public record WeightTrend(
            List<WeightEntry> chronological,
            WeightEntry latest,
            WeightEntry previous,
            Double deltaKg,
            List<BmiPoint> bmiPoints
    ) {
    }

    public record BmiPoint(LocalDate day, double weightKg, double bmi) {
    }
}
