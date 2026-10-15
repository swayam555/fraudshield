package com.fraudshield.txn.risk;

import com.fraudshield.contracts.Decision;
import com.fraudshield.contracts.Reason;
import com.fraudshield.contracts.ScoreRequest;
import com.fraudshield.contracts.ScoreResponse;
import com.fraudshield.txn.config.RiskClientProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

/**
 * Calls the risk engine inside a strict latency budget. If the engine is slow or down, the
 * circuit opens and payments are decided by a conservative fallback policy instead of queueing
 * up behind a failing dependency: small payments fail open (approved), large ones fail closed
 * (held for review). The decision is marked {@code degraded} so it can be re-scored later.
 */
@Component
public class RiskClient {

    private static final Logger log = LoggerFactory.getLogger(RiskClient.class);

    private final RestClient rest;
    private final CircuitBreaker breaker;
    private final RiskClientProperties props;
    private final MeterRegistry meters;

    public RiskClient(RestClient.Builder builder, Resilience4JCircuitBreakerFactory factory,
                      RiskClientProperties props, MeterRegistry meters) {
        this.props = props;
        this.meters = meters;
        HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofMillis(500)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(http);
        requestFactory.setReadTimeout(props.timeout());
        this.rest = builder.baseUrl(props.baseUrl()).requestFactory(requestFactory).build();

        factory.configure(b -> b
                .timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(props.timeout().plusMillis(50)).build())
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .slidingWindowSize(20)
                        .minimumNumberOfCalls(10)
                        .failureRateThreshold(50)
                        .slowCallDurationThreshold(props.timeout())
                        .waitDurationInOpenState(Duration.ofSeconds(10))
                        .permittedNumberOfCallsInHalfOpenState(3)
                        .build()), "risk-engine");
        this.breaker = factory.create("risk-engine");
    }

    public Scored score(ScoreRequest request) {
        return breaker.run(
                () -> new Scored(rest.post().uri("/internal/score").body(request).retrieve().body(ScoreResponse.class), false),
                error -> fallback(request, error));
    }

    /** Opens pooled connections to the risk engine before the first real payment needs them. */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void warmUp() {
        for (int i = 0; i < 5; i++) {
            try {
                rest.get().uri("/actuator/health").retrieve().toBodilessEntity();
            } catch (RuntimeException e) {
                log.info("Risk engine not reachable yet ({}); payments will use the fallback until it is", e.getMessage());
                return;
            }
        }
    }

    private Scored fallback(ScoreRequest request, Throwable error) {
        log.warn("Risk engine unavailable for {} ({}); applying fallback policy", request.txnId(), error.toString());
        meters.counter("risk.fallback").increment();
        boolean small = request.amount().compareTo(props.failOpenLimit()) <= 0;
        Decision decision = small ? Decision.APPROVE : Decision.REVIEW;
        String why = small
                ? "Risk engine unavailable: small payment approved under the fail-open policy"
                : "Risk engine unavailable: payment above the fail-open limit held for review";
        return new Scored(new ScoreResponse(request.txnId(), decision, small ? 0.0 : 0.5,
                List.of(new Reason("RISK_ENGINE_UNAVAILABLE", why, 0)), "fallback-policy", 0), true);
    }

    public record Scored(ScoreResponse response, boolean degraded) {
    }
}
