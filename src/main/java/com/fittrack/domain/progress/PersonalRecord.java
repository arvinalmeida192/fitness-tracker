package com.fittrack.domain.progress;

import com.fittrack.domain.common.BaseEntity;
import com.fittrack.domain.common.OneRepMaxFormula;
import com.fittrack.domain.common.PersonalRecordType;

import java.time.Instant;

public final class PersonalRecord extends BaseEntity {

    private long userId;
    private long exerciseId;
    private String exerciseName;
    private PersonalRecordType prType;
    private Integer reps;
    private Double weightKg;
    private Double estimated1rm;
    private OneRepMaxFormula formula;
    private Instant achievedAt;
    private Long loggedSetId;

    public PersonalRecord() {
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
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

    public PersonalRecordType getPrType() {
        return prType;
    }

    public void setPrType(PersonalRecordType prType) {
        this.prType = prType;
    }

    public Integer getReps() {
        return reps;
    }

    public void setReps(Integer reps) {
        this.reps = reps;
    }

    public Double getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(Double weightKg) {
        this.weightKg = weightKg;
    }

    public Double getEstimated1rm() {
        return estimated1rm;
    }

    public void setEstimated1rm(Double estimated1rm) {
        this.estimated1rm = estimated1rm;
    }

    public OneRepMaxFormula getFormula() {
        return formula;
    }

    public void setFormula(OneRepMaxFormula formula) {
        this.formula = formula;
    }

    public Instant getAchievedAt() {
        return achievedAt;
    }

    public void setAchievedAt(Instant achievedAt) {
        this.achievedAt = achievedAt;
    }

    public Long getLoggedSetId() {
        return loggedSetId;
    }

    public void setLoggedSetId(Long loggedSetId) {
        this.loggedSetId = loggedSetId;
    }
}
