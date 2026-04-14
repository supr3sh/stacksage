package com.stacksage.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "app.ai")
@Getter
@Setter
public class AIProviderConfig {

    private String apiKey = "";
    private String model = "meta-llama/llama-3.3-70b-instruct:free";
    private int maxTokens = 1024;
    private double temperature = 0.3;
    private String baseUrl = "https://openrouter.ai/api";
    private int connectTimeoutSeconds = 5;
    private int readTimeoutSeconds = 30;
    private String referer = "";
    private String appName = "StackSage";

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Bean
    public RestClient aiRestClient() {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .withReadTimeout(Duration.ofSeconds(readTimeoutSeconds));

        RestClient.Builder builder = RestClient.builder()
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        if (isConfigured()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        }

        if (referer != null && !referer.isBlank()) {
            builder.defaultHeader("HTTP-Referer", referer);
        }
        if (appName != null && !appName.isBlank()) {
            builder.defaultHeader("X-Title", appName);
        }

        return builder.build();
    }
}
