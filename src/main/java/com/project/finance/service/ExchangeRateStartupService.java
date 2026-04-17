package com.project.finance.service;

import com.project.finance.client.YahooFinanceClient;
import com.project.finance.client.YahooFinanceClient.YahooQuote;
import com.project.finance.entity.Currency;
import com.project.finance.entity.ExchangeRate;
import com.project.finance.repository.CurrencyRepository;
import com.project.finance.repository.ExchangeRateRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ExchangeRateStartupService {

    private static final Logger logger = LoggerFactory.getLogger(ExchangeRateStartupService.class);
    private static final String USD_CURRENCY_CODE = "USD";
    private static final int RATE_SCALE = 8;

    private final CurrencyRepository currencyRepository;
    private final ExchangeRateRepository exchangeRateRepository;
    private final YahooFinanceClient yahooFinanceClient;

    public ExchangeRateStartupService(
            CurrencyRepository currencyRepository,
            ExchangeRateRepository exchangeRateRepository,
            YahooFinanceClient yahooFinanceClient
    ) {
        this.currencyRepository = currencyRepository;
        this.exchangeRateRepository = exchangeRateRepository;
        this.yahooFinanceClient = yahooFinanceClient;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void populateUsdAnchoredRatesAtStartup() {
        try {
            syncAllCurrenciesToUsd();
        } catch (Exception ex) {
            logger.warn("Exchange-rate startup sync failed: {}", ex.getMessage());
        }
    }

    @Transactional
    public void syncAllCurrenciesToUsd() {
        Currency usdCurrency = findOrCreateCurrency(USD_CURRENCY_CODE);
        upsertRate(usdCurrency, usdCurrency, BigDecimal.ONE);

        Set<String> candidateCurrencyCodes = loadCandidateCurrencyCodes();
        int updated = 1; // USD->USD
        int skipped = 0;
        List<String> missingSample = new ArrayList<>();

        for (String currencyCode : candidateCurrencyCodes) {
            if (USD_CURRENCY_CODE.equals(currencyCode)) {
                skipped++;
                continue;
            }

            BigDecimal usdRate = fetchUsdRate(currencyCode);
            if (usdRate == null || usdRate.compareTo(BigDecimal.ZERO) <= 0) {
                skipped++;
                if (missingSample.size() < 20) {
                    missingSample.add(currencyCode);
                }
                continue;
            }

            Currency currency = findOrCreateCurrency(currencyCode);
            upsertRate(currency, usdCurrency, usdRate);
            updated++;
        }

        logger.info(
                "USD exchange-rate sync completed. updated={}, skipped={}, knownCurrencies={}, missingSample={}",
                updated,
                skipped,
                candidateCurrencyCodes.size(),
                missingSample
        );
    }

    private Set<String> loadCandidateCurrencyCodes() {
        Set<String> codes = new TreeSet<>();
        codes.add(USD_CURRENCY_CODE);

        for (java.util.Currency javaCurrency : java.util.Currency.getAvailableCurrencies()) {
            if (javaCurrency == null) {
                continue;
            }
            String normalized = normalizeCurrencyCode(javaCurrency.getCurrencyCode());
            if (normalized != null) {
                codes.add(normalized);
            }
        }

        List<Currency> dbCurrencies = currencyRepository.findAll();
        for (Currency dbCurrency : dbCurrencies) {
            if (dbCurrency == null) {
                continue;
            }
            String normalized = normalizeCurrencyCode(dbCurrency.getCurrencyCode());
            if (normalized != null) {
                codes.add(normalized);
            }
        }

        return codes;
    }

    private Currency findOrCreateCurrency(String currencyCode) {
        Currency existing = currencyRepository.findByCurrencyCodeIgnoreCase(currencyCode).orElse(null);
        if (existing != null) {
            return existing;
        }

        Currency created = new Currency();
        created.setCurrencyCode(currencyCode);
        created.setSymbol(currencyCode);
        created.setBaseUnit(Boolean.TRUE);
        return currencyRepository.save(created);
    }

    private void upsertRate(Currency startCurrency, Currency endCurrency, BigDecimal rate) {
        ExchangeRate exchangeRate = exchangeRateRepository
                .findTopByStartCurrencyCurrencyIdAndEndCurrencyCurrencyIdOrderByLastUpdatedDesc(
                        startCurrency.getCurrencyId(),
                        endCurrency.getCurrencyId()
                )
                .orElseGet(ExchangeRate::new);

        exchangeRate.setStartCurrency(startCurrency);
        exchangeRate.setEndCurrency(endCurrency);
        exchangeRate.setRate(rate);
        exchangeRateRepository.save(exchangeRate);
    }

    private BigDecimal fetchUsdRate(String sourceCurrencyCode) {
        if (USD_CURRENCY_CODE.equalsIgnoreCase(sourceCurrencyCode)) {
            return BigDecimal.ONE;
        }

        String directPairSymbol = sourceCurrencyCode + USD_CURRENCY_CODE + "=X";
        BigDecimal directRate = fetchPairRate(directPairSymbol);
        if (isValidRate(directRate)) {
            return directRate;
        }

        String inversePairSymbol = USD_CURRENCY_CODE + sourceCurrencyCode + "=X";
        BigDecimal inverseRate = fetchPairRate(inversePairSymbol);
        if (isValidRate(inverseRate)) {
            return BigDecimal.ONE.divide(inverseRate, RATE_SCALE, RoundingMode.HALF_UP);
        }

        return null;
    }

    private BigDecimal fetchPairRate(String pairSymbol) {
        try {
            List<YahooQuote> quotes = yahooFinanceClient.fetchQuotes(List.of(pairSymbol));
            if (quotes == null || quotes.isEmpty()) {
                return null;
            }
            return quotes.stream()
                    .filter(Objects::nonNull)
                    .filter(quote -> quote != null
                            && StringUtils.hasText(quote.symbol())
                            && pairSymbol.equalsIgnoreCase(quote.symbol())
                            && quote.regularMarketPrice() != null
                            && quote.regularMarketPrice().compareTo(BigDecimal.ZERO) > 0)
                    .map(YahooQuote::regularMarketPrice)
                    .findFirst()
                    .orElse(null);
        } catch (IllegalStateException ex) {
            logger.debug("Failed to fetch {} from Yahoo: {}", pairSymbol, ex.getMessage());
            return null;
        }
    }

    private boolean isValidRate(BigDecimal rate) {
        return rate != null && rate.compareTo(BigDecimal.ZERO) > 0;
    }

    private String normalizeCurrencyCode(String currencyCode) {
        if (!StringUtils.hasText(currencyCode)) {
            return null;
        }
        String normalized = currencyCode.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() != 3 || !normalized.chars().allMatch(Character::isLetter)) {
            return null;
        }
        return normalized;
    }
}
