package com.fittrack.domain;


import java.time.Instant;

public final class Ingredient extends BaseEntity {

    private Integer fdcId;
    private String name;
    private String brand;
    private Macros per100g = Macros.zero();
    private String source = "USDA";
    private Instant fetchedAt = Instant.now();

    public Ingredient() {
    }

    public Integer getFdcId() {
        return fdcId;
    }

    public void setFdcId(Integer fdcId) {
        this.fdcId = fdcId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public Macros getPer100g() {
        return per100g;
    }

    public void setPer100g(Macros per100g) {
        this.per100g = per100g == null ? Macros.zero() : per100g;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(Instant fetchedAt) {
        this.fetchedAt = fetchedAt;
    }

    @Override
    public String toString() {
        return name == null ? "Ingredient" : name;
    }
}
