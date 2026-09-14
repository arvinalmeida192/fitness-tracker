package com.fittrack.domain;


/** 1RM = w × (1 + r/30) */
public final class EpleyCalculator implements OneRepMaxCalculator {

    @Override
    public OneRepMaxFormula formula() {
        return OneRepMaxFormula.EPLEY;
    }

    @Override
    public double calculate(double weightKg, int reps) {
        requirePositiveLoad(weightKg, reps);
        return weightKg * (1.0 + reps / 30.0);
    }

    static void requirePositiveLoad(double weightKg, int reps) {
        if (weightKg <= 0) {
            throw new IllegalArgumentException("1RM formulas require weight > 0");
        }
        if (reps < 1) {
            throw new IllegalArgumentException("Reps must be at least 1");
        }
    }
}
