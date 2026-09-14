package com.fittrack.app;

import com.fittrack.api.nutrition.UsdaFoodDataClient;
import com.fittrack.concurrent.PrNotificationBuffer;
import com.fittrack.export.BackupService;
import com.fittrack.persistence.migration.MigrationRunner;
import com.fittrack.persistence.sqlite.BodyGoalDao;
import com.fittrack.persistence.sqlite.Database;
import com.fittrack.persistence.sqlite.DishDao;
import com.fittrack.persistence.sqlite.ExerciseDao;
import com.fittrack.persistence.sqlite.IngredientDao;
import com.fittrack.persistence.sqlite.MealLogDao;
import com.fittrack.persistence.sqlite.NutritionGoalDao;
import com.fittrack.persistence.sqlite.PersonalRecordDao;
import com.fittrack.persistence.sqlite.ProfileDao;
import com.fittrack.persistence.sqlite.UserDao;
import com.fittrack.persistence.sqlite.WeightEntryDao;
import com.fittrack.persistence.sqlite.WorkoutPlanDao;
import com.fittrack.persistence.sqlite.WorkoutSessionDao;
import com.fittrack.service.AuthService;
import com.fittrack.service.DashboardService;
import com.fittrack.service.DishService;
import com.fittrack.service.ExerciseService;
import com.fittrack.service.MealService;
import com.fittrack.service.MuscleVolumeService;
import com.fittrack.service.NutritionGoalService;
import com.fittrack.service.NutritionService;
import com.fittrack.service.OneRepMaxService;
import com.fittrack.service.ProfileService;
import com.fittrack.service.ProgressService;
import com.fittrack.service.UserSession;
import com.fittrack.service.WorkoutPlanService;
import com.fittrack.service.WorkoutSessionService;
import com.fittrack.util.PasswordHasher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Composition root for shared services.
 */
public final class AppContext {

    private static AppConfig config;
    private static Database database;
    private static UserSession session;
    private static AuthService authService;
    private static ProfileService profileService;
    private static ExerciseService exerciseService;
    private static WorkoutPlanService workoutPlanService;
    private static WorkoutSessionService workoutSessionService;
    private static OneRepMaxService oneRepMaxService;
    private static ProgressService progressService;
    private static NutritionService nutritionService;
    private static DishService dishService;
    private static MealService mealService;
    private static NutritionGoalService nutritionGoalService;
    private static DashboardService dashboardService;
    private static BackupService backupService;
    private static PrNotificationBuffer prNotificationBuffer;
    private static MuscleVolumeService muscleVolumeService;

    private AppContext() {
    }

    public static synchronized void initialize() {
        if (config != null) {
            return;
        }
        config = AppConfig.load();
        wireFromOpenDatabase(Database.open(config.get("db.path", "data/fittrack.db")));
    }

    private static void wireFromOpenDatabase(Database openDatabase) {
        database = openDatabase;
        new MigrationRunner(database).migrate();
        session = new UserSession();
        prNotificationBuffer = new PrNotificationBuffer();

        UserDao userDao = new UserDao(database);
        ProfileDao profileDao = new ProfileDao(database);
        WeightEntryDao weightEntryDao = new WeightEntryDao(database);
        ExerciseDao exerciseDao = new ExerciseDao(database);
        WorkoutPlanDao workoutPlanDao = new WorkoutPlanDao(database);
        WorkoutSessionDao workoutSessionDao = new WorkoutSessionDao(database);
        PersonalRecordDao personalRecordDao = new PersonalRecordDao(database);
        IngredientDao ingredientDao = new IngredientDao(database);
        DishDao dishDao = new DishDao(database);
        MealLogDao mealLogDao = new MealLogDao(database);
        NutritionGoalDao nutritionGoalDao = new NutritionGoalDao(database);
        BodyGoalDao bodyGoalDao = new BodyGoalDao(database);
        PasswordHasher passwordHasher = new PasswordHasher();

        oneRepMaxService = new OneRepMaxService();
        progressService = new ProgressService(
                personalRecordDao, profileDao, workoutSessionDao, oneRepMaxService, session, prNotificationBuffer);

        UsdaFoodDataClient usdaClient = new UsdaFoodDataClient(
                () -> config.get("usda.api.key", ""),
                config.get("usda.api.baseUrl", "https://api.nal.usda.gov/fdc/v1")
        );
        nutritionService = new NutritionService(ingredientDao, usdaClient, config);
        dishService = new DishService(database, dishDao, ingredientDao, session);
        mealService = new MealService(mealLogDao, dishService, ingredientDao, session);

        authService = new AuthService(userDao, profileDao, weightEntryDao, passwordHasher, session);
        profileService = new ProfileService(profileDao, weightEntryDao, session);
        nutritionGoalService = new NutritionGoalService(nutritionGoalDao, mealService, session);
        exerciseService = new ExerciseService(database, exerciseDao);
        exerciseService.ensureCatalog();
        workoutPlanService = new WorkoutPlanService(workoutPlanDao, exerciseDao, session);
        workoutSessionService = new WorkoutSessionService(
                workoutSessionDao,
                workoutPlanService,
                profileDao,
                oneRepMaxService,
                progressService,
                session
        );
        dashboardService = new DashboardService(
                nutritionGoalService,
                profileService,
                progressService,
                workoutPlanService,
                bodyGoalDao,
                session
        );
        backupService = new BackupService(
                database,
                config,
                session,
                profileService,
                nutritionGoalService,
                dishService,
                mealLogDao,
                bodyGoalDao,
                ingredientDao
        );
        muscleVolumeService = new MuscleVolumeService(exerciseDao, workoutSessionDao, session);
    }

