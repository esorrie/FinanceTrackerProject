package com.project.finance.dto;

import java.math.BigDecimal;
import java.util.List;

public record HoldingHistoryResponse(
        Integer userId,
        String username,
        String symbol,
        String assetName,
        BigDecimal units,
        String sourceCurrency,
        String targetCurrency,
        BigDecimal exchangeRate,
        String interval,
        List<HoldingHistoryPointResponse> points
) {
}
