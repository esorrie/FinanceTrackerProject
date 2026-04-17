package com.project.finance.dto;

public class AssetResponse {

    private Integer assetId;
    private String assetSymbol;
    private String assetName;
    private String currencyCode;

    public AssetResponse() {
    }

    public AssetResponse(Integer assetId, String assetSymbol, String assetName, String currencyCode) {
        this.assetId = assetId;
        this.assetSymbol = assetSymbol;
        this.assetName = assetName;
        this.currencyCode = currencyCode;
    }

    public Integer getAssetId() {
        return assetId;
    }

    public String getAssetSymbol() {
        return assetSymbol;
    }

    public String getAssetName() {
        return assetName;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }
}
