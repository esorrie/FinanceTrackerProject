package com.project.finance.service;

import com.project.finance.client.YahooFinanceClient;
import com.project.finance.client.YahooFinanceClient.ScreenerPage;
import com.project.finance.client.YahooFinanceClient.YahooQuote;
import com.project.finance.dto.MarketDataImportResponse;
import com.project.finance.dto.MarketDataStockImportResponse;
import com.project.finance.entity.Asset;
import com.project.finance.entity.AssetHistory;
import com.project.finance.entity.Currency;
import com.project.finance.repository.AssetHistoryRepository;
import com.project.finance.repository.AssetRepository;
import com.project.finance.repository.CurrencyRepository;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MarketDataImportService {

    private static final int SYMBOL_MAX_LENGTH = 10;
    private static final int ASSET_NAME_MAX_LENGTH = 50;
    private static final int CURRENCY_MAX_LENGTH = 3;
    private static final int MAX_PAGE_SIZE = 250;
    private static final int MAX_ALLOWED_PAGES = 200;
    private static final int SYMBOL_SAMPLE_LIMIT = 100;

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
            StoredQuoteResult storedQuote = storeQuote(quote, true, true);
            if (!storedQuote.stored()) {
                continue;
            }
            if (storedQuote.newAsset()) {
                newAssets++;
            }
            if (storedQuote.newCurrency()) {
                newCurrencies++;
            }
            if (storedQuote.historySaved()) {
                savedQuotes++;
            }
            symbolsSaved.add(storedQuote.symbol());
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

    public MarketDataStockImportResponse importStocksFromScreener(String screenerId, int pageSize, int maxPages) {
        return importStocksFromScreener(screenerId, pageSize, maxPages, true);
    }

    public MarketDataStockImportResponse importStocksFromScreener(
            String screenerId,
            int pageSize,
            int maxPages,
            boolean allowCreates
    ) {
        if (!StringUtils.hasText(screenerId)) {
            throw new IllegalArgumentException("screenerId is required.");
        }
        if (pageSize <= 0 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
        if (maxPages <= 0 || maxPages > MAX_ALLOWED_PAGES) {
            throw new IllegalArgumentException("maxPages must be between 1 and " + MAX_ALLOWED_PAGES + ".");
        }

        String normalizedScreenerId = screenerId.trim();
        int pagesProcessed = 0;
        int quotesReturned = 0;
        int assetsImported = 0;
        int newAssets = 0;
        int updatedAssets = 0;
        int newCurrencies = 0;
        int savedHistoryRows = 0;
        Integer totalAvailable = null;
        Set<String> symbolsSample = new LinkedHashSet<>();

        for (int pageIndex = 0; pageIndex < maxPages; pageIndex++) {
            int start = pageIndex * pageSize;
            ScreenerPage screenerPage = yahooFinanceClient.fetchScreenerPage(normalizedScreenerId, start, pageSize);
            pagesProcessed++;

            if (screenerPage.totalAvailable() != null && screenerPage.totalAvailable() >= 0) {
                totalAvailable = screenerPage.totalAvailable();
            }

            List<YahooQuote> pageQuotes = screenerPage.quotes() == null ? List.of() : screenerPage.quotes();
            if (pageQuotes.isEmpty()) {
                break;
            }

            quotesReturned += pageQuotes.size();

            for (YahooQuote quote : pageQuotes) {
                StoredQuoteResult storedQuote = storeQuote(quote, false, allowCreates);
                if (!storedQuote.stored()) {
                    continue;
                }

                assetsImported++;
                if (storedQuote.newAsset()) {
                    newAssets++;
                }
                if (storedQuote.updatedAsset()) {
                    updatedAssets++;
                }
                if (storedQuote.newCurrency()) {
                    newCurrencies++;
                }
                if (storedQuote.historySaved()) {
                    savedHistoryRows++;
                }
                if (symbolsSample.size() < SYMBOL_SAMPLE_LIMIT) {
                    symbolsSample.add(storedQuote.symbol());
                }
            }

            if (pageQuotes.size() < pageSize) {
                break;
            }
            if (totalAvailable != null && start + pageQuotes.size() >= totalAvailable) {
                break;
            }
        }

        return new MarketDataStockImportResponse(
                normalizedScreenerId,
                pageSize,
                maxPages,
                pagesProcessed,
                totalAvailable,
                quotesReturned,
                assetsImported,
                newAssets,
                updatedAssets,
                newCurrencies,
                savedHistoryRows,
                List.copyOf(symbolsSample)
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

    private StoredQuoteResult storeQuote(YahooQuote quote, boolean requirePrice, boolean allowCreates) {
        if (!canBeStored(quote, requirePrice)) {
            return StoredQuoteResult.skipped();
        }

        String symbol = quote.symbol().trim().toUpperCase(Locale.ROOT);
        String currencyCode = quote.currency().trim().toUpperCase(Locale.ROOT);
        String assetName = normalizeForColumn(resolveAssetName(quote), ASSET_NAME_MAX_LENGTH);

        if (!fitsColumnLimit(symbol, SYMBOL_MAX_LENGTH) || !fitsColumnLimit(currencyCode, CURRENCY_MAX_LENGTH)) {
            return StoredQuoteResult.skipped();
        }

        Currency currency = currencyRepository.findByCurrencyCodeIgnoreCase(currencyCode).orElse(null);
        boolean newCurrency = false;
        if (currency == null) {
            if (!allowCreates) {
                return StoredQuoteResult.skipped();
            }
            currency = new Currency();
            currency.setCurrencyCode(currencyCode);
            currency.setSymbol(currencyCode);
            currency.setBaseUnit(Boolean.TRUE);
            currency = currencyRepository.save(currency);
            newCurrency = true;
        }

        Asset asset = assetRepository.findByAssetSymbolIgnoreCase(symbol).orElse(null);
        boolean newAsset = false;
        boolean updatedAsset = false;
        if (asset == null) {
            if (!allowCreates) {
                return StoredQuoteResult.skipped();
            }
            asset = new Asset();
            asset.setAssetSymbol(symbol);
            asset.setAssetName(assetName);
            asset.setCurrency(currency);
            asset = assetRepository.save(asset);
            newAsset = true;
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
                updatedAsset = true;
            }
        }

        boolean historySaved = false;
        if (quote.regularMarketPrice() != null) {
            AssetHistory assetHistory = assetHistoryRepository
                    .findTopByAssetAssetIdAndCurrencyCurrencyIdOrderByLastUpdateDesc(
                            asset.getAssetId(),
                            currency.getCurrencyId()
                    )
                    .orElseGet(AssetHistory::new);
            assetHistory.setAsset(asset);
            assetHistory.setCurrency(currency);
            assetHistory.setPrice(quote.regularMarketPrice());
            assetHistoryRepository.save(assetHistory);
            historySaved = true;
        }

        return new StoredQuoteResult(true, symbol, newAsset, updatedAsset, newCurrency, historySaved);
    }

    private boolean canBeStored(YahooQuote quote, boolean requirePrice) {
        return quote != null
                && StringUtils.hasText(quote.symbol())
                && StringUtils.hasText(quote.currency())
                && (!requirePrice || quote.regularMarketPrice() != null);
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

    private record StoredQuoteResult(
            boolean stored,
            String symbol,
            boolean newAsset,
            boolean updatedAsset,
            boolean newCurrency,
            boolean historySaved
    ) {
        private static StoredQuoteResult skipped() {
            return new StoredQuoteResult(false, null, false, false, false, false);
        }
    }
}
