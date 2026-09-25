package io.github.muslimqol.compat;

import io.github.muslimqol.api.ClassificationProviderId;
import io.github.muslimqol.api.ClassificationResolution;
import io.github.muslimqol.api.FoodClassification;
import io.github.muslimqol.api.FoodClassificationCandidate;
import io.github.muslimqol.api.FoodClassificationProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable, thread-safe snapshot of active classification providers and compatibility metadata.
 * <p>
 * Provides both an allocation-light fast path for in-game hot paths (tooltips, overlays, eating checks)
 * and a full diagnostic resolution path for commands and debugging.
 */
public final class CompatibilitySnapshot {

    private static final Logger LOGGER = LoggerFactory.getLogger(CompatibilitySnapshot.class);

    private final List<FoodClassificationProvider> providers;
    private final List<List<FoodClassificationProvider>> tierGroups;
    private final ClassificationRuntimeState runtimeState;

    public CompatibilitySnapshot(
            List<FoodClassificationProvider> providers,
            ClassificationRuntimeState runtimeState
    ) {
        Objects.requireNonNull(providers, "providers must not be null");
        this.runtimeState = runtimeState != null ? runtimeState : ClassificationRuntimeState.EMPTY;

        // Sort providers deterministically: higher priority level first, then providerId lexicographically
        List<FoodClassificationProvider> sorted = new ArrayList<>(providers);
        sorted.sort((a, b) -> {
            int p = Integer.compare(b.priority().level(), a.priority().level());
            if (p != 0) return p;
            return a.id().compareTo(b.id());
        });
        this.providers = Collections.unmodifiableList(sorted);

        // Pre-group providers into priority tiers for fast-path short-circuiting
        Map<Integer, List<FoodClassificationProvider>> groups = new LinkedHashMap<>();
        for (FoodClassificationProvider p : this.providers) {
            groups.computeIfAbsent(p.priority().level(), k -> new ArrayList<>()).add(p);
        }
        List<List<FoodClassificationProvider>> tiers = new ArrayList<>();
        groups.values().forEach(list -> tiers.add(Collections.unmodifiableList(list)));
        this.tierGroups = Collections.unmodifiableList(tiers);
    }

    public CompatibilitySnapshot(
            List<FoodClassificationProvider> providers,
            Map<String, CompatibilityMetadata> activePacks,
            Map<String, CompatibilityMetadata> skippedPacks
    ) {
        this(providers, new ClassificationRuntimeState(Map.of(), Map.of(), activePacks, skippedPacks));
    }

    public List<FoodClassificationProvider> getProviders() {
        return providers;
    }

    public ClassificationRuntimeState getRuntimeState() {
        return runtimeState;
    }

    public Map<String, CompatibilityMetadata> getActivePacks() {
        return runtimeState.activePacks();
    }

    public Map<String, CompatibilityMetadata> getSkippedPacks() {
        return runtimeState.skippedPacks();
    }

    public ClassificationResolution resolve(ResourceLocation itemId) {
        return resolve(itemId, ItemStack.EMPTY);
    }

