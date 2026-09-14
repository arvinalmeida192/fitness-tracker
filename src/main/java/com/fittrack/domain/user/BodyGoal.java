package com.fittrack.domain.user;

import java.time.LocalDate;

/**
 * Optional target weight (and date) for body progress tracking.
 */
public final class BodyGoal {

    private long userId;
    private double targetWeightKg;
    private LocalDate targetDate;

    public BodyGoal() {
    }

    public BodyGoal(long userId, double targetWeightKg, LocalDate targetDate) {
        this.userId = userId;
        this.targetWeightKg = targetWeightKg;
        this.targetDate = targetDate;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public double getTargetWeightKg() {
        return targetWeightKg;
    }

    public void setTargetWeightKg(double targetWeightKg) {
        this.targetWeightKg = targetWeightKg;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public void setTargetDate(LocalDate targetDate) {
        this.targetDate = targetDate;
    }
}
