package io.github.muslimqol.command;

import com.mojang.brigadier.CommandDispatcher;
import io.github.muslimqol.api.ClassificationResolution;
import io.github.muslimqol.api.FoodClassification;
import io.github.muslimqol.api.FoodClassificationCandidate;
import io.github.muslimqol.api.FoodClassificationProvider;
import io.github.muslimqol.compat.CompatibilityMetadata;
import io.github.muslimqol.compat.CompatibilitySnapshot;
import io.github.muslimqol.compat.FoodCompatibilityManager;
import io.github.muslimqol.config.CommonConfig;
import io.github.muslimqol.food.FoodClassificationRegistry;
import io.github.muslimqol.food.FoodClassifier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Map;

/**
 * Commands for querying and managing MuslimQoL food classifications, providers, and status.
 */
public class MuslimQolCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("muslimqol")
                        .then(Commands.literal("status")
                                .executes(ctx -> showStatus(ctx.getSource()))
                        )
                        .then(Commands.literal("classify")
                                .then(Commands.argument("item", ItemArgument.item(event.getBuildContext()))
                                        .executes(ctx -> classifyItem(ctx.getSource(), ItemArgument.getItem(ctx, "item")))
                                )
                        )
                        .then(Commands.literal("providers")
                                .executes(ctx -> showProviders(ctx.getSource()))
                        )
                        .then(Commands.literal("compat")
                                .executes(ctx -> showProviders(ctx.getSource()))
                        )
                        .then(Commands.literal("reload")
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> reloadMod(ctx.getSource()))
                        )
        );
    }

    private static int showStatus(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable(
                "commands.muslimqol.status",
                CommonConfig.HALAL_POLICY.get().name(),
                CommonConfig.RESTRICTED_POLICY.get().name(),
                CommonConfig.DOUBTFUL_POLICY.get().name(),
                CommonConfig.UNKNOWN_POLICY.get().name(),
                CommonConfig.PIG_POLICY.get().name()
        ), false);
        return 1;
    }

    private static int classifyItem(CommandSourceStack source, ItemInput itemInput) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemInput.getItem());
        ClassificationResolution resolution = FoodClassifier.resolve(itemId);
        FoodClassification winner = resolution.selected();

        source.sendSuccess(() -> Component.translatable(
                "commands.muslimqol.classify.header",
                itemId.toString(),
                winner.status().name()
        ), false);

        if (resolution.candidates().isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.muslimqol.classify.winner_fallback"), false);
            source.sendSuccess(() -> Component.translatable("commands.muslimqol.classify.candidates_none"), false);
        } else {
            source.sendSuccess(() -> Component.translatable(
                    "commands.muslimqol.classify.winner",
                    winner.providerId().toString(),
                    winner.source().name(),
                    winner.priority().name(),
                    winner.reason()
            ), false);

            if (resolution.candidates().size() > 1) {
                source.sendSuccess(() -> Component.translatable("commands.muslimqol.classify.candidates_header"), false);
                for (FoodClassificationCandidate candidate : resolution.candidates()) {
                    boolean isWinner = candidate.classification() == winner;
                    String tag = isWinner ? " [WINNER]" : " (priority: " + candidate.priority().name() + ")";
                    source.sendSuccess(() -> Component.literal("  " + candidate.classification().status().name()
                            + " <- " + candidate.providerId().toString() + tag), false);
                }
            } else {
                source.sendSuccess(() -> Component.literal("Candidates:\n  " + winner.status().name() + " <- " + winner.providerId().toString()), false);
            }
        }

        String conflictStr = resolution.conflicted() ? "YES" : "no";
        source.sendSuccess(() -> Component.translatable("commands.muslimqol.classify.conflict", conflictStr), false);

        return 1;
    }

    private static int showProviders(CommandSourceStack source) {
        CompatibilitySnapshot snapshot = FoodCompatibilityManager.getActiveSnapshot();
        source.sendSuccess(() -> Component.translatable("commands.muslimqol.providers.header"), false);

        for (FoodClassificationProvider provider : snapshot.getProviders()) {
            source.sendSuccess(() -> Component.literal("  " + provider.id().toString() + " (" + provider.priority().name() + ")"), false);
        }

        Map<String, CompatibilityMetadata> active = snapshot.getActivePacks();
        Map<String, CompatibilityMetadata> skipped = snapshot.getSkippedPacks();

        if (!active.isEmpty() || !skipped.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.muslimqol.providers.packs_header"), false);
            for (Map.Entry<String, CompatibilityMetadata> entry : active.entrySet()) {
                CompatibilityMetadata meta = entry.getValue();
                String target = meta.targetMod() != null ? " [target: " + meta.targetMod() + "]" : "";
                source.sendSuccess(() -> Component.literal("  [Active] " + meta.name() + " (" + entry.getKey() + ")" + target), false);
            }
            for (Map.Entry<String, CompatibilityMetadata> entry : skipped.entrySet()) {
                CompatibilityMetadata meta = entry.getValue();
                String missing = meta.targetMod() != null ? " [missing mod: " + meta.targetMod() + "]" : "";
                source.sendSuccess(() -> Component.literal("  [Skipped] " + meta.name() + " (" + entry.getKey() + ")" + missing), false);
            }
        }

        return 1;
    }

    private static int reloadMod(CommandSourceStack source) {
        FoodClassificationRegistry.reloadUserOverridesFromConfig();
        source.sendSuccess(() -> Component.translatable("commands.muslimqol.reload.success"), true);
        return 1;
    }
}
