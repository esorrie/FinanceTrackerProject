package com.project.finance.service;

import com.project.finance.service.HoldingService.HoldingLastPriceRefreshSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class HoldingLastPriceRefreshScheduler {

    private static final Logger logger = LoggerFactory.getLogger(HoldingLastPriceRefreshScheduler.class);

    private final HoldingService holdingService;

    public HoldingLastPriceRefreshScheduler(HoldingService holdingService) {
        this.holdingService = holdingService;
    }

    @Scheduled(
            fixedDelayString = "${finance.holding-last-price-refresh.interval-ms:60000}",
            initialDelayString = "${finance.holding-last-price-refresh.initial-delay-ms:60000}"
    )
    public void refreshHoldingLastPrices() {
        try {
            HoldingLastPriceRefreshSummary summary = holdingService.refreshAllHoldingLastPrices();
            logger.info(
                    "Scheduled holding last-price refresh completed: totalHoldings={}, symbolsRequested={}, quotesReturned={}, holdingsUpdated={}, holdingsMissingQuote={}",
                    summary.totalHoldings(),
                    summary.symbolsRequested(),
                    summary.quotesReturned(),
                    summary.holdingsUpdated(),
                    summary.holdingsMissingQuote()
            );
        } catch (Exception ex) {
            logger.warn("Scheduled holding last-price refresh failed: {}", ex.getMessage());
        }
    }
}
