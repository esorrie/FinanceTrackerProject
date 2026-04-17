package com.project.finance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "yahoo.finance")
public class YahooFinanceProperties {

    private String baseUrl = "https://query2.finance.yahoo.com";
    private String quotePath = "/v8/finance/chart";
    private String screenerPath = "/v1/finance/screener/predefined/saved";
    private String region = "US";
    private String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getQuotePath() {
        return quotePath;
    }

    public void setQuotePath(String quotePath) {
        this.quotePath = quotePath;
    }

    public String getScreenerPath() {
        return screenerPath;
    }

    public void setScreenerPath(String screenerPath) {
        this.screenerPath = screenerPath;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
}
