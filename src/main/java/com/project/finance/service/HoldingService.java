package com.project.finance.service;

import com.project.finance.client.YahooFinanceClient;
import com.project.finance.client.YahooFinanceClient.YahooQuote;
import com.project.finance.dto.HoldingCurrencyViewResponse;
import com.project.finance.dto.HoldingCreateRequest;
import com.project.finance.dto.HoldingCreateResponse;
import com.project.finance.dto.HoldingsInCurrencyResponse;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class HoldingService {

    private static final String DEFAULT_PORTFOLIO = "Portfolio";
    private static final String USD_CURRENCY_CODE = "USD";
    private static final int MONEY_SCALE = 4;
    private static final int RATE_SCALE = 8;

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
        if (!equalsDecimal(asset.getOpenPrice(), openPrice)) {
            asset.setOpenPrice(openPrice);
            changed = true;
        }
        if (!equalsDecimal(asset.getClosePrice(), closePrice)) {
            asset.setClosePrice(closePrice);
            changed = true;
        }
        String normalizedExchange = normalizeNullableText(stockExchange);
        if (!equalsText(asset.getStockExchange(), normalizedExchange)) {
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
}
