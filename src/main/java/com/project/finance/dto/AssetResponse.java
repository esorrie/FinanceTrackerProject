package com.project.finance.dto;

import java.math.BigDecimal;

public class AssetResponse {

    private Integer assetId;
    private String assetSymbol;
    private String assetName;
    private String currencyCode;
    private BigDecimal openPrice;
    private BigDecimal closePrice;
    private String stockExchange;

    public AssetResponse() {
    }

    public AssetResponse(
            Integer assetId,
            String assetSymbol,
            String assetName,
            String currencyCode,
            BigDecimal openPrice,
            BigDecimal closePrice,
            String stockExchange
    ) {
        this.assetId = assetId;
        this.assetSymbol = assetSymbol;
        this.assetName = assetName;
        this.currencyCode = currencyCode;
        this.openPrice = openPrice;
        this.closePrice = closePrice;
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

    public BigDecimal getOpenPrice() {
        return openPrice;
    }

    public BigDecimal getClosePrice() {
        return closePrice;
    }

    public String getStockExchange() {
        return stockExchange;
    }
}
