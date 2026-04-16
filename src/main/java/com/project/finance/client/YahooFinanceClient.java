package com.project.finance.client;

import com.project.finance.config.YahooFinanceProperties;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
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
        if (useRapidApiMode()) {
            return fetchQuotesViaRapidApi(symbols);
        }
        return fetchQuotesViaPublicYahoo(symbols);
    }

    private List<YahooQuote> fetchQuotesViaRapidApi(Collection<String> symbols) {
        String symbolParam = symbols.stream().collect(Collectors.joining(","));
        validateRapidApiConfiguration();

        try {
            YahooQuoteApiResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path(properties.getQuotePath())
                            .queryParam("symbols", symbolParam)
                            .queryParamIfPresent("region", toOptionalParam(properties.getRegion()))
                            .build())
                    .headers(headers -> {
                        headers.set(HttpHeaders.ACCEPT, "application/json");
                        if (StringUtils.hasText(properties.getUserAgent())) {
                            headers.set(HttpHeaders.USER_AGENT, properties.getUserAgent());
                        }
                        if (StringUtils.hasText(properties.getRapidApiKey())) {
                            headers.set("x-rapidapi-key", properties.getRapidApiKey());
                        }
                        if (StringUtils.hasText(properties.getRapidApiHost())) {
                            headers.set("x-rapidapi-host", properties.getRapidApiHost());
                        }
                    })
                    .retrieve()
                    .body(YahooQuoteApiResponse.class);

            if (response == null || response.quoteResponse() == null || response.quoteResponse().result() == null) {
                return List.of();
            }

            return response.quoteResponse().result();
        } catch (RestClientResponseException ex) {
            throw new IllegalStateException(
                    "Yahoo Finance RapidAPI request failed with status "
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
            throw new IllegalStateException("Failed to fetch quotes from Yahoo Finance: " + ex.getMessage(), ex);
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
                    meta.regularMarketPrice()
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

    private java.util.Optional<String> toOptionalParam(String value) {
        if (!StringUtils.hasText(value)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(value);
    }

    private void validateRapidApiConfiguration() {
        if (!StringUtils.hasText(properties.getRapidApiKey()) || !StringUtils.hasText(properties.getRapidApiHost())) {
            throw new IllegalStateException(
                    "RapidAPI mode requires YAHOO_FINANCE_API_KEY and YAHOO_FINANCE_API_HOST."
            );
        }
    }

    private boolean useRapidApiMode() {
        return properties.getBaseUrl().contains("rapidapi.com")
                || StringUtils.hasText(properties.getRapidApiKey())
                || StringUtils.hasText(properties.getRapidApiHost());
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
            BigDecimal regularMarketPrice
    ) {
    }

    private record YahooQuoteApiResponse(QuoteResponse quoteResponse) {
    }

    private record QuoteResponse(List<YahooQuote> result, Object error) {
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
            BigDecimal regularMarketPrice
    ) {
    }
}
