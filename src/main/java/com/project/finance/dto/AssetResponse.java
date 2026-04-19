package com.project.finance.dto;

public class AssetResponse {

    private Integer assetId;
    private String assetSymbol;
    private String assetName;
    private String currencyCode;
    private java.math.BigDecimal price;
    private java.math.BigDecimal openingPrice;
    private java.math.BigDecimal closingPrice;
    private String stockExchange;

    public AssetResponse() {
    }

    public AssetResponse(Integer assetId, String assetSymbol, String assetName, String currencyCode,
                         java.math.BigDecimal price, java.math.BigDecimal openingPrice,
                         java.math.BigDecimal closingPrice, String stockExchange) {
        this.assetId = assetId;
        this.assetSymbol = assetSymbol;
        this.assetName = assetName;
        this.currencyCode = currencyCode;
        this.price = price;
        this.openingPrice = openingPrice;
        this.closingPrice = closingPrice;
        this.stockExchange = stockExchange;
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

    public java.math.BigDecimal getPrice() {
        return price;
    }

    public java.math.BigDecimal getOpeningPrice() {
        return openingPrice;
    }

    public java.math.BigDecimal getClosingPrice() {
        return closingPrice;
    }

    public String getStockExchange() {
        return stockExchange;
    }
}
