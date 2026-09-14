package com.fittrack.service;

import com.fittrack.api.nutrition.UsdaFoodDataClient;
import com.fittrack.api.nutrition.UsdaSearchHit;
import com.fittrack.app.AppConfig;
import com.fittrack.domain.common.NutritionApiException;
import com.fittrack.domain.common.OfflineDataException;
import com.fittrack.domain.common.ValidationException;
import com.fittrack.domain.nutrition.Ingredient;
import com.fittrack.persistence.sqlite.IngredientDao;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class NutritionService {

    public record SearchRow(
            Integer fdcId,
            Long cachedId,
            String name,
            String brand,
            String origin,
            Ingredient ingredient
    ) {
    }

    public record SearchOutcome(String query, List<SearchRow> rows, String message, boolean offline) {
    }

    private final IngredientDao ingredientDao;
    private final UsdaFoodDataClient usdaClient;
    private final AppConfig config;

    private final ScheduledExecutorService debounce = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "nutrition-search-debounce");
        t.setDaemon(true);
        return t;
    });
    private final ExecutorService workers = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "nutrition-api-worker");
        t.setDaemon(true);
        return t;
    });

    private final Object searchLock = new Object();
    private ScheduledFuture<?> pendingDebounce;
    private Future<?> pendingSearch;
    private final AtomicLong searchEpoch = new AtomicLong();

    public NutritionService(IngredientDao ingredientDao, UsdaFoodDataClient usdaClient, AppConfig config) {
        this.ingredientDao = ingredientDao;
        this.usdaClient = usdaClient;
        this.config = config;
    }

    public int cachedCount() {
        return ingredientDao.count();
    }

    public List<Ingredient> listCached(int limit) {
        return ingredientDao.listAll(limit);
    }

    public String apiKeyStatus() {
        String key = config.get("usda.api.key", "");
        if (key == null || key.isBlank()) {
            return "missing";
        }
        if ("DEMO_KEY".equalsIgnoreCase(key.trim())) {
            return "DEMO_KEY (rate-limited)";
        }
        return "set";
    }

    public void saveApiKey(String key) {
        String cleaned = key == null ? "" : key.trim();
        if (cleaned.isBlank()) {
            throw new ValidationException("API key cannot be blank");
        }
        config.saveLocalOverride("usda.api.key", cleaned);
    }

    /**
     * Debounced search: local cache first, then USDA when online. Cancels in-flight work on new queries.
     */
    public void searchDebounced(String query, long delayMs, Consumer<SearchOutcome> onDone) {
        synchronized (searchLock) {
            if (pendingDebounce != null) {
                pendingDebounce.cancel(false);
            }
            if (pendingSearch != null) {
                pendingSearch.cancel(true);
            }
            long epoch = searchEpoch.incrementAndGet();
            pendingDebounce = debounce.schedule(() -> {
                synchronized (searchLock) {
                    pendingSearch = workers.submit(() -> runSearch(query, epoch, onDone));
                }
            }, Math.max(0, delayMs), TimeUnit.MILLISECONDS);
        }
    }

    private SearchOutcome runSearch(String query, long epoch, Consumer<SearchOutcome> onDone) {
        String q = query == null ? "" : query.trim();
        SearchOutcome outcome;
        if (q.length() < 2) {
            outcome = new SearchOutcome(q, List.of(), "Type at least 2 characters", false);
        } else {
            List<Ingredient> local = ingredientDao.searchByName(q, 40);
            Map<Integer, SearchRow> byFdc = new LinkedHashMap<>();
            List<SearchRow> rows = new ArrayList<>();
            for (Ingredient ingredient : local) {
                SearchRow row = new SearchRow(
                        ingredient.getFdcId(),
                        ingredient.getId(),
                        ingredient.getName(),
                        ingredient.getBrand(),
                        "Cache",
                        ingredient
                );
                rows.add(row);
                if (ingredient.getFdcId() != null) {
                    byFdc.put(ingredient.getFdcId(), row);
                }
            }

            String message = local.isEmpty() ? "No local matches" : "Showing " + local.size() + " cached";
            boolean offline = false;
            try {
                List<UsdaSearchHit> hits = usdaClient.search(q, 25);
                int added = 0;
                for (UsdaSearchHit hit : hits) {
                    if (byFdc.containsKey(hit.getFdcId())) {
                        continue;
                    }
                    rows.add(new SearchRow(
                            hit.getFdcId(),
                            null,
                            hit.getDescription(),
                            hit.getBrandOwner(),
                            "USDA",
                            null
                    ));
                    added++;
                }
                message = "Cache " + local.size() + " · USDA " + added;
            } catch (OfflineDataException ex) {
                offline = true;
                if (local.isEmpty()) {
                    message = "Offline and not cached — connect once to fetch \"" + q + "\"";
                } else {
                    message = "Offline — showing cached matches only";
                }
            } catch (NutritionApiException ex) {
                message = "USDA error: " + ex.getMessage()
                        + (local.isEmpty() ? "" : " (showing cache)");
            }
            outcome = new SearchOutcome(q, rows, message, offline);
        }

        if (onDone != null && epoch == searchEpoch.get()) {
            onDone.accept(outcome);
        }
        return outcome;
    }

    /**
     * Fetches USDA detail (if needed) and upserts into the local SQLite cache.
     */
    public Ingredient cacheFromHit(SearchRow row) {
        if (row == null) {
            throw new ValidationException("Select an ingredient first");
        }
        if (row.ingredient() != null && row.cachedId() != null) {
            if (row.fdcId() != null) {
                return refreshFromUsda(row.fdcId());
            }
            return row.ingredient();
        }
        if (row.fdcId() == null) {
            throw new ValidationException("Cannot cache an item without an FDC id");
        }
        Optional<Ingredient> existing = ingredientDao.findByFdcId(row.fdcId());
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            Ingredient fetched = usdaClient.fetchFood(row.fdcId());
            return ingredientDao.upsert(fetched);
        } catch (OfflineDataException ex) {
            throw new OfflineDataException(
                    "Cannot fetch macros while offline, and this food is not cached yet", ex);
        }
    }

    /** Force re-fetch from USDA and overwrite the cache row. */
    private Ingredient refreshFromUsda(int fdcId) {
        try {
            Ingredient fetched = usdaClient.fetchFood(fdcId);
            return ingredientDao.upsert(fetched);
        } catch (OfflineDataException ex) {
            throw new OfflineDataException("Cannot refresh macros while offline", ex);
        }
    }

    public void runAsync(Runnable work) {
        workers.submit(work);
    }

    public void shutdown() {
        synchronized (searchLock) {
            if (pendingDebounce != null) {
                pendingDebounce.cancel(false);
            }
            if (pendingSearch != null) {
                pendingSearch.cancel(true);
            }
        }
        debounce.shutdownNow();
        workers.shutdownNow();
    }
}
