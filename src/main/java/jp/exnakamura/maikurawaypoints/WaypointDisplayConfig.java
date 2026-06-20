package jp.exnakamura.maikurawaypoints;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WaypointDisplayConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("maikura_waypoints_display.json");
    private static WaypointDisplayConfig INSTANCE;

    public boolean showDistance = true;
    public boolean showDimension = true;
    public boolean showCoordinates = true;
    public boolean enableManualSort = true;
    public boolean enableDistanceSort = true;
    public boolean enableNameSort = true;
    public boolean enableRegisteredSort = true;

    private WaypointDisplayConfig() {
    }

    public static WaypointDisplayConfig get() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                INSTANCE = GSON.fromJson(reader, WaypointDisplayConfig.class);
            } catch (Exception ignored) {
                INSTANCE = new WaypointDisplayConfig();
            }
        } else {
            INSTANCE = new WaypointDisplayConfig();
        }
        if (INSTANCE == null) {
            INSTANCE = new WaypointDisplayConfig();
        }
        INSTANCE.ensureAtLeastOneSort();
        INSTANCE.save();
    }

    public void save() {
        ensureAtLeastOneSort();
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException ignored) {
        }
    }

    public boolean canDisableSort(String id) {
        if (!isSortEnabled(id)) return true;
        return enabledSortCount() > 1;
    }

    public boolean isSortEnabled(String id) {
        return switch (id) {
            case "manual" -> enableManualSort;
            case "distance" -> enableDistanceSort;
            case "name" -> enableNameSort;
            case "registered" -> enableRegisteredSort;
            default -> true;
        };
    }

    public void toggleSort(String id) {
        if (isSortEnabled(id) && !canDisableSort(id)) {
            return;
        }
        switch (id) {
            case "manual" -> enableManualSort = !enableManualSort;
            case "distance" -> enableDistanceSort = !enableDistanceSort;
            case "name" -> enableNameSort = !enableNameSort;
            case "registered" -> enableRegisteredSort = !enableRegisteredSort;
        }
        ensureAtLeastOneSort();
        save();
    }

    public int enabledSortCount() {
        int count = 0;
        if (enableManualSort) count++;
        if (enableDistanceSort) count++;
        if (enableNameSort) count++;
        if (enableRegisteredSort) count++;
        return count;
    }

    public void ensureAtLeastOneSort() {
        if (enabledSortCount() <= 0) {
            enableDistanceSort = true;
        }
    }

}
