package com.project.finance.client;

import com.project.finance.config.YahooFinanceProperties;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
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
