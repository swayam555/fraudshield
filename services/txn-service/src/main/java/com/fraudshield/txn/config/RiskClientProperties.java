package com.fraudshield.txn.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * @param baseUrl              risk-engine base URL
 * @param timeout              hard budget for the synchronous risk call
 * @param failOpenLimit        when the risk engine is down, payments up to this amount are approved
 *                             (fail-open) and larger ones are held for review (fail-closed)
 */
@ConfigurationProperties(prefix = "risk-client")
public record RiskClientProperties(
        @DefaultValue("http://localhost:8082") String baseUrl,
        @DefaultValue("300ms") Duration timeout,
        @DefaultValue("2000") BigDecimal failOpenLimit) {
}
