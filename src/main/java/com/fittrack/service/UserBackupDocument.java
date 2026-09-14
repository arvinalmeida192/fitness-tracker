package com.fittrack.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Jackson-friendly snapshot of one user's portable data for JSON backup.
 */
public final class UserBackupDocument {

    public int formatVersion = 1;
    public String exportedAt;
    public String appVersion;
    public String username;

    public ProfileBackup profile;
    public List<WeightBackup> weights = new ArrayList<>();
    public NutritionGoalBackup nutritionGoal;
    public BodyGoalBackup bodyGoal;
    public List<MealBackup> meals = new ArrayList<>();
    public List<DishBackup> dishes = new ArrayList<>();

    public static final class ProfileBackup {
        public String fullName;
        public String dateOfBirth;
        public String sex;
        public double heightCm;
        public String activityLevel;
        public String preferred1rmFormula;
        public String units;
    }

    public static final class WeightBackup {
        public double weightKg;
        public String measuredAt;
        public String note;
    }

    public static final class NutritionGoalBackup {
        public double kcal;
        public double proteinG;
        public double carbsG;
        public double fatG;
        public Double fiberG;
        public String effectiveFrom;
    }

    public static final class BodyGoalBackup {
        public double targetWeightKg;
        public String targetDate;
    }

    public static final class MealBackup {
        public String eatenAt;
        public String mealType;
        public String dishName;
        public double portionG;
        public double scaleFactor;
        public double kcal;
        public double proteinG;
        public double carbsG;
        public double fatG;
        public double fiberG;
        public String notes;
    }

    public static final class DishBackup {
        public String name;
        public String notes;
        public double totalWeightG;
        public Double yieldWeightG;
        public List<DishItemBackup> items = new ArrayList<>();
    }

    public static final class DishItemBackup {
        public String ingredientName;
        public double amountG;
        public double kcalPer100;
        public double proteinPer100;
        public double carbsPer100;
        public double fatPer100;
        public double fiberPer100;
    }
}
