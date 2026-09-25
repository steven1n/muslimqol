package io.github.muslimqol.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import io.github.muslimqol.api.FoodClassification;
import io.github.muslimqol.food.FoodClassificationRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Reload listener loading food classification JSON data from datapacks under `data/<namespace>/muslimqol/food_classifications/`.
 */
public class FoodClassificationReloadListener extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(FoodClassificationReloadListener.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public FoodClassificationReloadListener() {
        super(GSON, "muslimqol/food_classifications");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> jsonMap, ResourceManager resourceManager, ProfilerFiller profiler) {
        LOGGER.info("Applying food classifications from datapacks...");
        Map<ResourceLocation, FoodClassification> parsed = FoodClassificationJsonLoader.parseAll(jsonMap);
        FoodClassificationRegistry.setDatapackClassifications(parsed);
        FoodClassificationRegistry.reloadUserOverridesFromConfig();
    }
}
