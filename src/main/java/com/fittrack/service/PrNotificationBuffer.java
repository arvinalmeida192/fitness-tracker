package com.fittrack.service;

import com.fittrack.domain.PersonalRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

/**
 * Thread-safe ring of recent PR notifications.
 * Uses {@link Vector} deliberately (syllabus concurrent collection) as a bounded buffer.
 */
public final class PrNotificationBuffer {

    private static final int CAPACITY = 20;

    private final Vector<PersonalRecord> recent = new Vector<>();

    public synchronized void pushAll(List<PersonalRecord> records) {
        if (records == null) {
            return;
        }
        for (PersonalRecord record : records) {
            push(record);
        }
    }

    public synchronized List<PersonalRecord> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(recent));
    }

    private synchronized void push(PersonalRecord record) {
        if (record == null) {
            return;
        }
        recent.add(0, record);
        while (recent.size() > CAPACITY) {
            recent.remove(recent.size() - 1);
        }
    }
}
