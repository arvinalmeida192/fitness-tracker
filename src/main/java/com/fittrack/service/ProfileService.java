package com.fittrack.service;

import com.fittrack.domain.common.ActivityLevel;
import com.fittrack.domain.common.OneRepMaxFormula;
import com.fittrack.domain.common.Sex;
import com.fittrack.domain.common.Units;
import com.fittrack.domain.common.ValidationException;
import com.fittrack.domain.user.Profile;
import com.fittrack.domain.user.User;
import com.fittrack.domain.user.WeightEntry;
import com.fittrack.persistence.sqlite.ProfileDao;
import com.fittrack.persistence.sqlite.WeightEntryDao;
import com.fittrack.util.BodyMetrics;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public final class ProfileService {

    private final ProfileDao profileDao;
    private final WeightEntryDao weightEntryDao;
    private final UserSession session;

    public ProfileService(ProfileDao profileDao, WeightEntryDao weightEntryDao, UserSession session) {
        this.profileDao = profileDao;
        this.weightEntryDao = weightEntryDao;
        this.session = session;
    }

    public Profile requireProfile() {
        User user = session.requireUser();
        return profileDao.findByUserId(user.getId())
                .orElseThrow(() -> new ValidationException("Profile not found for current user"));
    }

    public Profile updateProfile(
            String fullName,
            LocalDate dateOfBirth,
            Sex sex,
            double heightCm,
            OneRepMaxFormula formula,
            Units units
    ) {
        User user = session.requireUser();
        if (fullName == null || fullName.isBlank()) {
            throw new ValidationException("Full name is required");
        }
        if (dateOfBirth == null || dateOfBirth.isAfter(LocalDate.now().minusYears(10))) {
            throw new ValidationException("Enter a valid date of birth (age 10+)");
        }
        if (heightCm < 100 || heightCm > 250) {
            throw new ValidationException("Height must be between 100 and 250 cm");
        }
        if (sex == null || formula == null || units == null) {
            throw new ValidationException("All profile fields are required");
        }

        Profile profile = profileDao.findByUserId(user.getId()).orElseGet(Profile::new);
        profile.setUserId(user.getId());
        profile.setFullName(fullName.trim());
        profile.setDateOfBirth(dateOfBirth);
        profile.setSex(sex);
        profile.setHeightCm(heightCm);
        if (profile.getActivityLevel() == null) {
            profile.setActivityLevel(ActivityLevel.MODERATE);
        }
        profile.setPreferred1rmFormula(formula);
        profile.setUnits(units);
        profileDao.upsert(profile);
        return profile;
    }

    /** Used by JSON backup import; activity level is not shown in the profile UI. */
    public void restoreActivityLevel(ActivityLevel activityLevel) {
        if (activityLevel == null) {
            return;
        }
        Profile profile = requireProfile();
        profile.setActivityLevel(activityLevel);
        profileDao.upsert(profile);
    }

    public WeightEntry logWeight(double weightKg, String note) {
        User user = session.requireUser();
        if (weightKg < 30 || weightKg > 400) {
            throw new ValidationException("Weight must be between 30 and 400 kg");
        }
        WeightEntry entry = new WeightEntry(null, user.getId(), weightKg, Instant.now(),
                note == null || note.isBlank() ? null : note.trim());
        return weightEntryDao.insert(entry);
    }

    public List<WeightEntry> weightHistory() {
        return weightEntryDao.findByUserId(session.requireUser().getId());
    }

    private Optional<WeightEntry> latestWeight() {
        return weightEntryDao.findLatest(session.requireUser().getId());
    }

    public BodySnapshot snapshot() {
        Profile profile = requireProfile();
        Optional<WeightEntry> latest = latestWeight();
        Double weight = latest.map(WeightEntry::getWeightKg).orElse(null);
        Double bmi = null;
        BodyMetrics.BmiCategory category = null;
        Double bmr = null;
        Double tdee = null;
        if (weight != null) {
            bmi = BodyMetrics.bmi(weight, profile.getHeightCm());
            category = BodyMetrics.bmiCategory(bmi);
            bmr = BodyMetrics.bmr(weight, profile.getHeightCm(), profile.getAge(), profile.getSex());
            tdee = BodyMetrics.tdee(bmr, profile.getActivityLevel());
        }
        return new BodySnapshot(profile, weight, bmi, category, bmr, tdee);
    }

    public record BodySnapshot(
            Profile profile,
            Double weightKg,
            Double bmi,
            BodyMetrics.BmiCategory bmiCategory,
            Double bmr,
            Double tdee
    ) {
    }
}
