package com.project.finance.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.project.finance.dto.MarketDataStockImportResponse;
import com.project.finance.repository.AssetRepository;
import com.project.finance.repository.CurrencyRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssetCatalogStartupServiceTest {

    @Mock
    private MarketDataImportService marketDataImportService;
    @Mock
    private AssetRepository assetRepository;
    @Mock
    private CurrencyRepository currencyRepository;

    private AssetCatalogStartupService assetCatalogStartupService;

    @BeforeEach
    void setUp() {
        assetCatalogStartupService = new AssetCatalogStartupService(
                marketDataImportService,
                assetRepository,
                currencyRepository
        );
    }

    @Test
    void importAssetCatalogAtStartupCallsAllScreenersInConfiguredOrder() {
        when(assetRepository.count()).thenReturn(0L);
        when(currencyRepository.count()).thenReturn(0L);
        when(marketDataImportService.importStocksFromScreener(anyString(), eq(250), eq(200), eq(true)))
                .thenAnswer(invocation -> response((String) invocation.getArgument(0)));

        assetCatalogStartupService.importAssetCatalogAtStartup();

        InOrder inOrder = inOrder(marketDataImportService);
        inOrder.verify(marketDataImportService).importStocksFromScreener("aggressive_small_caps", 250, 200, true);
        inOrder.verify(marketDataImportService).importStocksFromScreener("most_actives", 250, 200, true);
        inOrder.verify(marketDataImportService).importStocksFromScreener("day_gainers", 250, 200, true);
        inOrder.verify(marketDataImportService).importStocksFromScreener("day_losers", 250, 200, true);
        inOrder.verify(marketDataImportService).importStocksFromScreener("small_cap_gainers", 250, 200, true);
        inOrder.verify(marketDataImportService).importStocksFromScreener("undervalued_large_caps", 250, 200, true);
        inOrder.verify(marketDataImportService).importStocksFromScreener("growth_technology_stocks", 250, 200, true);

        verify(marketDataImportService, times(7)).importStocksFromScreener(anyString(), eq(250), eq(200), eq(true));
    }

    @Test
    void importAssetCatalogAtStartupContinuesWhenOneScreenerFails() {
        when(assetRepository.count()).thenReturn(0L);
        when(currencyRepository.count()).thenReturn(0L);
        when(marketDataImportService.importStocksFromScreener(anyString(), eq(250), eq(200), eq(true)))
                .thenAnswer(invocation -> {
                    String screenerId = invocation.getArgument(0);
                    if ("day_gainers".equals(screenerId)) {
                        throw new IllegalStateException("simulated failure");
                    }
                    return response(screenerId);
                });

        assetCatalogStartupService.importAssetCatalogAtStartup();

        verify(marketDataImportService, times(7)).importStocksFromScreener(anyString(), eq(250), eq(200), eq(true));
    }

    @Test
    void importAssetCatalogAtStartupRunsInUpdateOnlyModeWhenAssetsAndCurrenciesAlreadyPopulated() {
        when(assetRepository.count()).thenReturn(10L);
        when(currencyRepository.count()).thenReturn(10L);
        when(marketDataImportService.importStocksFromScreener(anyString(), eq(250), eq(200), eq(false)))
                .thenAnswer(invocation -> response((String) invocation.getArgument(0)));

        assetCatalogStartupService.importAssetCatalogAtStartup();

        verify(marketDataImportService, times(7)).importStocksFromScreener(anyString(), eq(250), eq(200), eq(false));
        verify(marketDataImportService, never()).importStocksFromScreener(anyString(), eq(250), eq(200), eq(true));
    }

    private MarketDataStockImportResponse response(String screenerId) {
        return new MarketDataStockImportResponse(
                screenerId,
                250,
                200,
                1,
                100,
                100,
                100,
                90,
                10,
                0,
                100,
                List.of("AAPL")
        );
    }
}
