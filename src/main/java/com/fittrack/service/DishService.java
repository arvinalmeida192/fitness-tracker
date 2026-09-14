package com.fittrack.service;

import com.fittrack.domain.ValidationException;
import com.fittrack.domain.Dish;
import com.fittrack.domain.DishItem;
import com.fittrack.domain.Ingredient;
import com.fittrack.domain.PortionScaler;
import com.fittrack.domain.User;
import com.fittrack.persistence.ConnectionTxn;
import com.fittrack.persistence.Database;
import com.fittrack.persistence.DishDao;
import com.fittrack.persistence.IngredientDao;

import java.util.ArrayList;
import java.util.List;

public final class DishService {

    private final Database database;
    private final DishDao dishDao;
    private final IngredientDao ingredientDao;
    private final UserSession userSession;

    public DishService(Database database, DishDao dishDao, IngredientDao ingredientDao, UserSession userSession) {
        this.database = database;
        this.dishDao = dishDao;
        this.ingredientDao = ingredientDao;
        this.userSession = userSession;
    }

    public List<Dish> list() {
        return dishDao.listByUser(userSession.requireUser().getId());
    }

    public Dish get(long dishId) {
        return dishDao.findById(dishId, userSession.requireUser().getId())
                .orElseThrow(() -> new ValidationException("Dish not found"));
    }

    public Dish create(String name, String notes) {
        String cleaned = requireName(name);
        User user = userSession.requireUser();
        Dish dish = new Dish();
        dish.setUserId(user.getId());
        dish.setName(cleaned);
        dish.setNotes(blankToNull(notes));
        dish.setTotalWeightG(0);
        return dishDao.insert(dish);
    }

    public void updateMeta(long dishId, String name, String notes, Double yieldWeightG) {
        Dish dish = get(dishId);
        dish.setName(requireName(name));
        dish.setNotes(blankToNull(notes));
        if (yieldWeightG != null && yieldWeightG <= 0) {
            throw new ValidationException("Yield weight must be positive when set");
        }
        dish.setYieldWeightG(yieldWeightG);
        dishDao.updateMeta(dish);
    }

    public Dish saveItems(long dishId, List<DishItem> items) {
        Dish dish = get(dishId);
        List<DishItem> cleaned = validateItems(items);
        double total = PortionScaler.sumAmounts(cleaned);
        dish.setTotalWeightG(total);
        dish.setItems(cleaned);
        ConnectionTxn.run(database, () -> {
            dishDao.updateMeta(dish);
            dishDao.replaceItems(dishId, cleaned);
        });
        return get(dishId);
    }

    public void delete(long dishId) {
        dishDao.delete(dishId, userSession.requireUser().getId());
    }

    private List<DishItem> validateItems(List<DishItem> items) {
        if (items == null || items.isEmpty()) {
            throw new ValidationException("Add at least one ingredient");
        }
        List<DishItem> cleaned = new ArrayList<>();
        for (DishItem item : items) {
            if (item.getAmountG() <= 0) {
                throw new ValidationException("Each ingredient amount must be > 0 g");
            }
            Ingredient ingredient = ingredientDao.findById(item.getIngredientId())
                    .orElseThrow(() -> new ValidationException("Ingredient not found — cache it under Nutrition first"));
            DishItem row = new DishItem();
            row.setIngredientId(ingredient.getId());
            row.setIngredientName(ingredient.getName());
            row.setPer100g(ingredient.getPer100g());
            row.setAmountG(item.getAmountG());
            cleaned.add(row);
        }
        return cleaned;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("Dish name is required");
        }
        return name.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
