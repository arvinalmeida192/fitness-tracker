package com.fittrack.domain.common;

public class NutritionApiException extends RuntimeException {

    public NutritionApiException(String message) {
        super(message);
    }

    public NutritionApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
