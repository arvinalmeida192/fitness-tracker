package com.fittrack.domain;


public final class PlanItem extends BaseEntity {

    private long planId;
    private long exerciseId;
    private String exerciseName;
    private int orderIndex;
    private int targetSets;
    private int repsMin;
    private int repsMax;
    private Double targetWeight;
    private Integer restSec;
    private String notes;

    public PlanItem() {
    }

    public long getPlanId() {
        return planId;
    }

    public void setPlanId(long planId) {
        this.planId = planId;
    }

    public long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(long exerciseId) {
        this.exerciseId = exerciseId;
    }

    public String getExerciseName() {
        return exerciseName;
    }

    public void setExerciseName(String exerciseName) {
        this.exerciseName = exerciseName;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }

    public int getTargetSets() {
        return targetSets;
    }

    public void setTargetSets(int targetSets) {
        this.targetSets = targetSets;
    }

    public int getRepsMin() {
        return repsMin;
    }

    public void setRepsMin(int repsMin) {
        this.repsMin = repsMin;
    }

    public int getRepsMax() {
        return repsMax;
    }

    public void setRepsMax(int repsMax) {
        this.repsMax = repsMax;
    }

    public Double getTargetWeight() {
        return targetWeight;
    }

    public void setTargetWeight(Double targetWeight) {
        this.targetWeight = targetWeight;
    }

    public Integer getRestSec() {
        return restSec;
    }

    public void setRestSec(Integer restSec) {
        this.restSec = restSec;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String repsLabel() {
        if (repsMin == repsMax) {
            return Integer.toString(repsMin);
        }
        return repsMin + "–" + repsMax;
    }
}
