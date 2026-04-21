package com.project.finance.dto;

import java.util.List;

public record UserPortfoliosResponse(
        Integer userId,
        String username,
        int portfolioCount,
        List<PortfolioOptionResponse> portfolios
) {
}
