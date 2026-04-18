package com.project.finance.dto;

import java.util.List;

public record PortfolioHistoryResponse(
        Integer userId,
        String username,
        String targetCurrency,
        String interval,
        int holdingsCount,
        List<PortfolioHistoryPointResponse> points
) {
}
