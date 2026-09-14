package com.fittrack.domain;


/** 1RM = w × r^0.10 */
public final class LombardiCalculator implements OneRepMaxCalculator {

    @Override
    public OneRepMaxFormula formula() {
        return OneRepMaxFormula.LOMBARDI;
    }

    @Override
    public double calculate(double weightKg, int reps) {
        EpleyCalculator.requirePositiveLoad(weightKg, reps);
        return weightKg * Math.pow(reps, 0.10);
    }
}
