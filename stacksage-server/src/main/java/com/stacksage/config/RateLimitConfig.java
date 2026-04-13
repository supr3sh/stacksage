package com.stacksage.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.rate-limit")
@Getter
@Setter
public class RateLimitConfig {

    private boolean enabled = true;
    private int maxRequests = 20;
    private int windowSeconds = 60;
    private boolean trustProxy = false;
}
