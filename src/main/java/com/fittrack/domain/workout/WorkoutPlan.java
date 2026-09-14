package com.fittrack.domain.workout;

import com.fittrack.domain.common.BaseEntity;

import java.util.ArrayList;
import java.util.List;

public final class WorkoutPlan extends BaseEntity {

    private long userId;
    private String name;
    private String description;
    private boolean archived;
    private final List<PlanItem> items = new ArrayList<>();

    public WorkoutPlan() {
    }

    public WorkoutPlan(Long id, long userId, String name, String description, boolean archived) {
        super(id);
        this.userId = userId;
        this.name = name;
        this.description = description;
        this.archived = archived;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public List<PlanItem> getItems() {
        return items;
    }

    public void setItems(List<PlanItem> items) {
        this.items.clear();
        if (items != null) {
            this.items.addAll(items);
        }
    }

    public int itemCount() {
        return items.size();
    }

    @Override
    public String toString() {
        return name;
    }
}
