package com.fittrack.domain.progress;

import com.fittrack.domain.common.OneRepMaxFormula;

/**
 * Strategy for estimating one-rep max from a working set.
 */
public interface OneRepMaxCalculator {

    OneRepMaxFormula formula();

    /**
     * @throws IllegalArgumentException if weight/reps are outside the formula's valid range
     */
    double calculate(double weightKg, int reps);
}
