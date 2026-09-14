package com.fittrack.domain;

import java.util.Objects;

/**
 * Immutable macronutrient totals. All gram fields are non-negative; kcal may be derived or stored.
 */
public final class Macros {

    private final double kcal;
    private final double proteinG;
    private final double carbsG;
    private final double fatG;
    private final double fiberG;

    public Macros(double kcal, double proteinG, double carbsG, double fatG, double fiberG) {
        if (kcal < 0 || proteinG < 0 || carbsG < 0 || fatG < 0 || fiberG < 0) {
            throw new IllegalArgumentException("Macro values cannot be negative");
        }
        this.kcal = kcal;
        this.proteinG = proteinG;
        this.carbsG = carbsG;
        this.fatG = fatG;
        this.fiberG = fiberG;
    }

    public static Macros zero() {
        return new Macros(0, 0, 0, 0, 0);
    }

    public static Macros per100g(double kcal, double proteinG, double carbsG, double fatG, double fiberG) {
        return new Macros(kcal, proteinG, carbsG, fatG, fiberG);
    }

    public Macros scale(double factor) {
        if (factor < 0) {
            throw new IllegalArgumentException("Scale factor cannot be negative");
        }
        return new Macros(
                kcal * factor,
                proteinG * factor,
                carbsG * factor,
                fatG * factor,
                fiberG * factor
        );
    }

    /**
     * Scales per-100g macros to an absolute amount in grams.
     */
    public Macros forGrams(double grams) {
        return scale(grams / 100.0);
    }

    public Macros plus(Macros other) {
        Objects.requireNonNull(other, "other");
        return new Macros(
                kcal + other.kcal,
                proteinG + other.proteinG,
                carbsG + other.carbsG,
                fatG + other.fatG,
                fiberG + other.fiberG
        );
    }

    public double getKcal() {
        return kcal;
    }

    public double getProteinG() {
        return proteinG;
    }

    public double getCarbsG() {
        return carbsG;
    }

    public double getFatG() {
        return fatG;
    }

    public double getFiberG() {
        return fiberG;
    }

    @Override
    public String toString() {
        return "Macros{kcal=%.1f, P=%.1fg, C=%.1fg, F=%.1fg, fiber=%.1fg}"
                .formatted(kcal, proteinG, carbsG, fatG, fiberG);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Macros macros)) {
            return false;
        }
        return Double.compare(macros.kcal, kcal) == 0
                && Double.compare(macros.proteinG, proteinG) == 0
                && Double.compare(macros.carbsG, carbsG) == 0
                && Double.compare(macros.fatG, fatG) == 0
                && Double.compare(macros.fiberG, fiberG) == 0;
    }

    @Override
    public int hashCode() {
        int result = Double.hashCode(kcal);
        result = 31 * result + Double.hashCode(proteinG);
        result = 31 * result + Double.hashCode(carbsG);
        result = 31 * result + Double.hashCode(fatG);
        result = 31 * result + Double.hashCode(fiberG);
        return result;
    }
}
