package com.fittrack.service;

import com.fittrack.domain.Macros;
import com.fittrack.domain.ValidationException;
import com.fittrack.domain.MealLog;
import com.fittrack.domain.NutritionGoal;
import com.fittrack.persistence.NutritionGoalDao;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Nutrition targets and daily consumed-vs-goal aggregation.
 */
public final class NutritionGoalService {

    private final NutritionGoalDao nutritionGoalDao;
    private final MealService mealService;
    private final UserSession userSession;

    public NutritionGoalService(
            NutritionGoalDao nutritionGoalDao,
            MealService mealService,
            UserSession userSession
    ) {
        this.nutritionGoalDao = nutritionGoalDao;
        this.mealService = mealService;
        this.userSession = userSession;
    }

    public Optional<NutritionGoal> current() {
        return nutritionGoalDao.findByUserId(userSession.requireUser().getId());
    }

    public NutritionGoal save(
            double kcal,
            double proteinG,
            double carbsG,
            double fatG,
            Double fiberG
    ) {
        if (kcal < 800 || kcal > 8000) {
            throw new ValidationException("Calories must be between 800 and 8000");
        }
        if (proteinG < 0 || carbsG < 0 || fatG < 0) {
            throw new ValidationException("Macro grams cannot be negative");
        }
        if (fiberG != null && fiberG < 0) {
            throw new ValidationException("Fiber cannot be negative");
        }
        NutritionGoal goal = new NutritionGoal();
        goal.setUserId(userSession.requireUser().getId());
        goal.setKcal(kcal);
        goal.setProteinG(proteinG);
        goal.setCarbsG(carbsG);
        goal.setFatG(fatG);
        goal.setFiberG(fiberG);
        goal.setEffectiveFrom(LocalDate.now(ZoneId.systemDefault()));
        return nutritionGoalDao.upsert(goal);
    }

    public DayProgress todayProgress() {
        return progressOn(LocalDate.now(ZoneId.systemDefault()));
    }

    private DayProgress progressOn(LocalDate day) {
        Macros consumed = Macros.zero();
        for (MealLog log : mealService.mealsOn(day)) {
            consumed = consumed.plus(log.getMacros());
        }
        Optional<NutritionGoal> goal = current();
        return new DayProgress(day, consumed, goal.orElse(null));
    }

    /**
     * Last {@code days} calendar days (including today), oldest first.
     */
    public List<DayProgress> adherenceHistory(int days) {
        if (days < 1) {
            throw new ValidationException("Days must be at least 1");
        }
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        List<DayProgress> rows = new ArrayList<>(days);
        for (int i = days - 1; i >= 0; i--) {
            rows.add(progressOn(today.minusDays(i)));
        }
        return rows;
    }

    /**
     * Fraction of days in the window where calories landed within ±10% of goal (when a goal exists).
     */
    public double calorieAdherenceRate(int days) {
        List<DayProgress> history = adherenceHistory(days);
        int counted = 0;
        int hit = 0;
        for (DayProgress day : history) {
            if (day.goal() == null || day.goal().getKcal() <= 0) {
                continue;
            }
            counted++;
            double ratio = day.consumed().getKcal() / day.goal().getKcal();
            if (ratio >= 0.90 && ratio <= 1.10) {
                hit++;
            }
        }
        return counted == 0 ? 0 : (double) hit / counted;
    }

    public record DayProgress(LocalDate day, Macros consumed, NutritionGoal goal) {
        public double remainingKcal() {
            if (goal == null) {
                return 0;
            }
            return goal.getKcal() - consumed.getKcal();
        }

        public double remainingProteinG() {
            if (goal == null) {
                return 0;
            }
            return goal.getProteinG() - consumed.getProteinG();
        }
    }
}
