package org.p2p.solanaj.ws.listeners;

import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.rpc.RpcClient;
import java.util.Map;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * A listener for Solana log notifications.
 * This class implements the NotificationEventListener interface and provides
 * functionality to process and log notification events from the Solana blockchain.
 */
public class LogNotificationEventListener implements NotificationEventListener {
    private static final Logger LOGGER = Logger.getLogger(LogNotificationEventListener.class.getName());
    private final RpcClient client;
    private final PublicKey listeningPubkey;

    /**
     * Constructs a new LogNotificationEventListener.
     *
     * @param client The RpcClient used for communication with the Solana network.
     * @param listeningPubkey The PublicKey this listener is associated with.
     */
    public LogNotificationEventListener(RpcClient client, PublicKey listeningPubkey) {
        this.client = client;
        this.listeningPubkey = listeningPubkey;
    }

    /**
     * Processes a notification event.
     * This method logs the received notification data, including the transaction
     * signature and associated logs.
     *
     * @param data The notification data object.
     */
    @Override
    public void onNotificationEvent(Object data) {
        if (data == null) {
            LOGGER.warning("Received null data in onNotificationEvent");
            return;
        }

        if (!(data instanceof Map)) {
            LOGGER.warning("Received invalid data type in onNotificationEvent: " + data.getClass().getName());
            return;
        }

        try {
            Map<String, Object> notificationData = (Map<String, Object>) data;

            String signature = (String) notificationData.get("signature");
            List<String> logs = (List<String>) notificationData.get("logs");

            if (signature == null || logs == null) {
                LOGGER.warning("Missing required fields in notification data");
                return;
            }

            LOGGER.info("Received notification for transaction: " + signature);
            for (String log : logs) {
                LOGGER.info("Log: " + log);
            }

            // Here you could add more specific processing based on the log contents
            // For example, checking for specific program invocations or state changes

        } catch (ClassCastException e) {
            LOGGER.log(Level.WARNING, "Error processing notification data", e);
        }
    }

    /**
     * Gets the RpcClient associated with this listener.
     *
     * @return The RpcClient instance.
     */
    public RpcClient getClient() {
        return client;
    }

    /**
     * Gets the PublicKey this listener is associated with.
     *
     * @return The PublicKey instance.
     */
    public PublicKey getListeningPubkey() {
        return listeningPubkey;
    }
}
