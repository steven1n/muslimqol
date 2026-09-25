package io.github.muslimqol.compat;

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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable, thread-safe snapshot of active classification providers and compatibility metadata.
 */
public final class CompatibilitySnapshot {

    private static final Logger LOGGER = LoggerFactory.getLogger(CompatibilitySnapshot.class);

    private final List<FoodClassificationProvider> providers;
    private final Map<String, CompatibilityMetadata> activePacks;
    private final Map<String, CompatibilityMetadata> skippedPacks;

    public CompatibilitySnapshot(
            List<FoodClassificationProvider> providers,
            Map<String, CompatibilityMetadata> activePacks,
            Map<String, CompatibilityMetadata> skippedPacks
    ) {
        Objects.requireNonNull(providers, "providers must not be null");
        // Sort providers deterministically: higher priority tier first, then providerId
        List<FoodClassificationProvider> sorted = new ArrayList<>(providers);
        sorted.sort((a, b) -> {
            int p = Integer.compare(b.priority().level(), a.priority().level());
            if (p != 0) return p;
            return a.id().compareTo(b.id());
        });
        this.providers = Collections.unmodifiableList(sorted);
        this.activePacks = activePacks == null ? Map.of() : Map.copyOf(activePacks);
        this.skippedPacks = skippedPacks == null ? Map.of() : Map.copyOf(skippedPacks);
    }

    public List<FoodClassificationProvider> getProviders() {
        return providers;
    }

    public Map<String, CompatibilityMetadata> getActivePacks() {
        return activePacks;
    }

    public Map<String, CompatibilityMetadata> getSkippedPacks() {
        return skippedPacks;
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
                Optional<FoodClassification> opt = provider.classify(itemId, stack);
                if (opt.isPresent()) {
                    FoodClassification classification = opt.get();
                    candidates.add(new FoodClassificationCandidate(
                            provider.id(),
                            classification,
                            provider.priority()
                    ));
                }
            } catch (Throwable t) {
                LOGGER.warn("Classification provider '{}' encountered an error evaluating {}: {}",
                        provider.id(), itemId, t.getMessage());
            }
        }

        if (candidates.isEmpty()) {
            return new ClassificationResolution(FoodClassification.unknown(), List.of(), false);
        }

        // Sort candidates: descending priority level, then providerId tie-breaker
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

    /**
     * Fast-path lookup returning only the resolved classification.
     */
    public FoodClassification classify(ResourceLocation itemId, ItemStack stack) {
        return resolve(itemId, stack).selected();
    }
}
