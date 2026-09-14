package com.fittrack.domain;


import java.time.Instant;

public final class MealLog extends BaseEntity {

    private long userId;
    private Instant eatenAt;
    private MealType mealType = MealType.OTHER;
    private Long dishId;
    private String dishName;
    private double portionG;
    private double scaleFactor;
    private Macros macros = Macros.zero();
    private String notes;

    public MealLog() {
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public Instant getEatenAt() {
        return eatenAt;
    }

    public void setEatenAt(Instant eatenAt) {
        this.eatenAt = eatenAt;
    }

    public MealType getMealType() {
        return mealType;
    }

    public void setMealType(MealType mealType) {
        this.mealType = mealType == null ? MealType.OTHER : mealType;
    }

    public Long getDishId() {
        return dishId;
    }

    public void setDishId(Long dishId) {
        this.dishId = dishId;
    }

    public String getDishName() {
        return dishName;
    }

    public void setDishName(String dishName) {
        this.dishName = dishName;
    }

    public double getPortionG() {
        return portionG;
    }

    public void setPortionG(double portionG) {
        this.portionG = portionG;
    }

    public double getScaleFactor() {
        return scaleFactor;
    }

    public void setScaleFactor(double scaleFactor) {
        this.scaleFactor = scaleFactor;
    }

    public Macros getMacros() {
        return macros;
    }

    public void setMacros(Macros macros) {
        this.macros = macros == null ? Macros.zero() : macros;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String displayLabel() {
        if (dishName != null && !dishName.isBlank()) {
            return dishName;
        }
        if (notes != null && !notes.isBlank()) {
            return notes;
        }
        return dishId == null ? "Ad-hoc ingredient" : "Dish";
    }
}
