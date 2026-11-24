package com.learning.auction.support;

import java.lang.reflect.Type;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/** Minimal STOMP client for tests: connect, subscribe, and collect messages or errors in queues. */
public class StompTestClient implements AutoCloseable {

    private final WebSocketStompClient client;
    private final StompSession session;
    /** ERROR frames from the server, or transport failures (e.g. connection closed by the server). */
    public final CompletableFuture<String> error = new CompletableFuture<>();

    public StompTestClient(int port, String bearerToken) throws Exception {
        client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());
        StompHeaders connectHeaders = new StompHeaders();
        if (bearerToken != null) {
            connectHeaders.add("Authorization", "Bearer " + bearerToken);
        }
        session = client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connectHeaders,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        error.complete("ERROR frame: " + headers.getFirst("message"));
                    }

                    @Override
                    public void handleException(StompSession s, StompCommand command, StompHeaders headers,
                                                byte[] payload, Throwable exception) {
                        error.complete("exception: " + exception.getMessage());
                    }

                    @Override
                    public void handleTransportError(StompSession s, Throwable exception) {
                        error.complete("transport: " + exception.getMessage());
                    }
                }).get(10, TimeUnit.SECONDS);
    }

    public <T> BlockingQueue<T> subscribe(String destination, Class<T> type) {
        BlockingQueue<T> messages = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return type;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                messages.add(type.cast(payload));
            }
        });
        return messages;
    }

    @Override
    public void close() {
        try {
            if (session.isConnected()) {
                session.disconnect();
            }
        } catch (RuntimeException alreadyClosedByServer) {
            // expected in tests where the server rejects us and closes the socket
        }
        client.stop();
    }
}
