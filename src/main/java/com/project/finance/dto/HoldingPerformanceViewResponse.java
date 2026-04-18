package com.project.finance.dto;

import java.math.BigDecimal;

public record HoldingPerformanceViewResponse(
        Integer holdingId,
        Integer portfolioId,
        String portfolioName,
        String symbol,
        String assetName,
        BigDecimal units,
        String sourceCurrency,
        String targetCurrency,
        BigDecimal exchangeRate,
        BigDecimal currentPriceSource,
        BigDecimal previousCloseSource,
        BigDecimal priceChangeSource,
        BigDecimal priceChangePercent,
        BigDecimal currentValueTarget,
        BigDecimal previousCloseValueTarget,
        BigDecimal valueChangeTarget,
        String trend
) {
}
