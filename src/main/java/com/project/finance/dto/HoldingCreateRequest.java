package com.project.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record HoldingCreateRequest(
        String username,
        String symbol,
        BigDecimal units,
        BigDecimal avgPurchasePrice,
        String portfolioName,
        LocalDate purchaseDate
) {
}
