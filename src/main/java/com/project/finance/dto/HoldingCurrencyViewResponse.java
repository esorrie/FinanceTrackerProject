package com.project.finance.dto;

import java.math.BigDecimal;

public record HoldingCurrencyViewResponse(
        Integer holdingId,
        Integer portfolioId,
        String portfolioName,
        String symbol,
        String assetName,
        BigDecimal units,
        String sourceCurrency,
        String targetCurrency,
        BigDecimal exchangeRate,
        BigDecimal avgPurchasePriceSource,
        BigDecimal avgPurchasePriceTarget,
        BigDecimal lastPriceSource,
        BigDecimal lastPriceTarget,
        BigDecimal portfolioTotalValueTarget,
        BigDecimal investedAmountTarget,
        BigDecimal marketValueTarget,
        BigDecimal unrealizedPnlTarget
) {
}
