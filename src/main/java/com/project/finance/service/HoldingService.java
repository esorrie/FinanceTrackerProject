package com.project.finance.service;

import com.project.finance.client.YahooFinanceClient;
import com.project.finance.client.YahooFinanceClient.YahooQuote;
import com.project.finance.dto.HoldingCreateRequest;
import com.project.finance.dto.HoldingCreateResponse;
import com.project.finance.entity.Asset;
import com.project.finance.entity.AssetHistory;
import com.project.finance.entity.Currency;
import com.project.finance.entity.Holding;
import com.project.finance.entity.Portfolio;
import com.project.finance.entity.UserAccount;
import com.project.finance.repository.AssetHistoryRepository;
import com.project.finance.repository.AssetRepository;
import com.project.finance.repository.CurrencyRepository;
import com.project.finance.repository.HoldingRepository;
import com.project.finance.repository.PortfolioRepository;
import com.project.finance.repository.UserAccountRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class HoldingService {

    private static final String DEFAULT_PORTFOLIO = "Portfolio";

    private final YahooFinanceClient yahooFinanceClient;
    private final CurrencyRepository currencyRepository;
    private final AssetRepository assetRepository;
    private final AssetHistoryRepository assetHistoryRepository;
    private final UserAccountRepository userAccountRepository;
    private final PortfolioRepository portfolioRepository;
    private final HoldingRepository holdingRepository;

    public HoldingService(
            YahooFinanceClient yahooFinanceClient,
            CurrencyRepository currencyRepository,
            AssetRepository assetRepository,
            AssetHistoryRepository assetHistoryRepository,
            UserAccountRepository userAccountRepository,
            PortfolioRepository portfolioRepository,
            HoldingRepository holdingRepository
    ) {
        this.yahooFinanceClient = yahooFinanceClient;
        this.currencyRepository = currencyRepository;
        this.assetRepository = assetRepository;
        this.assetHistoryRepository = assetHistoryRepository;
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
        saveAssetHistory(asset, currency, quote.regularMarketPrice());

        UserAccount user = findOrCreateUser(username, currency);
        Portfolio portfolio = findOrCreatePortfolio(user, portfolioName);

        Holding holding = new Holding();
        holding.setUser(user);
        holding.setPortfolio(portfolio);
        holding.setAsset(asset);
        holding.setUnits(request.units());
        holding.setAvgPurchasePrice(request.avgPurchasePrice());
        holding.setLastPrice(quote.regularMarketPrice());
        holding = holdingRepository.save(holding);

        BigDecimal investedAmount = request.avgPurchasePrice().multiply(request.units());
        BigDecimal marketValue = quote.regularMarketPrice().multiply(request.units());
        BigDecimal unrealizedPnl = marketValue.subtract(investedAmount);

        return new HoldingCreateResponse(
                holding.getHoldingId(),
                user.getUserId(),
                portfolio.getPortfolioId(),
                user.getUsername(),
                portfolio.getPortfolioName(),
                asset.getAssetSymbol(),
                asset.getAssetName(),
                currency.getCurrencyCode(),
                holding.getUnits(),
                holding.getAvgPurchasePrice(),
                holding.getLastPrice(),
                investedAmount,
                marketValue,
                unrealizedPnl
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

    private Asset findOrCreateAsset(String symbol, YahooQuote quote, Currency currency) {
        Asset asset = assetRepository.findByAssetSymbolIgnoreCase(symbol).orElse(null);

        String assetName = resolveAssetName(quote);
        if (asset == null) {
            Asset created = new Asset();
            created.setAssetSymbol(limitLength(symbol, 10));
            created.setAssetName(limitLength(assetName, 50));
            created.setCurrency(currency);
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

        if (changed) {
            asset = assetRepository.save(asset);
        }

        return asset;
    }

    private void saveAssetHistory(Asset asset, Currency currency, BigDecimal price) {
        AssetHistory history = new AssetHistory();
        history.setAsset(asset);
        history.setCurrency(currency);
        history.setPrice(price);
        assetHistoryRepository.save(history);
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
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
