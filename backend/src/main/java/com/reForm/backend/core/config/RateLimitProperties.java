package com.reForm.backend.core.config;


import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "app.rate-limiting")
@Getter
@Setter
public class RateLimitProperties {
    private Map<String, Rule> limits = new HashMap<>();

    @Getter
    @Setter
    public static class Rule {
        private int capacity;
        private int refillTokens;
        private long refillSeconds;
    }

}
