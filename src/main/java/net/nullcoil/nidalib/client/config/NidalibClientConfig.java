package net.nullcoil.nidalib.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.nullcoil.nidalib.Nidalib;
import net.nullcoil.nidalib.client.lava.LavaShaderBlend;
import net.nullcoil.nidalib.lava.LavaHeat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Nidalib's client settings, stored in config/nidalib-client.json. */
@Environment(EnvType.CLIENT)
public final class NidalibClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("nidalib-client.json");

    private static NidalibClientConfig instance;

    private int blendLevels = LavaShaderBlend.LEVEL_OPTIONS.getFirst();
    private LavaShaderBlend.Mode shaderBlendMode = LavaShaderBlend.Mode.DITHER;
    private int blendWidth = LavaHeat.FADE_DISTANCE_OPTIONS.getFirst();

    private NidalibClientConfig() {}

    public static synchronized NidalibClientConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    /** Steps the lava crossfade uses under shaders; always one of {@link LavaShaderBlend#LEVEL_OPTIONS}. */
    public int blendLevels() {
        return blendLevels;
    }

    public void setBlendLevels(int levels) {
        this.blendLevels = LavaShaderBlend.LEVEL_OPTIONS.contains(levels) ? levels : LavaShaderBlend.LEVEL_OPTIONS.getFirst();
    }

    /** How lava crossfades under shaders. */
    public LavaShaderBlend.Mode shaderBlendMode() {
        return shaderBlendMode;
    }

    public void setShaderBlendMode(LavaShaderBlend.Mode mode) {
        this.shaderBlendMode = mode;
    }

    /** How many blocks lava fades over at biome borders; always one of {@link LavaHeat#FADE_DISTANCE_OPTIONS}. */
    public int blendWidth() {
        return blendWidth;
    }

    /** Also applies it to {@link LavaHeat}, so it takes effect immediately. */
    public void setBlendWidth(int blocks) {
        this.blendWidth = LavaHeat.FADE_DISTANCE_OPTIONS.contains(blocks) ? blocks : LavaHeat.FADE_DISTANCE_OPTIONS.getFirst();
        LavaHeat.setFadeDistance(this.blendWidth);
    }

    public synchronized void save() {
        JsonObject json = new JsonObject();
        json.addProperty("blendLevels", blendLevels);
        json.addProperty("shaderBlend", shaderBlendMode.id);
        json.addProperty("blendWidth", blendWidth);
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(json));
        } catch (IOException e) {
            Nidalib.LOGGER.warn("Couldn't save {}", FILE, e);
        }
    }

    private static NidalibClientConfig load() {
        NidalibClientConfig config = new NidalibClientConfig();
        LavaHeat.setFadeDistance(config.blendWidth);
        if (Files.exists(FILE)) {
            try {
                JsonObject json = GSON.fromJson(Files.readString(FILE), JsonObject.class);
                if (json != null) {
                    // "ditherLevels" is what earlier versions called it
                    if (json.has("blendLevels")) config.setBlendLevels(json.get("blendLevels").getAsInt());
                    else if (json.has("ditherLevels")) config.setBlendLevels(json.get("ditherLevels").getAsInt());
                    if (json.has("blendWidth")) config.setBlendWidth(json.get("blendWidth").getAsInt());
                    if (json.has("shaderBlend")) config.setShaderBlendMode(LavaShaderBlend.Mode.byId(json.get("shaderBlend").getAsString()));
                }
            } catch (Exception e) {
                Nidalib.LOGGER.warn("Couldn't read {}, using defaults", FILE, e);
            }
        }
        return config;
    }
}
