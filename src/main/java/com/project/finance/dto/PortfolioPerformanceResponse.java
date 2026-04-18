package com.project.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PortfolioPerformanceResponse(
        Integer userId,
        String username,
        String targetCurrency,
        LocalDateTime checkedAt,
        int holdingsCount,
        BigDecimal currentValueTarget,
        BigDecimal previousCloseValueTarget,
        BigDecimal valueChangeTarget,
        BigDecimal valueChangePercent,
        String trend,
        List<HoldingPerformanceViewResponse> holdings
) {
}
