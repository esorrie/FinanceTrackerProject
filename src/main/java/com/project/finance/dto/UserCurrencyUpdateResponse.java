package com.project.finance.dto;

public record UserCurrencyUpdateResponse(
        Integer userId,
        String username,
        String previousCurrency,
        String currency
) {
}
