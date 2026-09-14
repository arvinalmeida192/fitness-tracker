package com.fittrack.service;

import com.fittrack.domain.progress.PersonalRecord;
import com.fittrack.domain.workout.LoggedSet;

import java.util.List;

public record LogSetResult(LoggedSet set, List<PersonalRecord> newRecords) {
}
