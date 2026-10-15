package com.fraudshield.risk.config;

import com.fraudshield.contracts.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Every service declares every topic it produces to or consumes from, with the same partition
 * counts. Creation is idempotent, and broker auto-creation is disabled, so a consumer that starts
 * first can never trigger a 1-partition topic that the producer later widens (which would leave
 * the consumer blind to the new partitions until its next metadata refresh).
 */
@Configuration
public class KafkaTopicsConfig {

    /** Keyed by payer VPA: per-payer ordering, six-way consumer parallelism. */
    @Bean
    NewTopic paymentsScored() {
        return TopicBuilder.name(Topics.PAYMENTS_SCORED).partitions(6).replicas(1).build();
    }

    @Bean
    NewTopic riskAlerts() {
        return TopicBuilder.name(Topics.RISK_ALERTS).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic caseLabels() {
        return TopicBuilder.name(Topics.CASE_LABELS).partitions(3).replicas(1).build();
    }
}
