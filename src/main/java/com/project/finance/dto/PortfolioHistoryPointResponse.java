package com.project.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PortfolioHistoryPointResponse(
        LocalDate date,
        BigDecimal portfolioValueTarget
) {
}
