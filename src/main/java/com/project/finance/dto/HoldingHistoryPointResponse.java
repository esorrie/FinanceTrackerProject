package com.project.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record HoldingHistoryPointResponse(
        LocalDate date,
        BigDecimal closePriceSource,
        BigDecimal closePriceTarget,
        BigDecimal holdingValueTarget
) {
}
