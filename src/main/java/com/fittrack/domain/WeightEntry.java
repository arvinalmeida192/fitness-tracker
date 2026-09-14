package com.fittrack.domain;


import java.time.Instant;

public final class WeightEntry extends BaseEntity {

    private long userId;
    private double weightKg;
    private Instant measuredAt;
    private String note;

    public WeightEntry() {
    }

    public WeightEntry(Long id, long userId, double weightKg, Instant measuredAt, String note) {
        super(id);
        this.userId = userId;
        this.weightKg = weightKg;
        this.measuredAt = measuredAt;
        this.note = note;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public double getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(double weightKg) {
        this.weightKg = weightKg;
    }

    public Instant getMeasuredAt() {
        return measuredAt;
    }

    public void setMeasuredAt(Instant measuredAt) {
        this.measuredAt = measuredAt;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
