package io.github.muslimqol;

import io.github.muslimqol.client.ClientInit;
import io.github.muslimqol.command.MuslimQolCommands;
import io.github.muslimqol.config.ClientConfig;
import io.github.muslimqol.config.CommonConfig;
import io.github.muslimqol.data.FoodClassificationReloadListener;
import io.github.muslimqol.event.FoodConsumptionHandler;
import io.github.muslimqol.event.PigDropHandler;
import io.github.muslimqol.event.PigSpawnHandler;
import io.github.muslimqol.food.FoodClassificationRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for the MuslimQoL NeoForge mod.
 */
@Mod(MuslimQolMod.MOD_ID)
public class MuslimQolMod {

    public static final String MOD_ID = "muslimqol";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public MuslimQolMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Initializing MuslimQoL Mod (v0.1.0)");

        // Register Configurations
        modContainer.registerConfig(ModConfig.Type.COMMON, CommonConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);

        modEventBus.addListener(this::onConfigLoading);
        modEventBus.addListener(this::onConfigReloading);

        // Register Gameplay Event Handlers on Game Bus
        NeoForge.EVENT_BUS.register(FoodConsumptionHandler.class);
        NeoForge.EVENT_BUS.register(PigSpawnHandler.class);
        NeoForge.EVENT_BUS.register(PigDropHandler.class);
        NeoForge.EVENT_BUS.register(MuslimQolCommands.class);
        NeoForge.EVENT_BUS.addListener(this::onAddReloadListeners);

        // Client Initialization with strict client-only isolation (Rule 2)
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientInit.init(modEventBus);
        }
    }

    private void onConfigLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == CommonConfig.SPEC) {
            FoodClassificationRegistry.reloadUserOverridesFromConfig();
        }
    }

    private void onConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == CommonConfig.SPEC) {
            FoodClassificationRegistry.reloadUserOverridesFromConfig();
        }
    }

    private void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new FoodClassificationReloadListener());
    }
}
