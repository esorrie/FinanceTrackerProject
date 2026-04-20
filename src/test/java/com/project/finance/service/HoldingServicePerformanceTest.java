package com.project.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.project.finance.client.YahooFinanceClient;
import com.project.finance.dto.HoldingHistoryResponse;
import com.project.finance.dto.HoldingPerformanceViewResponse;
import com.project.finance.dto.PortfolioHistoryResponse;
import com.project.finance.dto.PortfolioPerformanceResponse;
import com.project.finance.entity.Asset;
import com.project.finance.entity.Currency;
import com.project.finance.entity.ExchangeRate;
import com.project.finance.entity.Holding;
import com.project.finance.entity.Portfolio;
import com.project.finance.entity.UserAccount;
import com.project.finance.repository.AssetRepository;
import com.project.finance.repository.CurrencyRepository;
import com.project.finance.repository.ExchangeRateRepository;
import com.project.finance.repository.HoldingRepository;
import com.project.finance.repository.PortfolioRepository;
import com.project.finance.repository.UserAccountRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HoldingServicePerformanceTest {

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
    void getPortfolioPerformanceCalculatesUpAndDownMovers() {
        Currency usd = currency(1, "USD");
        UserAccount user = user(7, "demo_user", usd);

        Holding aaplHolding = holding(
                101,
                user,
                portfolio(201, "Main"),
                asset("AAPL", "Apple", usd),
                "2.0000",
                "95.0000",
                "100.0000"
        );
        Holding msftHolding = holding(
                102,
                user,
                portfolio(201, "Main"),
                asset("MSFT", "Microsoft", usd),
                "4.0000",
                "52.0000",
                "50.0000"
        );

        when(userAccountRepository.findByUsernameIgnoreCase("demo_user")).thenReturn(Optional.of(user));
        when(holdingRepository.findByUserUserIdOrderByHoldingIdAsc(7)).thenReturn(List.of(aaplHolding, msftHolding));
        when(yahooFinanceClient.fetchQuotes(any())).thenReturn(List.of(
                new YahooFinanceClient.YahooQuote(
                        "AAPL",
                        "Apple",
                        "Apple",
                        "USD",
                        new BigDecimal("110.0000"),
                        new BigDecimal("108.0000"),
                        new BigDecimal("100.0000"),
                        "NASDAQ"
                ),
                new YahooFinanceClient.YahooQuote(
                        "MSFT",
                        "Microsoft",
                        "Microsoft",
                        "USD",
                        new BigDecimal("45.0000"),
                        new BigDecimal("47.0000"),
                        new BigDecimal("50.0000"),
                        "NASDAQ"
                )
        ));

        PortfolioPerformanceResponse response = holdingService.getPortfolioPerformance("demo_user", null);

        assertEquals("USD", response.targetCurrency());
        assertEquals(2, response.holdingsCount());
        assertEquals(new BigDecimal("400.0000"), response.currentValueTarget());
        assertEquals(new BigDecimal("400.0000"), response.previousCloseValueTarget());
        assertEquals(new BigDecimal("0.0000"), response.valueChangeTarget());
        assertEquals(new BigDecimal("0.0000"), response.valueChangePercent());
        assertEquals("FLAT", response.trend());

        HoldingPerformanceViewResponse aapl = response.holdings().stream()
                .filter(item -> "AAPL".equals(item.symbol()))
                .findFirst()
                .orElseThrow();
        assertEquals(new BigDecimal("10.0000"), aapl.priceChangeSource());
        assertEquals(new BigDecimal("10.0000"), aapl.priceChangePercent());
        assertEquals("UP", aapl.trend());

        HoldingPerformanceViewResponse msft = response.holdings().stream()
                .filter(item -> "MSFT".equals(item.symbol()))
                .findFirst()
                .orElseThrow();
        assertEquals(new BigDecimal("-5.0000"), msft.priceChangeSource());
        assertEquals(new BigDecimal("-10.0000"), msft.priceChangePercent());
        assertEquals("DOWN", msft.trend());
    }

    @Test
    void getPortfolioPerformanceConvertsFromNonUsdHoldingCurrency() {
        Currency usd = currency(1, "USD");
        Currency eur = currency(2, "EUR");
        UserAccount user = user(9, "fx_user", usd);

        Holding sapHolding = holding(
                301,
                user,
                portfolio(401, "FX"),
                asset("SAP.DE", "SAP", eur),
                "3.0000",
                "90.0000",
                "100.0000"
        );

        ExchangeRate eurUsd = new ExchangeRate();
        eurUsd.setRate(new BigDecimal("1.2000"));

        when(userAccountRepository.findByUsernameIgnoreCase("fx_user")).thenReturn(Optional.of(user));
        when(holdingRepository.findByUserUserIdOrderByHoldingIdAsc(9)).thenReturn(List.of(sapHolding));
        when(yahooFinanceClient.fetchQuotes(any())).thenReturn(List.of(
                new YahooFinanceClient.YahooQuote(
                        "SAP.DE",
                        "SAP",
                        "SAP",
                        "EUR",
                        new BigDecimal("120.0000"),
                        new BigDecimal("118.0000"),
                        new BigDecimal("100.0000"),
                        "XETRA"
                )
        ));
        when(exchangeRateRepository
                .findTopByStartCurrencyCurrencyCodeIgnoreCaseAndEndCurrencyCurrencyCodeIgnoreCaseOrderByLastUpdatedDesc(
                        "EUR",
                        "USD"
                ))
                .thenReturn(Optional.of(eurUsd));

        PortfolioPerformanceResponse response = holdingService.getPortfolioPerformance("fx_user", null);

        assertEquals("USD", response.targetCurrency());
        assertEquals(new BigDecimal("432.0000"), response.currentValueTarget());
        assertEquals(new BigDecimal("360.0000"), response.previousCloseValueTarget());
        assertEquals(new BigDecimal("72.0000"), response.valueChangeTarget());
        assertEquals(new BigDecimal("20.0000"), response.valueChangePercent());
        assertEquals("UP", response.trend());
        assertEquals(new BigDecimal("1.20000000"), response.holdings().get(0).exchangeRate());
    }

    @Test
    void getHoldingHistoryConvertsBetweenTwoNonUsdCurrencies() {
        Currency usd = currency(1, "USD");
        Currency eur = currency(2, "EUR");
        Currency gbp = currency(3, "GBP");
        UserAccount user = user(12, "history_user", usd);

        Holding sapHolding = holding(
                501,
                user,
                portfolio(601, "FX"),
                asset("SAP.DE", "SAP", eur),
                "2.0000",
                "90.0000",
                "100.0000"
        );
        sapHolding.setPurchaseDate(LocalDateTime.now().minusDays(40));

        ExchangeRate eurUsd = new ExchangeRate();
        eurUsd.setRate(new BigDecimal("1.2000"));
        ExchangeRate gbpUsd = new ExchangeRate();
        gbpUsd.setRate(new BigDecimal("1.5000"));

        when(userAccountRepository.findByUsernameIgnoreCase("history_user")).thenReturn(Optional.of(user));
        when(currencyRepository.findByCurrencyCodeIgnoreCase("GBP")).thenReturn(Optional.of(gbp));
        when(holdingRepository.findByUserUserIdOrderByHoldingIdAsc(12)).thenReturn(List.of(sapHolding));
        when(exchangeRateRepository
                .findTopByStartCurrencyCurrencyCodeIgnoreCaseAndEndCurrencyCurrencyCodeIgnoreCaseOrderByLastUpdatedDesc(
                        "EUR",
                        "USD"
                ))
                .thenReturn(Optional.of(eurUsd));
        when(exchangeRateRepository
                .findTopByStartCurrencyCurrencyCodeIgnoreCaseAndEndCurrencyCurrencyCodeIgnoreCaseOrderByLastUpdatedDesc(
                        "GBP",
                        "USD"
                ))
                .thenReturn(Optional.of(gbpUsd));

        when(yahooFinanceClient.fetchHistoricalClosePrices(eq("SAP.DE"), any(LocalDate.class), any(LocalDate.class), anyString()))
                .thenReturn(List.of(
                        new YahooFinanceClient.HistoricalPricePoint(LocalDate.now().minusDays(7), new BigDecimal("100.0000")),
                        new YahooFinanceClient.HistoricalPricePoint(LocalDate.now().minusDays(1), new BigDecimal("120.0000"))
                ));

        HoldingHistoryResponse response = holdingService.getHoldingHistory("history_user", "sap.de", "GBP", null);

        assertEquals("GBP", response.targetCurrency());
        assertEquals("EUR", response.sourceCurrency());
        assertEquals("1wk", response.interval());
        assertEquals(new BigDecimal("0.80000000"), response.exchangeRate());
        assertFalse(response.points().isEmpty());
        assertEquals(new BigDecimal("80.0000"), response.points().get(0).closePriceTarget());
        assertEquals(new BigDecimal("160.0000"), response.points().get(0).holdingValueTarget());
    }

    @Test
    void getPortfolioHistoryCarriesForwardSeriesForAggregation() {
        Currency usd = currency(1, "USD");
        Currency eur = currency(2, "EUR");
        UserAccount user = user(20, "portfolio_history_user", usd);

        Holding aaplHolding = holding(
                701,
                user,
                portfolio(801, "Main"),
                asset("AAPL", "Apple", usd),
                "2.0000",
                "90.0000",
                "100.0000"
        );
        aaplHolding.setPurchaseDate(LocalDateTime.now().minusDays(2));

        Holding msftHolding = holding(
                702,
                user,
                portfolio(801, "Main"),
                asset("MSFT", "Microsoft", usd),
                "1.0000",
                "40.0000",
                "50.0000"
        );
        msftHolding.setPurchaseDate(LocalDateTime.now().minusDays(2));

        ExchangeRate eurUsd = new ExchangeRate();
        eurUsd.setRate(new BigDecimal("1.2500"));

        LocalDate day1 = LocalDate.now().minusDays(2);
        LocalDate day2 = LocalDate.now().minusDays(1);

        when(userAccountRepository.findByUsernameIgnoreCase("portfolio_history_user")).thenReturn(Optional.of(user));
        when(currencyRepository.findByCurrencyCodeIgnoreCase("EUR")).thenReturn(Optional.of(eur));
        when(holdingRepository.findByUserUserIdOrderByHoldingIdAsc(20)).thenReturn(List.of(aaplHolding, msftHolding));
        when(exchangeRateRepository
                .findTopByStartCurrencyCurrencyCodeIgnoreCaseAndEndCurrencyCurrencyCodeIgnoreCaseOrderByLastUpdatedDesc(
                        "EUR",
                        "USD"
                ))
                .thenReturn(Optional.of(eurUsd));

        when(yahooFinanceClient.fetchHistoricalClosePrices(eq("AAPL"), any(LocalDate.class), any(LocalDate.class), anyString()))
                .thenReturn(List.of(
                        new YahooFinanceClient.HistoricalPricePoint(day1, new BigDecimal("100.0000")),
                        new YahooFinanceClient.HistoricalPricePoint(day2, new BigDecimal("110.0000"))
                ));
        when(yahooFinanceClient.fetchHistoricalClosePrices(eq("MSFT"), any(LocalDate.class), any(LocalDate.class), anyString()))
                .thenReturn(List.of(
                        new YahooFinanceClient.HistoricalPricePoint(day1, new BigDecimal("50.0000"))
                ));

        PortfolioHistoryResponse response = holdingService.getPortfolioHistory("portfolio_history_user", "EUR", null);

        assertEquals("EUR", response.targetCurrency());
        assertEquals("1d", response.interval());
        assertFalse(response.points().isEmpty());
        assertEquals(new BigDecimal("200.0000"), response.points().get(0).portfolioValueTarget());
        assertEquals(new BigDecimal("216.0000"), response.points().get(response.points().size() - 1).portfolioValueTarget());
    }

    private Currency currency(int id, String code) {
        Currency currency = new Currency();
        currency.setCurrencyId(id);
        currency.setCurrencyCode(code);
        currency.setSymbol(code);
        currency.setBaseUnit(Boolean.TRUE);
        return currency;
    }

    private UserAccount user(int id, String username, Currency currency) {
        UserAccount user = new UserAccount();
        user.setUserId(id);
        user.setUsername(username);
        user.setCurrency(currency);
        return user;
    }

    private Portfolio portfolio(int id, String name) {
        Portfolio portfolio = new Portfolio();
        portfolio.setPortfolioId(id);
        portfolio.setPortfolioName(name);
        return portfolio;
    }

    private Asset asset(String symbol, String name, Currency currency) {
        Asset asset = new Asset();
        asset.setAssetSymbol(symbol);
        asset.setAssetName(name);
        asset.setCurrency(currency);
        return asset;
    }

    private Holding holding(
            int holdingId,
            UserAccount user,
            Portfolio portfolio,
            Asset asset,
            String units,
            String avgPurchasePrice,
            String lastPrice
    ) {
        Holding holding = new Holding();
        holding.setHoldingId(holdingId);
        holding.setUser(user);
        holding.setPortfolio(portfolio);
        holding.setAsset(asset);
        holding.setUnits(new BigDecimal(units));
        holding.setAvgPurchasePrice(new BigDecimal(avgPurchasePrice));
        holding.setLastPrice(new BigDecimal(lastPrice));
        holding.setPurchaseDate(LocalDateTime.now().minusDays(5));
        return holding;
    }
}
