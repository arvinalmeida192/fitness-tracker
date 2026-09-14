package com.fittrack.api.nutrition;

/**
 * Lightweight search hit before macros are fully resolved/cached.
 */
public final class UsdaSearchHit {

    private final int fdcId;
    private final String description;
    private final String brandOwner;
    private final String dataType;

    public UsdaSearchHit(int fdcId, String description, String brandOwner, String dataType) {
        this.fdcId = fdcId;
        this.description = description;
        this.brandOwner = brandOwner;
        this.dataType = dataType;
    }

    public int getFdcId() {
        return fdcId;
    }

    public String getDescription() {
        return description;
    }

    public String getBrandOwner() {
        return brandOwner;
    }

    public String getDataType() {
        return dataType;
    }
}
