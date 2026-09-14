package com.fittrack.domain;


import java.time.LocalDate;
import java.time.Period;

public final class Profile extends BaseEntity {

    private long userId;
    private String fullName;
    private LocalDate dateOfBirth;
    private Sex sex;
    private double heightCm;
    private ActivityLevel activityLevel;
    private OneRepMaxFormula preferred1rmFormula = OneRepMaxFormula.EPLEY;
    private Units units = Units.METRIC;

    public Profile() {
    }

    public int getAge() {
        return getAgeOn(LocalDate.now());
    }

    public int getAgeOn(LocalDate onDate) {
        if (dateOfBirth == null) {
            throw new IllegalStateException("dateOfBirth is required to compute age");
        }
        if (onDate.isBefore(dateOfBirth)) {
            throw new IllegalArgumentException("Reference date is before date of birth");
        }
        return Period.between(dateOfBirth, onDate).getYears();
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
        setId(userId);
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public Sex getSex() {
        return sex;
    }

    public void setSex(Sex sex) {
        this.sex = sex;
    }

    public double getHeightCm() {
        return heightCm;
    }

    public void setHeightCm(double heightCm) {
        this.heightCm = heightCm;
    }

    public ActivityLevel getActivityLevel() {
        return activityLevel;
    }

    public void setActivityLevel(ActivityLevel activityLevel) {
        this.activityLevel = activityLevel;
    }

    public OneRepMaxFormula getPreferred1rmFormula() {
        return preferred1rmFormula;
    }

    public void setPreferred1rmFormula(OneRepMaxFormula preferred1rmFormula) {
        this.preferred1rmFormula = preferred1rmFormula;
    }

    public Units getUnits() {
        return units;
    }

    public void setUnits(Units units) {
        this.units = units;
    }
}
