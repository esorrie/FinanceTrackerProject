package com.project.finance.dto;

import java.math.BigDecimal;

public record HoldingCreateResponse(
        Integer holdingId,
        Integer userId,
        Integer portfolioId,
        String username,
        String portfolioName,
        String symbol,
        String assetName,
        String currency,
        BigDecimal units,
        BigDecimal avgPurchasePrice,
        BigDecimal lastPrice,
        BigDecimal portfolioTotalValue,
        BigDecimal investedAmount,
        BigDecimal marketValue,
        BigDecimal unrealizedPnl
) {
}
