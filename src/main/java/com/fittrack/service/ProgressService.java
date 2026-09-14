package com.fittrack.service;

import com.fittrack.concurrent.PrNotificationBuffer;
import com.fittrack.domain.common.OneRepMaxFormula;
import com.fittrack.domain.common.PersonalRecordType;
import com.fittrack.domain.progress.PersonalRecord;
import com.fittrack.domain.user.Profile;
import com.fittrack.domain.workout.LoggedSet;
import com.fittrack.persistence.sqlite.PersonalRecordDao;
import com.fittrack.persistence.sqlite.ProfileDao;
import com.fittrack.persistence.sqlite.WorkoutSessionDao;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ProgressService {

    private final PersonalRecordDao personalRecordDao;
    private final ProfileDao profileDao;
    private final WorkoutSessionDao workoutSessionDao;
    private final OneRepMaxService oneRepMaxService;
    private final UserSession userSession;
    private final PrNotificationBuffer prNotifications;

    public ProgressService(
            PersonalRecordDao personalRecordDao,
            ProfileDao profileDao,
            WorkoutSessionDao workoutSessionDao,
            OneRepMaxService oneRepMaxService,
            UserSession userSession,
            PrNotificationBuffer prNotifications
    ) {
        this.personalRecordDao = personalRecordDao;
        this.profileDao = profileDao;
        this.workoutSessionDao = workoutSessionDao;
        this.oneRepMaxService = oneRepMaxService;
        this.userSession = userSession;
        this.prNotifications = prNotifications;
    }

    public PrNotificationBuffer notifications() {
        return prNotifications;
    }

    public OneRepMaxFormula preferredFormula() {
        return profileDao.findByUserId(userSession.requireUser().getId())
                .map(Profile::getPreferred1rmFormula)
                .orElse(OneRepMaxFormula.EPLEY);
    }

    public List<PersonalRecord> listRecords() {
        return personalRecordDao.findByUser(userSession.requireUser().getId());
    }

    public List<LoggedSet> oneRmHistory(long exerciseId) {
        return workoutSessionDao.findSetsWithOneRm(userSession.requireUser().getId(), exerciseId);
    }

    public List<WorkoutSessionDao.ExerciseSummary> exercisesWithLoggedSets() {
        return workoutSessionDao.findExercisesWithSets(userSession.requireUser().getId());
    }

    /**
     * Compares the logged set to stored PRs and inserts any new records.
     */
    public List<PersonalRecord> evaluateAfterSet(long userId, LoggedSet set, OneRepMaxFormula preferred) {
        List<PersonalRecord> created = new ArrayList<>();
        if (!set.isCompleted() || set.getWeightKg() <= 0) {
            return created;
        }

        OneRepMaxFormula formula = preferred == null ? OneRepMaxFormula.EPLEY : preferred;
        Optional<Double> estimate = Optional.ofNullable(oneRepMaxService.preferredEstimate(set, formula));

        Optional<PersonalRecord> bestAtReps =
                personalRecordDao.findBestHeaviestAtReps(userId, set.getExerciseId(), set.getReps());
        boolean heaviestPr = bestAtReps.isEmpty()
                || set.getWeightKg() > bestAtReps.get().getWeightKg() + 1e-9;
        if (heaviestPr) {
            PersonalRecord record = new PersonalRecord();
            record.setUserId(userId);
            record.setExerciseId(set.getExerciseId());
            record.setExerciseName(set.getExerciseNameSnapshot());
            record.setPrType(PersonalRecordType.HEAVIEST_AT_REPS);
            record.setReps(set.getReps());
            record.setWeightKg(set.getWeightKg());
            estimate.ifPresent(record::setEstimated1rm);
            record.setFormula(formula);
            record.setAchievedAt(set.getLoggedAt());
            record.setLoggedSetId(set.getId());
            created.add(personalRecordDao.insert(record));
        }

        if (estimate.isPresent()) {
            Optional<PersonalRecord> best1rm =
                    personalRecordDao.findBestEstimated1rm(userId, set.getExerciseId());
            boolean oneRmPr = best1rm.isEmpty()
                    || estimate.get() > best1rm.get().getEstimated1rm() + 1e-9;
            if (oneRmPr) {
                PersonalRecord record = new PersonalRecord();
                record.setUserId(userId);
                record.setExerciseId(set.getExerciseId());
                record.setExerciseName(set.getExerciseNameSnapshot());
                record.setPrType(PersonalRecordType.ESTIMATED_1RM);
                record.setReps(set.getReps());
                record.setWeightKg(set.getWeightKg());
                record.setEstimated1rm(estimate.get());
                record.setFormula(formula);
                record.setAchievedAt(set.getLoggedAt());
                record.setLoggedSetId(set.getId());
                created.add(personalRecordDao.insert(record));
            }
        }

        prNotifications.pushAll(created);
        return created;
    }
}
