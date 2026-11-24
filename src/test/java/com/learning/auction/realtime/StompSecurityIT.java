package com.learning.auction.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.learning.auction.support.IntegrationTest;
import com.learning.auction.support.StompTestClient;

/** The STOMP security rules from StompAuthInterceptor. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StompSecurityIT extends IntegrationTest {

    @LocalServerPort int port;

    /** Stands in for Keycloak: "good-token" is valid, anything else is rejected. */
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void anonymousWatcherCanSubscribeToAnAuction() throws Exception {
        try (StompTestClient client = new StompTestClient(port, null)) {
            client.subscribe("/topic/auctions/00000000-0000-0000-0000-000000000001", AuctionUpdateMessage.class);

            assertThatThrownBy(() -> client.error.get(1, TimeUnit.SECONDS))
                    .as("no error expected").isInstanceOf(TimeoutException.class);
        }
    }

    @Test
    void subscribingOutsideTheAllowedDestinationsIsRejected() throws Exception {
        try (StompTestClient client = new StompTestClient(port, null)) {
            client.subscribe("/topic/admin-secrets", Object.class);

            assertThat(client.error.get(5, TimeUnit.SECONDS)).isNotBlank();
        }
    }

    @Test
    void privateQueueNeedsALoggedInUser() throws Exception {
        try (StompTestClient client = new StompTestClient(port, null)) {
            client.subscribe("/user/queue/notifications", Object.class);

            assertThat(client.error.get(5, TimeUnit.SECONDS)).isNotBlank();
        }
    }

    @Test
    void invalidTokenIsRejectedOnConnect() throws Exception {
        given(jwtDecoder.decode(anyString())).willThrow(new BadJwtException("expired"));

        StompTestClient client;
        try {
            client = new StompTestClient(port, "bad-token");
        } catch (Exception rejectedDuringConnect) {
            return; // the server refused the CONNECT frame - exactly what we want
        }
        try (client) {
            assertThat(client.error.get(5, TimeUnit.SECONDS)).as("server must report an error").isNotBlank();
        }
    }
}
