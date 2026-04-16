package com.project.finance.dto;

import java.util.List;

public record MarketDataImportResponse(
        int requestedSymbols,
        int quotesReturned,
        int savedQuotes,
        int newAssets,
        int newCurrencies,
        List<String> symbolsSaved
) {
}
