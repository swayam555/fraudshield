package com.fraudshield.cases.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "fraudshield")
public record AppProperties(Jwt jwt, Ai ai, @DefaultValue("http://localhost:8082") String riskEngineUrl) {

    public record Jwt(String secret, @DefaultValue("8h") Duration ttl) {
    }

    /**
     * Chat model used by the copilot.
     *
     * @param provider {@code none} (rule-based fallback), {@code openai} (any OpenAI-compatible API:
     *                 OpenAI, Groq, OpenRouter, Together, LM Studio...), {@code anthropic} or {@code ollama}
     */
    public record Ai(@DefaultValue("none") String provider,
                     String apiKey,
                     String baseUrl,
                     String model,
                     @DefaultValue("0.2") double temperature,
                     @DefaultValue("60s") Duration timeout) {
    }
}
