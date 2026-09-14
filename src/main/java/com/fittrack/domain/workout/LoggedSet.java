package com.fittrack.domain.workout;

import com.fittrack.domain.common.BaseEntity;

import java.time.Instant;

public final class LoggedSet extends BaseEntity {

    private long sessionId;
    private long exerciseId;
    private String exerciseNameSnapshot;
    private int setIndex;
    private double weightKg;
    private int reps;
    private Double rpe;
    private boolean completed = true;
    private Instant loggedAt;
    private Double epley1rm;
    private Double brzycki1rm;
    private Double lombardi1rm;

    public LoggedSet() {
    }

    public long getSessionId() {
        return sessionId;
    }

    public void setSessionId(long sessionId) {
        this.sessionId = sessionId;
    }

    public long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(long exerciseId) {
        this.exerciseId = exerciseId;
    }

    public String getExerciseNameSnapshot() {
        return exerciseNameSnapshot;
    }

    public void setExerciseNameSnapshot(String exerciseNameSnapshot) {
        this.exerciseNameSnapshot = exerciseNameSnapshot;
    }

    public int getSetIndex() {
        return setIndex;
    }

    public void setSetIndex(int setIndex) {
        this.setIndex = setIndex;
    }

    public double getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(double weightKg) {
        this.weightKg = weightKg;
    }

    public int getReps() {
        return reps;
    }

    public void setReps(int reps) {
        this.reps = reps;
    }

    public Double getRpe() {
        return rpe;
    }

    public void setRpe(Double rpe) {
        this.rpe = rpe;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public Instant getLoggedAt() {
        return loggedAt;
    }

    public void setLoggedAt(Instant loggedAt) {
        this.loggedAt = loggedAt;
    }

    public Double getEpley1rm() {
        return epley1rm;
    }

    public void setEpley1rm(Double epley1rm) {
        this.epley1rm = epley1rm;
    }

    public Double getBrzycki1rm() {
        return brzycki1rm;
    }

    public void setBrzycki1rm(Double brzycki1rm) {
        this.brzycki1rm = brzycki1rm;
    }

    public Double getLombardi1rm() {
        return lombardi1rm;
    }

    public void setLombardi1rm(Double lombardi1rm) {
        this.lombardi1rm = lombardi1rm;
    }
}
