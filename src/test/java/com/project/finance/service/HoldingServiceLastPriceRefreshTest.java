package com.project.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.project.finance.client.YahooFinanceClient;
import com.project.finance.entity.Asset;
import com.project.finance.entity.Currency;
import com.project.finance.entity.Holding;
import com.project.finance.entity.Portfolio;
import com.project.finance.entity.UserAccount;
import com.project.finance.repository.AssetRepository;
import com.project.finance.repository.CurrencyRepository;
import com.project.finance.repository.ExchangeRateRepository;
import com.project.finance.repository.HoldingRepository;
import com.project.finance.repository.PortfolioRepository;
import com.project.finance.repository.UserAccountRepository;
import com.project.finance.service.HoldingService.HoldingLastPriceRefreshSummary;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HoldingServiceLastPriceRefreshTest {

    @Mock
    private YahooFinanceClient yahooFinanceClient;
    @Mock
    private CurrencyRepository currencyRepository;
    @Mock
    private AssetRepository assetRepository;
    @Mock
    private ExchangeRateRepository exchangeRateRepository;
    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private PortfolioRepository portfolioRepository;
    @Mock
    private HoldingRepository holdingRepository;

    private HoldingService holdingService;

    @BeforeEach
    void setUp() {
        holdingService = new HoldingService(
                yahooFinanceClient,
                currencyRepository,
                assetRepository,
                exchangeRateRepository,
                userAccountRepository,
                portfolioRepository,
                holdingRepository
        );
    }

    @Test
    void refreshAllHoldingLastPricesUpdatesOnlyChangedHoldings() {
        Currency usd = currency("USD");
        Holding aaplHolding = holding(1, "AAPL", usd, "1.0000", "100.0000");
        Holding msftHolding = holding(2, "MSFT", usd, "2.0000", "50.0000");
        Holding unknownHolding = holdingWithoutAsset(3, "3.0000", "70.0000");

        when(holdingRepository.findAll()).thenReturn(List.of(aaplHolding, msftHolding, unknownHolding));
        when(yahooFinanceClient.fetchQuotes(List.of("AAPL", "MSFT"))).thenReturn(List.of(
                new YahooFinanceClient.YahooQuote(
                        "AAPL",
                        "Apple",
                        "Apple",
                        "USD",
                        new BigDecimal("110.0000"),
                        null,
                        new BigDecimal("109.0000"),
                        "NASDAQ"
                ),
                new YahooFinanceClient.YahooQuote(
                        "MSFT",
                        "Microsoft",
                        "Microsoft",
                        "USD",
                        new BigDecimal("50.0000"),
                        null,
                        new BigDecimal("49.0000"),
                        "NASDAQ"
                )
        ));
        when(holdingRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        HoldingLastPriceRefreshSummary summary = holdingService.refreshAllHoldingLastPrices();

        assertEquals(3, summary.totalHoldings());
        assertEquals(2, summary.symbolsRequested());
        assertEquals(2, summary.quotesReturned());
        assertEquals(1, summary.holdingsUpdated());
        assertEquals(1, summary.holdingsMissingQuote());
        assertEquals(new BigDecimal("110.0000"), aaplHolding.getLastPrice());
        assertEquals(new BigDecimal("50.0000"), msftHolding.getLastPrice());

        ArgumentCaptor<List<Holding>> changedHoldingsCaptor = ArgumentCaptor.forClass(List.class);
        verify(holdingRepository).saveAll(changedHoldingsCaptor.capture());
        List<Holding> changedHoldings = changedHoldingsCaptor.getValue();
        assertEquals(1, changedHoldings.size());
        assertSame(aaplHolding, changedHoldings.get(0));
    }

    @Test
    void refreshAllHoldingLastPricesFallsBackToPreviousClose() {
        Currency usd = currency("USD");
        Holding tslaHolding = holding(4, "TSLA", usd, "1.0000", "200.0000");

        when(holdingRepository.findAll()).thenReturn(List.of(tslaHolding));
        when(yahooFinanceClient.fetchQuotes(List.of("TSLA"))).thenReturn(List.of(
                new YahooFinanceClient.YahooQuote(
                        "TSLA",
                        "Tesla",
                        "Tesla",
                        "USD",
                        null,
                        null,
                        new BigDecimal("210.1250"),
                        "NASDAQ"
                )
        ));
        when(holdingRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        HoldingLastPriceRefreshSummary summary = holdingService.refreshAllHoldingLastPrices();

        assertEquals(1, summary.holdingsUpdated());
        assertEquals(0, summary.holdingsMissingQuote());
        assertEquals(new BigDecimal("210.1250"), tslaHolding.getLastPrice());
    }

    @Test
    void refreshAllHoldingLastPricesReturnsEmptySummaryWhenNoHoldings() {
        when(holdingRepository.findAll()).thenReturn(List.of());

        HoldingLastPriceRefreshSummary summary = holdingService.refreshAllHoldingLastPrices();

        assertEquals(0, summary.totalHoldings());
        assertEquals(0, summary.symbolsRequested());
        assertEquals(0, summary.quotesReturned());
        assertEquals(0, summary.holdingsUpdated());
        assertEquals(0, summary.holdingsMissingQuote());
        verify(yahooFinanceClient, never()).fetchQuotes(any());
        verify(holdingRepository, never()).saveAll(anyList());
    }

    private Currency currency(String code) {
        Currency currency = new Currency();
        currency.setCurrencyCode(code);
        currency.setSymbol(code);
        currency.setBaseUnit(Boolean.TRUE);
        return currency;
    }

    private Holding holding(int id, String symbol, Currency currency, String units, String lastPrice) {
        Asset asset = new Asset();
        asset.setAssetSymbol(symbol);
        asset.setAssetName(symbol);
        asset.setCurrency(currency);

        Portfolio portfolio = new Portfolio();
        portfolio.setPortfolioId(1);
        portfolio.setPortfolioName("Main");

        UserAccount user = new UserAccount();
        user.setUserId(1);
        user.setUsername("demo");
        user.setCurrency(currency);

        Holding holding = new Holding();
        holding.setHoldingId(id);
        holding.setUser(user);
        holding.setPortfolio(portfolio);
        holding.setAsset(asset);
        holding.setUnits(new BigDecimal(units));
        holding.setAvgPurchasePrice(new BigDecimal("10.0000"));
        holding.setLastPrice(new BigDecimal(lastPrice));
        holding.setPortfolioTotalValue(new BigDecimal("0.0000"));
        holding.setPurchaseDate(LocalDateTime.now().minusDays(1));
        return holding;
    }

    private Holding holdingWithoutAsset(int id, String units, String lastPrice) {
        Currency usd = currency("USD");
        Portfolio portfolio = new Portfolio();
        portfolio.setPortfolioId(1);
        portfolio.setPortfolioName("Main");

        UserAccount user = new UserAccount();
        user.setUserId(1);
        user.setUsername("demo");
        user.setCurrency(usd);

        Holding holding = new Holding();
        holding.setHoldingId(id);
        holding.setUser(user);
        holding.setPortfolio(portfolio);
        holding.setAsset(null);
        holding.setUnits(new BigDecimal(units));
        holding.setAvgPurchasePrice(new BigDecimal("10.0000"));
        holding.setLastPrice(new BigDecimal(lastPrice));
        holding.setPortfolioTotalValue(new BigDecimal("0.0000"));
        holding.setPurchaseDate(LocalDateTime.now().minusDays(1));
        return holding;
    }
}
