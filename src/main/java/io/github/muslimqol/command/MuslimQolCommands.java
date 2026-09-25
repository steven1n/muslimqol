package io.github.muslimqol.command;

import com.mojang.brigadier.CommandDispatcher;
import io.github.muslimqol.api.FoodClassification;
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

/**
 * Commands for querying and managing MuslimQoL food classifications and status.
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
        FoodClassification classification = FoodClassifier.classify(itemId);

        source.sendSuccess(() -> Component.translatable(
                "commands.muslimqol.classify.result",
                itemId.toString(),
                classification.status().name(),
                classification.source().name(),
                classification.reason()
        ), false);
        return 1;
    }

    private static int reloadMod(CommandSourceStack source) {
        FoodClassificationRegistry.reloadUserOverridesFromConfig();
        source.sendSuccess(() -> Component.translatable("commands.muslimqol.reload.success"), true);
        return 1;
    }
}
