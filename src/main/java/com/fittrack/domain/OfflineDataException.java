package com.fittrack.domain;

public class OfflineDataException extends RuntimeException {

    public OfflineDataException(String message) {
        super(message);
    }

    public OfflineDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
