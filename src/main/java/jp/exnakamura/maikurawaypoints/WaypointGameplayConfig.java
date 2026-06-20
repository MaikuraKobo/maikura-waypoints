package jp.exnakamura.maikurawaypoints;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WaypointGameplayConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("maikura_waypoints.json");
    private static WaypointGameplayConfig INSTANCE;

    public boolean warpCostEnabled = true;
    public boolean returnCrystalEnabled = true;

    private WaypointGameplayConfig() {
    }

    public static WaypointGameplayConfig get() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                INSTANCE = GSON.fromJson(reader, WaypointGameplayConfig.class);
            } catch (Exception ignored) {
                INSTANCE = new WaypointGameplayConfig();
            }
        } else {
            INSTANCE = new WaypointGameplayConfig();
        }
        if (INSTANCE == null) {
            INSTANCE = new WaypointGameplayConfig();
        }
        INSTANCE.save();
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException ignored) {
        }
    }
}
