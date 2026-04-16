package com.project.finance.service;

import com.project.finance.client.YahooFinanceClient;
import com.project.finance.client.YahooFinanceClient.YahooQuote;
import com.project.finance.dto.MarketDataImportResponse;
import com.project.finance.entity.Asset;
import com.project.finance.entity.AssetHistory;
import com.project.finance.entity.Currency;
import com.project.finance.repository.AssetHistoryRepository;
import com.project.finance.repository.AssetRepository;
import com.project.finance.repository.CurrencyRepository;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MarketDataImportService {

    private final YahooFinanceClient yahooFinanceClient;
    private final AssetRepository assetRepository;
    private final CurrencyRepository currencyRepository;
    private final AssetHistoryRepository assetHistoryRepository;

    public MarketDataImportService(
            YahooFinanceClient yahooFinanceClient,
            AssetRepository assetRepository,
            CurrencyRepository currencyRepository,
            AssetHistoryRepository assetHistoryRepository
    ) {
        this.yahooFinanceClient = yahooFinanceClient;
        this.assetRepository = assetRepository;
        this.currencyRepository = currencyRepository;
        this.assetHistoryRepository = assetHistoryRepository;
    }

    @Transactional
    public MarketDataImportResponse importQuotes(List<String> symbols) {
        List<String> normalizedSymbols = normalizeSymbols(symbols);
        if (normalizedSymbols.isEmpty()) {
            throw new IllegalArgumentException("At least one symbol is required.");
        }

        List<YahooQuote> quotes = yahooFinanceClient.fetchQuotes(normalizedSymbols);

        int newAssets = 0;
        int newCurrencies = 0;
        int savedQuotes = 0;
        List<String> symbolsSaved = new ArrayList<>();

        for (YahooQuote quote : quotes) {
            if (!canBeStored(quote)) {
                continue;
            }

            String symbol = quote.symbol().trim().toUpperCase(Locale.ROOT);
            String currencyCode = quote.currency().trim().toUpperCase(Locale.ROOT);
            String assetName = normalizeForColumn(resolveAssetName(quote), 50);

            if (!fitsColumnLimit(symbol, 10) || !fitsColumnLimit(currencyCode, 3)) {
                continue;
            }

            Currency currency = currencyRepository.findByCurrencyCodeIgnoreCase(currencyCode).orElse(null);
            if (currency == null) {
                currency = new Currency();
                currency.setCurrencyCode(currencyCode);
                currency.setSymbol(currencyCode);
                currency.setBaseUnit(Boolean.TRUE);
                currency = currencyRepository.save(currency);
                newCurrencies++;
            }

            Asset asset = assetRepository.findByAssetSymbolIgnoreCase(symbol).orElse(null);
            if (asset == null) {
                asset = new Asset();
                asset.setAssetSymbol(symbol);
                asset.setAssetName(assetName);
                asset.setCurrency(currency);
                asset = assetRepository.save(asset);
                newAssets++;
            } else {
                boolean changed = false;
                if (StringUtils.hasText(assetName) && !assetName.equals(asset.getAssetName())) {
                    asset.setAssetName(assetName);
                    changed = true;
                }
                if (asset.getCurrency() == null
                        || !currencyCode.equalsIgnoreCase(asset.getCurrency().getCurrencyCode())) {
                    asset.setCurrency(currency);
                    changed = true;
                }
                if (changed) {
                    asset = assetRepository.save(asset);
                }
            }

            AssetHistory assetHistory = new AssetHistory();
            assetHistory.setAsset(asset);
            assetHistory.setCurrency(currency);
            assetHistory.setPrice(quote.regularMarketPrice());
            assetHistoryRepository.save(assetHistory);

            savedQuotes++;
            symbolsSaved.add(symbol);
        }

        return new MarketDataImportResponse(
                normalizedSymbols.size(),
                quotes.size(),
                savedQuotes,
                newAssets,
                newCurrencies,
                List.copyOf(symbolsSaved)
        );
    }

    private List<String> normalizeSymbols(List<String> symbols) {
        if (symbols == null) {
            return List.of();
        }

        return symbols.stream()
                .filter(StringUtils::hasText)
                .map(symbol -> symbol.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private boolean canBeStored(YahooQuote quote) {
        return quote != null
                && StringUtils.hasText(quote.symbol())
                && StringUtils.hasText(quote.currency())
                && quote.regularMarketPrice() != null;
    }

    private String resolveAssetName(YahooQuote quote) {
        if (StringUtils.hasText(quote.longName())) {
            return quote.longName().trim();
        }
        if (StringUtils.hasText(quote.shortName())) {
            return quote.shortName().trim();
        }
        return quote.symbol().trim().toUpperCase(Locale.ROOT);
    }

    private boolean fitsColumnLimit(String value, int maxLength) {
        return value.length() <= maxLength;
    }

    private String normalizeForColumn(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
