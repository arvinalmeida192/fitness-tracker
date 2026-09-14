package com.fittrack.service;

import com.fittrack.domain.workout.PlanItem;
import com.fittrack.domain.workout.WorkoutSession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * In-memory pointer into a live plan-driven workout.
 */
public final class ActiveWorkout {

    private final WorkoutSession session;
    private final String planName;
    private final List<PlanItem> items;
    private int itemIndex;
    private int nextSetNumber = 1;
    private boolean finished;

    public ActiveWorkout(WorkoutSession session, String planName, List<PlanItem> items) {
        this.session = session;
        this.planName = planName;
        this.items = new ArrayList<>(items);
        this.itemIndex = 0;
        this.nextSetNumber = 1;
    }

    public WorkoutSession getSession() {
        return session;
    }

    public String getPlanName() {
        return planName;
    }

    public List<PlanItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public boolean isFinished() {
        return finished || itemIndex >= items.size();
    }

    public void markFinished() {
        this.finished = true;
    }

    public PlanItem currentItem() {
        if (isFinished()) {
            return null;
        }
        return items.get(itemIndex);
    }

    public int getItemIndex() {
        return itemIndex;
    }

    public int getNextSetNumber() {
        return nextSetNumber;
    }

    void advanceAfterLoggedSet() {
        PlanItem current = currentItem();
        if (current == null) {
            return;
        }
        if (nextSetNumber >= current.getTargetSets()) {
            itemIndex++;
            nextSetNumber = 1;
        } else {
            nextSetNumber++;
        }
    }

    void skipCurrentExercise() {
        if (isFinished()) {
            return;
        }
        itemIndex++;
        nextSetNumber = 1;
    }
}
