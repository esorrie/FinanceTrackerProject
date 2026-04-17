package com.project.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.project.finance.client.YahooFinanceClient;
import com.project.finance.dto.HoldingCurrencyViewResponse;
import com.project.finance.dto.HoldingsInCurrencyResponse;
import com.project.finance.dto.UserCurrencyUpdateResponse;
import com.project.finance.entity.Asset;
import com.project.finance.entity.Currency;
import com.project.finance.entity.ExchangeRate;
import com.project.finance.entity.Holding;
import com.project.finance.entity.Portfolio;
import com.project.finance.entity.UserAccount;
import com.project.finance.repository.AssetHistoryRepository;
import com.project.finance.repository.AssetRepository;
import com.project.finance.repository.CurrencyRepository;
import com.project.finance.repository.ExchangeRateRepository;
import com.project.finance.repository.HoldingRepository;
import com.project.finance.repository.PortfolioRepository;
import com.project.finance.repository.UserAccountRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HoldingServiceCurrencyTest {

    @Mock
    private YahooFinanceClient yahooFinanceClient;
    @Mock
    private CurrencyRepository currencyRepository;
    @Mock
    private AssetRepository assetRepository;
    @Mock
    private AssetHistoryRepository assetHistoryRepository;
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
                assetHistoryRepository,
                exchangeRateRepository,
                userAccountRepository,
                portfolioRepository,
                holdingRepository
        );
    }

    @Test
    void getHoldingsInCurrencyUsesUsdAnchoredRates() {
        Currency usd = currency(1, "USD");
        Currency eur = currency(2, "EUR");
        UserAccount user = user(7, "alice", usd);
        Holding holding = holding(101, "AAPL", "Apple Inc.", usd, "Main", 3, "2.0000", "10.0000", "12.0000");

        ExchangeRate eurToUsd = new ExchangeRate();
        eurToUsd.setRate(new BigDecimal("1.2500"));

        when(userAccountRepository.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(user));
        when(currencyRepository.findByCurrencyCodeIgnoreCase("EUR")).thenReturn(Optional.of(eur));
        when(holdingRepository.findByUserUserIdOrderByHoldingIdAsc(7)).thenReturn(List.of(holding));
        when(exchangeRateRepository
                .findTopByStartCurrencyCurrencyCodeIgnoreCaseAndEndCurrencyCurrencyCodeIgnoreCaseOrderByLastUpdatedDesc(
                        "EUR",
                        "USD"
                ))
                .thenReturn(Optional.of(eurToUsd));

        HoldingsInCurrencyResponse response = holdingService.getHoldingsInCurrency("alice", "EUR");

        assertEquals("EUR", response.targetCurrency());
        assertEquals(new BigDecimal("16.0000"), response.totalInvestedTarget());
        assertEquals(new BigDecimal("19.2000"), response.totalMarketValueTarget());
        assertEquals(new BigDecimal("3.2000"), response.totalUnrealizedPnlTarget());

        HoldingCurrencyViewResponse item = response.holdings().get(0);
        assertEquals("USD", item.sourceCurrency());
        assertEquals("EUR", item.targetCurrency());
        assertEquals(0, item.exchangeRate().compareTo(new BigDecimal("0.80000000")));
        assertEquals(new BigDecimal("8.0000"), item.avgPurchasePriceTarget());
        assertEquals(new BigDecimal("9.6000"), item.lastPriceTarget());
        assertEquals(new BigDecimal("16.0000"), item.investedAmountTarget());
        assertEquals(new BigDecimal("19.2000"), item.marketValueTarget());
        assertEquals(new BigDecimal("3.2000"), item.unrealizedPnlTarget());
    }

    @Test
    void getHoldingsInCurrencyFetchesAndStoresUsdRateWhenMissing() {
        Currency usd = currency(1, "USD");
        Currency eur = currency(2, "EUR");
        UserAccount user = user(9, "bob", usd);
        Holding holding = holding(201, "MSFT", "Microsoft", eur, "Long", 4, "2.0000", "10.0000", "12.0000");

        YahooFinanceClient.YahooQuote eurUsdQuote = new YahooFinanceClient.YahooQuote(
                "EURUSD=X",
                "EUR/USD",
                "EUR/USD",
                "USD",
                new BigDecimal("1.1000")
        );

        when(userAccountRepository.findByUsernameIgnoreCase("bob")).thenReturn(Optional.of(user));
        when(currencyRepository.findByCurrencyCodeIgnoreCase("EUR")).thenReturn(Optional.of(eur));
        when(currencyRepository.findByCurrencyCodeIgnoreCase("USD")).thenReturn(Optional.of(usd));
        when(holdingRepository.findByUserUserIdOrderByHoldingIdAsc(9)).thenReturn(List.of(holding));
        when(exchangeRateRepository
                .findTopByStartCurrencyCurrencyCodeIgnoreCaseAndEndCurrencyCurrencyCodeIgnoreCaseOrderByLastUpdatedDesc(
                        "EUR",
                        "USD"
                ))
                .thenReturn(Optional.empty());
        when(yahooFinanceClient.fetchQuotes(List.of("EURUSD=X"))).thenReturn(List.of(eurUsdQuote));
        when(exchangeRateRepository
                .findTopByStartCurrencyCurrencyIdAndEndCurrencyCurrencyIdOrderByLastUpdatedDesc(
                        eur.getCurrencyId(),
                        usd.getCurrencyId()
                ))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository.save(any(ExchangeRate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HoldingsInCurrencyResponse response = holdingService.getHoldingsInCurrency("bob", "USD");

        HoldingCurrencyViewResponse item = response.holdings().get(0);
        assertEquals(new BigDecimal("22.0000"), response.totalInvestedTarget());
        assertEquals(new BigDecimal("26.4000"), response.totalMarketValueTarget());
        assertEquals(new BigDecimal("4.4000"), response.totalUnrealizedPnlTarget());
        assertEquals(0, item.exchangeRate().compareTo(new BigDecimal("1.10000000")));
        verify(yahooFinanceClient).fetchQuotes(List.of("EURUSD=X"));
        verify(exchangeRateRepository).save(any(ExchangeRate.class));
    }

    @Test
    void updateUserCurrencyUpdatesPreference() {
        Currency usd = currency(1, "USD");
        Currency eur = currency(2, "EUR");
        UserAccount user = user(15, "charlie", usd);

        when(userAccountRepository.findByUsernameIgnoreCase("charlie")).thenReturn(Optional.of(user));
        when(currencyRepository.findByCurrencyCodeIgnoreCase("EUR")).thenReturn(Optional.of(eur));
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserCurrencyUpdateResponse response = holdingService.updateUserCurrency("charlie", "eur");

        assertEquals("USD", response.previousCurrency());
        assertEquals("EUR", response.currency());
        assertSame(eur, user.getCurrency());
    }

    private Currency currency(int id, String code) {
        Currency currency = new Currency();
        currency.setCurrencyId(id);
        currency.setCurrencyCode(code);
        currency.setSymbol(code);
        currency.setBaseUnit(Boolean.TRUE);
        return currency;
    }

    private UserAccount user(int userId, String username, Currency currency) {
        UserAccount user = new UserAccount();
        user.setUserId(userId);
        user.setUsername(username);
        user.setCurrency(currency);
        return user;
    }

    private Holding holding(
            int holdingId,
            String symbol,
            String assetName,
            Currency currency,
            String portfolioName,
            int portfolioId,
            String units,
            String avgPurchasePrice,
            String lastPrice
    ) {
        Asset asset = new Asset();
        asset.setAssetSymbol(symbol);
        asset.setAssetName(assetName);
        asset.setCurrency(currency);

        Portfolio portfolio = new Portfolio();
        portfolio.setPortfolioId(portfolioId);
        portfolio.setPortfolioName(portfolioName);

        Holding holding = new Holding();
        holding.setHoldingId(holdingId);
        holding.setAsset(asset);
        holding.setPortfolio(portfolio);
        holding.setUnits(new BigDecimal(units));
        holding.setAvgPurchasePrice(new BigDecimal(avgPurchasePrice));
        holding.setLastPrice(new BigDecimal(lastPrice));
        return holding;
    }
}
