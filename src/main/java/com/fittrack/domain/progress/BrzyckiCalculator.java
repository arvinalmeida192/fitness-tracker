package com.fittrack.domain.progress;

import com.fittrack.domain.common.OneRepMaxFormula;

/** 1RM = w × 36 / (37 − r); valid for r &lt; 37 */
public final class BrzyckiCalculator implements OneRepMaxCalculator {

    @Override
    public OneRepMaxFormula formula() {
        return OneRepMaxFormula.BRZYCKI;
    }

    @Override
    public double calculate(double weightKg, int reps) {
        EpleyCalculator.requirePositiveLoad(weightKg, reps);
        if (reps >= 37) {
            throw new IllegalArgumentException("Brzycki requires reps under 37");
        }
        return weightKg * 36.0 / (37.0 - reps);
    }
}