    /**
     * Replaces the live SQLite file with a backup, then re-wires services. User must log in again.
     */
    public static synchronized void restoreDatabaseFrom(Path backupFile) throws IOException {
        requireInitialized();
        if (backupFile == null || !Files.isRegularFile(backupFile)) {
            throw new IllegalArgumentException("Backup file not found");
        }
        Path dbPath = database.getDbPath();
        String path = dbPath.toString();
        shutdownWorkersOnly();
        if (database != null) {
            database.close();
            database = null;
        }
        clearServiceRefsKeepConfig();
        Files.copy(backupFile, dbPath, StandardCopyOption.REPLACE_EXISTING);
        // Drop WAL sidecars so restore is clean
        Files.deleteIfExists(Path.of(path + "-wal"));
        Files.deleteIfExists(Path.of(path + "-shm"));
        wireFromOpenDatabase(Database.open(path));
    }

    public static AppConfig config() {
        requireInitialized();
        return config;
    }

    public static Database database() {
        requireInitialized();
        return database;
    }

    public static UserSession session() {
        requireInitialized();
        return session;
    }

    public static AuthService authService() {
        requireInitialized();
        return authService;
    }

    public static ProfileService profileService() {
        requireInitialized();
        return profileService;
    }

    public static ExerciseService exerciseService() {
        requireInitialized();
        return exerciseService;
    }

    public static WorkoutPlanService workoutPlanService() {
        requireInitialized();
        return workoutPlanService;
    }

    public static WorkoutSessionService workoutSessionService() {
        requireInitialized();
        return workoutSessionService;
    }

    public static OneRepMaxService oneRepMaxService() {
        requireInitialized();
        return oneRepMaxService;
    }

    public static ProgressService progressService() {
        requireInitialized();
        return progressService;
    }

    public static NutritionService nutritionService() {
        requireInitialized();
        return nutritionService;
    }

    public static DishService dishService() {
        requireInitialized();
        return dishService;
    }

    public static MealService mealService() {
        requireInitialized();
        return mealService;
    }

    public static NutritionGoalService nutritionGoalService() {
        requireInitialized();
        return nutritionGoalService;
    }

    public static DashboardService dashboardService() {
        requireInitialized();
        return dashboardService;
    }

    public static BackupService backupService() {
        requireInitialized();
        return backupService;
    }

    public static MuscleVolumeService muscleVolumeService() {
        requireInitialized();
        return muscleVolumeService;
    }

    public static synchronized void shutdown() {
        shutdownWorkersOnly();
        if (session != null) {
            session.logout();
            session = null;
        }
        if (database != null) {
            database.close();
            database = null;
        }
        clearServiceRefsKeepConfig();
        config = null;
        prNotificationBuffer = null;
    }

    private static void shutdownWorkersOnly() {
        if (nutritionService != null) {
            nutritionService.shutdown();
        }
        if (backupService != null) {
            backupService.shutdown();
        }
    }

    private static void clearServiceRefsKeepConfig() {
        authService = null;
        profileService = null;
        exerciseService = null;
        workoutPlanService = null;
        workoutSessionService = null;
        oneRepMaxService = null;
        progressService = null;
        nutritionService = null;
        dishService = null;
        mealService = null;
        nutritionGoalService = null;
        dashboardService = null;
        backupService = null;
        muscleVolumeService = null;
        session = null;
        prNotificationBuffer = null;
    }

    private static void requireInitialized() {
        if (config == null || database == null) {
            throw new IllegalStateException("AppContext has not been initialized");
        }
    }
}
