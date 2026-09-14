package com.fittrack.domain.workout;

import com.fittrack.domain.common.BaseEntity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class WorkoutSession extends BaseEntity {

    private long userId;
    private Long planId;
    private Instant startedAt;
    private Instant endedAt;
    private String notes;
    private final List<LoggedSet> sets = new ArrayList<>();

    public WorkoutSession() {
    }

    public WorkoutSession(Long id, long userId, Long planId, Instant startedAt, Instant endedAt, String notes) {
        super(id);
        this.userId = userId;
        this.planId = planId;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.notes = notes;
    }

    public boolean isOpen() {
        return endedAt == null;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public Long getPlanId() {
        return planId;
    }

    public void setPlanId(Long planId) {
        this.planId = planId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public List<LoggedSet> getSets() {
        return sets;
    }

    public void setSets(List<LoggedSet> sets) {
        this.sets.clear();
        if (sets != null) {
            this.sets.addAll(sets);
        }
    }
}