    /**
     * Resolves the effective food classification for an item, recording full diagnostics and conflict state.
     */
    public ClassificationResolution resolve(ResourceLocation itemId, ItemStack stack) {
        if (itemId == null) {
            return ClassificationResolution.ofSingle(FoodClassification.unknown());
        }

        List<FoodClassificationCandidate> candidates = new ArrayList<>();

        for (FoodClassificationProvider provider : providers) {
            try {
                List<FoodClassification> list = provider.classifyAll(itemId, stack);
                if (list != null && !list.isEmpty()) {
                    for (FoodClassification raw : list) {
                        if (raw == null) continue;
                        FoodClassification normalized = normalize(provider, raw);
                        candidates.add(new FoodClassificationCandidate(
                                normalized.providerId(),
                                normalized,
                                normalized.priority(),
                                normalized.ruleId()
                        ));
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("Classification provider '{}' encountered an error evaluating {}: {}",
                        provider.id(), itemId, e.getMessage());
            }
        }

        if (candidates.isEmpty()) {
            return new ClassificationResolution(FoodClassification.unknown(), List.of(), false);
        }

        // Sort candidates: descending priority level, then providerId tie-breaker, ruleId, status, reason
        Collections.sort(candidates);

        FoodClassificationCandidate winner = candidates.get(0);

        // Check for conflicts among all candidates at the winning priority tier
        boolean conflicted = false;
        int topLevel = winner.priority().level();
        for (int i = 1; i < candidates.size(); i++) {
            FoodClassificationCandidate cand = candidates.get(i);
            if (cand.priority().level() != topLevel) {
                break;
            }
            if (cand.classification().status() != winner.classification().status()) {
                conflicted = true;
                LOGGER.debug("Classification conflict for {}: winner is {} from '{}', but competing candidate {} has same priority from '{}'",
                        itemId, winner.classification().status(), winner.providerId(),
                        cand.classification().status(), cand.providerId());
            }
        }

        return new ClassificationResolution(winner.classification(), candidates, conflicted);
    }

    public FoodClassification classify(ResourceLocation itemId) {
        return classify(itemId, ItemStack.EMPTY);
    }

    /**
     * Allocation-light fast-path lookup returning only the resolved classification.
     * Evaluates strictly tier-by-tier and short-circuits immediately when a winning tier matches.
     */
    public FoodClassification classify(ResourceLocation itemId, ItemStack stack) {
        if (itemId == null) {
            return FoodClassification.unknown();
        }

        for (List<FoodClassificationProvider> tier : tierGroups) {
            FoodClassification tierWinner = null;
            FoodClassificationCandidate bestCandidate = null;

            for (FoodClassificationProvider provider : tier) {
                try {
                    List<FoodClassification> list = provider.classifyAll(itemId, stack);
                    if (list != null && !list.isEmpty()) {
                        for (FoodClassification raw : list) {
                            if (raw == null) continue;
                            FoodClassification normalized = normalize(provider, raw);
                            FoodClassificationCandidate cand = new FoodClassificationCandidate(
                                    normalized.providerId(),
                                    normalized,
                                    normalized.priority(),
                                    normalized.ruleId()
                            );
                            if (bestCandidate == null || cand.compareTo(bestCandidate) < 0) {
                                bestCandidate = cand;
                                tierWinner = normalized;
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warn("Classification provider '{}' encountered an error evaluating {}: {}",
                            provider.id(), itemId, e.getMessage());
                }
            }

            // If any provider in this priority tier matched, this tier definitively wins over all lower tiers
            if (tierWinner != null) {
                return tierWinner;
            }
        }

        return FoodClassification.unknown();
    }

    /**
     * Normalizes a returned classification against authoritative provider registration metadata.
     * Guarantees that a provider cannot impersonate an unauthorized priority tier or identity.
     */
    private FoodClassification normalize(FoodClassificationProvider provider, FoodClassification raw) {
        if (raw == null) {
            return FoodClassification.unknown();
        }

        boolean priorityMatches = raw.priority() == provider.priority();
        // Allow datapack multi-pack providers to specify their pack-specific namespace
        boolean idMatches = provider.id().equals(raw.providerId()) ||
                (provider.id().equals(ClassificationProviderId.DATAPACK) && !raw.providerId().equals(ClassificationProviderId.BUILTIN));

        if (priorityMatches && idMatches) {
            return raw;
        }

        LOGGER.debug("Normalizing classification for provider '{}': registered priority {} overrides returned priority {}",
                provider.id(), provider.priority(), raw.priority());

        return new FoodClassification(
                raw.status(),
                raw.reason(),
                raw.source(),
                idMatches ? raw.providerId() : provider.id(),
                provider.priority(), // registered provider priority is ALWAYS authoritative
                raw.ruleId()
        );
    }
}
