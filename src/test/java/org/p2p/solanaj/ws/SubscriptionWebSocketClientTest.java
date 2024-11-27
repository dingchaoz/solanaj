package org.p2p.solanaj.ws;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;

import org.p2p.solanaj.ws.listeners.NotificationEventListener;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.Map;

class SubscriptionWebSocketClientTest {

    private static final String DEVNET_WS_URL = "wss://api.devnet.solana.com";  // Updated for STOMP
    private SubscriptionWebSocketClient client;

    @BeforeEach
    void setUp() throws Exception {
        client = SubscriptionWebSocketClient.getInstance(DEVNET_WS_URL);
        assertTrue(client.waitForConnection(30, TimeUnit.SECONDS), "Connection timed out");
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.disconnect();
        }
    }

    @Test
    void testConnectionEstablished() throws InterruptedException {
        assertTrue(client.waitForConnection(5, TimeUnit.SECONDS), "WebSocket should be connected");
    }

    @Test
    void testSubscribeAndReceiveMessage() throws Exception {
        CountDownLatch messageLatch = new CountDownLatch(1);
        final Map<String, Object>[] receivedMessage = new Map[1];

        NotificationEventListener listener = payload -> {
            receivedMessage[0] = (Map<String, Object>) payload;
            messageLatch.countDown();
        };

        // Subscribe to a test account
        String testAccount = "11111111111111111111111111111111";
        client.accountSubscribe(testAccount, listener);

        assertTrue(messageLatch.await(10, TimeUnit.SECONDS), "Message response timed out");
        assertNotNull(receivedMessage[0], "Received message should not be null");
    }

    @Test
    void testSubscriptionAndUnsubscription() throws Exception {
        CountDownLatch subscribeLatch = new CountDownLatch(1);
        
        NotificationEventListener listener = payload -> {
            subscribeLatch.countDown();
        };

        // Subscribe to a test signature
        String testSignature = "11111111111111111111111111111111";
        client.signatureSubscribe(testSignature, listener);

        // Wait briefly to ensure subscription is processed
        Thread.sleep(1000);

        // Unsubscribe (note: in real implementation, you'd need to store and use the subscription ID)
        // This is more of a structural test
        client.unsubscribe(testSignature);
    }
}
