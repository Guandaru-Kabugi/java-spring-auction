package com.learning.auction.realtime;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Security for STOMP frames. Browsers can't set an Authorization header on the WebSocket
 * handshake, so the HTTP layer lets {@code /ws} through and authentication happens here, on the
 * STOMP CONNECT frame: {@code CONNECT\nAuthorization:Bearer eyJ...}. The token is validated by the
 * same {@link JwtDecoder} that protects the REST API (signature, issuer, audience, expiry).
 * <ul>
 *   <li><b>CONNECT</b> - token optional. Without one you are an anonymous watcher.</li>
 *   <li><b>SUBSCRIBE</b> - only {@code /topic/auctions/*} (public) or {@code /user/queue/*} (logged in).</li>
 *   <li><b>SEND</b> - rejected. Bids go through REST, which has validation, idempotency and a single
 *       write path; the socket is push-only.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
class StompAuthInterceptor implements ChannelInterceptor {

    private static final String BEARER = "Bearer ";

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor stomp = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (stomp == null || stomp.getCommand() == null) {
            return message; // heartbeats etc.
        }
        switch (stomp.getCommand()) {
            case CONNECT -> authenticate(stomp);
            case SUBSCRIBE -> authorizeSubscription(stomp);
            case SEND -> throw new MessageDeliveryException("Sending over the socket is not supported; use the REST API");
            default -> { }
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor stomp) {
        String header = stomp.getFirstNativeHeader("Authorization");
        if (header == null || header.isBlank()) {
            return; // anonymous watcher
        }
        if (!header.startsWith(BEARER)) {
            throw new MessageDeliveryException("Authorization header must be 'Bearer <token>'");
        }
        try {
            AbstractAuthenticationToken authentication =
                    jwtAuthenticationConverter.convert(jwtDecoder.decode(header.substring(BEARER.length())));
            stomp.setUser(authentication); // becomes the Principal for the whole session
        } catch (JwtException e) {
            throw new MessageDeliveryException("Invalid access token: " + e.getMessage());
        }
    }

    private void authorizeSubscription(StompHeaderAccessor stomp) {
        String destination = String.valueOf(stomp.getDestination());
        if (destination.startsWith("/topic/auctions/")) {
            return;
        }
        if (destination.startsWith("/user/queue/")) {
            if (stomp.getUser() == null) {
                throw new MessageDeliveryException("Log in (send a token on CONNECT) to receive private messages");
            }
            return;
        }
        throw new MessageDeliveryException("Subscribing to " + destination + " is not allowed");
    }
}
