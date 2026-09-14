package com.fittrack.service;

import com.fittrack.domain.common.ValidationException;
import com.fittrack.domain.exercise.Exercise;
import com.fittrack.domain.user.User;
import com.fittrack.domain.workout.PlanItem;
import com.fittrack.domain.workout.WorkoutPlan;
import com.fittrack.persistence.sqlite.ExerciseDao;
import com.fittrack.persistence.sqlite.WorkoutPlanDao;

import java.util.ArrayList;
import java.util.List;

public final class WorkoutPlanService {

    private final WorkoutPlanDao planDao;
    private final ExerciseDao exerciseDao;
    private final UserSession session;

    public WorkoutPlanService(WorkoutPlanDao planDao, ExerciseDao exerciseDao, UserSession session) {
        this.planDao = planDao;
        this.exerciseDao = exerciseDao;
        this.session = session;
    }

    public List<WorkoutPlan> listPlans(boolean includeArchived) {
        User user = session.requireUser();
        List<WorkoutPlan> plans = planDao.findByUser(user.getId(), includeArchived);
        for (WorkoutPlan plan : plans) {
            plan.setItems(planDao.findItems(plan.getId()));
        }
        return plans;
    }

    public WorkoutPlan getPlan(long planId) {
        WorkoutPlan plan = requireOwned(planId);
        plan.setItems(planDao.findItems(planId));
        return plan;
    }

    public WorkoutPlan create(String name, String description) {
        User user = session.requireUser();
        WorkoutPlan plan = new WorkoutPlan(null, user.getId(), requireName(name), blankToNull(description), false);
        return planDao.insert(plan);
    }

    public WorkoutPlan updateMeta(long planId, String name, String description) {
        WorkoutPlan plan = requireOwned(planId);
        plan.setName(requireName(name));
        plan.setDescription(blankToNull(description));
        planDao.updateMeta(plan);
        plan.setItems(planDao.findItems(planId));
        return plan;
    }

    public WorkoutPlan saveItems(long planId, List<PlanItem> items) {
        requireOwned(planId);
        List<PlanItem> cleaned = validateItems(items);
        planDao.replaceItems(planId, cleaned);
        return getPlan(planId);
    }

    public WorkoutPlan setArchived(long planId, boolean archived) {
        WorkoutPlan plan = requireOwned(planId);
        plan.setArchived(archived);
        planDao.updateMeta(plan);
        plan.setItems(planDao.findItems(planId));
        return plan;
    }

    public void delete(long planId) {
        requireOwned(planId);
        planDao.delete(planId);
    }

    private WorkoutPlan requireOwned(long planId) {
        User user = session.requireUser();
        WorkoutPlan plan = planDao.findById(planId)
                .orElseThrow(() -> new ValidationException("Workout plan not found"));
        if (plan.getUserId() != user.getId()) {
            throw new ValidationException("Workout plan not found");
        }
        return plan;
    }

    private List<PlanItem> validateItems(List<PlanItem> items) {
        if (items == null || items.isEmpty()) {
            throw new ValidationException("Add at least one exercise to the plan");
        }
        List<PlanItem> cleaned = new ArrayList<>();
        int order = 0;
        for (PlanItem raw : items) {
            if (raw.getExerciseId() <= 0) {
                throw new ValidationException("Each row needs an exercise");
            }
            Exercise exercise = exerciseDao.findById(raw.getExerciseId())
                    .orElseThrow(() -> new ValidationException("Unknown exercise in plan"));
            if (raw.getTargetSets() < 1 || raw.getTargetSets() > 20) {
                throw new ValidationException("Sets must be between 1 and 20");
            }
            if (raw.getRepsMin() < 1 || raw.getRepsMax() < raw.getRepsMin() || raw.getRepsMax() > 100) {
                throw new ValidationException("Rep range is invalid");
            }
            if (raw.getTargetWeight() != null && raw.getTargetWeight() < 0) {
                throw new ValidationException("Target weight cannot be negative");
            }
            if (raw.getRestSec() != null && (raw.getRestSec() < 0 || raw.getRestSec() > 600)) {
                throw new ValidationException("Rest must be between 0 and 600 seconds");
            }
            PlanItem item = copyItem(raw);
            item.setExerciseName(exercise.getName());
            item.setOrderIndex(order++);
            if (item.getRestSec() == null) {
                item.setRestSec(exercise.getDefaultRestSec());
            }
            cleaned.add(item);
        }
        return cleaned;
    }

    private static PlanItem copyItem(PlanItem source) {
        PlanItem item = new PlanItem();
        item.setExerciseId(source.getExerciseId());
        item.setExerciseName(source.getExerciseName());
        item.setTargetSets(source.getTargetSets());
        item.setRepsMin(source.getRepsMin());
        item.setRepsMax(source.getRepsMax());
        item.setTargetWeight(source.getTargetWeight());
        item.setRestSec(source.getRestSec());
        item.setNotes(blankToNull(source.getNotes()));
        return item;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("Plan name is required");
        }
        return name.trim();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
