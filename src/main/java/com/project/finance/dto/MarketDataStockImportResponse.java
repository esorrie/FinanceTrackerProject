package com.project.finance.dto;

import java.util.List;

public record MarketDataStockImportResponse(
        String screenerId,
        int requestedPageSize,
        int requestedMaxPages,
        int pagesProcessed,
        Integer totalAvailableFromScreener,
        int quotesReturned,
        int assetsImported,
        int newAssets,
        int updatedAssets,
        int newCurrencies,
        int savedHistoryRows,
        List<String> symbolsSample
) {
}
