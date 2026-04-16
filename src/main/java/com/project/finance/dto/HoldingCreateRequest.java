package com.project.finance.dto;

import java.math.BigDecimal;

public record HoldingCreateRequest(
        String username,
        String symbol,
        BigDecimal units,
        BigDecimal avgPurchasePrice,
        String portfolioName
) {
}
