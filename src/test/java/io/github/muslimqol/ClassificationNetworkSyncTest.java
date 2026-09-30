package io.github.muslimqol;

import io.github.muslimqol.api.ClassificationPriority;
import io.github.muslimqol.api.ClassificationProviderId;
import io.github.muslimqol.api.ClassificationRuleId;
import io.github.muslimqol.api.ClassificationSource;
import io.github.muslimqol.api.ConsumptionPolicy;
import io.github.muslimqol.api.FoodClassification;
import io.github.muslimqol.api.FoodStatus;
import io.github.muslimqol.client.ClientClassificationSyncHandler;
import io.github.muslimqol.client.FoodClassificationTooltipFormatter;
import io.github.muslimqol.compat.ClassificationRuntimeState;
import io.github.muslimqol.compat.CompatibilityMetadata;
import io.github.muslimqol.compat.CompatibilitySnapshot;
import io.github.muslimqol.compat.FoodCompatibilityManager;
import io.github.muslimqol.food.FoodClassificationRegistry;
import io.github.muslimqol.food.FoodClassifier;
import io.github.muslimqol.network.ClassificationSyncPayload;
import io.github.muslimqol.network.MuslimQolNetwork;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for dedicated server -> client food classification network synchronization.
 */
class ClassificationNetworkSyncTest {

    @BeforeAll
    static void init() {
        try {
            net.minecraft.SharedConstants.tryDetectVersion();
            net.minecraft.server.Bootstrap.bootStrap();
        } catch (Throwable ignored) {}
    }

    @BeforeEach
    void setUp() {
        FoodClassificationRegistry.clearAll();
        MuslimQolNetwork.resetHooksToDefault();
        ClassificationSyncPayload.resetTruncationWarningState();
    }

    @AfterEach
    void tearDown() {
        FoodClassificationRegistry.clearAll();
        MuslimQolNetwork.resetHooksToDefault();
        ClassificationSyncPayload.resetTruncationWarningState();
    }

