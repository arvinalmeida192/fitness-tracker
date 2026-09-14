package com.fittrack.service;

import com.fittrack.domain.PersonalRecord;
import com.fittrack.domain.LoggedSet;

import java.util.List;

public record LogSetResult(LoggedSet set, List<PersonalRecord> newRecords) {
}
