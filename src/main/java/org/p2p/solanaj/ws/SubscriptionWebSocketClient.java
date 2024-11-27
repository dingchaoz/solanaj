package org.p2p.solanaj.ws;

import org.p2p.solanaj.rpc.types.config.Commitment;
import org.p2p.solanaj.ws.listeners.NotificationEventListener;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SubscriptionWebSocketClient {
    private static SubscriptionWebSocketClient instance;
    private static final Object lock = new Object();
    
    private SubscriptionWebSocketClient() {
    }
    
    public static SubscriptionWebSocketClient getInstance(String endpoint) {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new SubscriptionWebSocketClient();
                    instance.connect(endpoint);
                }
            }
        }
        return instance;
    }
    
    private static final Logger LOGGER = Logger.getLogger(SubscriptionWebSocketClient.class.getName());
    private final Map<String, SubscriptionParams> activeSubscriptions = new ConcurrentHashMap<>();
    private final Map<String, StompSession.Subscription> stompSubscriptions = new ConcurrentHashMap<>();
    private WebSocketStompClient stompClient;
    private StompSession stompSession;
    private final CountDownLatch connectLatch = new CountDownLatch(1);

    private static class SubscriptionParams {
        final String destination;
        final NotificationEventListener listener;

        SubscriptionParams(String destination, NotificationEventListener listener) {
            this.destination = destination;
            this.listener = listener;
        }
    }

    public void connect(String endpoint) {
        LOGGER.info("Attempting to connect to: " + endpoint);
        WebSocketClient webSocketClient = new StandardWebSocketClient();
        stompClient = new WebSocketStompClient(webSocketClient);
        
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.initialize();
        stompClient.setTaskScheduler(taskScheduler);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        StompSessionHandler sessionHandler = new StompSessionHandler() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                LOGGER.info("Successfully connected to WebSocket");
                stompSession = session;
                connectLatch.countDown();
                resubscribeAll();
            }

            @Override
            public void handleException(StompSession session, StompCommand command, 
                    StompHeaders headers, byte[] payload, Throwable exception) {
                LOGGER.severe("Error in STOMP session: " + exception.getMessage());
                exception.printStackTrace();
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                LOGGER.severe("Transport error: " + exception.getMessage());
                exception.printStackTrace();
                scheduleReconnect();
            }

            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                // Default implementation for unhandled frames
            }
        };

        try {
            stompClient.connect(endpoint, sessionHandler);
            LOGGER.info("Connection attempt initiated");
        } catch (Exception e) {
            LOGGER.severe("Failed to connect: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void accountSubscribe(String key, NotificationEventListener listener, 
            Commitment commitment, String encoding) {
        String destination = String.format("/topic/account/%s/%s/%s", 
            key, 
            commitment.getValue(), 
            encoding);
        subscribe(destination, listener);
    }

    public void signatureSubscribe(String signature, NotificationEventListener listener, 
            Commitment commitment) {
        String destination = String.format("/topic/signature/%s/%s", 
            signature, 
            commitment.getValue());
        subscribe(destination, listener);
    }

    public void logsSubscribe(List<String> mentions, NotificationEventListener listener, 
            Commitment commitment) {
        String destination = String.format("/topic/logs/%s/%s",
            String.join(",", mentions),
            commitment.getValue());
        subscribe(destination, listener);
    }

    public void accountSubscribe(String key, NotificationEventListener listener) {
        accountSubscribe(key, listener, Commitment.FINALIZED, "jsonParsed");
    }

    public void signatureSubscribe(String signature, NotificationEventListener listener) {
        signatureSubscribe(signature, listener, Commitment.FINALIZED);
    }

    public void logsSubscribe(List<String> mentions, NotificationEventListener listener) {
        logsSubscribe(mentions, listener, Commitment.FINALIZED);
    }

    private void subscribe(String destination, NotificationEventListener listener) {
        if (stompSession != null && stompSession.isConnected()) {
            StompSession.Subscription subscription = stompSession.subscribe(destination, 
                new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return Map.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        listener.onNotificationEvent(payload);
                    }
                });
            
            String subscriptionId = subscription.getSubscriptionId();
            activeSubscriptions.put(subscriptionId, new SubscriptionParams(destination, listener));
            stompSubscriptions.put(subscriptionId, subscription);
        } else {
            LOGGER.warning("STOMP session not connected. Adding to pending subscriptions.");
            String tempId = UUID.randomUUID().toString();
            activeSubscriptions.put(tempId, new SubscriptionParams(destination, listener));
        }
    }

    public void unsubscribe(String subscriptionId) {
        StompSession.Subscription subscription = stompSubscriptions.remove(subscriptionId);
        if (subscription != null) {
            subscription.unsubscribe();
            activeSubscriptions.remove(subscriptionId);
            LOGGER.info("Unsubscribed from " + subscriptionId);
        }
    }

    private void resubscribeAll() {
        LOGGER.info("Resubscribing to all active subscriptions");
        Map<String, SubscriptionParams> subscriptionsToRestore = new HashMap<>(activeSubscriptions);
        activeSubscriptions.clear();
        stompSubscriptions.clear();

        for (SubscriptionParams params : subscriptionsToRestore.values()) {
            subscribe(params.destination, params.listener);
        }
    }

    private void scheduleReconnect() {
        // Implement reconnection logic here
        // You might want to use Spring's TaskScheduler for this
    }

    public boolean waitForConnection(long timeout, TimeUnit unit) throws InterruptedException {
        return connectLatch.await(timeout, unit);
    }

    public void disconnect() {
        if (stompSession != null && stompSession.isConnected()) {
            stompSession.disconnect();
        }
    }

    /**
     * Returns the subscription ID for a given account.
     * @param account The account address
     * @return The subscription ID, or null if not found
     */
    public String getSubscriptionIdByAccount(String account) {
        String searchDestination = String.format("/topic/account/%s", account);
        return activeSubscriptions.entrySet().stream()
            .filter(entry -> entry.getValue().destination.startsWith(searchDestination))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);
    }
}