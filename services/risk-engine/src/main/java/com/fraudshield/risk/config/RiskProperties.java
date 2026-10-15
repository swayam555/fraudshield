package com.fraudshield.risk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tunables for the risk engine.
 *
 * @param reviewThreshold probability above which a payment is held for step-up authentication
 * @param blockThreshold  probability above which a payment is declined
 * @param microAmount     payments at or below this many rupees count as "probing" payments
 * @param muleAlertScore  mule score at which an alert is raised and the account is watch-listed
 */
@ConfigurationProperties(prefix = "risk")
public record RiskProperties(
        @DefaultValue("0.5") double reviewThreshold,
        @DefaultValue("0.85") double blockThreshold,
        @DefaultValue("10") double microAmount,
        @DefaultValue("0.7") double muleAlertScore) {
}
