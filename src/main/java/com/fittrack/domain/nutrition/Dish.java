package com.fittrack.domain.nutrition;

import com.fittrack.domain.common.BaseEntity;
import com.fittrack.domain.common.Macros;

import java.util.ArrayList;
import java.util.List;

public final class Dish extends BaseEntity {

    private long userId;
    private String name;
    private String notes;
    private double totalWeightG;
    private Double yieldWeightG;
    private final List<DishItem> items = new ArrayList<>();

    public Dish() {
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public double getTotalWeightG() {
        return totalWeightG;
    }

    public void setTotalWeightG(double totalWeightG) {
        this.totalWeightG = totalWeightG;
    }

    public Double getYieldWeightG() {
        return yieldWeightG;
    }

    public void setYieldWeightG(Double yieldWeightG) {
        this.yieldWeightG = yieldWeightG;
    }

    public List<DishItem> getItems() {
        return items;
    }

    public void setItems(List<DishItem> items) {
        this.items.clear();
        if (items != null) {
            this.items.addAll(items);
        }
    }

    @Override
    public String toString() {
        return name == null ? "Dish" : name;
    }

    /** Weight used for portion math: cooked yield if set, else raw batch sum. */
    public double basisWeightG() {
        if (yieldWeightG != null && yieldWeightG > 0) {
            return yieldWeightG;
        }
        return totalWeightG;
    }

    public Macros batchMacros() {
        return PortionScaler.batchMacros(items);
    }
}
