package com.fittrack.domain.common;

public class OfflineDataException extends RuntimeException {

    public OfflineDataException(String message) {
        super(message);
    }

    public OfflineDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
