package com.fraudshield.cases.copilot;

import com.fraudshield.cases.config.AppProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

/** Pulls live account intelligence (mule signals, devices, history) to ground the copilot. */
@Component
public class RiskEngineClient {

    private final RestClient rest;

    public RiskEngineClient(RestClient.Builder builder, AppProperties props) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(1)).build());
        factory.setReadTimeout(Duration.ofSeconds(2));
        this.rest = builder.baseUrl(props.riskEngineUrl()).requestFactory(factory).build();
    }

    public Map<String, Object> account(String vpa) {
        try {
            Map<String, Object> body = rest.get().uri("/api/risk/accounts/{vpa}", vpa).retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return body == null ? Map.of() : body;
        } catch (RuntimeException e) {
            return Map.of();
        }
    }
}
