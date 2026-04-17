package com.project.finance.service;

import com.project.finance.dto.MarketDataStockImportResponse;
import com.project.finance.repository.AssetRepository;
import com.project.finance.repository.CurrencyRepository;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class AssetCatalogStartupService {

    private static final Logger logger = LoggerFactory.getLogger(AssetCatalogStartupService.class);
    private static final long MIN_EXISTING_ROWS_TO_SKIP_IMPORT = 10L;

    private static final int PAGE_SIZE = 250;
    private static final int MAX_PAGES = 200;

    private static final List<String> STARTUP_SCREENER_IDS = List.of(
            "aggressive_small_caps",
            "most_actives",
            "day_gainers",
            "day_losers",
            "small_cap_gainers",
            "undervalued_large_caps",
            "growth_technology_stocks"
    );

    private final MarketDataImportService marketDataImportService;
    private final AssetRepository assetRepository;
    private final CurrencyRepository currencyRepository;

    public AssetCatalogStartupService(
            MarketDataImportService marketDataImportService,
            AssetRepository assetRepository,
            CurrencyRepository currencyRepository
    ) {
        this.marketDataImportService = marketDataImportService;
        this.assetRepository = assetRepository;
        this.currencyRepository = currencyRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void importAssetCatalogAtStartup() {
        long assetCount = assetRepository.count();
        long currencyCount = currencyRepository.count();
        boolean updateOnlyMode = assetCount >= MIN_EXISTING_ROWS_TO_SKIP_IMPORT
                && currencyCount >= MIN_EXISTING_ROWS_TO_SKIP_IMPORT;
        boolean allowCreates = !updateOnlyMode;

        logger.info(
                "Startup stock import mode={} (allowCreates={}) with tbl_asset={}, tbl_currency={}, threshold={}.",
                updateOnlyMode ? "update-only" : "full-import",
                allowCreates,
                assetCount,
                currencyCount,
                MIN_EXISTING_ROWS_TO_SKIP_IMPORT
        );

        long startedAt = System.currentTimeMillis();
        int successfulScreeners = 0;
        int totalQuotesReturned = 0;
        int totalAssetsImported = 0;
        int totalNewAssets = 0;
        int totalUpdatedAssets = 0;
        List<String> failedScreeners = new ArrayList<>();

        for (String screenerId : STARTUP_SCREENER_IDS) {
            try {
                MarketDataStockImportResponse response = marketDataImportService.importStocksFromScreener(
                        screenerId,
                        PAGE_SIZE,
                        MAX_PAGES,
                        allowCreates
                );
                successfulScreeners++;
                totalQuotesReturned += response.quotesReturned();
                totalAssetsImported += response.assetsImported();
                totalNewAssets += response.newAssets();
                totalUpdatedAssets += response.updatedAssets();

                logger.info(
                        "Startup stock import completed for {}: quotesReturned={}, assetsImported={}, newAssets={}, updatedAssets={}",
                        screenerId,
                        response.quotesReturned(),
                        response.assetsImported(),
                        response.newAssets(),
                        response.updatedAssets()
                );
            } catch (Exception ex) {
                failedScreeners.add(screenerId);
                logger.warn("Startup stock import failed for {}: {}", screenerId, ex.getMessage());
            }
        }

        logger.info(
                "Startup stock import summary: mode={}, allowCreates={}, successfulScreeners={}/{}, quotesReturned={}, assetsImported={}, newAssets={}, updatedAssets={}, failedScreeners={}, durationMs={}",
                updateOnlyMode ? "update-only" : "full-import",
                allowCreates,
                successfulScreeners,
                STARTUP_SCREENER_IDS.size(),
                totalQuotesReturned,
                totalAssetsImported,
                totalNewAssets,
                totalUpdatedAssets,
                failedScreeners,
                System.currentTimeMillis() - startedAt
        );
    }
}
