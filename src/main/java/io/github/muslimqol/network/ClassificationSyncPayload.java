package io.github.muslimqol.network;

import io.github.muslimqol.api.ClassificationPriority;
import io.github.muslimqol.api.ClassificationProviderId;
import io.github.muslimqol.api.ClassificationRuleId;
import io.github.muslimqol.api.ClassificationSource;
import io.github.muslimqol.api.ConsumptionPolicy;
import io.github.muslimqol.api.FoodClassification;
import io.github.muslimqol.api.FoodStatus;
import io.github.muslimqol.compat.ClassificationRuntimeState;
import io.github.muslimqol.food.ClientSyncedClassificationState;
import io.github.muslimqol.food.FoodClassificationRegistry;
import io.github.muslimqol.food.FoodClassifier;
import io.github.muslimqol.util.ResourceLocationUtil;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Server-to-client custom payload synchronizing active {@code DATAPACK} and {@code USER_OVERRIDE}
 * food classifications along with the four server consumption policies.
 * <p>
 * Excludes server-only compatibility pack diagnostics ({@code activePacks}, {@code skippedPacks},
 * {@code packStates}) and deduplicates repeated {@code reason}, {@code providerId} ({@code sourceId}),
 * and {@code ruleId} strings via an indexed string table to stay well within Minecraft 1.21.1's
 * {@code ClientboundCustomPayloadPacket.MAX_PAYLOAD_SIZE} (1,048,576 bytes / 1 MiB).
 * <p>
 * If server state exceeds any configured entry, candidate, string-table, or byte-size limit, encoding
 * deterministically truncates excess entries and logs a warning whenever the set of affected
 * item namespaces changes. Any unexpected {@link RuntimeException} during encoding is caught,
 * logged via {@code LOGGER.error} with stack trace, and degraded to an empty snapshot
 * (已捕获运行时异常并降级为空快照).
 */
