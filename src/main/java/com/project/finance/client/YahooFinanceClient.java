package com.project.finance.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.project.finance.config.YahooFinanceProperties;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

@Component
public class YahooFinanceClient {

    private final RestClient restClient;
    private final YahooFinanceProperties properties;

    public YahooFinanceClient(RestClient yahooFinanceRestClient, YahooFinanceProperties properties) {
        this.restClient = yahooFinanceRestClient;
        this.properties = properties;
    }

    public List<YahooQuote> fetchQuotes(Collection<String> symbols) {
        return fetchQuotesViaPublicYahoo(symbols);
    }

    public ScreenerPage fetchScreenerPage(String screenerId, int start, int count) {
        if (!StringUtils.hasText(screenerId)) {
            throw new IllegalArgumentException("screenerId is required.");
        }
        if (start < 0) {
            throw new IllegalArgumentException("start must be zero or greater.");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("count must be greater than zero.");
        }

        try {
            JsonNode response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path(properties.getScreenerPath())
                            .queryParam("formatted", "false")
                            .queryParam("scrIds", screenerId.trim())
                            .queryParam("count", count)
                            .queryParam("start", start)
                            .queryParamIfPresent("region", toOptionalParam(properties.getRegion()))
                            .build())
                    .headers(headers -> {
                        headers.set(HttpHeaders.ACCEPT, "application/json");
                        if (StringUtils.hasText(properties.getUserAgent())) {
                            headers.set(HttpHeaders.USER_AGENT, properties.getUserAgent());
                        }
                    })
                    .retrieve()
                    .body(JsonNode.class);

            return mapScreenerPage(response);
        } catch (RestClientResponseException ex) {
            throw new IllegalStateException(
                    "Yahoo screener request failed for screenerId "
                            + screenerId
                            + " with status "
                            + ex.getStatusCode().value()
                            + " ("
                            + ex.getStatusText()
                            + "). Body: "
                            + summarizeBody(ex.getResponseBodyAsString()),
                    ex
            );
        } catch (ResourceAccessException ex) {
            throw new IllegalStateException("Could not reach Yahoo Finance endpoint. Check internet/proxy/firewall.", ex);
        } catch (RestClientException ex) {
            throw new IllegalStateException(
                    "Failed to fetch Yahoo screener data for "
                            + screenerId
                            + " (start="
                            + start
                            + ", count="
                            + count
                            + "): "
                            + ex.getMessage(),
                    ex
            );
        }
    }

    private List<YahooQuote> fetchQuotesViaPublicYahoo(Collection<String> symbols) {
        return symbols.stream()
                .map(this::fetchSingleSymbolViaPublicYahoo)
                .filter(Objects::nonNull)
                .toList();
    }

    private YahooQuote fetchSingleSymbolViaPublicYahoo(String symbol) {
        try {
            YahooChartApiResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path(properties.getQuotePath())
                            .path("/" + symbol)
                            .queryParam("interval", "1d")
                            .queryParam("range", "1d")
                            .queryParamIfPresent("region", toOptionalParam(properties.getRegion()))
                            .build())
                    .headers(headers -> {
                        headers.set(HttpHeaders.ACCEPT, "application/json");
                        if (StringUtils.hasText(properties.getUserAgent())) {
                            headers.set(HttpHeaders.USER_AGENT, properties.getUserAgent());
                        }
                    })
                    .retrieve()
                    .body(YahooChartApiResponse.class);

            if (response == null
                    || response.chart() == null
                    || response.chart().result() == null
                    || response.chart().result().isEmpty()
                    || response.chart().result().get(0) == null
                    || response.chart().result().get(0).meta() == null) {
                return null;
            }

            ChartMeta meta = response.chart().result().get(0).meta();
            return new YahooQuote(
                    meta.symbol(),
                    meta.shortName(),
                    meta.longName(),
                    meta.currency(),
                    meta.regularMarketPrice(),
                    meta.regularMarketOpen(),
                    meta.regularMarketPreviousClose(),
                    resolveStockExchange(meta.fullExchangeName(), meta.exchangeName(), meta.exchange())
            );
        } catch (RestClientResponseException ex) {
            throw new IllegalStateException(
                    "Yahoo Finance public request failed for symbol "
                            + symbol
                            + " with status "
                            + ex.getStatusCode().value()
                            + " ("
                            + ex.getStatusText()
                            + "). Body: "
                            + summarizeBody(ex.getResponseBodyAsString()),
                    ex
            );
        } catch (ResourceAccessException ex) {
            throw new IllegalStateException("Could not reach Yahoo Finance endpoint. Check internet/proxy/firewall.", ex);
        } catch (RestClientException ex) {
            throw new IllegalStateException("Failed to fetch Yahoo quote for " + symbol + ": " + ex.getMessage(), ex);
        }
    }

    private ScreenerPage mapScreenerPage(JsonNode response) {
        JsonNode resultsNode = response == null
                ? null
                : response.path("finance").path("result");
        if (resultsNode == null || !resultsNode.isArray() || resultsNode.isEmpty()) {
            return new ScreenerPage(List.of(), null);
        }

        JsonNode resultNode = resultsNode.get(0);
        Integer totalAvailable = extractTotalAvailable(resultNode);
        JsonNode quotesNode = resultNode.path("quotes");
        if (!quotesNode.isArray() || quotesNode.isEmpty()) {
            return new ScreenerPage(List.of(), totalAvailable);
        }

        List<YahooQuote> quotes = new ArrayList<>();
        for (JsonNode quoteNode : quotesNode) {
            if (quoteNode == null || quoteNode.isNull()) {
                continue;
            }

            String symbol = readTextField(quoteNode, "symbol");
            if (!StringUtils.hasText(symbol)) {
                continue;
            }

            quotes.add(new YahooQuote(
                    symbol,
                    readTextField(quoteNode, "shortName"),
                    readTextField(quoteNode, "longName"),
                    readTextField(quoteNode, "currency"),
                    readDecimalField(quoteNode, "regularMarketPrice"),
                    readDecimalField(quoteNode, "regularMarketOpen"),
                    readDecimalField(quoteNode, "regularMarketPreviousClose"),
                    resolveStockExchange(
                            readTextField(quoteNode, "fullExchangeName"),
                            readTextField(quoteNode, "exchangeName"),
                            readTextField(quoteNode, "exchange")
                    )
            ));
        }

        return new ScreenerPage(List.copyOf(quotes), totalAvailable);
    }

    private Integer extractTotalAvailable(JsonNode resultNode) {
        if (resultNode == null || resultNode.isNull()) {
            return null;
        }

        Integer total = readIntegerField(resultNode, "total");
        if (total != null) {
            return total;
        }

        Integer criteriaSize = readIntegerField(resultNode.path("criteriaMeta"), "size");
        if (criteriaSize != null) {
            return criteriaSize;
        }

        return readIntegerField(resultNode, "count");
    }

    private Integer readIntegerField(JsonNode parentNode, String fieldName) {
        if (parentNode == null || parentNode.isNull()) {
            return null;
        }
        return readIntegerValue(parentNode.path(fieldName));
    }

    private Integer readIntegerValue(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isInt() || node.isLong() || node.isIntegralNumber()) {
            return node.intValue();
        }
        if (node.isTextual()) {
            String value = node.asText().trim();
            if (!StringUtils.hasText(value)) {
                return null;
            }
            try {
                return Integer.valueOf(value);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return readIntegerValue(node.path("raw"));
    }

    private BigDecimal readDecimalField(JsonNode parentNode, String fieldName) {
        if (parentNode == null || parentNode.isNull()) {
            return null;
        }
        return readDecimalValue(parentNode.path(fieldName));
    }

    private BigDecimal readDecimalValue(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isTextual()) {
            String value = node.asText().trim();
            if (!StringUtils.hasText(value)) {
                return null;
            }
            try {
                return new BigDecimal(value);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return readDecimalValue(node.path("raw"));
    }

    private String readTextField(JsonNode parentNode, String fieldName) {
        if (parentNode == null || parentNode.isNull()) {
            return null;
        }
        return readTextValue(parentNode.path(fieldName));
    }

    private String readTextValue(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isTextual()) {
            String value = node.asText().trim();
            return StringUtils.hasText(value) ? value : null;
        }
        if (node.isNumber() || node.isBoolean()) {
            return node.asText();
        }

        String formatted = readTextValue(node.path("fmt"));
        if (StringUtils.hasText(formatted)) {
            return formatted;
        }
        return readTextValue(node.path("raw"));
    }

    private Optional<String> toOptionalParam(String value) {
        if (!StringUtils.hasText(value)) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    private String resolveStockExchange(String fullExchangeName, String exchangeName, String exchangeCode) {
        if (StringUtils.hasText(fullExchangeName)) {
            return fullExchangeName.trim();
        }
        if (StringUtils.hasText(exchangeName)) {
            return exchangeName.trim();
        }
        if (StringUtils.hasText(exchangeCode)) {
            return exchangeCode.trim();
        }
        return null;
    }

    private String summarizeBody(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return "<empty>";
        }

        String compact = responseBody.replaceAll("\\s+", " ").trim();
        int maxLength = 240;
        if (compact.length() <= maxLength) {
            return compact;
        }
        return compact.substring(0, maxLength) + "...";
    }

    public record YahooQuote(
            String symbol,
            String shortName,
            String longName,
            String currency,
            BigDecimal regularMarketPrice,
            BigDecimal regularMarketOpen,
            BigDecimal regularMarketPreviousClose,
            String stockExchange
    ) {
    }

    public record ScreenerPage(
            List<YahooQuote> quotes,
            Integer totalAvailable
    ) {
    }

    private record YahooChartApiResponse(Chart chart) {
    }

    private record Chart(List<ChartResult> result, Object error) {
    }

    private record ChartResult(ChartMeta meta) {
    }

    private record ChartMeta(
            String symbol,
            String shortName,
            String longName,
            String currency,
            BigDecimal regularMarketPrice,
            BigDecimal regularMarketOpen,
            BigDecimal regularMarketPreviousClose,
            String fullExchangeName,
            String exchangeName,
            String exchange
    ) {
    }
}
