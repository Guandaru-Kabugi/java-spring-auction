package com.learning.auction.realtime;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import lombok.RequiredArgsConstructor;

/**
 * STOMP over WebSocket. STOMP adds pub/sub on top of a raw socket: clients SUBSCRIBE to a
 * destination and the server pushes MESSAGE frames to everyone subscribed.
 *
 * <pre>
 *   ws://localhost:8080/ws            WebSocket endpoint (STOMP frames)
 *   /topic/auctions/{auctionId}       public: live updates for one auction (anyone can watch)
 *   /user/queue/notifications         private: per-user messages, needs a JWT on CONNECT (Phase 4)
 * </pre>
 *
 * The in-memory "simple broker" lives inside each app instance. That still scales horizontally
 * here because Kafka fans every event out to <i>all</i> instances (see AuctionBroadcaster), and each
 * instance pushes to the clients connected to it. Alternative: a relay to an external broker
 * (RabbitMQ STOMP plugin) via {@code enableStompBrokerRelay}.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthInterceptor stompAuthInterceptor;

    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(allowedOrigins.toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthInterceptor);
    }
}