public record ClassificationSyncPayload(
        Map<ResourceLocation, List<FoodClassification>> datapackEntries,
        Map<ResourceLocation, FoodClassification> userOverrides,
        ConsumptionPolicy halalPolicy,
        ConsumptionPolicy restrictedPolicy,
        ConsumptionPolicy doubtfulPolicy,
        ConsumptionPolicy unknownPolicy
) implements CustomPacketPayload {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClassificationSyncPayload.class);

    public static final Type<ClassificationSyncPayload> TYPE =
            new Type<>(ResourceLocationUtil.modLoc("classification_sync"));

    /**
     * Vanilla 1.21.1 S2C custom payload hard limit ({@code ClientboundCustomPayloadPacket.MAX_PAYLOAD_SIZE}).
     */
    public static final int VANILLA_MAX_CUSTOM_PAYLOAD_BYTES = 1_048_576;

    /**
     * Maximum serialized payload size enforced by MuslimQoL (960 KiB = 983,040 bytes),
     * leaving 64 KiB headroom below {@link #VANILLA_MAX_CUSTOM_PAYLOAD_BYTES}.
     */
    public static final int MAX_PAYLOAD_BYTES = 960 * 1024;

    public static final int MAX_STRING_LENGTH = 256;
    public static final int MAX_STRING_TABLE_ENTRIES = 2_048;
    public static final int MAX_DATAPACK_ITEMS = 8_192;
    public static final int MAX_CANDIDATES_PER_ITEM = 8;
    public static final int MAX_TOTAL_CANDIDATES = 16_384;
    public static final int MAX_USER_OVERRIDES = 1_024;

    private static final AtomicInteger TRUNCATION_WARNING_COUNT = new AtomicInteger(0);
    private static final AtomicReference<Set<String>> LAST_TRUNCATED_NAMESPACES = new AtomicReference<>(Set.of());

    public static final StreamCodec<FriendlyByteBuf, ClassificationSyncPayload> STREAM_CODEC =
            StreamCodec.of(ClassificationSyncPayload::encode, ClassificationSyncPayload::decode);

    public ClassificationSyncPayload {
        Objects.requireNonNull(halalPolicy, "halalPolicy must not be null");
        Objects.requireNonNull(restrictedPolicy, "restrictedPolicy must not be null");
        Objects.requireNonNull(doubtfulPolicy, "doubtfulPolicy must not be null");
        Objects.requireNonNull(unknownPolicy, "unknownPolicy must not be null");

        if (datapackEntries == null || datapackEntries.isEmpty()) {
            datapackEntries = Map.of();
        } else {
            Map<ResourceLocation, List<FoodClassification>> copy = new LinkedHashMap<>();
            for (var entry : datapackEntries.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null && !entry.getValue().isEmpty()) {
                    copy.put(entry.getKey(), List.copyOf(entry.getValue()));
                }
            }
            datapackEntries = Collections.unmodifiableMap(copy);
        }

        if (userOverrides == null || userOverrides.isEmpty()) {
            userOverrides = Map.of();
        } else {
            userOverrides = Map.copyOf(userOverrides);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Converts this network payload into an immutable client-side classification state.
     */
    public ClientSyncedClassificationState toClientSyncedState() {
        return new ClientSyncedClassificationState(
                datapackEntries,
                userOverrides,
                halalPolicy,
                restrictedPolicy,
                doubtfulPolicy,
                unknownPolicy
        );
    }

    /**
     * Captures the current server-side classification runtime state and consumption policies.
     * Reads directly from {@link FoodClassificationRegistry#getRuntimeState()} and
     * {@link FoodClassifier#getServerPolicy(FoodStatus)} so server paths never read {@code CLIENT_SYNCED_STATE}.
     */
    public static ClassificationSyncPayload fromCurrentServerState() {
        ClassificationRuntimeState state = FoodClassificationRegistry.getRuntimeState();
        return new ClassificationSyncPayload(
                state.datapackEntries(),
                state.userOverrides(),
                FoodClassifier.getServerPolicy(FoodStatus.HALAL),
                FoodClassifier.getServerPolicy(FoodStatus.RESTRICTED),
                FoodClassifier.getServerPolicy(FoodStatus.DOUBTFUL),
                FoodClassifier.getServerPolicy(FoodStatus.UNKNOWN)
        );
    }

    /**
     * Resets the truncation warning state and last truncated namespace set (used in unit tests).
     */
    public static void resetTruncationWarningState() {
        TRUNCATION_WARNING_COUNT.set(0);
        LAST_TRUNCATED_NAMESPACES.set(Set.of());
    }

    /**
     * Returns the number of times a truncation warning has been emitted since startup or last reset.
     */
    public static int getTruncationWarningCount() {
        return TRUNCATION_WARNING_COUNT.get();
    }

    /**
     * Returns the sorted set of item namespaces affected by the most recent truncation warning.
     */
    public static Set<String> getLastTruncatedNamespaces() {
        return LAST_TRUNCATED_NAMESPACES.get();
    }

    private record EncodedEntry(ResourceLocation itemId, String itemKey, List<EncodedClassification> classifications, int wireBytes) {}

    private record EncodedClassification(
            FoodStatus status,
            int reasonIdx,
            ClassificationSource source,
            int providerIdx,
            ClassificationPriority priority,
            int ruleIdxPlusOne
    ) {
        int wireBytes() {
            return varIntBytes(status.ordinal())
                    + varIntBytes(reasonIdx)
                    + varIntBytes(source.ordinal())
                    + varIntBytes(providerIdx)
                    + varIntBytes(priority.ordinal())
                    + varIntBytes(ruleIdxPlusOne);
        }
    }

    private static void encode(FriendlyByteBuf buf, ClassificationSyncPayload payload) {
        int startWriterIndex = buf.writerIndex();
        try {
            Map<String, Integer> stringIndexMap = new LinkedHashMap<>();
            List<String> stringTable = new ArrayList<>();

            List<EncodedEntry> encodedOverrides = new ArrayList<>();
            List<EncodedEntry> encodedDatapack = new ArrayList<>();
            List<String> truncationReasons = new ArrayList<>();
            Set<String> truncatedNamespaces = new TreeSet<>();

            // Sort keys deterministically so any count-based or byte-guard truncation drops identical tail entries
            List<Map.Entry<ResourceLocation, FoodClassification>> overrideSource =
                    new ArrayList<>(payload.userOverrides().entrySet());
            if (overrideSource.size() > 1) {
                overrideSource.sort(Comparator.comparing(e -> e.getKey().toString()));
            }

            // 1. Encode user overrides first (highest priority tier: USER_OVERRIDE)
            int droppedOverrides = 0;
            int overrideEntriesBytes = 0;
            for (var entry : overrideSource) {
                String itemNamespace = entry.getKey().getNamespace();
                if (encodedOverrides.size() >= MAX_USER_OVERRIDES) {
                    droppedOverrides++;
                    truncatedNamespaces.add(itemNamespace);
                    continue;
                }
                String itemKey = entry.getKey().toString();
                if (!isValidWireUtf(itemKey)) {
                    droppedOverrides++;
                    truncatedNamespaces.add(itemNamespace);
                    continue;
                }
                EncodedClassification enc = tryInternClassification(
                        entry.getValue(), itemNamespace, stringIndexMap, stringTable, truncationReasons, truncatedNamespaces);
                if (enc == null) {
                    droppedOverrides++;
                    truncatedNamespaces.add(itemNamespace);
                    continue;
                }
                int entryWireBytes = utfWireBytes(itemKey) + enc.wireBytes();
                encodedOverrides.add(new EncodedEntry(entry.getKey(), itemKey, List.of(enc), entryWireBytes));
                overrideEntriesBytes += entryWireBytes;
            }
            if (droppedOverrides > 0) {
                truncationReasons.add("dropped " + droppedOverrides + " userOverrides (limit " + MAX_USER_OVERRIDES + ")");
            }

            // 2. Encode datapack entries (preserving winning candidates first)
            List<Map.Entry<ResourceLocation, List<FoodClassification>>> datapackSource =
                    new ArrayList<>(payload.datapackEntries().entrySet());
            if (datapackSource.size() > 1) {
                datapackSource.sort(Comparator.comparing(e -> e.getKey().toString()));
            }

            int totalCandidates = 0;
            int droppedItems = 0;
            int droppedCandidates = 0;
            int datapackEntriesBytes = 0;

            for (var entry : datapackSource) {
                String itemNamespace = entry.getKey().getNamespace();
                if (encodedDatapack.size() >= MAX_DATAPACK_ITEMS || totalCandidates >= MAX_TOTAL_CANDIDATES) {
                    droppedItems++;
                    truncatedNamespaces.add(itemNamespace);
                    continue;
                }
                String itemKey = entry.getKey().toString();
                if (!isValidWireUtf(itemKey)) {
                    droppedItems++;
                    truncatedNamespaces.add(itemNamespace);
                    continue;
                }
                List<FoodClassification> candidates = entry.getValue();
                List<EncodedClassification> accepted = new ArrayList<>(Math.min(candidates.size(), MAX_CANDIDATES_PER_ITEM));
                int candidatesBytes = 0;
                for (FoodClassification c : candidates) {
                    if (accepted.size() >= MAX_CANDIDATES_PER_ITEM || totalCandidates >= MAX_TOTAL_CANDIDATES) {
                        droppedCandidates++;
                        truncatedNamespaces.add(itemNamespace);
                        continue;
                    }
                    EncodedClassification enc = tryInternClassification(
                            c, itemNamespace, stringIndexMap, stringTable, truncationReasons, truncatedNamespaces);
                    if (enc == null) {
                        droppedCandidates++;
                        truncatedNamespaces.add(itemNamespace);
                        continue;
                    }
                    accepted.add(enc);
                    candidatesBytes += enc.wireBytes();
                    totalCandidates++;
                }
                if (!accepted.isEmpty()) {
                    int entryWireBytes = utfWireBytes(itemKey) + varIntBytes(accepted.size()) + candidatesBytes;
                    encodedDatapack.add(new EncodedEntry(entry.getKey(), itemKey, accepted, entryWireBytes));
                    datapackEntriesBytes += entryWireBytes;
                } else {
                    droppedItems++;
                    truncatedNamespaces.add(itemNamespace);
                }
            }

            if (droppedItems > 0) {
                truncationReasons.add("dropped " + droppedItems + " datapack items (limit " + MAX_DATAPACK_ITEMS
                        + ", total candidate limit " + MAX_TOTAL_CANDIDATES + ")");
            }
            if (droppedCandidates > 0) {
                truncationReasons.add("dropped " + droppedCandidates + " extra datapack candidates (per-item limit "
                        + MAX_CANDIDATES_PER_ITEM + ", total limit " + MAX_TOTAL_CANDIDATES
                        + ", string table limit " + MAX_STRING_TABLE_ENTRIES + ")");
            }

            // 3. Final byte-size guard (最终体积守卫): if worst-case long strings cause total serialized bytes
            // to exceed MAX_PAYLOAD_BYTES (960 KiB), deterministically drop tail entries and re-compact
            // the string table until the payload fits strictly within MAX_PAYLOAD_BYTES.
            int stringTableBytes = 0;
            for (String s : stringTable) {
                stringTableBytes += utfWireBytes(s);
            }

            int droppedByByteGuard = 0;
            if (computeTotalWireBytes(
                    stringTableBytes, stringTable.size(),
                    datapackEntriesBytes, encodedDatapack.size(),
                    overrideEntriesBytes, encodedOverrides.size(),
                    payload) > MAX_PAYLOAD_BYTES) {
                int[] stringRefCounts = new int[stringTable.size()];
                for (EncodedEntry e : encodedOverrides) {
                    incrementStringRefs(e, stringRefCounts);
                }
                for (EncodedEntry e : encodedDatapack) {
                    incrementStringRefs(e, stringRefCounts);
                }
                int activeStringCount = stringTable.size();

                while (computeTotalWireBytes(
                        stringTableBytes, activeStringCount,
                        datapackEntriesBytes, encodedDatapack.size(),
                        overrideEntriesBytes, encodedOverrides.size(),
                        payload) > MAX_PAYLOAD_BYTES
                        && (!encodedDatapack.isEmpty() || !encodedOverrides.isEmpty())) {
                    EncodedEntry removed;
                    if (!encodedDatapack.isEmpty()) {
                        removed = encodedDatapack.remove(encodedDatapack.size() - 1);
                        datapackEntriesBytes -= removed.wireBytes();
                    } else {
                        removed = encodedOverrides.remove(encodedOverrides.size() - 1);
                        overrideEntriesBytes -= removed.wireBytes();
                    }
                    for (EncodedClassification c : removed.classifications()) {
                        if (--stringRefCounts[c.reasonIdx()] == 0) {
                            stringTableBytes -= utfWireBytes(stringTable.get(c.reasonIdx()));
                            activeStringCount--;
                        }
                        if (--stringRefCounts[c.providerIdx()] == 0) {
                            stringTableBytes -= utfWireBytes(stringTable.get(c.providerIdx()));
                            activeStringCount--;
                        }
                        if (c.ruleIdxPlusOne() > 0) {
                            int ruleIdx = c.ruleIdxPlusOne() - 1;
                            if (--stringRefCounts[ruleIdx] == 0) {
                                stringTableBytes -= utfWireBytes(stringTable.get(ruleIdx));
                                activeStringCount--;
                            }
                        }
                    }
                    truncatedNamespaces.add(removed.itemId().getNamespace());
                    droppedByByteGuard++;
                }
                // Compact unused strings from stringTable if tail entries were dropped
                CompactResult compacted = compactStringTable(stringTable, encodedOverrides, encodedDatapack);
                stringTable = compacted.stringTable();
                encodedOverrides = compacted.encodedOverrides();
                encodedDatapack = compacted.encodedDatapack();
            }

            if (droppedByByteGuard > 0) {
                truncationReasons.add("dropped " + droppedByByteGuard
                        + " entries via final byte-size guard to stay within " + MAX_PAYLOAD_BYTES + " bytes");
            }

            if (!truncationReasons.isEmpty()) {
                warnIfTruncatedNamespacesChanged(
                        String.join("; ", truncationReasons),
                        Collections.unmodifiableSet(new TreeSet<>(truncatedNamespaces))
                );
            } else {
                LAST_TRUNCATED_NAMESPACES.set(Set.of());
            }

            // Write deduplicated string table (reason / providerId / ruleId)
            buf.writeVarInt(stringTable.size());
            for (String s : stringTable) {
                buf.writeUtf(s, MAX_STRING_LENGTH);
            }

            // Write datapack entries
            buf.writeVarInt(encodedDatapack.size());
            for (EncodedEntry entry : encodedDatapack) {
                buf.writeUtf(entry.itemKey(), MAX_STRING_LENGTH);
                List<EncodedClassification> candidates = entry.classifications();
                buf.writeVarInt(candidates.size());
                for (EncodedClassification c : candidates) {
                    writeEncodedClassification(buf, c);
                }
            }

            // Write user overrides
            buf.writeVarInt(encodedOverrides.size());
            for (EncodedEntry entry : encodedOverrides) {
                buf.writeUtf(entry.itemKey(), MAX_STRING_LENGTH);
                writeEncodedClassification(buf, entry.classifications().get(0));
            }

            // Write 4 consumption policies
            buf.writeVarInt(payload.halalPolicy().ordinal());
            buf.writeVarInt(payload.restrictedPolicy().ordinal());
            buf.writeVarInt(payload.doubtfulPolicy().ordinal());
            buf.writeVarInt(payload.unknownPolicy().ordinal());
        } catch (RuntimeException e) {
            // Runtime exception fail-safe (已捕获运行时异常并降级为空快照):
            // reset writerIndex, log full stack trace (not subject to warn-once), and write empty snapshot
            buf.writerIndex(startWriterIndex);
            LOGGER.error("Unexpected RuntimeException while encoding ClassificationSyncPayload; falling back to empty snapshot", e);
            buf.writeVarInt(0);
            buf.writeVarInt(0);
            buf.writeVarInt(0);
            buf.writeVarInt(payload.halalPolicy().ordinal());
            buf.writeVarInt(payload.restrictedPolicy().ordinal());
            buf.writeVarInt(payload.doubtfulPolicy().ordinal());
            buf.writeVarInt(payload.unknownPolicy().ordinal());
        }
    }

    private static void incrementStringRefs(EncodedEntry entry, int[] stringRefCounts) {
        for (EncodedClassification c : entry.classifications()) {
            stringRefCounts[c.reasonIdx()]++;
            stringRefCounts[c.providerIdx()]++;
            if (c.ruleIdxPlusOne() > 0) {
                stringRefCounts[c.ruleIdxPlusOne() - 1]++;
            }
        }
    }

    private record CompactResult(
            List<String> stringTable,
            List<EncodedEntry> encodedOverrides,
            List<EncodedEntry> encodedDatapack
    ) {}

    private static CompactResult compactStringTable(
            List<String> oldTable,
            List<EncodedEntry> overrides,
            List<EncodedEntry> datapack
    ) {
        Map<String, Integer> newIndexMap = new LinkedHashMap<>();
        List<String> newTable = new ArrayList<>();

        List<EncodedEntry> reindexedOverrides = new ArrayList<>(overrides.size());
        for (EncodedEntry entry : overrides) {
            EncodedClassification oldC = entry.classifications().get(0);
            EncodedClassification newC = reindexClassification(oldC, oldTable, newIndexMap, newTable);
            int wireBytes = utfWireBytes(entry.itemKey()) + newC.wireBytes();
            reindexedOverrides.add(new EncodedEntry(entry.itemId(), entry.itemKey(), List.of(newC), wireBytes));
        }

        List<EncodedEntry> reindexedDatapack = new ArrayList<>(datapack.size());
        for (EncodedEntry entry : datapack) {
            List<EncodedClassification> newList = new ArrayList<>(entry.classifications().size());
            int candidatesBytes = 0;
            for (EncodedClassification oldC : entry.classifications()) {
                EncodedClassification newC = reindexClassification(oldC, oldTable, newIndexMap, newTable);
                newList.add(newC);
                candidatesBytes += newC.wireBytes();
            }
            int wireBytes = utfWireBytes(entry.itemKey()) + varIntBytes(newList.size()) + candidatesBytes;
            reindexedDatapack.add(new EncodedEntry(entry.itemId(), entry.itemKey(), newList, wireBytes));
        }

        return new CompactResult(newTable, reindexedOverrides, reindexedDatapack);
    }

    private static EncodedClassification reindexClassification(
            EncodedClassification oldC,
            List<String> oldTable,
            Map<String, Integer> newIndexMap,
            List<String> newTable
    ) {
        int reasonIdx = internOrGet(oldTable.get(oldC.reasonIdx()), newIndexMap, newTable);
        int providerIdx = internOrGet(oldTable.get(oldC.providerIdx()), newIndexMap, newTable);
        int ruleIdxPlusOne = 0;
        if (oldC.ruleIdxPlusOne() > 0) {
            ruleIdxPlusOne = internOrGet(oldTable.get(oldC.ruleIdxPlusOne() - 1), newIndexMap, newTable) + 1;
        }
        return new EncodedClassification(
                oldC.status(),
                reasonIdx,
                oldC.source(),
                providerIdx,
                oldC.priority(),
                ruleIdxPlusOne
        );
    }

    private static int computeTotalWireBytes(
            int stringTableBytes,
            int stringTableCount,
            int datapackEntriesBytes,
            int datapackCount,
            int overrideEntriesBytes,
            int overrideCount,
            ClassificationSyncPayload payload
    ) {
        return varIntBytes(stringTableCount) + stringTableBytes
                + varIntBytes(datapackCount) + datapackEntriesBytes
                + varIntBytes(overrideCount) + overrideEntriesBytes
                + varIntBytes(payload.halalPolicy().ordinal())
                + varIntBytes(payload.restrictedPolicy().ordinal())
                + varIntBytes(payload.doubtfulPolicy().ordinal())
                + varIntBytes(payload.unknownPolicy().ordinal());
    }

    private static boolean isValidWireUtf(String s) {
        return s != null && s.length() <= MAX_STRING_LENGTH
                && io.netty.buffer.ByteBufUtil.utf8Bytes(s) <= MAX_STRING_LENGTH * 3;
    }

    private static int utfWireBytes(String s) {
        int utf8Len = io.netty.buffer.ByteBufUtil.utf8Bytes(s);
        return varIntBytes(utf8Len) + utf8Len;
    }

    private static int varIntBytes(int value) {
        if ((value & -128) == 0) return 1;
        if ((value & -16384) == 0) return 2;
        if ((value & -2097152) == 0) return 3;
        if ((value & -268435456) == 0) return 4;
        return 5;
    }

    private static void warnIfTruncatedNamespacesChanged(String summary, Set<String> truncatedNamespaces) {
        Set<String> previous = LAST_TRUNCATED_NAMESPACES.getAndSet(truncatedNamespaces);
        if (!truncatedNamespaces.equals(previous)) {
            TRUNCATION_WARNING_COUNT.incrementAndGet();
            LOGGER.warn("ClassificationSyncPayload exceeded network bounds and was truncated before sending: "
                            + "{}; truncatedNamespaces={}. Server-side enforcement remains active, but truncated items may display as UNKNOWN on the client.",
                    summary, truncatedNamespaces);
        }
    }

    private static EncodedClassification tryInternClassification(
            FoodClassification c,
            String itemNamespace,
            Map<String, Integer> stringIndexMap,
            List<String> stringTable,
            List<String> truncationReasons,
            Set<String> truncatedNamespaces
    ) {
        int reasonsBefore = truncationReasons.size();
        String reason = clampString(c.reason(), "reason", truncationReasons);
        String providerStr = c.providerId().toString();
        if (!isValidWireUtf(providerStr)) {
            providerStr = ClassificationProviderId.DATAPACK.toString();
            truncationReasons.add("clamped overlong providerId");
        }
        String ruleStr = null;
        if (c.ruleId() != null) {
            String rawRule = c.ruleId().toString();
            if (isValidWireUtf(rawRule)) {
                ruleStr = rawRule;
            } else {
                truncationReasons.add("omitted overlong ruleId");
            }
        }

        // Count how many new string table slots are needed for (reason, providerStr)
        int neededRequired = 0;
        if (!stringIndexMap.containsKey(reason)) neededRequired++;
        if (!stringIndexMap.containsKey(providerStr) && !providerStr.equals(reason)) neededRequired++;

        if (stringTable.size() + neededRequired > MAX_STRING_TABLE_ENTRIES) {
            return null;
        }

        int reasonIdx = internOrGet(reason, stringIndexMap, stringTable);
        int providerIdx = internOrGet(providerStr, stringIndexMap, stringTable);
        int ruleIdxPlusOne = 0;
        if (ruleStr != null) {
            Integer existingRule = stringIndexMap.get(ruleStr);
            if (existingRule != null) {
                ruleIdxPlusOne = existingRule + 1;
            } else if (stringTable.size() < MAX_STRING_TABLE_ENTRIES) {
                ruleIdxPlusOne = internOrGet(ruleStr, stringIndexMap, stringTable) + 1;
            } else {
                truncationReasons.add("omitted ruleId due to full string table");
            }
        }

        if (truncationReasons.size() > reasonsBefore) {
            truncatedNamespaces.add(itemNamespace);
        }

        return new EncodedClassification(
                c.status(),
                reasonIdx,
                c.source(),
                providerIdx,
                c.priority(),
                ruleIdxPlusOne
        );
    }

    private static String clampString(String value, String fieldName, List<String> truncationReasons) {
        if (value == null) {
            return "unclassified";
        }
        if (isValidWireUtf(value)) {
            return value;
        }
        int end = Math.min(value.length(), MAX_STRING_LENGTH);
        if (end > 0 && Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        String clamped = value.substring(0, end);
        truncationReasons.add("truncated " + fieldName + " from " + value.length() + " to " + clamped.length() + " chars");
        return clamped;
    }

    private static int internOrGet(
            String value,
            Map<String, Integer> stringIndexMap,
            List<String> stringTable
    ) {
        Integer existing = stringIndexMap.get(value);
        if (existing != null) {
            return existing;
        }
        int idx = stringTable.size();
        stringTable.add(value);
        stringIndexMap.put(value, idx);
        return idx;
    }

    private static void writeEncodedClassification(FriendlyByteBuf buf, EncodedClassification c) {
        buf.writeVarInt(c.status().ordinal());
        buf.writeVarInt(c.reasonIdx());
        buf.writeVarInt(c.source().ordinal());
        buf.writeVarInt(c.providerIdx());
        buf.writeVarInt(c.priority().ordinal());
        buf.writeVarInt(c.ruleIdxPlusOne());
    }

    private static ClassificationSyncPayload decode(FriendlyByteBuf buf) {
        if (buf.readableBytes() > MAX_PAYLOAD_BYTES) {
            throw new DecoderException("ClassificationSyncPayload readable bytes " + buf.readableBytes()
                    + " exceeds maximum allowed " + MAX_PAYLOAD_BYTES + " bytes");
        }
        int startReaderIndex = buf.readerIndex();

        // 1. Read deduplicated string table (validate count before allocating list)
        int stringTableSize = buf.readVarInt();
        if (stringTableSize < 0 || stringTableSize > MAX_STRING_TABLE_ENTRIES || stringTableSize > buf.readableBytes()) {
            throw new DecoderException("String table size " + stringTableSize
                    + " out of bounds [0, " + MAX_STRING_TABLE_ENTRIES + "]");
        }
        List<String> stringTable = new ArrayList<>(stringTableSize);
        for (int i = 0; i < stringTableSize; i++) {
            stringTable.add(buf.readUtf(MAX_STRING_LENGTH));
        }

        // 2. Read datapack entries (validate count before allocating map)
        int datapackCount = buf.readVarInt();
        if (datapackCount < 0 || datapackCount > MAX_DATAPACK_ITEMS || datapackCount > buf.readableBytes()) {
            throw new DecoderException("datapackEntries count " + datapackCount
                    + " out of bounds [0, " + MAX_DATAPACK_ITEMS + "]");
        }
        Map<ResourceLocation, List<FoodClassification>> datapackEntries = new LinkedHashMap<>(Math.min(datapackCount, 1024));
        int totalCandidates = 0;
        for (int i = 0; i < datapackCount; i++) {
            ResourceLocation itemId = readResourceLocationBounded(buf, "datapack itemId");
            int candidateCount = buf.readVarInt();
            if (candidateCount <= 0 || candidateCount > MAX_CANDIDATES_PER_ITEM || candidateCount > buf.readableBytes()) {
                throw new DecoderException("Candidate count " + candidateCount + " for " + itemId
                        + " out of bounds [1, " + MAX_CANDIDATES_PER_ITEM + "]");
            }
            totalCandidates += candidateCount;
            if (totalCandidates > MAX_TOTAL_CANDIDATES) {
                throw new DecoderException("Total datapack candidates " + totalCandidates
                        + " exceeds max " + MAX_TOTAL_CANDIDATES);
            }
            List<FoodClassification> candidates = new ArrayList<>(candidateCount);
            for (int j = 0; j < candidateCount; j++) {
                candidates.add(readClassification(buf, stringTable));
            }
            datapackEntries.put(itemId, Collections.unmodifiableList(candidates));
        }

        // 3. Read user overrides (validate count before allocating map)
        int overrideCount = buf.readVarInt();
        if (overrideCount < 0 || overrideCount > MAX_USER_OVERRIDES || overrideCount > buf.readableBytes()) {
            throw new DecoderException("userOverrides count " + overrideCount
                    + " out of bounds [0, " + MAX_USER_OVERRIDES + "]");
        }
        Map<ResourceLocation, FoodClassification> userOverrides = new LinkedHashMap<>(Math.min(overrideCount, 256));
        for (int i = 0; i < overrideCount; i++) {
            ResourceLocation itemId = readResourceLocationBounded(buf, "override itemId");
            userOverrides.put(itemId, readClassification(buf, stringTable));
        }

        // 4. Read 4 consumption policies
        ConsumptionPolicy halalPolicy = readEnumBounded(buf, ConsumptionPolicy.values(), "halalPolicy");
        ConsumptionPolicy restrictedPolicy = readEnumBounded(buf, ConsumptionPolicy.values(), "restrictedPolicy");
        ConsumptionPolicy doubtfulPolicy = readEnumBounded(buf, ConsumptionPolicy.values(), "doubtfulPolicy");
        ConsumptionPolicy unknownPolicy = readEnumBounded(buf, ConsumptionPolicy.values(), "unknownPolicy");

        int consumedBytes = buf.readerIndex() - startReaderIndex;
        if (consumedBytes > MAX_PAYLOAD_BYTES) {
            throw new DecoderException("ClassificationSyncPayload consumed " + consumedBytes
                    + " bytes, exceeding maximum allowed " + MAX_PAYLOAD_BYTES + " bytes");
        }

        return new ClassificationSyncPayload(
                datapackEntries,
                userOverrides,
                halalPolicy,
                restrictedPolicy,
                doubtfulPolicy,
                unknownPolicy
        );
    }

    private static FoodClassification readClassification(FriendlyByteBuf buf, List<String> stringTable) {
        FoodStatus status = readEnumBounded(buf, FoodStatus.values(), "FoodStatus");
        String reason = readStringTableEntry(buf, stringTable, "reason");
        ClassificationSource source = readEnumBounded(buf, ClassificationSource.values(), "ClassificationSource");
        String providerStr = readStringTableEntry(buf, stringTable, "providerId");
        ClassificationProviderId providerId;
        try {
            providerId = ClassificationProviderId.parse(providerStr);
        } catch (IllegalArgumentException e) {
            throw new DecoderException("Invalid providerId in sync payload: " + providerStr, e);
        }
        ClassificationPriority priority = readEnumBounded(buf, ClassificationPriority.values(), "ClassificationPriority");
        int ruleIndexPlusOne = buf.readVarInt();
        ClassificationRuleId ruleId = null;
        if (ruleIndexPlusOne < 0 || ruleIndexPlusOne > stringTable.size()) {
            throw new DecoderException("ruleId string table index out of bounds: " + ruleIndexPlusOne);
        }
        if (ruleIndexPlusOne > 0) {
            String ruleStr = stringTable.get(ruleIndexPlusOne - 1);
            try {
                ruleId = ClassificationRuleId.parse(ruleStr);
            } catch (IllegalArgumentException e) {
                throw new DecoderException("Invalid ruleId in sync payload: " + ruleStr, e);
            }
        }
        return new FoodClassification(status, reason, source, providerId, priority, ruleId);
    }

    private static String readStringTableEntry(FriendlyByteBuf buf, List<String> stringTable, String fieldName) {
        int index = buf.readVarInt();
        if (index < 0 || index >= stringTable.size()) {
            throw new DecoderException(fieldName + " string table index " + index
                    + " out of bounds [0, " + stringTable.size() + ")");
        }
        return stringTable.get(index);
    }

    private static ResourceLocation readResourceLocationBounded(FriendlyByteBuf buf, String fieldName) {
        String raw = buf.readUtf(MAX_STRING_LENGTH);
        if (raw.length() > MAX_STRING_LENGTH) {
            throw new DecoderException(fieldName + " length " + raw.length() + " exceeds max " + MAX_STRING_LENGTH);
        }
        return ResourceLocationUtil.tryParse(raw)
                .orElseThrow(() -> new DecoderException("Invalid ResourceLocation for " + fieldName + ": " + raw));
    }

    private static <E extends Enum<E>> E readEnumBounded(FriendlyByteBuf buf, E[] values, String fieldName) {
        int ordinal = buf.readVarInt();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new DecoderException("Invalid " + fieldName + " ordinal: " + ordinal);
        }
        return values[ordinal];
    }
}
