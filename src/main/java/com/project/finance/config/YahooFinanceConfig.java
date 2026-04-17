package com.project.finance.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(YahooFinanceProperties.class)
public class YahooFinanceConfig {

    @Bean
    public RestClient yahooFinanceRestClient(
            RestClient.Builder restClientBuilder,
            YahooFinanceProperties yahooFinanceProperties
    ) {
        return restClientBuilder
                .baseUrl(yahooFinanceProperties.getBaseUrl())
                .build();
    }
}
