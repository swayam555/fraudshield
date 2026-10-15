package com.fraudshield.risk;

import com.fraudshield.contracts.Channel;
import com.fraudshield.contracts.ScoreRequest;
import com.fraudshield.risk.config.RiskProperties;
import com.fraudshield.risk.feature.FeatureStore;
import com.fraudshield.risk.feature.RawSignals;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the real Lua script against a real Redis. */
@Testcontainers
class FeatureStoreIT {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static FeatureStore store;

    @BeforeAll
    static void setUp() throws Exception {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        store = new FeatureStore(template, new RiskProperties(0.5, 0.85, 10, 0.7));
    }

    private static ScoreRequest pay(String from, String to, double amount, String device, Instant at) {
        return new ScoreRequest(UUID.randomUUID().toString(), from, to, BigDecimal.valueOf(amount),
                Channel.UPI_INTENT, device, 19.07, 72.87, at, null);
    }

    @Test
    void countsDistinctPayersIntoAMuleAccount() {
        Instant now = Instant.now();
        for (int i = 0; i < 12; i++) {
            store.readAndRecord(pay("victim" + i + "@upi", "mule1@upi", 5000, "dev-v" + i, now));
        }
        RawSignals s = store.readAndRecord(pay("victim99@upi", "mule1@upi", 5000, "dev-v99", now));
        assertThat(s.payeeDistinctPayers()).isEqualTo(12);
        assertThat(s.payeeKnown()).isFalse();
    }

    @Test
    void tracksVelocityProfileAndPassThrough() {
        Instant now = Instant.now();
        store.readAndRecord(pay("sender@upi", "relay@upi", 20_000, "d1", now));
        approve(pay("relay@upi", "boss@upi", 100, "d2", now));
        RawSignals s = store.readAndRecord(pay("relay@upi", "boss@upi", 19_000, "d2", now));

        // Signals describe the account *before* this payment: one earlier payment of ₹100.
        assertThat(s.payerTxnLastMinute()).isEqualTo(1);
        assertThat(s.payerHistoryCount()).isEqualTo(1);
        assertThat(s.payerMeanAmount()).isEqualTo(100);
        assertThat(s.payerInflow30m()).isEqualTo(20_000);
        assertThat(s.payerOutflow30m()).isEqualTo(100);
        assertThat(s.payeeKnown()).isTrue();
        assertThat(s.deviceKnown()).isTrue();
    }

    @Test
    void remembersWhenDevicesAndPayeesWereFirstSeen() {
        Instant now = Instant.now();
        approve(pay("owner@upi", "shop@upi", 100, "phone-1", now.minusSeconds(86_400 * 10)));
        approve(pay("owner@upi", "thief@upi", 40_000, "phone-2", now.minusSeconds(60)));
        RawSignals s = store.readAndRecord(pay("owner@upi", "thief@upi", 40_000, "phone-2", now));
        assertThat(s.knownDeviceCount()).isEqualTo(2);
        assertThat(s.deviceFirstSeenMillis()).isEqualTo(now.minusSeconds(60).toEpochMilli());
        assertThat(s.payeeFirstPaidMillis()).isEqualTo(now.minusSeconds(60).toEpochMilli());
    }

    @Test
    void blockedAttemptsNeverTeachTheTrustedProfile() {
        Instant now = Instant.now();
        approve(pay("victim@upi", "gas@upi", 400, "own-phone", now.minusSeconds(3600)));
        // An attacker retries from their own phone, in another city, for a large amount. Every
        // attempt is blocked, so none of them is learned...
        for (int i = 0; i < 5; i++) {
            store.readAndRecord(new ScoreRequest("atk-" + i, "victim@upi", "fastpay@ybl", BigDecimal.valueOf(74_000),
                    Channel.UPI_COLLECT, "attacker-phone", 28.61, 77.20, now.minusSeconds(60 - i), null));
        }
        RawSignals s = store.readAndRecord(new ScoreRequest("atk-next", "victim@upi", "fastpay@ybl",
                BigDecimal.valueOf(74_000), Channel.UPI_COLLECT, "attacker-phone", 28.61, 77.20, now, null));
        // ...and the next one still looks exactly as unusual as the first.
        assertThat(s.deviceKnown()).isFalse();
        assertThat(s.payeeKnown()).isFalse();
        assertThat(s.payerMeanAmount()).isEqualTo(400);
        assertThat(s.payerHistoryCount()).isEqualTo(1);
        // Attempt signals still see every try.
        assertThat(s.payerTxnLastMinute()).isEqualTo(5);
    }

    @Test
    void countsAccountsSharingADevice() {
        Instant now = Instant.now();
        for (int i = 0; i < 4; i++) store.readAndRecord(pay("farm" + i + "@upi", "x@upi", 50, "farm-device", now));
        RawSignals s = store.readAndRecord(pay("farm9@upi", "x@upi", 50, "farm-device", now));
        assertThat(s.accountsOnDevice()).isEqualTo(5);
    }

    private RawSignals approve(ScoreRequest r) {
        RawSignals s = store.readAndRecord(r);
        store.learnApproved(r);
        return s;
    }
}
