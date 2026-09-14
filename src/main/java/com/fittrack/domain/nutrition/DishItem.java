package com.fittrack.domain.nutrition;

import com.fittrack.domain.common.BaseEntity;
import com.fittrack.domain.common.Macros;

public final class DishItem extends BaseEntity {

    private long dishId;
    private long ingredientId;
    private String ingredientName;
    private Macros per100g = Macros.zero();
    private double amountG;

    public DishItem() {
    }

    public long getDishId() {
        return dishId;
    }

    public void setDishId(long dishId) {
        this.dishId = dishId;
    }

    public long getIngredientId() {
        return ingredientId;
    }

    public void setIngredientId(long ingredientId) {
        this.ingredientId = ingredientId;
    }

    public String getIngredientName() {
        return ingredientName;
    }

    public void setIngredientName(String ingredientName) {
        this.ingredientName = ingredientName;
    }

    public Macros getPer100g() {
        return per100g;
    }

    public void setPer100g(Macros per100g) {
        this.per100g = per100g == null ? Macros.zero() : per100g;
    }

    public double getAmountG() {
        return amountG;
    }

    public void setAmountG(double amountG) {
        this.amountG = amountG;
    }

    public Macros lineMacros() {
        return per100g.forGrams(amountG);
    }
}
