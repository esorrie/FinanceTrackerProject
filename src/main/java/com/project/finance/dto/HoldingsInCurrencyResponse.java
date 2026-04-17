package com.project.finance.dto;

import java.math.BigDecimal;
import java.util.List;

public record HoldingsInCurrencyResponse(
        Integer userId,
        String username,
        String targetCurrency,
        int holdingsCount,
        BigDecimal totalInvestedTarget,
        BigDecimal totalMarketValueTarget,
        BigDecimal totalUnrealizedPnlTarget,
        List<HoldingCurrencyViewResponse> holdings
) {
}
