package com.project.finance.dto;

import java.math.BigDecimal;

public record HoldingUnitsUpdateRequest(
        String username,
        String symbol,
        BigDecimal units,
        Boolean removeAll
) {
}
