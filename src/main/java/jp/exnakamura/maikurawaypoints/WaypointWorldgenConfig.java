package jp.exnakamura.maikurawaypoints;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WaypointWorldgenConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("maikura_waypoints_worldgen.json");
    private static WaypointWorldgenConfig INSTANCE;

    /** Ancient shrine generation chance in percent. */
    public int generationChance = 1;
    /** Minimum horizontal distance between generated shrines. */
    public int minimumDistance = 768;
    /** Chance in percent to ignore the minimum distance check. 0 disables distance ignore. */
    public int ignoreDistanceChance = 0;
    /** Kept for config compatibility. Public builds do not emit debug logs. */
    public boolean debugLogging = false;

    private WaypointWorldgenConfig() {
    }

    public static WaypointWorldgenConfig get() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                INSTANCE = GSON.fromJson(reader, WaypointWorldgenConfig.class);
            } catch (Exception ignored) {
                INSTANCE = new WaypointWorldgenConfig();
            }
        } else {
            INSTANCE = new WaypointWorldgenConfig();
        }
        if (INSTANCE == null) {
            INSTANCE = new WaypointWorldgenConfig();
        }
        INSTANCE.sanitize();
        INSTANCE.save();
    }

    public void save() {
        sanitize();
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException ignored) {
        }
    }

    public void sanitize() {
        generationChance = clamp(generationChance, 0, 100);
        minimumDistance = Math.max(0, minimumDistance);
        ignoreDistanceChance = clamp(ignoreDistanceChance, 0, 100);
        debugLogging = false;
    }

    public boolean shouldIgnoreDistance(long mixedSeed) {
        sanitize();
        return ignoreDistanceChance > 0 && Math.floorMod(mixedSeed >>> 32, 100L) < ignoreDistanceChance;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
