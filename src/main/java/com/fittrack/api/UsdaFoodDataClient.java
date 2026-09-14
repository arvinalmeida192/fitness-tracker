package com.fittrack.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fittrack.domain.Macros;
import com.fittrack.domain.NutritionApiException;
import com.fittrack.domain.OfflineDataException;
import com.fittrack.domain.Ingredient;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * USDA FoodData Central HTTP client (search + food detail).
 */
public final class UsdaFoodDataClient {

    private static final int ENERGY_KCAL = 1008;
    private static final int PROTEIN = 1003;
    private static final int FAT = 1004;
    private static final int CARBS = 1005;
    private static final int FIBER = 1079;

    private final Supplier<String> apiKeySupplier;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public UsdaFoodDataClient(Supplier<String> apiKeySupplier, String baseUrl) {
        this.apiKeySupplier = apiKeySupplier;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public List<UsdaSearchHit> search(String query, int pageSize) {
        String q = query == null ? "" : query.trim();
        if (q.isBlank()) {
            return List.of();
        }
        String key = requireKey();
        String encoded = URLEncoder.encode(q, StandardCharsets.UTF_8);
        String url = baseUrl + "/foods/search?api_key=" + encode(key)
                + "&query=" + encoded
                + "&pageSize=" + Math.min(Math.max(pageSize, 1), 50)
                // SR Legacy has consistent per-100g macros; Foundation rows are often incomplete for MVP use.
                + "&dataType=SR%20Legacy";
        JsonNode root = getJson(url);
        JsonNode foods = root.path("foods");
        List<UsdaSearchHit> hits = new ArrayList<>();
        if (!foods.isArray()) {
            return hits;
        }
        for (JsonNode food : foods) {
            int fdcId = food.path("fdcId").asInt(0);
            if (fdcId <= 0) {
                continue;
            }
            String description = text(food, "description");
            if (description == null || description.isBlank()) {
                description = text(food, "lowercaseDescription");
            }
            String brand = text(food, "brandOwner");
            if (brand == null) {
                brand = text(food, "brandName");
            }
            hits.add(new UsdaSearchHit(fdcId, description, brand, text(food, "dataType")));
        }
        return hits;
    }

    public Ingredient fetchFood(int fdcId) {
        String key = requireKey();
        String url = baseUrl + "/food/" + fdcId + "?api_key=" + encode(key);
        JsonNode root = getJson(url);
        return mapFood(root);
    }

    private Ingredient mapFood(JsonNode root) {
        int fdcId = root.path("fdcId").asInt(0);
        if (fdcId <= 0) {
            throw new NutritionApiException("USDA response missing fdcId");
        }
        String name = text(root, "description");
        if (name == null || name.isBlank()) {
            throw new NutritionApiException("USDA food missing description");
        }
        String brand = text(root, "brandOwner");
        if (brand == null) {
            brand = text(root, "brandName");
        }

        NutrientBag bag = readNutrients(root.path("foodNutrients"));
        double scale = 1.0;
        String servingUnit = text(root, "servingSizeUnit");
        double servingSize = root.path("servingSize").asDouble(0);
        // Branded foods often report nutrients per labeled serving; normalize to per 100 g when possible.
        if (servingSize > 0 && servingUnit != null && servingUnit.equalsIgnoreCase("g")
                && "Branded".equalsIgnoreCase(text(root, "dataType"))) {
            scale = 100.0 / servingSize;
        }

        Macros macros = new Macros(
                bag.kcal * scale,
                bag.protein * scale,
                bag.carbs * scale,
                bag.fat * scale,
                bag.fiber * scale
        );
        if (macros.getKcal() <= 0 && macros.getProteinG() <= 0 && macros.getCarbsG() <= 0 && macros.getFatG() <= 0) {
            throw new NutritionApiException(
                    "USDA food \"" + name + "\" has no usable per-100g macros — try another result");
        }

        Ingredient ingredient = new Ingredient();
        ingredient.setFdcId(fdcId);
        ingredient.setName(name);
        ingredient.setBrand(brand);
        ingredient.setPer100g(macros);
        ingredient.setSource("USDA");
        ingredient.setFetchedAt(Instant.now());
        return ingredient;
    }

    private NutrientBag readNutrients(JsonNode nutrients) {
        NutrientBag bag = new NutrientBag();
        if (!nutrients.isArray()) {
            return bag;
        }
        Double energyKcal = null;
        Double energyAtwater = null;
        for (JsonNode n : nutrients) {
            int id = n.path("nutrient").path("id").asInt(0);
            if (id == 0) {
                id = n.path("nutrientId").asInt(0);
            }
            String number = n.path("nutrient").path("number").asText("");
            if (number.isBlank()) {
                number = n.path("nutrientNumber").asText("");
            }
            String unit = n.path("nutrient").path("unitName").asText(null);
            if (unit == null || unit.isBlank()) {
                unit = n.path("unitName").asText("");
            }
            double amount = n.path("amount").asDouble(Double.NaN);
            if (Double.isNaN(amount)) {
                amount = n.path("value").asDouble(Double.NaN);
            }
            if (Double.isNaN(amount)) {
                continue;
            }
            String unitLower = unit == null ? "" : unit.toLowerCase(Locale.ROOT);
            if (id == ENERGY_KCAL || "208".equals(number)) {
                if (unitLower.contains("kj")) {
                    continue;
                }
                energyKcal = amount;
            } else if (id == 2047 || id == 2048) {
                // Foundation foods sometimes only expose Atwater energy
                if (!unitLower.contains("kj")) {
                    energyAtwater = amount;
                }
            } else if (id == PROTEIN || "203".equals(number)) {
                bag.protein = amount;
            } else if (id == FAT || "204".equals(number)) {
                bag.fat = amount;
            } else if (id == CARBS || "205".equals(number)) {
                bag.carbs = amount;
            } else if (id == FIBER || "291".equals(number)) {
                bag.fiber = amount;
            }
        }
        if (energyKcal != null) {
            bag.kcal = energyKcal;
        } else if (energyAtwater != null) {
            bag.kcal = energyAtwater;
        }
        return bag;
    }

    private JsonNode getJson(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            if (code == 401 || code == 403) {
                throw new NutritionApiException("USDA API key rejected (HTTP " + code + ")");
            }
            if (code == 429) {
                throw new NutritionApiException("USDA rate limit exceeded — try again shortly");
            }
            if (code >= 400) {
                throw new NutritionApiException("USDA request failed (HTTP " + code + ")");
            }
            return mapper.readTree(response.body());
        } catch (NutritionApiException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new OfflineDataException("Network unavailable for USDA FoodData Central", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new NutritionApiException("USDA request interrupted", ex);
        }
    }

    private String requireKey() {
        String key = apiKeySupplier.get();
        if (key == null || key.isBlank()) {
            throw new NutritionApiException("Set usda.api.key in config.local.properties or USDA_API_KEY");
        }
        return key.trim();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private static final class NutrientBag {
        private double kcal;
        private double protein;
        private double carbs;
        private double fat;
        private double fiber;
    }
}
