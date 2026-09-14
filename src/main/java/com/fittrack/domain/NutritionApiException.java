package com.fittrack.domain;

public class NutritionApiException extends RuntimeException {

    public NutritionApiException(String message) {
        super(message);
    }

    public NutritionApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
