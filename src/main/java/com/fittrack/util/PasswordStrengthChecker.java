package com.fittrack.util;

import java.util.regex.Pattern;

/**
 * Classifies passwords using regex rules (syllabus: Strings / Regex).
 */
public final class PasswordStrengthChecker {

    public enum Strength {
        WEAK,
        MODERATE,
        STRONG
    }

    private static final Pattern HAS_LOWER = Pattern.compile("[a-z]");
    private static final Pattern HAS_UPPER = Pattern.compile("[A-Z]");
    private static final Pattern HAS_DIGIT = Pattern.compile("\\d");
    private static final Pattern HAS_SPECIAL = Pattern.compile("[^A-Za-z0-9]");

    private PasswordStrengthChecker() {
    }

    public static Strength check(String password) {
        if (password == null || password.length() < 6) {
            return Strength.WEAK;
        }

        int score = 0;
        if (password.length() >= 8) {
            score++;
        }
        if (password.length() >= 12) {
            score++;
        }
        if (HAS_LOWER.matcher(password).find()) {
            score++;
        }
        if (HAS_UPPER.matcher(password).find()) {
            score++;
        }
        if (HAS_DIGIT.matcher(password).find()) {
            score++;
        }
        if (HAS_SPECIAL.matcher(password).find()) {
            score++;
        }

        if (score >= 5) {
            return Strength.STRONG;
        }
        if (score >= 3) {
            return Strength.MODERATE;
        }
        return Strength.WEAK;
    }

    public static boolean isAcceptable(String password) {
        return check(password) != Strength.WEAK;
    }
}
