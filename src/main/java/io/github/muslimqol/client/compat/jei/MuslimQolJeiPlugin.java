package io.github.muslimqol.client.compat.jei;

import io.github.muslimqol.MuslimQolMod;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-only JEI plugin for MuslimQoL.
 * <p>
 * This plugin is loaded dynamically by JEI when JEI is present on the client.
 * In NeoForge 1.21.1, JEI gathers ingredient tooltip lines via {@code ItemStack.getTooltipLines},
 * which dispatches NeoForge's {@code ItemTooltipEvent}. Therefore, MuslimQoL's standard tooltip
 * handler automatically provides food status lines in the JEI ingredient list, search results,
 * and recipe views without registering redundant tooltip callbacks that would cause duplication.
 */
@JeiPlugin
public class MuslimQolJeiPlugin implements IModPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger(MuslimQolJeiPlugin.class);
    public static final ResourceLocation PLUGIN_UID = ResourceLocation.fromNamespaceAndPath(MuslimQolMod.MOD_ID, "jei");

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        LOGGER.info("MuslimQoL JEI integration active [uid: {}]. Inheriting standard item tooltip classification.", PLUGIN_UID);
    }
}
