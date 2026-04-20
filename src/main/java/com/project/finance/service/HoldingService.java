package com.project.finance.service;

import com.project.finance.client.YahooFinanceClient;
import com.project.finance.client.YahooFinanceClient.HistoricalPricePoint;
import com.project.finance.client.YahooFinanceClient.YahooQuote;
import com.project.finance.dto.HoldingCurrencyViewResponse;
import com.project.finance.dto.HoldingCreateRequest;
import com.project.finance.dto.HoldingCreateResponse;
import com.project.finance.dto.HoldingHistoryPointResponse;
import com.project.finance.dto.HoldingHistoryResponse;
import com.project.finance.dto.HoldingUnitsUpdateRequest;
import com.project.finance.dto.HoldingUnitsUpdateResponse;
import com.project.finance.dto.HoldingPerformanceViewResponse;
import com.project.finance.dto.HoldingsInCurrencyResponse;
import com.project.finance.dto.PortfolioHistoryPointResponse;
import com.project.finance.dto.PortfolioHistoryResponse;
import com.project.finance.dto.PortfolioPerformanceResponse;
import com.project.finance.dto.UserCurrencyUpdateResponse;
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
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeSet;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class HoldingService {

    private static final String DEFAULT_PORTFOLIO = "Portfolio";
    private static final String USD_CURRENCY_CODE = "USD";
    private static final int MONEY_SCALE = 4;
    private static final int PERCENT_SCALE = 4;
    private static final int RATE_SCALE = 8;
    private static final String TREND_UP = "UP";
    private static final String TREND_DOWN = "DOWN";
    private static final String TREND_FLAT = "FLAT";
    private static final String INTERVAL_DAILY = "1d";
    private static final String INTERVAL_WEEKLY = "1wk";
    private static final String INTERVAL_MONTHLY = "1mo";
    private static final long DAILY_INTERVAL_MAX_DAYS = 31;
    private static final long WEEKLY_INTERVAL_MAX_DAYS = 365;

    private final YahooFinanceClient yahooFinanceClient;
    private final CurrencyRepository currencyRepository;
    private final AssetRepository assetRepository;
    private final ExchangeRateRepository exchangeRateRepository;
    private final UserAccountRepository userAccountRepository;
    private final PortfolioRepository portfolioRepository;
    private final HoldingRepository holdingRepository;

    public HoldingService(
            YahooFinanceClient yahooFinanceClient,
            CurrencyRepository currencyRepository,
            AssetRepository assetRepository,
            ExchangeRateRepository exchangeRateRepository,
            UserAccountRepository userAccountRepository,
            PortfolioRepository portfolioRepository,
            HoldingRepository holdingRepository
    ) {
        this.yahooFinanceClient = yahooFinanceClient;
        this.currencyRepository = currencyRepository;
        this.assetRepository = assetRepository;
        this.exchangeRateRepository = exchangeRateRepository;
        this.userAccountRepository = userAccountRepository;
        this.portfolioRepository = portfolioRepository;
        this.holdingRepository = holdingRepository;
    }

    @Transactional
    public HoldingCreateResponse createHolding(HoldingCreateRequest request) {
        validateRequest(request);

        String username = request.username().trim();
        String symbol = request.symbol().trim().toUpperCase(Locale.ROOT);
        String portfolioName = StringUtils.hasText(request.portfolioName())
                ? request.portfolioName().trim()
                : DEFAULT_PORTFOLIO;

        YahooQuote quote = fetchQuote(symbol);
        String currencyCode = quote.currency().trim().toUpperCase(Locale.ROOT);

        Currency currency = findOrCreateCurrency(currencyCode);
        Asset asset = findOrCreateAsset(symbol, quote, currency);

        UserAccount user = findOrCreateUser(username, currency);
        Portfolio portfolio = findOrCreatePortfolio(user, portfolioName);
        LocalDate requestedPurchaseDate = request.purchaseDate() != null
            ? request.purchaseDate()
            : LocalDate.now();
        LocalDateTime requestedPurchaseDateTime = requestedPurchaseDate.atStartOfDay();

        List<Holding> existingUserAssetHoldings = holdingRepository
                .findByUserUserIdAndAssetAssetIdOrderByHoldingIdAsc(user.getUserId(), asset.getAssetId());
        Set<Integer> impactedPortfolioIds = new LinkedHashSet<>();
        for (Holding existing : existingUserAssetHoldings) {
            if (existing.getPortfolio() != null && existing.getPortfolio().getPortfolioId() != null) {
                impactedPortfolioIds.add(existing.getPortfolio().getPortfolioId());
            }
        }
        impactedPortfolioIds.add(portfolio.getPortfolioId());

        Holding holding;
        if (existingUserAssetHoldings.isEmpty()) {
            holding = new Holding();
            holding.setUser(user);
            holding.setPortfolio(portfolio);
            holding.setAsset(asset);
            holding.setUnits(request.units());
            holding.setAvgPurchasePrice(request.avgPurchasePrice().setScale(MONEY_SCALE, RoundingMode.HALF_UP));
            holding.setLastPrice(quote.regularMarketPrice().setScale(MONEY_SCALE, RoundingMode.HALF_UP));
            holding.setPortfolioTotalValue(zeroMoney());
            holding.setPurchaseDate(requestedPurchaseDateTime);
            holding = holdingRepository.save(holding);
        } else {
            Holding primaryHolding = selectPrimaryHolding(existingUserAssetHoldings, portfolio.getPortfolioId());
            BigDecimal existingTotalUnits = existingUserAssetHoldings.stream()
                    .map(Holding::getUnits)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal existingTotalCost = existingUserAssetHoldings.stream()
                    .map(item -> item.getAvgPurchasePrice().multiply(item.getUnits()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal combinedUnits = existingTotalUnits.add(request.units());
            BigDecimal combinedCost = existingTotalCost.add(request.avgPurchasePrice().multiply(request.units()));
            BigDecimal weightedAveragePrice = combinedCost.divide(combinedUnits, MONEY_SCALE, RoundingMode.HALF_UP);

            primaryHolding.setUnits(combinedUnits);
            primaryHolding.setAvgPurchasePrice(weightedAveragePrice);
            primaryHolding.setLastPrice(quote.regularMarketPrice().setScale(MONEY_SCALE, RoundingMode.HALF_UP));
            LocalDate oldestPurchaseDate = resolveOldestPurchaseDate(existingUserAssetHoldings, requestedPurchaseDate);
            primaryHolding.setPurchaseDate(oldestPurchaseDate.atStartOfDay());
            if (primaryHolding.getPortfolio() == null) {
                primaryHolding.setPortfolio(portfolio);
            }
            if (primaryHolding.getPortfolioTotalValue() == null) {
                primaryHolding.setPortfolioTotalValue(zeroMoney());
            }
            holding = holdingRepository.save(primaryHolding);
            Integer primaryHoldingId = holding.getHoldingId();

            List<Holding> duplicateHoldings = existingUserAssetHoldings.stream()
                    .filter(item -> !item.getHoldingId().equals(primaryHoldingId))
                    .toList();
            if (!duplicateHoldings.isEmpty()) {
                holdingRepository.deleteAll(duplicateHoldings);
            }
        }

        refreshPortfolioTotals(impactedPortfolioIds);

        BigDecimal investedAmount = holding.getAvgPurchasePrice().multiply(holding.getUnits())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal marketValue = holding.getLastPrice().multiply(holding.getUnits())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal unrealizedPnl = marketValue.subtract(investedAmount);

        return new HoldingCreateResponse(
                holding.getHoldingId(),
                user.getUserId(),
                holding.getPortfolio().getPortfolioId(),
                user.getUsername(),
                holding.getPortfolio().getPortfolioName(),
                asset.getAssetSymbol(),
                asset.getAssetName(),
                currency.getCurrencyCode(),
                holding.getUnits(),
                holding.getAvgPurchasePrice(),
                holding.getLastPrice(),
                holding.getPortfolioTotalValue(),
                investedAmount,
                marketValue,
                unrealizedPnl
        );
    }

    @Transactional
    public HoldingLastPriceRefreshSummary refreshAllHoldingLastPrices() {
        List<Holding> holdings = holdingRepository.findAll();
        int totalHoldings = holdings.size();
        if (totalHoldings == 0) {
            return new HoldingLastPriceRefreshSummary(0, 0, 0, 0, 0);
        }

        List<String> symbols = holdings.stream()
                .map(holding -> holding.getAsset() == null ? null : holding.getAsset().getAssetSymbol())
                .filter(StringUtils::hasText)
                .map(symbol -> symbol.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();

        if (symbols.isEmpty()) {
            return new HoldingLastPriceRefreshSummary(totalHoldings, 0, 0, 0, totalHoldings);
        }

        List<YahooQuote> quotes = yahooFinanceClient.fetchQuotes(symbols);
        Map<String, BigDecimal> latestPriceBySymbol = new HashMap<>();
        for (YahooQuote quote : quotes) {
            if (quote == null || !StringUtils.hasText(quote.symbol())) {
                continue;
            }
            BigDecimal latestPrice = resolveLatestQuotePrice(quote);
            if (!isValidRate(latestPrice)) {
                continue;
            }
            latestPriceBySymbol.put(quote.symbol().trim().toUpperCase(Locale.ROOT), latestPrice);
        }

        int holdingsUpdated = 0;
        int holdingsMissingQuote = 0;
        List<Holding> changedHoldings = new ArrayList<>();

        for (Holding holding : holdings) {
            String symbol = holding.getAsset() == null ? null : holding.getAsset().getAssetSymbol();
            if (!StringUtils.hasText(symbol)) {
                holdingsMissingQuote++;
                continue;
            }

            BigDecimal latestPrice = latestPriceBySymbol.get(symbol.trim().toUpperCase(Locale.ROOT));
            if (!isValidRate(latestPrice)) {
                holdingsMissingQuote++;
                continue;
            }

            BigDecimal normalizedLatestPrice = latestPrice.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            if (!equalsDecimal(holding.getLastPrice(), normalizedLatestPrice)) {
                holding.setLastPrice(normalizedLatestPrice);
                changedHoldings.add(holding);
                holdingsUpdated++;
            }
        }

        if (!changedHoldings.isEmpty()) {
            holdingRepository.saveAll(changedHoldings);
        }

        return new HoldingLastPriceRefreshSummary(
                totalHoldings,
                symbols.size(),
                quotes.size(),
                holdingsUpdated,
                holdingsMissingQuote
        );
    }

    @Transactional
    public HoldingsInCurrencyResponse getHoldingsInCurrency(String username, String requestedCurrencyCode) {
        String normalizedUsername = normalizeUsername(username);

        UserAccount user = userAccountRepository.findByUsernameIgnoreCase(normalizedUsername)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + normalizedUsername));

        Currency targetCurrency = resolveTargetCurrency(user, requestedCurrencyCode);
        String targetCurrencyCode = targetCurrency.getCurrencyCode().trim().toUpperCase(Locale.ROOT);

        List<Holding> holdings = holdingRepository.findByUserUserIdOrderByHoldingIdAsc(user.getUserId());
        List<HoldingCurrencyViewResponse> holdingViews = new ArrayList<>(holdings.size());
        Map<String, BigDecimal> exchangeRateCache = new HashMap<>();

        BigDecimal totalInvestedTarget = zeroMoney();
        BigDecimal totalMarketValueTarget = zeroMoney();

        for (Holding holding : holdings) {
            String sourceCurrencyCode = resolveHoldingCurrencyCode(holding);
            BigDecimal exchangeRate = exchangeRateCache.computeIfAbsent(
                    sourceCurrencyCode,
                    code -> resolveExchangeRate(code, targetCurrencyCode)
            );

            BigDecimal investedSource = holding.getAvgPurchasePrice().multiply(holding.getUnits());
            BigDecimal marketValueSource = holding.getLastPrice().multiply(holding.getUnits());
            BigDecimal portfolioTotalValueSource = holding.getPortfolioTotalValue() == null
                    ? marketValueSource
                    : holding.getPortfolioTotalValue();

            BigDecimal avgPurchasePriceTarget = convertAmount(holding.getAvgPurchasePrice(), exchangeRate);
            BigDecimal lastPriceTarget = convertAmount(holding.getLastPrice(), exchangeRate);
            BigDecimal investedAmountTarget = convertAmount(investedSource, exchangeRate);
            BigDecimal marketValueTarget = convertAmount(marketValueSource, exchangeRate);
            BigDecimal portfolioTotalValueTarget = convertAmount(portfolioTotalValueSource, exchangeRate);
            BigDecimal unrealizedPnlTarget = marketValueTarget.subtract(investedAmountTarget)
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

            totalInvestedTarget = totalInvestedTarget.add(investedAmountTarget);
            totalMarketValueTarget = totalMarketValueTarget.add(marketValueTarget);

            holdingViews.add(new HoldingCurrencyViewResponse(
                    holding.getHoldingId(),
                    holding.getPortfolio().getPortfolioId(),
                    holding.getPortfolio().getPortfolioName(),
                    holding.getAsset().getAssetSymbol(),
                    holding.getAsset().getAssetName(),
                    holding.getUnits(),
                    sourceCurrencyCode,
                    targetCurrencyCode,
                    exchangeRate,
                    holding.getAvgPurchasePrice(),
                    avgPurchasePriceTarget,
                    holding.getLastPrice(),
                    lastPriceTarget,
                    portfolioTotalValueTarget,
                    investedAmountTarget,
                    marketValueTarget,
                    unrealizedPnlTarget
            ));
        }

        BigDecimal totalUnrealizedPnlTarget = totalMarketValueTarget.subtract(totalInvestedTarget)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        return new HoldingsInCurrencyResponse(
                user.getUserId(),
                user.getUsername(),
                targetCurrencyCode,
                holdingViews.size(),
                totalInvestedTarget,
                totalMarketValueTarget,
                totalUnrealizedPnlTarget,
                List.copyOf(holdingViews)
        );
    }

    @Transactional
    public PortfolioPerformanceResponse getPortfolioPerformance(String username, String requestedCurrencyCode) {
        String normalizedUsername = normalizeUsername(username);

        UserAccount user = userAccountRepository.findByUsernameIgnoreCase(normalizedUsername)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + normalizedUsername));

        Currency targetCurrency = resolveTargetCurrency(user, requestedCurrencyCode);
        String targetCurrencyCode = targetCurrency.getCurrencyCode().trim().toUpperCase(Locale.ROOT);

        List<Holding> holdings = holdingRepository.findByUserUserIdOrderByHoldingIdAsc(user.getUserId());
        if (holdings.isEmpty()) {
            BigDecimal zeroPercent = BigDecimal.ZERO.setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
            return new PortfolioPerformanceResponse(
                    user.getUserId(),
                    user.getUsername(),
                    targetCurrencyCode,
                    LocalDateTime.now(),
                    0,
                    zeroMoney(),
                    zeroMoney(),
                    zeroMoney(),
                    zeroPercent,
                    TREND_FLAT,
                    List.of()
            );
        }

        List<String> symbols = holdings.stream()
                .map(holding -> holding.getAsset() == null ? null : holding.getAsset().getAssetSymbol())
                .filter(StringUtils::hasText)
                .map(symbol -> symbol.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();

        Map<String, YahooQuote> quotesBySymbol = new HashMap<>();
        List<YahooQuote> yahooQuotes = yahooFinanceClient.fetchQuotes(symbols);
        for (YahooQuote quote : yahooQuotes) {
            if (quote == null || !StringUtils.hasText(quote.symbol())) {
                continue;
            }
            quotesBySymbol.put(quote.symbol().trim().toUpperCase(Locale.ROOT), quote);
        }

        Map<String, BigDecimal> exchangeRateCache = new HashMap<>();
        List<HoldingPerformanceViewResponse> holdingViews = new ArrayList<>();

        BigDecimal totalCurrentValueTarget = zeroMoney();
        BigDecimal totalPreviousCloseValueTarget = zeroMoney();

        for (Holding holding : holdings) {
            if (holding.getAsset() == null || !StringUtils.hasText(holding.getAsset().getAssetSymbol())) {
                continue;
            }

            String symbol = holding.getAsset().getAssetSymbol().trim().toUpperCase(Locale.ROOT);
            YahooQuote quote = quotesBySymbol.get(symbol);

            BigDecimal currentPriceSource = resolveCurrentPriceSource(holding, quote);
            BigDecimal previousCloseSource = resolvePreviousCloseSource(currentPriceSource, quote);
            String sourceCurrencyCode = resolveHoldingCurrencyCode(holding);

            BigDecimal exchangeRate = exchangeRateCache.computeIfAbsent(
                    sourceCurrencyCode,
                    code -> resolveExchangeRate(code, targetCurrencyCode)
            );

            BigDecimal priceChangeSource = currentPriceSource.subtract(previousCloseSource)
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal priceChangePercent = calculatePercentChange(priceChangeSource, previousCloseSource);

            BigDecimal currentValueTarget = convertAmount(currentPriceSource.multiply(holding.getUnits()), exchangeRate);
            BigDecimal previousCloseValueTarget = convertAmount(
                    previousCloseSource.multiply(holding.getUnits()),
                    exchangeRate
            );
            BigDecimal valueChangeTarget = currentValueTarget.subtract(previousCloseValueTarget)
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

            totalCurrentValueTarget = totalCurrentValueTarget.add(currentValueTarget);
            totalPreviousCloseValueTarget = totalPreviousCloseValueTarget.add(previousCloseValueTarget);

            holdingViews.add(new HoldingPerformanceViewResponse(
                    holding.getHoldingId(),
                    holding.getPortfolio().getPortfolioId(),
                    holding.getPortfolio().getPortfolioName(),
                    symbol,
                    holding.getAsset().getAssetName(),
                    holding.getUnits(),
                    sourceCurrencyCode,
                    targetCurrencyCode,
                    exchangeRate,
                    currentPriceSource.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                    previousCloseSource.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                    priceChangeSource,
                    priceChangePercent,
                    currentValueTarget,
                    previousCloseValueTarget,
                    valueChangeTarget,
                    resolveTrend(valueChangeTarget)
            ));
        }

        BigDecimal portfolioValueChangeTarget = totalCurrentValueTarget.subtract(totalPreviousCloseValueTarget)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal portfolioValueChangePercent = calculatePercentChange(
                portfolioValueChangeTarget,
                totalPreviousCloseValueTarget
        );

        return new PortfolioPerformanceResponse(
                user.getUserId(),
                user.getUsername(),
                targetCurrencyCode,
                LocalDateTime.now(),
                holdingViews.size(),
                totalCurrentValueTarget.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                totalPreviousCloseValueTarget.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                portfolioValueChangeTarget,
                portfolioValueChangePercent,
                resolveTrend(portfolioValueChangeTarget),
                List.copyOf(holdingViews)
        );
    }

    @Transactional
    public HoldingHistoryResponse getHoldingHistory(
            String username,
            String symbol,
            String requestedCurrencyCode,
            String requestedInterval
    ) {
        String normalizedUsername = normalizeUsername(username);
        String normalizedSymbol = normalizeSymbol(symbol);

        UserAccount user = userAccountRepository.findByUsernameIgnoreCase(normalizedUsername)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + normalizedUsername));

        Currency targetCurrency = resolveTargetCurrency(user, requestedCurrencyCode);
        String targetCurrencyCode = targetCurrency.getCurrencyCode().trim().toUpperCase(Locale.ROOT);

        List<Holding> userHoldings = holdingRepository.findByUserUserIdOrderByHoldingIdAsc(user.getUserId());
        List<Holding> symbolHoldings = userHoldings.stream()
                .filter(holding -> holding.getAsset() != null
                        && StringUtils.hasText(holding.getAsset().getAssetSymbol())
                        && normalizedSymbol.equalsIgnoreCase(holding.getAsset().getAssetSymbol()))
                .toList();

        if (symbolHoldings.isEmpty()) {
            throw new IllegalArgumentException("Holding not found for symbol: " + normalizedSymbol);
        }

        Holding primaryHolding = symbolHoldings.get(0);
        String sourceCurrencyCode = resolveHoldingCurrencyCode(primaryHolding);
        BigDecimal exchangeRate = resolveExchangeRate(sourceCurrencyCode, targetCurrencyCode);

        BigDecimal totalUnits = symbolHoldings.stream()
                .map(Holding::getUnits)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        LocalDate today = LocalDate.now();
        LocalDate fromDate = symbolHoldings.stream()
                .map(this::resolveHoldingStartDate)
                .min(LocalDate::compareTo)
                .orElse(today.minusMonths(3));

        String effectiveInterval = resolveEffectiveInterval(requestedInterval, fromDate, today);
        List<HistoricalPricePoint> history = yahooFinanceClient.fetchHistoricalClosePrices(
                normalizedSymbol,
                fromDate,
                today,
                effectiveInterval
        );

        List<HoldingHistoryPointResponse> points = new ArrayList<>();
        for (HistoricalPricePoint point : history) {
            if (point == null || point.date() == null || point.closePrice() == null) {
                continue;
            }

            BigDecimal closePriceSource = point.closePrice().setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal closePriceTarget = convertAmount(closePriceSource, exchangeRate);
            BigDecimal holdingValueTarget = closePriceTarget.multiply(totalUnits)
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            points.add(new HoldingHistoryPointResponse(
                    point.date(),
                    closePriceSource,
                    closePriceTarget,
                    holdingValueTarget
            ));
        }

        if (points.isEmpty()) {
            BigDecimal fallbackCloseSource = resolveCurrentPriceSource(primaryHolding, null)
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal fallbackCloseTarget = convertAmount(fallbackCloseSource, exchangeRate);
            BigDecimal fallbackHoldingValueTarget = fallbackCloseTarget.multiply(totalUnits)
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            points.add(new HoldingHistoryPointResponse(
                    today,
                    fallbackCloseSource,
                    fallbackCloseTarget,
                    fallbackHoldingValueTarget
            ));
        }

        String assetName = primaryHolding.getAsset() == null ? normalizedSymbol : primaryHolding.getAsset().getAssetName();

        return new HoldingHistoryResponse(
                user.getUserId(),
                user.getUsername(),
                normalizedSymbol,
                assetName,
                totalUnits,
                sourceCurrencyCode,
                targetCurrencyCode,
                exchangeRate,
                effectiveInterval,
                List.copyOf(points)
        );
    }

    @Transactional
    public PortfolioHistoryResponse getPortfolioHistory(
            String username,
            String requestedCurrencyCode,
            String requestedInterval
    ) {
        String normalizedUsername = normalizeUsername(username);

        UserAccount user = userAccountRepository.findByUsernameIgnoreCase(normalizedUsername)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + normalizedUsername));

        Currency targetCurrency = resolveTargetCurrency(user, requestedCurrencyCode);
        String targetCurrencyCode = targetCurrency.getCurrencyCode().trim().toUpperCase(Locale.ROOT);

        List<Holding> holdings = holdingRepository.findByUserUserIdOrderByHoldingIdAsc(user.getUserId());
        if (holdings.isEmpty()) {
            return new PortfolioHistoryResponse(
                    user.getUserId(),
                    user.getUsername(),
                    targetCurrencyCode,
                    INTERVAL_DAILY,
                    0,
                    List.of()
            );
        }

        LocalDate today = LocalDate.now();
        LocalDate fromDate = holdings.stream()
                .map(this::resolveHoldingStartDate)
                .min(LocalDate::compareTo)
                .orElse(today.minusMonths(3));

        String effectiveInterval = resolveEffectiveInterval(requestedInterval, fromDate, today);
        Map<String, BigDecimal> exchangeRateCache = new HashMap<>();
        List<HoldingHistorySeries> seriesByHolding = new ArrayList<>();

        for (Holding holding : holdings) {
            if (holding.getAsset() == null || !StringUtils.hasText(holding.getAsset().getAssetSymbol())) {
                continue;
            }

            String symbol = holding.getAsset().getAssetSymbol().trim().toUpperCase(Locale.ROOT);
            LocalDate holdingStartDate = resolveHoldingStartDate(holding);
            String sourceCurrencyCode = resolveHoldingCurrencyCode(holding);
            BigDecimal exchangeRate = exchangeRateCache.computeIfAbsent(
                    sourceCurrencyCode,
                    code -> resolveExchangeRate(code, targetCurrencyCode)
            );

            List<HistoricalPricePoint> history = yahooFinanceClient.fetchHistoricalClosePrices(
                    symbol,
                    holdingStartDate,
                    today,
                    effectiveInterval
            );

            NavigableMap<LocalDate, BigDecimal> closePriceTargetByDate = new TreeMap<>();
            for (HistoricalPricePoint point : history) {
                if (point == null || point.date() == null || point.closePrice() == null) {
                    continue;
                }
                BigDecimal closePriceTarget = convertAmount(point.closePrice(), exchangeRate);
                closePriceTargetByDate.put(point.date(), closePriceTarget);
            }

            if (closePriceTargetByDate.isEmpty()) {
                BigDecimal fallbackSourcePrice = resolveCurrentPriceSource(holding, null);
                closePriceTargetByDate.put(today, convertAmount(fallbackSourcePrice, exchangeRate));
            }

            seriesByHolding.add(new HoldingHistorySeries(holdingStartDate, holding.getUnits(), closePriceTargetByDate));
        }

        if (seriesByHolding.isEmpty()) {
            return new PortfolioHistoryResponse(
                    user.getUserId(),
                    user.getUsername(),
                    targetCurrencyCode,
                    effectiveInterval,
                    0,
                    List.of()
            );
        }

        Set<LocalDate> allDates = new TreeSet<>();
        allDates.add(fromDate);
        allDates.add(today);
        for (HoldingHistorySeries series : seriesByHolding) {
            allDates.addAll(series.closePriceTargetByDate().keySet());
        }

        List<PortfolioHistoryPointResponse> points = new ArrayList<>();
        for (LocalDate date : allDates) {
            BigDecimal totalValueTarget = BigDecimal.ZERO;
            boolean anyValueFound = false;

            for (HoldingHistorySeries series : seriesByHolding) {
                if (date.isBefore(series.startDate())) {
                    continue;
                }

                Map.Entry<LocalDate, BigDecimal> floorEntry = series.closePriceTargetByDate().floorEntry(date);
                if (floorEntry == null || floorEntry.getValue() == null) {
                    continue;
                }

                BigDecimal holdingValueTarget = floorEntry.getValue().multiply(series.units());
                totalValueTarget = totalValueTarget.add(holdingValueTarget);
                anyValueFound = true;
            }

            if (anyValueFound) {
                points.add(new PortfolioHistoryPointResponse(
                        date,
                        totalValueTarget.setScale(MONEY_SCALE, RoundingMode.HALF_UP)
                ));
            }
        }

        if (points.isEmpty()) {
            BigDecimal fallbackTotal = zeroMoney();
            for (Holding holding : holdings) {
                if (holding.getAsset() == null || !StringUtils.hasText(holding.getAsset().getAssetSymbol())) {
                    continue;
                }

                String sourceCurrencyCode = resolveHoldingCurrencyCode(holding);
                BigDecimal exchangeRate = exchangeRateCache.computeIfAbsent(
                        sourceCurrencyCode,
                        code -> resolveExchangeRate(code, targetCurrencyCode)
                );
                BigDecimal fallbackPriceTarget = convertAmount(resolveCurrentPriceSource(holding, null), exchangeRate);
                fallbackTotal = fallbackTotal.add(fallbackPriceTarget.multiply(holding.getUnits()));
            }
            points.add(new PortfolioHistoryPointResponse(today, fallbackTotal.setScale(MONEY_SCALE, RoundingMode.HALF_UP)));
        }

        return new PortfolioHistoryResponse(
                user.getUserId(),
                user.getUsername(),
                targetCurrencyCode,
                effectiveInterval,
                seriesByHolding.size(),
                List.copyOf(points)
        );
    }

    @Transactional
    public UserCurrencyUpdateResponse updateUserCurrency(String username, String currencyCode) {
        String normalizedUsername = normalizeUsername(username);
        String normalizedCurrencyCode = normalizeCurrencyCode(currencyCode, "currency");

        UserAccount user = userAccountRepository.findByUsernameIgnoreCase(normalizedUsername)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + normalizedUsername));

        String previousCurrencyCode = user.getCurrency() == null
                ? null
                : user.getCurrency().getCurrencyCode().trim().toUpperCase(Locale.ROOT);

        Currency currency = findOrCreateCurrency(normalizedCurrencyCode);
        if (previousCurrencyCode != null && previousCurrencyCode.equalsIgnoreCase(currency.getCurrencyCode())) {
            return new UserCurrencyUpdateResponse(
                    user.getUserId(),
                    user.getUsername(),
                    previousCurrencyCode,
                    currency.getCurrencyCode().trim().toUpperCase(Locale.ROOT)
            );
        }

        user.setCurrency(currency);
        user = userAccountRepository.save(user);

        return new UserCurrencyUpdateResponse(
                user.getUserId(),
                user.getUsername(),
                previousCurrencyCode,
                currency.getCurrencyCode().trim().toUpperCase(Locale.ROOT)
        );
    }

    @Transactional
    public HoldingUnitsUpdateResponse updateHoldingUnits(HoldingUnitsUpdateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required.");
        }

        String normalizedUsername = normalizeUsername(request.username());
        String normalizedSymbol = normalizeSymbol(request.symbol());

        UserAccount user = userAccountRepository.findByUsernameIgnoreCase(normalizedUsername)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + normalizedUsername));

        Asset asset = assetRepository.findByAssetSymbolIgnoreCase(normalizedSymbol)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found: " + normalizedSymbol));

        List<Holding> existingUserAssetHoldings = holdingRepository
                .findByUserUserIdAndAssetAssetIdOrderByHoldingIdAsc(user.getUserId(), asset.getAssetId());
        if (existingUserAssetHoldings.isEmpty()) {
            throw new IllegalArgumentException("No holdings found for symbol: " + normalizedSymbol);
        }

        BigDecimal totalUnits = existingUserAssetHoldings.stream()
                .map(Holding::getUnits)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        boolean removeAll = Boolean.TRUE.equals(request.removeAll());
        BigDecimal unitsToRemove = request.units() == null ? BigDecimal.ZERO : request.units();

        if (!removeAll) {
            if (unitsToRemove.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("units must be greater than 0 when removeAll is false.");
            }
        } else {
            unitsToRemove = totalUnits;
        }

        if (unitsToRemove.compareTo(totalUnits) > 0) {
            throw new IllegalArgumentException("Cannot remove more units than currently held.");
        }

        Set<Integer> impactedPortfolioIds = new LinkedHashSet<>();
        for (Holding existing : existingUserAssetHoldings) {
            if (existing.getPortfolio() != null && existing.getPortfolio().getPortfolioId() != null) {
                impactedPortfolioIds.add(existing.getPortfolio().getPortfolioId());
            }
        }

        if (removeAll || unitsToRemove.compareTo(totalUnits) == 0) {
            holdingRepository.deleteAll(existingUserAssetHoldings);
            refreshPortfolioTotals(impactedPortfolioIds);
            return new HoldingUnitsUpdateResponse(
                    user.getUserId(),
                    user.getUsername(),
                    asset.getAssetSymbol(),
                    true,
                    unitsToRemove.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                    zeroMoney(),
                    "Removed all units for " + asset.getAssetSymbol()
            );
        }

        Holding primaryHolding = selectPrimaryHolding(existingUserAssetHoldings, null);
        BigDecimal remainingUnits = totalUnits.subtract(unitsToRemove).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        primaryHolding.setUnits(remainingUnits);
        if (primaryHolding.getPortfolioTotalValue() == null) {
            primaryHolding.setPortfolioTotalValue(zeroMoney());
        }
        holdingRepository.save(primaryHolding);

        Integer primaryHoldingId = primaryHolding.getHoldingId();
        List<Holding> duplicateHoldings = existingUserAssetHoldings.stream()
                .filter(item -> !item.getHoldingId().equals(primaryHoldingId))
                .toList();
        if (!duplicateHoldings.isEmpty()) {
            holdingRepository.deleteAll(duplicateHoldings);
        }

        refreshPortfolioTotals(impactedPortfolioIds);

        return new HoldingUnitsUpdateResponse(
                user.getUserId(),
                user.getUsername(),
                asset.getAssetSymbol(),
                false,
                unitsToRemove.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                remainingUnits,
                "Updated units for " + asset.getAssetSymbol()
        );
    }

    private void validateRequest(HoldingCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required.");
        }
        if (!StringUtils.hasText(request.username())) {
            throw new IllegalArgumentException("username is required.");
        }
        if (!StringUtils.hasText(request.symbol())) {
            throw new IllegalArgumentException("symbol is required.");
        }
        if (request.username().trim().length() > 50) {
            throw new IllegalArgumentException("username cannot exceed 50 characters.");
        }
        if (request.symbol().trim().length() > 10) {
            throw new IllegalArgumentException("symbol cannot exceed 10 characters.");
        }
        if (request.units() == null || request.units().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("units must be greater than 0.");
        }
        if (request.avgPurchasePrice() == null || request.avgPurchasePrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("avgPurchasePrice must be greater than 0.");
        }
        if (request.purchaseDate() != null && request.purchaseDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("purchaseDate cannot be in the future.");
        }
    }

    private LocalDate resolveOldestPurchaseDate(List<Holding> existingUserAssetHoldings, LocalDate requestedPurchaseDate) {
        LocalDate oldest = requestedPurchaseDate;
        for (Holding existing : existingUserAssetHoldings) {
            if (existing == null || existing.getPurchaseDate() == null) {
                continue;
            }
            LocalDate existingDate = existing.getPurchaseDate().toLocalDate();
            if (existingDate.isBefore(oldest)) {
                oldest = existingDate;
            }
        }
        return oldest;
    }

    private YahooQuote fetchQuote(String symbol) {
        List<YahooQuote> quotes = yahooFinanceClient.fetchQuotes(List.of(symbol));

        return quotes.stream()
                .filter(quote -> quote != null
                        && StringUtils.hasText(quote.symbol())
                        && symbol.equalsIgnoreCase(quote.symbol())
                        && quote.regularMarketPrice() != null
                        && StringUtils.hasText(quote.currency()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No quote data returned for symbol: " + symbol));
    }

    private Currency findOrCreateCurrency(String currencyCode) {
        if (currencyCode.length() > 3) {
            throw new IllegalStateException("Unsupported currency code returned by quote: " + currencyCode);
        }

        Currency currency = currencyRepository.findByCurrencyCodeIgnoreCase(currencyCode).orElse(null);
        if (currency != null) {
            return currency;
        }

        Currency created = new Currency();
        created.setCurrencyCode(currencyCode);
        created.setSymbol(currencyCode);
        created.setBaseUnit(Boolean.TRUE);
        return currencyRepository.save(created);
    }

    private Currency resolveTargetCurrency(UserAccount user, String requestedCurrencyCode) {
        if (StringUtils.hasText(requestedCurrencyCode)) {
            String normalizedCurrencyCode = normalizeCurrencyCode(requestedCurrencyCode, "currency");
            return findOrCreateCurrency(normalizedCurrencyCode);
        }

        if (user.getCurrency() == null || !StringUtils.hasText(user.getCurrency().getCurrencyCode())) {
            throw new IllegalStateException("User does not have a default currency configured.");
        }

        return user.getCurrency();
    }

    private String resolveHoldingCurrencyCode(Holding holding) {
        if (holding.getAsset() == null
                || holding.getAsset().getCurrency() == null
                || !StringUtils.hasText(holding.getAsset().getCurrency().getCurrencyCode())) {
            throw new IllegalStateException("Holding " + holding.getHoldingId() + " is missing asset currency.");
        }
        return holding.getAsset().getCurrency().getCurrencyCode().trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal resolveExchangeRate(String sourceCurrencyCode, String targetCurrencyCode) {
        if (sourceCurrencyCode.equalsIgnoreCase(targetCurrencyCode)) {
            return BigDecimal.ONE;
        }

        BigDecimal sourceToUsd = resolveUsdAnchoredRate(sourceCurrencyCode);
        BigDecimal targetToUsd = resolveUsdAnchoredRate(targetCurrencyCode);

        return sourceToUsd.divide(targetToUsd, RATE_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveUsdAnchoredRate(String sourceCurrencyCode) {
        if (USD_CURRENCY_CODE.equalsIgnoreCase(sourceCurrencyCode)) {
            return BigDecimal.ONE;
        }

        ExchangeRate storedRate = exchangeRateRepository
                .findTopByStartCurrencyCurrencyCodeIgnoreCaseAndEndCurrencyCurrencyCodeIgnoreCaseOrderByLastUpdatedDesc(
                        sourceCurrencyCode,
                        USD_CURRENCY_CODE
                )
                .orElse(null);
        if (storedRate != null && isValidRate(storedRate.getRate())) {
            return storedRate.getRate();
        }

        BigDecimal fetchedRate = fetchAndStoreUsdAnchoredRate(sourceCurrencyCode);
        if (isValidRate(fetchedRate)) {
            return fetchedRate;
        }

        throw new IllegalArgumentException("Missing exchange rate from " + sourceCurrencyCode + " to USD.");
    }

    private BigDecimal fetchAndStoreUsdAnchoredRate(String sourceCurrencyCode) {
        if (USD_CURRENCY_CODE.equalsIgnoreCase(sourceCurrencyCode)) {
            return BigDecimal.ONE;
        }

        String directPairSymbol = sourceCurrencyCode + USD_CURRENCY_CODE + "=X";
        BigDecimal latestRate = fetchFxPairRate(directPairSymbol);
        if (!isValidRate(latestRate)) {
            String inversePairSymbol = USD_CURRENCY_CODE + sourceCurrencyCode + "=X";
            BigDecimal inverseRate = fetchFxPairRate(inversePairSymbol);
            if (isValidRate(inverseRate)) {
                latestRate = BigDecimal.ONE.divide(inverseRate, RATE_SCALE, RoundingMode.HALF_UP);
            }
        }

        if (!isValidRate(latestRate)) {
            return null;
        }

        Currency sourceCurrency = findOrCreateCurrency(sourceCurrencyCode);
        Currency usdCurrency = findOrCreateCurrency(USD_CURRENCY_CODE);

        ExchangeRate exchangeRate = exchangeRateRepository
                .findTopByStartCurrencyCurrencyIdAndEndCurrencyCurrencyIdOrderByLastUpdatedDesc(
                        sourceCurrency.getCurrencyId(),
                        usdCurrency.getCurrencyId()
                )
                .orElseGet(ExchangeRate::new);

        exchangeRate.setStartCurrency(sourceCurrency);
        exchangeRate.setEndCurrency(usdCurrency);
        exchangeRate.setRate(latestRate);
        exchangeRateRepository.save(exchangeRate);
        return latestRate;
    }

    private BigDecimal fetchFxPairRate(String pairSymbol) {
        List<YahooQuote> quotes = yahooFinanceClient.fetchQuotes(List.of(pairSymbol));
        if (quotes == null || quotes.isEmpty()) {
            return null;
        }

        return quotes.stream()
                .filter(quote -> quote != null
                        && StringUtils.hasText(quote.symbol())
                        && pairSymbol.equalsIgnoreCase(quote.symbol())
                        && isValidRate(quote.regularMarketPrice()))
                .map(YahooQuote::regularMarketPrice)
                .findFirst()
                .orElse(null);
    }

    private BigDecimal resolveCurrentPriceSource(Holding holding, YahooQuote quote) {
        if (quote != null && isValidRate(quote.regularMarketPrice())) {
            return quote.regularMarketPrice();
        }
        if (isValidRate(holding.getLastPrice())) {
            return holding.getLastPrice();
        }
        if (holding.getAsset() != null && isValidRate(holding.getAsset().getClosePrice())) {
            return holding.getAsset().getClosePrice();
        }

        String symbol = holding.getAsset() == null ? "<unknown>" : holding.getAsset().getAssetSymbol();
        throw new IllegalStateException("Missing current market price for symbol: " + symbol);
    }

    private BigDecimal resolvePreviousCloseSource(BigDecimal currentPriceSource, YahooQuote quote) {
        if (quote != null && isValidRate(quote.regularMarketPreviousClose())) {
            return quote.regularMarketPreviousClose();
        }
        return currentPriceSource;
    }

    private BigDecimal resolveLatestQuotePrice(YahooQuote quote) {
        if (quote == null) {
            return null;
        }
        if (isValidRate(quote.regularMarketPrice())) {
            return quote.regularMarketPrice();
        }
        if (isValidRate(quote.regularMarketPreviousClose())) {
            return quote.regularMarketPreviousClose();
        }
        return null;
    }

    private BigDecimal calculatePercentChange(BigDecimal change, BigDecimal base) {
        if (base == null || base.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
        }

        return change
                .divide(base, PERCENT_SCALE + 6, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    private String resolveTrend(BigDecimal change) {
        if (change == null || change.compareTo(BigDecimal.ZERO) == 0) {
            return TREND_FLAT;
        }
        if (change.compareTo(BigDecimal.ZERO) > 0) {
            return TREND_UP;
        }
        return TREND_DOWN;
    }

    private boolean isValidRate(BigDecimal rate) {
        return rate != null && rate.compareTo(BigDecimal.ZERO) > 0;
    }

    private BigDecimal convertAmount(BigDecimal amount, BigDecimal exchangeRate) {
        return amount.multiply(exchangeRate).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal zeroMoney() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private String normalizeUsername(String username) {
        if (!StringUtils.hasText(username)) {
            throw new IllegalArgumentException("username is required.");
        }

        String normalizedUsername = username.trim();
        if (normalizedUsername.length() > 50) {
            throw new IllegalArgumentException("username cannot exceed 50 characters.");
        }
        return normalizedUsername;
    }

    private String normalizeCurrencyCode(String currencyCode, String fieldName) {
        if (!StringUtils.hasText(currencyCode)) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }

        String normalizedCurrencyCode = currencyCode.trim().toUpperCase(Locale.ROOT);
        if (normalizedCurrencyCode.length() != 3
                || !normalizedCurrencyCode.chars().allMatch(Character::isLetter)) {
            throw new IllegalArgumentException(fieldName + " must be a 3-letter currency code.");
        }
        return normalizedCurrencyCode;
    }

    private String normalizeSymbol(String symbol) {
        if (!StringUtils.hasText(symbol)) {
            throw new IllegalArgumentException("symbol is required.");
        }

        String normalizedSymbol = symbol.trim().toUpperCase(Locale.ROOT);
        if (normalizedSymbol.length() > 10) {
            throw new IllegalArgumentException("symbol cannot exceed 10 characters.");
        }
        return normalizedSymbol;
    }

    private LocalDate resolveHoldingStartDate(Holding holding) {
        if (holding != null && holding.getPurchaseDate() != null) {
            return holding.getPurchaseDate().toLocalDate();
        }
        return LocalDate.now().minusMonths(3);
    }

    private String resolveEffectiveInterval(String requestedInterval, LocalDate fromDate, LocalDate toDate) {
        if (StringUtils.hasText(requestedInterval)) {
            return normalizeHistoryInterval(requestedInterval);
        }

        long ageDays = Math.max(1, ChronoUnit.DAYS.between(fromDate, toDate));
        if (ageDays <= DAILY_INTERVAL_MAX_DAYS) {
            return INTERVAL_DAILY;
        }
        if (ageDays <= WEEKLY_INTERVAL_MAX_DAYS) {
            return INTERVAL_WEEKLY;
        }
        return INTERVAL_MONTHLY;
    }

    private String normalizeHistoryInterval(String interval) {
        if (!StringUtils.hasText(interval)) {
            throw new IllegalArgumentException("interval is required.");
        }

        String normalized = interval.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "1d", "1day", "day", "daily" -> INTERVAL_DAILY;
            case "1wk", "1w", "week", "weekly" -> INTERVAL_WEEKLY;
            case "1mo", "1m", "month", "monthly" -> INTERVAL_MONTHLY;
            default -> throw new IllegalArgumentException("Unsupported interval: " + interval);
        };
    }

    private Asset findOrCreateAsset(String symbol, YahooQuote quote, Currency currency) {
        Asset asset = assetRepository.findByAssetSymbolIgnoreCase(symbol).orElse(null);

        String assetName = resolveAssetName(quote);
        BigDecimal openPrice = quote.regularMarketOpen();
        BigDecimal closePrice = resolveClosePrice(quote);
        String stockExchange = normalizeNullableText(quote.stockExchange());

        if (asset == null) {
            Asset created = new Asset();
            created.setAssetSymbol(limitLength(symbol, 10));
            created.setAssetName(limitLength(assetName, 50));
            created.setCurrency(currency);
            created.setOpenPrice(openPrice);
            created.setClosePrice(closePrice);
            created.setStockExchange(limitLength(stockExchange, 50));
            return assetRepository.save(created);
        }

        boolean changed = false;
        if (!assetName.equals(asset.getAssetName())) {
            asset.setAssetName(limitLength(assetName, 50));
            changed = true;
        }

        if (asset.getCurrency() == null
                || !currency.getCurrencyCode().equalsIgnoreCase(asset.getCurrency().getCurrencyCode())) {
            asset.setCurrency(currency);
            changed = true;
        }
        if (openPrice != null && !equalsDecimal(asset.getOpenPrice(), openPrice)) {
            asset.setOpenPrice(openPrice);
            changed = true;
        }
        if (closePrice != null && !equalsDecimal(asset.getClosePrice(), closePrice)) {
            asset.setClosePrice(closePrice);
            changed = true;
        }
        String normalizedExchange = normalizeNullableText(stockExchange);
        if (StringUtils.hasText(normalizedExchange) && !equalsText(asset.getStockExchange(), normalizedExchange)) {
            asset.setStockExchange(limitLength(normalizedExchange, 50));
            changed = true;
        }

        if (changed) {
            asset = assetRepository.save(asset);
        }

        return asset;
    }

    private BigDecimal resolveClosePrice(YahooQuote quote) {
        if (quote.regularMarketPrice() != null) {
            return quote.regularMarketPrice();
        }
        return quote.regularMarketPreviousClose();
    }

    private Holding selectPrimaryHolding(List<Holding> holdings, Integer requestedPortfolioId) {
        if (requestedPortfolioId != null) {
            for (Holding holding : holdings) {
                if (holding.getPortfolio() != null
                        && requestedPortfolioId.equals(holding.getPortfolio().getPortfolioId())) {
                    return holding;
                }
            }
        }
        return holdings.get(0);
    }

    private void refreshPortfolioTotals(Set<Integer> portfolioIds) {
        for (Integer portfolioId : portfolioIds) {
            if (portfolioId == null) {
                continue;
            }

            List<Holding> portfolioHoldings = holdingRepository.findByPortfolioPortfolioIdOrderByHoldingIdAsc(portfolioId);
            if (portfolioHoldings.isEmpty()) {
                continue;
            }

            BigDecimal totalMarketValue = portfolioHoldings.stream()
                    .map(holding -> holding.getLastPrice().multiply(holding.getUnits()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

            for (Holding holding : portfolioHoldings) {
                holding.setPortfolioTotalValue(totalMarketValue);
            }
            holdingRepository.saveAll(portfolioHoldings);
        }
    }

    private UserAccount findOrCreateUser(String username, Currency currency) {
        UserAccount user = userAccountRepository.findByUsernameIgnoreCase(username).orElse(null);
        if (user != null) {
            return user;
        }

        UserAccount created = new UserAccount();
        created.setUsername(username);
        created.setCurrency(currency);
        return userAccountRepository.save(created);
    }

    private Portfolio findOrCreatePortfolio(UserAccount user, String portfolioName) {
        Portfolio portfolio = portfolioRepository
                .findByUserUserIdAndPortfolioNameIgnoreCase(user.getUserId(), portfolioName)
                .orElse(null);
        if (portfolio != null) {
            return portfolio;
        }

        Portfolio created = new Portfolio();
        created.setUser(user);
        created.setPortfolioName(limitLength(portfolioName, 50));
        return portfolioRepository.save(created);
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

    private String limitLength(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String normalizeNullableText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private boolean equalsDecimal(BigDecimal left, BigDecimal right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.compareTo(right) == 0;
    }

    private boolean equalsText(String left, String right) {
        if (!StringUtils.hasText(left) && !StringUtils.hasText(right)) {
            return true;
        }
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return false;
        }
        return left.trim().equalsIgnoreCase(right.trim());
    }

    private record HoldingHistorySeries(
            LocalDate startDate,
            BigDecimal units,
            NavigableMap<LocalDate, BigDecimal> closePriceTargetByDate
    ) {
    }

    public record HoldingLastPriceRefreshSummary(
            int totalHoldings,
            int symbolsRequested,
            int quotesReturned,
            int holdingsUpdated,
            int holdingsMissingQuote
    ) {
    }
}
