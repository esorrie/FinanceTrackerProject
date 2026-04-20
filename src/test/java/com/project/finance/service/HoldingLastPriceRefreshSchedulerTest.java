package com.project.finance.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.project.finance.service.HoldingService.HoldingLastPriceRefreshSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HoldingLastPriceRefreshSchedulerTest {

    @Mock
    private HoldingService holdingService;

    private HoldingLastPriceRefreshScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new HoldingLastPriceRefreshScheduler(holdingService);
    }

    @Test
    void refreshHoldingLastPricesRefreshesAllHoldings() {
        when(holdingService.refreshAllHoldingLastPrices())
                .thenReturn(new HoldingLastPriceRefreshSummary(10, 2, 2, 7, 1));

        scheduler.refreshHoldingLastPrices();

        verify(holdingService).refreshAllHoldingLastPrices();
    }

    @Test
    void refreshHoldingLastPricesHandlesFailures() {
        when(holdingService.refreshAllHoldingLastPrices())
                .thenThrow(new IllegalStateException("Yahoo unavailable"));

        scheduler.refreshHoldingLastPrices();

        verify(holdingService).refreshAllHoldingLastPrices();
    }
}
