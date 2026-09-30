package io.github.muslimqol.network;

import io.github.muslimqol.food.FoodClassificationRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Common network registration and server-to-client synchronization coordinator.
 * <p>
 * Strictly free of {@code net.minecraft.client.*} and {@code io.github.muslimqol.client.*} references
 * so that dedicated servers never load client classes.
 */
public final class MuslimQolNetwork {

    private static final Logger LOGGER = LoggerFactory.getLogger(MuslimQolNetwork.class);

    /**
     * Network protocol version for {@link ClassificationSyncPayload}.
     * <p>
     * IMPORTANT: {@link io.github.muslimqol.api.FoodStatus}, {@link io.github.muslimqol.api.ClassificationSource},
     * {@link io.github.muslimqol.api.ClassificationPriority}, and {@link io.github.muslimqol.api.ConsumptionPolicy}
     * are serialized on the wire by {@link Enum#ordinal()}.
     * 修改枚举顺序必须同步升级 PROTOCOL_VERSION (modifying enum declaration order or inserting/removing constants
     * requires incrementing {@code PROTOCOL_VERSION}).
     */
    public static final String PROTOCOL_VERSION = "1";

    private static volatile BiConsumer<ClassificationSyncPayload, Boolean> clientSyncReceiver =
            MuslimQolNetwork::defaultApplyClientSync;

    private static volatile BiConsumer<ServerPlayer, ClassificationSyncPayload> playerPacketSender =
            PacketDistributor::sendToPlayer;

    private static volatile Predicate<ServerPlayer> channelSupportChecker =
            player -> player != null
                    && player.connection != null
                    && player.connection.hasChannel(ClassificationSyncPayload.TYPE);

    private MuslimQolNetwork() {}

    /**
     * Registers payload codecs and handlers on the mod event bus.
     * Uses {@link PayloadRegistrar#optional()} so clients with MuslimQoL can connect to vanilla/unmodded
     * servers and unmodded clients can connect to servers running MuslimQoL.
     */
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION).optional();
        registrar.playToClient(
                ClassificationSyncPayload.TYPE,
                ClassificationSyncPayload.STREAM_CODEC,
                MuslimQolNetwork::handleClassificationSyncPayload
        );
        LOGGER.debug("Registered optional S2C payload {} (protocol version {})",
                ClassificationSyncPayload.TYPE.id(), PROTOCOL_VERSION);
    }

    /**
     * Handles {@link OnDatapackSyncEvent} on the game event bus (covers player login and {@code /reload}).
     * Sends the classification snapshot only to {@link OnDatapackSyncEvent#getRelevantPlayers()}.
     */
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        sendToPlayers(event.getRelevantPlayers());
    }

    /**
     * Broadcasts the current server classification snapshot to all players on the given server.
     * Used after {@code /muslimqol reload} and {@code ModConfigEvent.Reloading}.
     */
    public static void broadcastClassificationSync(MinecraftServer server) {
        if (server == null || server.getPlayerList() == null) {
            return;
        }
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players == null || players.isEmpty()) {
            return;
        }
        sendToPlayers(players.stream());
    }

    /**
     * Broadcasts the current server classification snapshot to all online players if a logical server is active.
     * Safe to invoke from background config watcher threads ({@code ModConfigEvent.Reloading}).
     */
    public static void broadcastToCurrentServer() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        if (server.isSameThread()) {
            broadcastClassificationSync(server);
        } else {
            server.execute(() -> broadcastClassificationSync(server));
        }
    }

    /**
     * Builds a single snapshot payload and sends it to all relevant players whose connections support the channel.
     *
     * @return number of players to whom the payload was sent
     */
    public static int sendToPlayers(Stream<ServerPlayer> players) {
        return sendToRecipients(players, channelSupportChecker, playerPacketSender);
    }

    /**
     * Generic recipient dispatcher building a single snapshot payload and sending it only to recipients
     * satisfying {@code channelSupportFilter}.
     */
    public static <P> int sendToRecipients(
            Stream<P> recipients,
            Predicate<P> channelSupportFilter,
            BiConsumer<P, ClassificationSyncPayload> sender
    ) {
        if (recipients == null || channelSupportFilter == null || sender == null) {
            return 0;
        }
        List<P> validRecipients = recipients
                .filter(Objects::nonNull)
                .filter(channelSupportFilter)
                .toList();
        if (validRecipients.isEmpty()) {
            return 0;
        }
        ClassificationSyncPayload payload = ClassificationSyncPayload.fromCurrentServerState();
        for (P recipient : validRecipients) {
            sender.accept(recipient, payload);
        }
        LOGGER.debug("Sent ClassificationSyncPayload ({} datapack keys, {} user overrides) to {} player(s)",
                payload.datapackEntries().size(), payload.userOverrides().size(), validRecipients.size());
        return validRecipients.size();
    }

    private static void handleClassificationSyncPayload(ClassificationSyncPayload payload, IPayloadContext context) {
        boolean memoryConnection = context.connection() != null && context.connection().isMemoryConnection();
        context.enqueueWork(() -> dispatchClientSync(payload, memoryConnection));
    }

    /**
     * Dispatches a received sync payload to the active client receiver.
     */
    public static void dispatchClientSync(ClassificationSyncPayload payload, boolean isMemoryConnection) {
        if (payload == null) {
            return;
        }
        clientSyncReceiver.accept(payload, isMemoryConnection);
    }

    /**
     * Default pure handler used when no client-specific delegate is installed.
     */
    public static void defaultApplyClientSync(ClassificationSyncPayload payload, boolean isMemoryConnection) {
        if (isMemoryConnection) {
            LOGGER.debug("Ignoring ClassificationSyncPayload on memory/singleplayer connection");
            return;
        }
        FoodClassificationRegistry.applyClientSyncedState(payload.toClientSyncedState());
    }

    /**
     * Installs the client-package delegate during {@code ClientInit.init(modEventBus)}.
     */
    public static void setClientSyncReceiver(BiConsumer<ClassificationSyncPayload, Boolean> receiver) {
        clientSyncReceiver = receiver != null ? receiver : MuslimQolNetwork::defaultApplyClientSync;
    }

    /**
     * Package/test hook for overriding packet transmission in deterministic unit tests.
     */
    public static void setPlayerPacketSender(
            BiConsumer<ServerPlayer, ClassificationSyncPayload> sender,
            Predicate<ServerPlayer> channelChecker
    ) {
        playerPacketSender = sender != null ? sender : PacketDistributor::sendToPlayer;
        channelSupportChecker = channelChecker != null
                ? channelChecker
                : player -> player != null
                        && player.connection != null
                        && player.connection.hasChannel(ClassificationSyncPayload.TYPE);
    }

    /**
     * Resets network hooks to production defaults.
     */
    public static void resetHooksToDefault() {
        clientSyncReceiver = MuslimQolNetwork::defaultApplyClientSync;
        setPlayerPacketSender(null, null);
    }
}
