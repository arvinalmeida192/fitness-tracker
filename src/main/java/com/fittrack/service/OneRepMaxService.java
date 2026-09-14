package com.fittrack.service;

import com.fittrack.domain.common.OneRepMaxFormula;
import com.fittrack.domain.progress.BrzyckiCalculator;
import com.fittrack.domain.progress.EpleyCalculator;
import com.fittrack.domain.progress.LombardiCalculator;
import com.fittrack.domain.progress.OneRepMaxCalculator;
import com.fittrack.domain.workout.LoggedSet;

import java.util.EnumMap;
import java.util.Map;

/**
 * Registry of 1RM strategies keyed by formula preference.
 */
public final class OneRepMaxService {

    private final Map<OneRepMaxFormula, OneRepMaxCalculator> calculators = new EnumMap<>(OneRepMaxFormula.class);

    public OneRepMaxService() {
        register(new EpleyCalculator());
        register(new BrzyckiCalculator());
        register(new LombardiCalculator());
    }

    private void register(OneRepMaxCalculator calculator) {
        calculators.put(calculator.formula(), calculator);
    }

    private OneRepMaxCalculator require(OneRepMaxFormula formula) {
        OneRepMaxCalculator calculator = calculators.get(formula);
        if (calculator == null) {
            throw new IllegalArgumentException("Unknown 1RM formula: " + formula);
        }
        return calculator;
    }

    /**
     * Fills all three estimate columns when weight &gt; 0. Bodyweight (0) leaves them null.
     */
    public void applyAll(LoggedSet set) {
        if (set.getWeightKg() <= 0 || set.getReps() < 1) {
            set.setEpley1rm(null);
            set.setBrzycki1rm(null);
            set.setLombardi1rm(null);
            return;
        }
        set.setEpley1rm(safeCalc(OneRepMaxFormula.EPLEY, set.getWeightKg(), set.getReps()));
        set.setBrzycki1rm(safeCalc(OneRepMaxFormula.BRZYCKI, set.getWeightKg(), set.getReps()));
        set.setLombardi1rm(safeCalc(OneRepMaxFormula.LOMBARDI, set.getWeightKg(), set.getReps()));
    }

    public Double preferredEstimate(LoggedSet set, OneRepMaxFormula preferred) {
        return switch (preferred == null ? OneRepMaxFormula.EPLEY : preferred) {
            case EPLEY -> set.getEpley1rm();
            case BRZYCKI -> set.getBrzycki1rm();
            case LOMBARDI -> set.getLombardi1rm();
        };
    }

    public boolean estimateLessReliable(int reps) {
        return reps > 10;
    }

    private Double safeCalc(OneRepMaxFormula formula, double weightKg, int reps) {
        try {
            return require(formula).calculate(weightKg, reps);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
