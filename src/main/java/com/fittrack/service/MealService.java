package com.fittrack.service;

import com.fittrack.domain.common.Macros;
import com.fittrack.domain.common.MealType;
import com.fittrack.domain.common.ValidationException;
import com.fittrack.domain.nutrition.Dish;
import com.fittrack.domain.nutrition.Ingredient;
import com.fittrack.domain.nutrition.MealLog;
import com.fittrack.domain.nutrition.PortionScaler;
import com.fittrack.persistence.sqlite.IngredientDao;
import com.fittrack.persistence.sqlite.MealLogDao;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

public final class MealService {

    private final MealLogDao mealLogDao;
    private final DishService dishService;
    private final IngredientDao ingredientDao;
    private final UserSession userSession;

    public MealService(
            MealLogDao mealLogDao,
            DishService dishService,
            IngredientDao ingredientDao,
            UserSession userSession
    ) {
        this.mealLogDao = mealLogDao;
        this.dishService = dishService;
        this.ingredientDao = ingredientDao;
        this.userSession = userSession;
    }

    public List<MealLog> todaysMeals() {
        return mealsOn(LocalDate.now(ZoneId.systemDefault()));
    }

    public List<MealLog> mealsOn(LocalDate day) {
        ZoneId zone = ZoneId.systemDefault();
        Instant from = day.atStartOfDay(zone).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(zone).toInstant();
        return mealLogDao.findForUserBetween(userSession.requireUser().getId(), from, to);
    }

    public Macros todaysTotals() {
        Macros total = Macros.zero();
        for (MealLog log : todaysMeals()) {
            total = total.plus(log.getMacros());
        }
        return total;
    }

    public MealLog logDish(long dishId, double portionG, MealType mealType, String notes) {
        if (portionG <= 0) {
            throw new ValidationException("Portion must be greater than 0 g");
        }
        Dish dish = dishService.get(dishId);
        if (dish.getItems().isEmpty()) {
            throw new ValidationException("Dish has no ingredients");
        }
        double scale = PortionScaler.scaleFactor(dish, portionG);
        Macros macros = PortionScaler.macrosForPortion(dish, portionG);

        MealLog log = new MealLog();
        log.setUserId(userSession.requireUser().getId());
        log.setEatenAt(Instant.now());
        log.setMealType(mealType == null ? MealType.OTHER : mealType);
        log.setDishId(dish.getId());
        log.setDishName(dish.getName());
        log.setPortionG(portionG);
        log.setScaleFactor(scale);
        log.setMacros(macros);
        log.setNotes(blankToNull(notes));
        return mealLogDao.insert(log);
    }

    public MealLog logIngredient(long ingredientId, double grams, MealType mealType, String notes) {
        if (grams <= 0) {
            throw new ValidationException("Amount must be greater than 0 g");
        }
        Ingredient ingredient = ingredientDao.findById(ingredientId)
                .orElseThrow(() -> new ValidationException("Ingredient not found"));
        Macros macros = ingredient.getPer100g().forGrams(grams);

        MealLog log = new MealLog();
        log.setUserId(userSession.requireUser().getId());
        log.setEatenAt(Instant.now());
        log.setMealType(mealType == null ? MealType.OTHER : mealType);
        log.setDishId(null);
        log.setDishName(ingredient.getName());
        log.setPortionG(grams);
        log.setScaleFactor(grams / 100.0);
        log.setMacros(macros);
        String cleanedNotes = blankToNull(notes);
        log.setNotes(cleanedNotes == null
                ? ingredient.getName()
                : ingredient.getName() + " — " + cleanedNotes);
        return mealLogDao.insert(log);
    }

    public void delete(long mealLogId) {
        mealLogDao.delete(mealLogId, userSession.requireUser().getId());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
