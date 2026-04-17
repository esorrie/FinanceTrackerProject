package com.project.finance.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.project.finance.client.YahooFinanceClient;
import com.project.finance.entity.Currency;
import com.project.finance.entity.ExchangeRate;
import com.project.finance.repository.CurrencyRepository;
import com.project.finance.repository.ExchangeRateRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExchangeRateStartupServiceTest {

    @Mock
    private CurrencyRepository currencyRepository;
    @Mock
    private ExchangeRateRepository exchangeRateRepository;
    @Mock
    private YahooFinanceClient yahooFinanceClient;

    private ExchangeRateStartupService exchangeRateStartupService;

    @BeforeEach
    void setUp() {
        exchangeRateStartupService = new ExchangeRateStartupService(
                currencyRepository,
                exchangeRateRepository,
                yahooFinanceClient
        );
    }

    @Test
    void syncAllCurrenciesToUsdStoresUsdAndAvailablePairs() {
        Currency usd = currency(1, "USD");
        Currency eur = currency(2, "EUR");

        when(currencyRepository.findByCurrencyCodeIgnoreCase("USD")).thenReturn(Optional.of(usd));
        when(currencyRepository.findByCurrencyCodeIgnoreCase("EUR")).thenReturn(Optional.of(eur));
        when(currencyRepository.findAll()).thenReturn(List.of(usd, eur));
        when(exchangeRateRepository
                .findTopByStartCurrencyCurrencyIdAndEndCurrencyCurrencyIdOrderByLastUpdatedDesc(1, 1))
                .thenReturn(Optional.empty());
        when(exchangeRateRepository
                .findTopByStartCurrencyCurrencyIdAndEndCurrencyCurrencyIdOrderByLastUpdatedDesc(2, 1))
                .thenReturn(Optional.empty());
        when(yahooFinanceClient.fetchQuotes(anyList())).thenAnswer(invocation -> {
            Object argument = invocation.getArgument(0);
            if (argument instanceof List<?> symbols
                    && symbols.size() == 1
                    && "EURUSD=X".equalsIgnoreCase(String.valueOf(symbols.get(0)))) {
                return List.of(
                        new YahooFinanceClient.YahooQuote(
                                "EURUSD=X",
                                "EUR/USD",
                                "EUR/USD",
                                "USD",
                                new BigDecimal("1.1000")
                        )
                );
            }
            return List.of();
        });
        when(exchangeRateRepository.save(any(ExchangeRate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        exchangeRateStartupService.syncAllCurrenciesToUsd();

        verify(exchangeRateRepository, times(2)).save(any(ExchangeRate.class));
    }

    private Currency currency(int id, String code) {
        Currency currency = new Currency();
        currency.setCurrencyId(id);
        currency.setCurrencyCode(code);
        currency.setSymbol(code);
        currency.setBaseUnit(Boolean.TRUE);
        return currency;
    }
}
