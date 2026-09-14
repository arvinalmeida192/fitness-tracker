package com.fittrack.service;

import com.fittrack.domain.common.OneRepMaxFormula;
import com.fittrack.domain.common.ValidationException;
import com.fittrack.domain.progress.PersonalRecord;
import com.fittrack.domain.user.User;
import com.fittrack.domain.workout.LoggedSet;
import com.fittrack.domain.workout.PlanItem;
import com.fittrack.domain.workout.WorkoutPlan;
import com.fittrack.domain.workout.WorkoutSession;
import com.fittrack.persistence.sqlite.ProfileDao;
import com.fittrack.persistence.sqlite.WorkoutSessionDao;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public final class WorkoutSessionService {

    private final WorkoutSessionDao sessionDao;
    private final WorkoutPlanService planService;
    private final ProfileDao profileDao;
    private final OneRepMaxService oneRepMaxService;
    private final ProgressService progressService;
    private final UserSession userSession;

    private ActiveWorkout active;

    public WorkoutSessionService(
            WorkoutSessionDao sessionDao,
            WorkoutPlanService planService,
            ProfileDao profileDao,
            OneRepMaxService oneRepMaxService,
            ProgressService progressService,
            UserSession userSession
    ) {
        this.sessionDao = sessionDao;
        this.planService = planService;
        this.profileDao = profileDao;
        this.oneRepMaxService = oneRepMaxService;
        this.progressService = progressService;
        this.userSession = userSession;
    }

    public Optional<ActiveWorkout> current() {
        return Optional.ofNullable(active);
    }

    public ActiveWorkout startFromPlan(long planId) {
        if (active != null && !active.isFinished()) {
            throw new ValidationException("Finish or end the current workout first");
        }
        User user = userSession.requireUser();
        WorkoutPlan plan = planService.getPlan(planId);
        if (plan.getUserId() != user.getId()) {
            throw new ValidationException("Workout plan not found");
        }
        if (plan.getItems().isEmpty()) {
            throw new ValidationException("Add exercises to the plan before starting");
        }

        WorkoutSession session = new WorkoutSession(
                null,
                user.getId(),
                plan.getId(),
                Instant.now(),
                null,
                null
        );
        sessionDao.insert(session);
        active = new ActiveWorkout(session, plan.getName(), plan.getItems());
        return active;
    }

    public LogSetResult logSet(double weightKg, int reps, Double rpe) {
        ActiveWorkout workout = requireActive();
        PlanItem item = workout.currentItem();
        if (item == null) {
            throw new ValidationException("No exercise left in this workout");
        }
        if (weightKg < 0 || weightKg > 1000) {
            throw new ValidationException("Weight must be between 0 and 1000 kg");
        }
        if (reps < 1 || reps > 100) {
            throw new ValidationException("Reps must be between 1 and 100");
        }
        if (rpe != null && (rpe < 1 || rpe > 10)) {
            throw new ValidationException("RPE must be between 1 and 10");
        }

        LoggedSet set = new LoggedSet();
        set.setSessionId(workout.getSession().getId());
        set.setExerciseId(item.getExerciseId());
        set.setExerciseNameSnapshot(item.getExerciseName());
        set.setSetIndex(workout.getNextSetNumber());
        set.setWeightKg(weightKg);
        set.setReps(reps);
        set.setRpe(rpe);
        set.setCompleted(true);
        set.setLoggedAt(Instant.now());
        oneRepMaxService.applyAll(set);

        sessionDao.insertSet(set);
        workout.getSession().getSets().add(set);
        workout.advanceAfterLoggedSet();

        OneRepMaxFormula preferred = profileDao.findByUserId(userSession.requireUser().getId())
                .map(p -> p.getPreferred1rmFormula())
                .orElse(OneRepMaxFormula.EPLEY);
        List<PersonalRecord> newRecords = progressService.evaluateAfterSet(
                userSession.requireUser().getId(), set, preferred);
        return new LogSetResult(set, newRecords);
    }

    public void skipExercise() {
        ActiveWorkout workout = requireActive();
        if (workout.currentItem() == null) {
            throw new ValidationException("No exercise to skip");
        }
        workout.skipCurrentExercise();
    }

    public WorkoutSession finish(String notes) {
        ActiveWorkout workout = requireActive();
        Instant ended = Instant.now();
        String cleaned = notes == null || notes.isBlank() ? null : notes.trim();
        sessionDao.finish(workout.getSession().getId(), ended, cleaned);
        workout.getSession().setEndedAt(ended);
        workout.getSession().setNotes(cleaned);
        workout.getSession().setSets(sessionDao.findSetsBySession(workout.getSession().getId()));
        workout.markFinished();
        WorkoutSession done = workout.getSession();
        active = null;
        return done;
    }

    public Optional<LoggedSet> previousPerformance() {
        ActiveWorkout workout = requireActive();
        PlanItem item = workout.currentItem();
        if (item == null) {
            return Optional.empty();
        }
        return sessionDao.findPreviousPerformance(
                userSession.requireUser().getId(),
                item.getExerciseId(),
                workout.getSession().getId()
        );
    }

    /** Best completed set for the current exercise earlier in this same session. */
    public Optional<LoggedSet> lastSetThisExercise() {
        ActiveWorkout workout = requireActive();
        PlanItem item = workout.currentItem();
        if (item == null) {
            return Optional.empty();
        }
        LoggedSet last = null;
        for (LoggedSet set : workout.getSession().getSets()) {
            if (set.getExerciseId() == item.getExerciseId() && set.isCompleted()) {
                last = set;
            }
        }
        return Optional.ofNullable(last);
    }

    private ActiveWorkout requireActive() {
        if (active == null || active.getSession() == null) {
            throw new ValidationException("No active workout");
        }
        return active;
    }
}
