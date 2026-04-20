package com.project.finance.dto;

import java.math.BigDecimal;

public record HoldingUnitsUpdateResponse(
        Integer userId,
        String username,
        String symbol,
        boolean removedAll,
        BigDecimal removedUnits,
        BigDecimal remainingUnits,
        String message
) {
}
