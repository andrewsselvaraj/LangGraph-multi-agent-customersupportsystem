package com.customersupport.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "support.llm")
public record LlmProperties(String apiKey, String model, Integer maxTokens) {

    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}
