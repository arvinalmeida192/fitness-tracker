package com.fittrack.service;

import com.fittrack.domain.ActivityLevel;
import com.fittrack.domain.AuthenticationException;
import com.fittrack.domain.DataAccessException;
import com.fittrack.domain.OneRepMaxFormula;
import com.fittrack.domain.Sex;
import com.fittrack.domain.Units;
import com.fittrack.domain.ValidationException;
import com.fittrack.domain.Profile;
import com.fittrack.domain.User;
import com.fittrack.domain.WeightEntry;
import com.fittrack.persistence.ProfileDao;
import com.fittrack.persistence.UserDao;
import com.fittrack.persistence.WeightEntryDao;
import com.fittrack.util.PasswordHasher;
import com.fittrack.util.PasswordStrengthChecker;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.regex.Pattern;

public final class AuthService {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,32}$");

    private final UserDao userDao;
    private final ProfileDao profileDao;
    private final WeightEntryDao weightEntryDao;
    private final PasswordHasher passwordHasher;
    private final UserSession session;

    public AuthService(
            UserDao userDao,
            ProfileDao profileDao,
            WeightEntryDao weightEntryDao,
            PasswordHasher passwordHasher,
            UserSession session
    ) {
        this.userDao = userDao;
        this.profileDao = profileDao;
        this.weightEntryDao = weightEntryDao;
        this.passwordHasher = passwordHasher;
        this.session = session;
    }

    public User register(
            String username,
            String password,
            String fullName,
            LocalDate dateOfBirth,
            Sex sex,
            double heightCm,
            double initialWeightKg
    ) {
        validateUsername(username);
        validatePassword(password);
        validateProfileFields(fullName, dateOfBirth, sex, heightCm, initialWeightKg);

        if (userDao.findByUsername(username).isPresent()) {
            throw new ValidationException("Username is already taken");
        }

        String salt = passwordHasher.generateSalt();
        String hash = passwordHasher.hash(password, salt);

        User user = new User(null, username.trim(), hash, salt, Instant.now());
        Profile profile = new Profile();
        profile.setFullName(fullName.trim());
        profile.setDateOfBirth(dateOfBirth);
        profile.setSex(sex);
        profile.setHeightCm(heightCm);
        profile.setActivityLevel(ActivityLevel.MODERATE);
        profile.setPreferred1rmFormula(OneRepMaxFormula.EPLEY);
        profile.setUnits(Units.METRIC);

        Connection conn = userDao.connection();
        try {
            conn.setAutoCommit(false);
            userDao.insert(user);
            profile.setUserId(user.getId());
            profileDao.upsert(profile);

            WeightEntry entry = new WeightEntry(null, user.getId(), initialWeightKg, Instant.now(), "Initial weight");
            weightEntryDao.insert(entry);
            conn.commit();
        } catch (DataAccessException e) {
            rollbackQuietly(conn);
            throw e;
        } catch (SQLException e) {
            rollbackQuietly(conn);
            throw new DataAccessException("Registration failed", e);
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ignored) {
                // ignore
            }
        }

        session.login(user);
        return user;
    }

    public User login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            throw new ValidationException("Username and password are required");
        }
        User user = userDao.findByUsername(username.trim())
                .orElseThrow(() -> new AuthenticationException("Invalid username or password"));
        if (!passwordHasher.matches(password, user.getSalt(), user.getPasswordHash())) {
            throw new AuthenticationException("Invalid username or password");
        }
        session.login(user);
        return user;
    }

    public void logout() {
        session.logout();
    }

    private static void validateUsername(String username) {
        if (username == null || !USERNAME_PATTERN.matcher(username.trim()).matches()) {
            throw new ValidationException("Username must be 3–32 characters: letters, digits, underscore");
        }
    }

    private static void validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new ValidationException("Password is required");
        }
        if (!PasswordStrengthChecker.isAcceptable(password)) {
            throw new ValidationException("Password is too weak — use 8+ chars with mixed case/digits");
        }
    }

    private static void validateProfileFields(
            String fullName,
            LocalDate dateOfBirth,
            Sex sex,
            double heightCm,
            double initialWeightKg
    ) {
        if (fullName == null || fullName.isBlank()) {
            throw new ValidationException("Full name is required");
        }
        if (dateOfBirth == null || dateOfBirth.isAfter(LocalDate.now().minusYears(10))) {
            throw new ValidationException("Enter a valid date of birth (age 10+)");
        }
        if (dateOfBirth.isBefore(LocalDate.now().minusYears(120))) {
            throw new ValidationException("Date of birth is out of range");
        }
        if (sex == null) {
            throw new ValidationException("Sex is required");
        }
        if (heightCm < 100 || heightCm > 250) {
            throw new ValidationException("Height must be between 100 and 250 cm");
        }
        if (initialWeightKg < 30 || initialWeightKg > 400) {
            throw new ValidationException("Weight must be between 30 and 400 kg");
        }
    }

    private static void rollbackQuietly(Connection conn) {
        try {
            conn.rollback();
        } catch (SQLException ignored) {
            // ignore
        }
    }
}