    @Test
    void testStreamCodecRoundTripPreservesDatapackUserOverridesAndFourPolicies() {
        ResourceLocation bacon = ResourceLocation.parse("farmersdelight:bacon");
        ResourceLocation cabbage = ResourceLocation.parse("farmersdelight:cabbage");
        ResourceLocation dumplings = ResourceLocation.parse("farmersdelight:dumplings");
        ResourceLocation beef = ResourceLocation.parse("minecraft:beef");

        ClassificationProviderId fdProvider = ClassificationProviderId.parse("muslimqol_farmersdelight:datapack");
        ClassificationProviderId customProvider = ClassificationProviderId.parse("custom_pack:datapack");

        FoodClassification baconPrimary = new FoodClassification(
                FoodStatus.RESTRICTED,
                "swine",
                ClassificationSource.DATAPACK,
                fdProvider,
                ClassificationPriority.DATAPACK,
                ClassificationRuleId.parse("muslimqol_farmersdelight:food_classifications/pork")
        );
        FoodClassification baconSecondary = new FoodClassification(
                FoodStatus.DOUBTFUL,
                "unknown_ingredients",
                ClassificationSource.DATAPACK,
                customProvider,
                ClassificationPriority.DATAPACK,
                null
        );
        FoodClassification cabbageEntry = new FoodClassification(
                FoodStatus.HALAL,
                "plant_based",
                ClassificationSource.DATAPACK,
                fdProvider,
                ClassificationPriority.DATAPACK,
                ClassificationRuleId.parse("muslimqol_farmersdelight:food_classifications/plants")
        );
        FoodClassification dumplingsEntry = new FoodClassification(
                FoodStatus.DOUBTFUL,
                "unknown_ingredients",
                ClassificationSource.DATAPACK,
                fdProvider,
                ClassificationPriority.DATAPACK,
                ClassificationRuleId.parse("muslimqol_farmersdelight:food_classifications/doubtful")
        );
        FoodClassification beefOverride = new FoodClassification(
                FoodStatus.HALAL,
                "zabiha",
                ClassificationSource.USER_OVERRIDE,
                ClassificationProviderId.USER_OVERRIDE,
                ClassificationPriority.USER_OVERRIDE,
                null
        );

        Map<ResourceLocation, List<FoodClassification>> datapack = new LinkedHashMap<>();
        datapack.put(bacon, List.of(baconPrimary, baconSecondary));
        datapack.put(cabbage, List.of(cabbageEntry));
        datapack.put(dumplings, List.of(dumplingsEntry));

        Map<ResourceLocation, FoodClassification> overrides = Map.of(beef, beefOverride);

        ClassificationSyncPayload original = new ClassificationSyncPayload(
                datapack,
                overrides,
                ConsumptionPolicy.WARN,
                ConsumptionPolicy.BLOCK,
                ConsumptionPolicy.BLOCK,
                ConsumptionPolicy.WARN
        );

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ClassificationSyncPayload.STREAM_CODEC.encode(buf, original);
            ClassificationSyncPayload decoded = ClassificationSyncPayload.STREAM_CODEC.decode(buf);

            assertEquals(0, buf.readableBytes(), "All encoded bytes must be consumed during decode");
            assertEquals(original.datapackEntries(), decoded.datapackEntries());
            assertEquals(original.userOverrides(), decoded.userOverrides());
            assertEquals(ConsumptionPolicy.WARN, decoded.halalPolicy());
            assertEquals(ConsumptionPolicy.BLOCK, decoded.restrictedPolicy());
            assertEquals(ConsumptionPolicy.BLOCK, decoded.doubtfulPolicy());
            assertEquals(ConsumptionPolicy.WARN, decoded.unknownPolicy());
        } finally {
            buf.release();
        }
    }

    @Test
    void testStringTableDeduplicationCompressesRepeatedStrings() {
        ClassificationProviderId fdProvider = ClassificationProviderId.parse("muslimqol_farmersdelight:datapack");
        ClassificationRuleId porkRule = ClassificationRuleId.parse("muslimqol_farmersdelight:food_classifications/pork");

        Map<ResourceLocation, List<FoodClassification>> datapack = new LinkedHashMap<>();
        for (int i = 0; i < 500; i++) {
            ResourceLocation id = ResourceLocation.parse("farmersdelight:test_item_" + i);
            datapack.put(id, List.of(new FoodClassification(
                    FoodStatus.RESTRICTED,
                    "swine",
                    ClassificationSource.DATAPACK,
                    fdProvider,
                    ClassificationPriority.DATAPACK,
                    porkRule
            )));
        }

        ClassificationSyncPayload payload = new ClassificationSyncPayload(
                datapack,
                Map.of(),
                ConsumptionPolicy.ALLOW,
                ConsumptionPolicy.BLOCK,
                ConsumptionPolicy.WARN,
                ConsumptionPolicy.ALLOW
        );

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ClassificationSyncPayload.STREAM_CODEC.encode(buf, payload);
            int totalBytes = buf.readableBytes();
            assertTrue(totalBytes < 20_000, "500 deduplicated items should serialize under 20 KB, got: " + totalBytes);

            // Peek first VarInt: string table size should be exactly 3 ("swine", providerId, ruleId)
            buf.markReaderIndex();
            int stringTableSize = buf.readVarInt();
            buf.resetReaderIndex();
            assertEquals(3, stringTableSize, "Repeated reason/providerId/ruleId must be deduplicated to 3 entries");

            ClassificationSyncPayload decoded = ClassificationSyncPayload.STREAM_CODEC.decode(buf);
            assertEquals(500, decoded.datapackEntries().size());
        } finally {
            buf.release();
        }
    }

    @Test
    void testApplyAndClearClientSyncedStateDoesNotTouchCompatibilityManagerOrLocalRuntimeState() {
        // 1. Setup local runtime state with compatibility pack metadata
        ResourceLocation localItem = ResourceLocation.parse("minecraft:carrot");
        ClassificationRuntimeState localState = new ClassificationRuntimeState(
                Map.of(localItem, List.of(new FoodClassification(
                        FoodStatus.DOUBTFUL, "local_datapack", ClassificationSource.DATAPACK
                ))),
                Map.of(),
                Map.of("muslimqol_farmersdelight", new CompatibilityMetadata(1, "FD Pack", "farmersdelight", "1.3.4", "1.21.1")),
                Map.of()
        );
        FoodClassificationRegistry.applyRuntimeState(localState);
        CompatibilitySnapshot snapshotBeforeClientSync = FoodCompatibilityManager.getActiveSnapshot();

        // 2. Apply remote dedicated server state via client-dedicated method
        ResourceLocation bacon = ResourceLocation.parse("farmersdelight:bacon");
        ResourceLocation cabbage = ResourceLocation.parse("farmersdelight:cabbage");
        ResourceLocation dumplings = ResourceLocation.parse("farmersdelight:dumplings");
        ResourceLocation beef = ResourceLocation.parse("minecraft:beef");

        ClassificationProviderId fdProvider = ClassificationProviderId.parse("muslimqol_farmersdelight:datapack");
        Map<ResourceLocation, List<FoodClassification>> remoteDatapack = Map.of(
                bacon, List.of(new FoodClassification(FoodStatus.RESTRICTED, "swine", ClassificationSource.DATAPACK, fdProvider)),
                cabbage, List.of(new FoodClassification(FoodStatus.HALAL, "plant_based", ClassificationSource.DATAPACK, fdProvider)),
                dumplings, List.of(new FoodClassification(FoodStatus.DOUBTFUL, "unknown_ingredients", ClassificationSource.DATAPACK, fdProvider))
        );
        Map<ResourceLocation, FoodClassification> remoteOverrides = Map.of(
                beef, new FoodClassification(FoodStatus.HALAL, "zabiha", ClassificationSource.USER_OVERRIDE)
        );

        ClassificationSyncPayload payload = new ClassificationSyncPayload(
                remoteDatapack,
                remoteOverrides,
                ConsumptionPolicy.ALLOW,
                ConsumptionPolicy.BLOCK,
                ConsumptionPolicy.BLOCK,
                ConsumptionPolicy.WARN
        );

        ClientClassificationSyncHandler.handleSyncPayload(payload, false);

        // Verify FoodCompatibilityManager.applyRuntimeState was NOT called (snapshot instance unchanged)
        assertSame(snapshotBeforeClientSync, FoodCompatibilityManager.getActiveSnapshot(),
                "Remote client sync must not invoke FoodCompatibilityManager.applyRuntimeState");
        assertSame(localState, FoodClassificationRegistry.getRuntimeState(),
                "Local RUNTIME_STATE must remain untouched while client sync state is active");

        // Verify client FoodClassifier & TooltipFormatter resolve remote classifications & policies
        assertEquals(FoodStatus.RESTRICTED, FoodClassifier.classify(bacon).status());
        assertEquals("swine", FoodClassifier.classify(bacon).reason());
        assertEquals(FoodStatus.HALAL, FoodClassifier.classify(cabbage).status());
        assertEquals("plant_based", FoodClassifier.classify(cabbage).reason());
        assertEquals(FoodStatus.DOUBTFUL, FoodClassifier.classify(dumplings).status());
        assertEquals("unknown_ingredients", FoodClassifier.classify(dumplings).reason());
        assertEquals(FoodStatus.HALAL, FoodClassifier.classify(beef).status());
        assertEquals("zabiha", FoodClassifier.classify(beef).reason());

        assertEquals(ConsumptionPolicy.ALLOW, FoodClassifier.getPolicy(FoodStatus.HALAL));
        assertEquals(ConsumptionPolicy.BLOCK, FoodClassifier.getPolicy(FoodStatus.RESTRICTED));
        assertEquals(ConsumptionPolicy.BLOCK, FoodClassifier.getPolicy(FoodStatus.DOUBTFUL));
        assertEquals(ConsumptionPolicy.WARN, FoodClassifier.getPolicy(FoodStatus.UNKNOWN));

        List<Component> baconTooltip = FoodClassificationTooltipFormatter.formatTooltip(bacon);
        assertFalse(baconTooltip.isEmpty(), "Synced modded item must produce tooltip lines on client");

        // 3. Disconnect cleanup restores local state immediately
        ClientClassificationSyncHandler.onClientLoggingOut(null);
        assertFalse(FoodClassificationRegistry.hasClientSyncedState());
        assertEquals(FoodStatus.UNKNOWN, FoodClassifier.classify(bacon).status());
        assertEquals(FoodStatus.UNKNOWN, FoodClassifier.classify(beef).status());
        assertEquals(FoodStatus.DOUBTFUL, FoodClassifier.classify(localItem).status(),
                "Local datapack state must remain intact after remote disconnect");
    }

    @Test
    void testSingleplayerMemoryConnectionIgnoresSyncPayloadAndDisconnectDoesNotClearSharedState() {
        ResourceLocation bacon = ResourceLocation.parse("farmersdelight:bacon");
        FoodClassification integratedServerEntry = new FoodClassification(
                FoodStatus.RESTRICTED, "swine", ClassificationSource.DATAPACK
        );
        ClassificationRuntimeState integratedState = new ClassificationRuntimeState(
                Map.of(bacon, List.of(integratedServerEntry)),
                Map.of(),
                Map.of(),
                Map.of()
        );
        FoodClassificationRegistry.applyRuntimeState(integratedState);

        // Attempt to apply a conflicting payload on a memory (singleplayer) connection
        ClassificationSyncPayload conflictingPayload = new ClassificationSyncPayload(
                Map.of(bacon, List.of(new FoodClassification(FoodStatus.HALAL, "wrong", ClassificationSource.DATAPACK))),
                Map.of(),
                ConsumptionPolicy.BLOCK,
                ConsumptionPolicy.ALLOW,
                ConsumptionPolicy.ALLOW,
                ConsumptionPolicy.BLOCK
        );

        ClientClassificationSyncHandler.handleSyncPayload(conflictingPayload, true);
        assertFalse(FoodClassificationRegistry.hasClientSyncedState(),
                "Singleplayer memory connection must not activate remote client sync state");
        assertEquals(FoodStatus.RESTRICTED, FoodClassifier.classify(bacon).status(),
                "Integrated server shared classification must not be overwritten");

        // Disconnect event in singleplayer must not clear integrated server RUNTIME_STATE
        ClientClassificationSyncHandler.onClientLoggingOut(null);
        assertEquals(FoodStatus.RESTRICTED, FoodClassifier.classify(bacon).status(),
                "Integrated server state must survive ClientPlayerNetworkEvent.LoggingOut");
    }

    @Test
    void testServerOversizedDatapackTruncatesDeterministicallyAndWarnsOnlyOnce() {
        ClassificationProviderId provider = ClassificationProviderId.parse("hugepack:datapack");
        Map<ResourceLocation, List<FoodClassification>> hugeDatapack = new LinkedHashMap<>();
        int totalToCreate = ClassificationSyncPayload.MAX_DATAPACK_ITEMS + 150;
        for (int i = 0; i < totalToCreate; i++) {
            ResourceLocation id = ResourceLocation.parse(String.format("hugepack:food_%05d", i));
            hugeDatapack.put(id, List.of(new FoodClassification(
                    FoodStatus.HALAL,
                    "plant_based",
                    ClassificationSource.DATAPACK,
                    provider
            )));
        }

        ClassificationSyncPayload oversized = new ClassificationSyncPayload(
                hugeDatapack,
                Map.of(),
                ConsumptionPolicy.ALLOW,
                ConsumptionPolicy.BLOCK,
                ConsumptionPolicy.WARN,
                ConsumptionPolicy.ALLOW
        );

        assertEquals(0, ClassificationSyncPayload.getTruncationWarningCount());
        assertTrue(ClassificationSyncPayload.getLastTruncatedNamespaces().isEmpty());

        FriendlyByteBuf buf1 = new FriendlyByteBuf(Unpooled.buffer());
        FriendlyByteBuf buf2 = new FriendlyByteBuf(Unpooled.buffer());
        try {
            // First encode (e.g. Player 1 joins): must NOT throw, must truncate to MAX_DATAPACK_ITEMS, and warn once
            assertDoesNotThrow(() -> ClassificationSyncPayload.STREAM_CODEC.encode(buf1, oversized));
            assertEquals(1, ClassificationSyncPayload.getTruncationWarningCount(),
                    "First oversized encode must emit exactly one warning");
            assertEquals(java.util.Set.of("hugepack"), ClassificationSyncPayload.getLastTruncatedNamespaces(),
                    "Truncation warning must record the affected namespace(s)");

            // Second encode (e.g. Player 2 joins): must NOT throw and must NOT warn again
            assertDoesNotThrow(() -> ClassificationSyncPayload.STREAM_CODEC.encode(buf2, oversized));
            assertEquals(1, ClassificationSyncPayload.getTruncationWarningCount(),
                    "Subsequent oversized encodes must not spam duplicate warnings");

            ClassificationSyncPayload decoded = ClassificationSyncPayload.STREAM_CODEC.decode(buf1);
            assertEquals(ClassificationSyncPayload.MAX_DATAPACK_ITEMS, decoded.datapackEntries().size());
            assertTrue(decoded.datapackEntries().containsKey(ResourceLocation.parse("hugepack:food_00000")));
            assertTrue(decoded.datapackEntries().containsKey(ResourceLocation.parse("hugepack:food_08191")));
            assertFalse(decoded.datapackEntries().containsKey(ResourceLocation.parse("hugepack:food_08192")));
        } finally {
            buf1.release();
            buf2.release();
        }
    }

    @Test
    void testServerOversizedStringTableAndCandidatesTruncateGracefully() {
        Map<ResourceLocation, List<FoodClassification>> datapack = new LinkedHashMap<>();
        // Create 2,200 items each with a distinct reason string (exceeding MAX_STRING_TABLE_ENTRIES = 2,048)
        // and first item having 12 candidates (exceeding MAX_CANDIDATES_PER_ITEM = 8)
        List<FoodClassification> manyCandidates = new ArrayList<>();
        for (int c = 0; c < 12; c++) {
            manyCandidates.add(new FoodClassification(
                    FoodStatus.RESTRICTED,
                    "shared_reason",
                    ClassificationSource.DATAPACK,
                    ClassificationProviderId.parse("pack_" + c + ":datapack")
            ));
        }
        datapack.put(ResourceLocation.parse("testmod:multi_candidate_item"), manyCandidates);

        for (int i = 0; i < 2200; i++) {
            datapack.put(
                    ResourceLocation.parse("zotherpack:unique_reason_item_" + i),
                    List.of(new FoodClassification(
                            FoodStatus.HALAL,
                            "unique_reason_" + i,
                            ClassificationSource.DATAPACK
                    ))
            );
        }

        ClassificationSyncPayload payload = new ClassificationSyncPayload(
                datapack,
                Map.of(),
                ConsumptionPolicy.ALLOW,
                ConsumptionPolicy.BLOCK,
                ConsumptionPolicy.WARN,
                ConsumptionPolicy.ALLOW
        );

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            assertDoesNotThrow(() -> ClassificationSyncPayload.STREAM_CODEC.encode(buf, payload));
            assertEquals(1, ClassificationSyncPayload.getTruncationWarningCount());
            assertEquals(java.util.Set.of("testmod", "zotherpack"), ClassificationSyncPayload.getLastTruncatedNamespaces(),
                    "Truncation warning must record all namespaces with truncated candidates or items");

            ClassificationSyncPayload decoded = ClassificationSyncPayload.STREAM_CODEC.decode(buf);
            List<FoodClassification> decodedMulti = decoded.datapackEntries()
                    .get(ResourceLocation.parse("testmod:multi_candidate_item"));
            assertNotNull(decodedMulti);
            assertEquals(ClassificationSyncPayload.MAX_CANDIDATES_PER_ITEM, decodedMulti.size(),
                    "Per-item candidates must be truncated to MAX_CANDIDATES_PER_ITEM");
            assertTrue(decoded.datapackEntries().size() < 2200,
                    "Items requiring new string table entries beyond MAX_STRING_TABLE_ENTRIES must be truncated");
        } finally {
            buf.release();
        }
    }

    @Test
    void testServerMaxLengthStringsExceedingByteBudgetTruncateByFinalSizeGuardWithoutThrowing() {
        // Construct data well within item/string count caps (5,000 < 8,192 items; 1,500 < 2,048 strings),
        // but with near-MAX_STRING_LENGTH (256-char) strings and ResourceLocations so raw size (~1.6 MiB)
        // exceeds MAX_PAYLOAD_BYTES (960 KiB = 983,040 bytes).
        String pathPad = "a".repeat(220);
        String reasonPad = "r".repeat(240);
        ClassificationProviderId longProvider = ClassificationProviderId.parse("longmod:datapack");

        List<String> longReasons = new ArrayList<>(1500);
        for (int r = 0; r < 1500; r++) {
            String rawReason = String.format("%s_%04d", reasonPad, r);
            longReasons.add(rawReason.substring(0, Math.min(rawReason.length(), ClassificationSyncPayload.MAX_STRING_LENGTH)));
        }

        Map<ResourceLocation, List<FoodClassification>> datapack = new LinkedHashMap<>();
        for (int i = 0; i < 5000; i++) {
            ResourceLocation id = ResourceLocation.parse(String.format("longmod:%s_%05d", pathPad, i));
            String reason = longReasons.get(i % longReasons.size());
            datapack.put(id, List.of(new FoodClassification(
                    FoodStatus.HALAL,
                    reason,
                    ClassificationSource.DATAPACK,
                    longProvider
            )));
        }

        ClassificationSyncPayload payload = new ClassificationSyncPayload(
                datapack,
                Map.of(),
                ConsumptionPolicy.ALLOW,
                ConsumptionPolicy.BLOCK,
                ConsumptionPolicy.WARN,
                ConsumptionPolicy.ALLOW
        );

        FriendlyByteBuf buf1 = new FriendlyByteBuf(Unpooled.buffer());
        FriendlyByteBuf buf2 = new FriendlyByteBuf(Unpooled.buffer());
        try {
            assertDoesNotThrow(() -> ClassificationSyncPayload.STREAM_CODEC.encode(buf1, payload),
                    "Encoding near-max-length strings exceeding 960 KiB must never throw an exception");
            int encodedBytes = buf1.readableBytes();
            assertTrue(encodedBytes <= ClassificationSyncPayload.MAX_PAYLOAD_BYTES,
                    "Encoded byte size (" + encodedBytes + ") must not exceed MAX_PAYLOAD_BYTES ("
                            + ClassificationSyncPayload.MAX_PAYLOAD_BYTES + ")");
            assertTrue(encodedBytes > 900_000,
                    "Final byte-size guard should pack entries up near the 960 KiB limit, got: " + encodedBytes);
            assertEquals(1, ClassificationSyncPayload.getTruncationWarningCount(),
                    "Final byte-size guard must emit warn-once log");
            assertEquals(java.util.Set.of("longmod"), ClassificationSyncPayload.getLastTruncatedNamespaces());

            // Second encode must not warn again
            assertDoesNotThrow(() -> ClassificationSyncPayload.STREAM_CODEC.encode(buf2, payload));
            assertEquals(1, ClassificationSyncPayload.getTruncationWarningCount());

            // Decoded payload must preserve the deterministic prefix cleanly
            ClassificationSyncPayload decoded = ClassificationSyncPayload.STREAM_CODEC.decode(buf1);
            assertTrue(decoded.datapackEntries().size() > 2000 && decoded.datapackEntries().size() < 5000);
            ResourceLocation firstKey = ResourceLocation.parse(String.format("longmod:%s_%05d", pathPad, 0));
            ResourceLocation lastKey = ResourceLocation.parse(String.format("longmod:%s_%05d", pathPad, 4999));
            assertTrue(decoded.datapackEntries().containsKey(firstKey), "Lexicographically first item must be kept");
            assertFalse(decoded.datapackEntries().containsKey(lastKey), "Lexicographically tail item must be dropped by byte guard");
        } finally {
            buf1.release();
            buf2.release();
        }
    }

    @Test
    void testClientDecoderRejectsMalformedWirePayloads() {
        // 1. String table count exceeds MAX_STRING_TABLE_ENTRIES
        FriendlyByteBuf badStringTableBuf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            badStringTableBuf.writeVarInt(ClassificationSyncPayload.MAX_STRING_TABLE_ENTRIES + 1);
            assertThrows(DecoderException.class, () -> ClassificationSyncPayload.STREAM_CODEC.decode(badStringTableBuf));
        } finally {
            badStringTableBuf.release();
        }

        // 2. Datapack item count exceeds MAX_DATAPACK_ITEMS
        FriendlyByteBuf badDatapackCountBuf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            badDatapackCountBuf.writeVarInt(0); // string table size = 0
            badDatapackCountBuf.writeVarInt(ClassificationSyncPayload.MAX_DATAPACK_ITEMS + 1);
            assertThrows(DecoderException.class, () -> ClassificationSyncPayload.STREAM_CODEC.decode(badDatapackCountBuf));
        } finally {
            badDatapackCountBuf.release();
        }

        // 3. Per-item candidate count exceeds MAX_CANDIDATES_PER_ITEM
        FriendlyByteBuf badCandidateCountBuf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            badCandidateCountBuf.writeVarInt(0); // string table size = 0
            badCandidateCountBuf.writeVarInt(1); // 1 datapack item
            badCandidateCountBuf.writeUtf("minecraft:apple");
            badCandidateCountBuf.writeVarInt(ClassificationSyncPayload.MAX_CANDIDATES_PER_ITEM + 1);
            assertThrows(DecoderException.class, () -> ClassificationSyncPayload.STREAM_CODEC.decode(badCandidateCountBuf));
        } finally {
            badCandidateCountBuf.release();
        }

        // 4. Out-of-bounds string table index throws DecoderException
        FriendlyByteBuf badStringIndexBuf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            badStringIndexBuf.writeVarInt(1); // string table size = 1 (valid index: 0)
            badStringIndexBuf.writeUtf("swine");
            badStringIndexBuf.writeVarInt(1); // 1 datapack item
            badStringIndexBuf.writeUtf("minecraft:porkchop");
            badStringIndexBuf.writeVarInt(1); // 1 candidate
            badStringIndexBuf.writeVarInt(FoodStatus.RESTRICTED.ordinal());
            badStringIndexBuf.writeVarInt(5); // INVALID reason string table index 5 >= 1
            badStringIndexBuf.writeVarInt(ClassificationSource.DATAPACK.ordinal());
            badStringIndexBuf.writeVarInt(0);
            badStringIndexBuf.writeVarInt(ClassificationPriority.DATAPACK.ordinal());
            badStringIndexBuf.writeVarInt(0);
            assertThrows(DecoderException.class, () -> ClassificationSyncPayload.STREAM_CODEC.decode(badStringIndexBuf));
        } finally {
            badStringIndexBuf.release();
        }
    }

    @Test
    void testReloadAndConfigResyncBroadcastsUpdatedSnapshotOnlyToModdedPlayers() {
        record DummyConnection(String username, boolean hasChannel) {}
        DummyConnection moddedPlayer = new DummyConnection("DevModded", true);
        DummyConnection vanillaPlayer = new DummyConnection("VanillaUser", false);

        List<ClassificationSyncPayload> receivedByModded = new ArrayList<>();

        // Initial login / datapack sync
        ResourceLocation bacon = ResourceLocation.parse("farmersdelight:bacon");
        FoodClassificationRegistry.setDatapackClassifications(Map.of(
                bacon, new FoodClassification(FoodStatus.RESTRICTED, "swine", ClassificationSource.DATAPACK)
        ));
        int sentCount1 = MuslimQolNetwork.sendToRecipients(
                Stream.of(moddedPlayer, vanillaPlayer),
                DummyConnection::hasChannel,
                (player, payload) -> {
                    if (player == moddedPlayer) {
                        receivedByModded.add(payload);
                    } else {
                        fail("Should not send payload to player without channel support");
                    }
                }
        );
        assertEquals(1, sentCount1);
        assertEquals(1, receivedByModded.size());
        assertEquals(1, receivedByModded.get(0).datapackEntries().size());
        assertTrue(receivedByModded.get(0).userOverrides().isEmpty());

        // Simulate /muslimqol reload or ModConfigEvent.Reloading adding a user override
        ResourceLocation beef = ResourceLocation.parse("minecraft:beef");
        FoodClassificationRegistry.registerUserOverride(
                beef, new FoodClassification(FoodStatus.HALAL, "zabiha", ClassificationSource.USER_OVERRIDE)
        );
        int sentCount2 = MuslimQolNetwork.sendToRecipients(
                Stream.of(moddedPlayer, vanillaPlayer),
                DummyConnection::hasChannel,
                (player, payload) -> receivedByModded.add(payload)
        );
        assertEquals(1, sentCount2);
        assertEquals(2, receivedByModded.size());
        assertEquals(FoodStatus.HALAL, receivedByModded.get(1).userOverrides().get(beef).status());
    }
}
