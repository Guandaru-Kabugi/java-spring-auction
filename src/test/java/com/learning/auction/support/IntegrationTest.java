package com.learning.auction.support;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base class for tests against real PostgreSQL and Kafka in Docker. Skipped when Docker isn't running.
 * <p>
 * The containers are static singletons with no {@code @Container} annotation: Spring Boot starts them
 * on first use, and they then live for the whole test run. Every test class (and every cached Spring
 * context) shares them, which keeps the suite fast.
 */
@SpringBootTest(properties = {
        "auction.scheduler.enabled=false", // tests drive the scheduler and outbox relay by hand
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
@Testcontainers(disabledWithoutDocker = true)
@Import({IntegrationTest.TestClockConfig.class, TestData.class})
public abstract class IntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.0.0");

    /** A fake access token, as if issued by Keycloak for this user. */
    protected static Jwt jwtFor(String username) {
        return Jwt.withTokenValue("test-token-" + username)
                .header("alg", "none")
                .subject("kc-" + username)
                .claim("preferred_username", username)
                .claim("email", username + "@example.test")
                .build();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClockConfig {

        /** @Primary so it wins over the system clock from ClockConfig. */
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        }
    }
}
