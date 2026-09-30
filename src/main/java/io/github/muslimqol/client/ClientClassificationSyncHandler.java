package io.github.muslimqol.client;

import io.github.muslimqol.food.ClientSyncedClassificationState;
import io.github.muslimqol.food.FoodClassificationRegistry;
import io.github.muslimqol.network.ClassificationSyncPayload;
import io.github.muslimqol.network.MuslimQolNetwork;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-only handler for applying and clearing remote server food classification snapshots.
 * <p>
 * Isolated inside {@code io.github.muslimqol.client} so dedicated servers never load client classes.
 */
public final class ClientClassificationSyncHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientClassificationSyncHandler.class);

    private ClientClassificationSyncHandler() {}

    public static void init() {
        MuslimQolNetwork.setClientSyncReceiver(ClientClassificationSyncHandler::handleSyncPayload);
        NeoForge.EVENT_BUS.addListener(ClientClassificationSyncHandler::onClientLoggingOut);
    }

    /**
     * Applies a received classification sync payload on the client main thread unless running
     * against an integrated singleplayer server.
     */
    public static void handleSyncPayload(ClassificationSyncPayload payload, boolean isMemoryConnection) {
        if (payload == null) {
            return;
        }
        if (isMemoryConnection || isSingleplayerIntegratedServer()) {
            LOGGER.debug("Skipping ClassificationSyncPayload in singleplayer (integrated server shares JVM state)");
            return;
        }
        ClientSyncedClassificationState syncedState = payload.toClientSyncedState();
        FoodClassificationRegistry.applyClientSyncedState(syncedState);
    }

    /**
     * Clears remote server classification state when the client disconnects from a session.
     */
    public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        FoodClassificationRegistry.clearClientSyncedState();
    }

    private static boolean isSingleplayerIntegratedServer() {
        try {
            Minecraft mc = Minecraft.getInstance();
            return mc != null && mc.hasSingleplayerServer();
        } catch (Throwable ignored) {
            return false;
        }
    }
}

