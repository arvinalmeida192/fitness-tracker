package com.fittrack.domain.nutrition;

import com.fittrack.domain.common.Macros;

import java.time.LocalDate;

/**
 * Daily macro targets for one user (one active row per user).
 */
public final class NutritionGoal {

    private long userId;
    private double kcal;
    private double proteinG;
    private double carbsG;
    private double fatG;
    private Double fiberG;
    private LocalDate effectiveFrom;

    public NutritionGoal() {
    }

    public Macros asMacros() {
        return new Macros(kcal, proteinG, carbsG, fatG, fiberG == null ? 0 : fiberG);
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public double getKcal() {
        return kcal;
    }

    public void setKcal(double kcal) {
        this.kcal = kcal;
    }

    public double getProteinG() {
        return proteinG;
    }

    public void setProteinG(double proteinG) {
        this.proteinG = proteinG;
    }

    public double getCarbsG() {
        return carbsG;
    }

    public void setCarbsG(double carbsG) {
        this.carbsG = carbsG;
    }

    public double getFatG() {
        return fatG;
    }

    public void setFatG(double fatG) {
        this.fatG = fatG;
    }

    public Double getFiberG() {
        return fiberG;
    }

    public void setFiberG(Double fiberG) {
        this.fiberG = fiberG;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }
}
