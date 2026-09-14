package com.fittrack.domain.exercise;

import com.fittrack.domain.common.BaseEntity;
import com.fittrack.domain.common.MuscleGroup;

import java.util.ArrayList;
import java.util.List;

public final class Exercise extends BaseEntity {

    private String name;
    /** Primary display / legacy filter muscle (usually first of {@link #primaryMuscles}). */
    private MuscleGroup muscleGroup;
    private List<MuscleGroup> primaryMuscles = new ArrayList<>();
    private List<MuscleGroup> secondaryMuscles = new ArrayList<>();
    private String equipment;
    private String instructions;
    private int defaultRestSec = 90;
    private String externalId;

    public Exercise() {
    }

    public Exercise(
            Long id,
            String name,
            MuscleGroup muscleGroup,
            String equipment,
            String instructions,
            int defaultRestSec
    ) {
        super(id);
        this.name = name;
        setPrimaryMuscles(muscleGroup == null ? List.of() : List.of(muscleGroup));
        this.equipment = equipment;
        this.instructions = instructions;
        this.defaultRestSec = defaultRestSec;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public MuscleGroup getMuscleGroup() {
        return muscleGroup;
    }

    public void setMuscleGroup(MuscleGroup muscleGroup) {
        this.muscleGroup = muscleGroup;
        if (primaryMuscles.isEmpty() && muscleGroup != null) {
            primaryMuscles = new ArrayList<>(List.of(muscleGroup));
        }
    }

    public List<MuscleGroup> getPrimaryMuscles() {
        return List.copyOf(primaryMuscles);
    }

    public void setPrimaryMuscles(List<MuscleGroup> primaryMuscles) {
        this.primaryMuscles = primaryMuscles == null
                ? new ArrayList<>()
                : new ArrayList<>(primaryMuscles);
        if (!this.primaryMuscles.isEmpty()) {
            this.muscleGroup = this.primaryMuscles.get(0);
        } else if (this.muscleGroup == null) {
            this.muscleGroup = MuscleGroup.OTHER;
            this.primaryMuscles = new ArrayList<>(List.of(MuscleGroup.OTHER));
        } else {
            this.primaryMuscles = new ArrayList<>(List.of(this.muscleGroup));
        }
    }

    public List<MuscleGroup> getSecondaryMuscles() {
        return List.copyOf(secondaryMuscles);
    }

    public void setSecondaryMuscles(List<MuscleGroup> secondaryMuscles) {
        this.secondaryMuscles = secondaryMuscles == null
                ? new ArrayList<>()
                : new ArrayList<>(secondaryMuscles);
    }

    public boolean targetsMuscle(MuscleGroup muscle) {
        if (muscle == null) {
            return true;
        }
        return primaryMuscles.contains(muscle) || secondaryMuscles.contains(muscle)
                || muscleGroup == muscle;
    }

    public String secondaryLabel() {
        if (secondaryMuscles.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (MuscleGroup m : secondaryMuscles) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(m.name());
        }
        return sb.toString();
    }

    public String getEquipment() {
        return equipment;
    }

    public void setEquipment(String equipment) {
        this.equipment = equipment;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public int getDefaultRestSec() {
        return defaultRestSec;
    }

    public void setDefaultRestSec(int defaultRestSec) {
        this.defaultRestSec = defaultRestSec;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    @Override
    public String toString() {
        return name;
    }
}
