package com.fittrack.domain.nutrition;

import com.fittrack.domain.common.Macros;

import java.util.List;

/**
 * Batch totals and portion scaling: {@code logged = batch × (portionG / basisWeightG)}.
 */
public final class PortionScaler {

    private PortionScaler() {
    }

    public static Macros batchMacros(List<DishItem> items) {
        Macros total = Macros.zero();
        if (items == null) {
            return total;
        }
        for (DishItem item : items) {
            total = total.plus(item.lineMacros());
        }
        return total;
    }

    public static double sumAmounts(List<DishItem> items) {
        double sum = 0;
        if (items == null) {
            return sum;
        }
        for (DishItem item : items) {
            sum += item.getAmountG();
        }
        return sum;
    }

    public static double basisWeightG(Dish dish) {
        return dish.basisWeightG();
    }

    public static double scaleFactor(Dish dish, double portionG) {
        double basis = basisWeightG(dish);
        if (basis <= 0) {
            throw new IllegalArgumentException("Dish has no basis weight for portion scaling");
        }
        if (portionG < 0) {
            throw new IllegalArgumentException("Portion cannot be negative");
        }
        return portionG / basis;
    }

    public static Macros macrosForPortion(Dish dish, double portionG) {
        return batchMacros(dish.getItems()).scale(scaleFactor(dish, portionG));
    }
}
