package com.fittrack.util;

import com.fittrack.domain.common.ActivityLevel;
import com.fittrack.domain.common.Sex;

/**
 * BMI and Mifflin–St Jeor energy estimates.
 */
public final class BodyMetrics {

    public enum BmiCategory {
        UNDERWEIGHT,
        NORMAL,
        OVERWEIGHT,
        OBESE
    }

    private BodyMetrics() {
    }

    public static double bmi(double weightKg, double heightCm) {
        if (weightKg <= 0 || heightCm <= 0) {
            throw new IllegalArgumentException("Weight and height must be positive");
        }
        double heightM = heightCm / 100.0;
        return weightKg / (heightM * heightM);
    }

    public static BmiCategory bmiCategory(double bmi) {
        if (bmi < 18.5) {
            return BmiCategory.UNDERWEIGHT;
        }
        if (bmi < 25.0) {
            return BmiCategory.NORMAL;
        }
        if (bmi < 30.0) {
            return BmiCategory.OVERWEIGHT;
        }
        return BmiCategory.OBESE;
    }

    /**
     * Mifflin–St Jeor BMR (kcal/day). {@link Sex#OTHER} uses the female equation as a neutral default.
     */
    public static double bmr(double weightKg, double heightCm, int ageYears, Sex sex) {
        if (ageYears < 0) {
            throw new IllegalArgumentException("Age cannot be negative");
        }
        double base = (10 * weightKg) + (6.25 * heightCm) - (5 * ageYears);
        if (sex == Sex.MALE) {
            return base + 5;
        }
        return base - 161;
    }

    public static double tdee(double bmr, ActivityLevel activityLevel) {
        if (activityLevel == null) {
            throw new NullPointerException("activityLevel");
        }
        return bmr * activityLevel.getMultiplier();
    }
}
