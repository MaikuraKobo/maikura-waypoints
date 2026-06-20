package jp.exnakamura.maikurawaypoints;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.io.Reader;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maikura GUI Editor Core 連携用ブリッジ。
 * GUI Editor Core が入っていない環境でも Waypoints 単体で起動できるように reflection で呼び出す。
 */
public final class GuiEditorBridge {
    private static final String MOD_ID = "maikura_gui_editor";
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("maikura_gui_editor");
    private static final Map<String, Map<String, SavedElement>> FALLBACK_LAYOUTS = new LinkedHashMap<>();

    private static Class<?> guiEditorClass;
    private static boolean initialized;
    private static boolean available;
    private static String currentScreen = "default";

    private GuiEditorBridge() {
    }

    public static boolean available() {
        init();
        return available;
    }

    public static void begin(String screenId) {
        currentScreen = safeId(screenId);
        if (available()) {
            callVoid("begin", new Class<?>[]{String.class}, currentScreen);
        } else {
            loadFallback(currentScreen);
        }
    }

    public static void register(String id, int x, int y, int width, int height) {
        if (available()) {
            call("register", new Class<?>[]{String.class, int.class, int.class, int.class, int.class}, id, x, y, width, height);
        } else {
            loadFallback(currentScreen);
        }
    }

    public static int getX(String id, int defaultX) {
        if (available()) {
            Object value = call("getX", new Class<?>[]{String.class, int.class}, id, defaultX);
            return value instanceof Integer i ? i : defaultX;
        }
        SavedElement saved = fallbackElement(id);
        return saved == null ? defaultX : saved.x;
    }

    public static int getY(String id, int defaultY) {
        if (available()) {
            Object value = call("getY", new Class<?>[]{String.class, int.class}, id, defaultY);
            return value instanceof Integer i ? i : defaultY;
        }
        SavedElement saved = fallbackElement(id);
        return saved == null ? defaultY : saved.y;
    }


    public static int getWidth(String id, int defaultWidth) {
        if (available()) {
            Object value = call("getWidth", new Class<?>[]{String.class, int.class}, id, defaultWidth);
            return value instanceof Integer i ? i : defaultWidth;
        }
        SavedElement saved = fallbackElement(id);
        return saved == null || saved.width == Integer.MIN_VALUE ? defaultWidth : saved.width;
    }

    public static int getHeight(String id, int defaultHeight) {
        if (available()) {
            Object value = call("getHeight", new Class<?>[]{String.class, int.class}, id, defaultHeight);
            return value instanceof Integer i ? i : defaultHeight;
        }
        SavedElement saved = fallbackElement(id);
        return saved == null || saved.height == Integer.MIN_VALUE ? defaultHeight : saved.height;
    }

    public static boolean isEditMode() {
        Object value = call("isEditMode", new Class<?>[]{});
        return value instanceof Boolean b && b;
    }

    public static boolean handleKey(int keyCode) {
        Object value = call("handleKey", new Class<?>[]{int.class}, keyCode);
        return value instanceof Boolean b && b;
    }

    public static void handlePolledMouse(int mouseX, int mouseY) {
        if (!available()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) return;
        boolean leftDown = GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        callVoid("handlePolledMouse", new Class<?>[]{int.class, int.class, boolean.class}, mouseX, mouseY, leftDown);
    }

    public static boolean mouseClicked(double mouseX, double mouseY, int button) {
        Object value = call("mouseClicked", new Class<?>[]{double.class, double.class, int.class}, mouseX, mouseY, button);
        return value instanceof Boolean b && b;
    }

    public static boolean mouseDragged(double mouseX, double mouseY, int button) {
        Object value = call("mouseDragged", new Class<?>[]{double.class, double.class, int.class}, mouseX, mouseY, button);
        return value instanceof Boolean b && b;
    }

    public static boolean mouseReleased(double mouseX, double mouseY, int button) {
        Object value = call("mouseReleased", new Class<?>[]{double.class, double.class, int.class}, mouseX, mouseY, button);
        return value instanceof Boolean b && b;
    }

    public static void render(DrawContext context, int mouseX, int mouseY) {
        callVoid("render", new Class<?>[]{DrawContext.class, int.class, int.class, boolean.class}, context, mouseX, mouseY, true);
    }

    private static void init() {
        if (initialized) return;
        initialized = true;
        try {
            if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
                available = false;
                return;
            }
            guiEditorClass = Class.forName("jp.exnakamura.maikuraguieditor.GuiEditor");
            available = true;
        } catch (Throwable t) {
            available = false;
            guiEditorClass = null;
        }
    }

    private static void callVoid(String name, Class<?>[] types, Object... args) {
        call(name, types, args);
    }

    private static Object call(String name, Class<?>[] types, Object... args) {
        init();
        if (!available || guiEditorClass == null) return null;
        try {
            Method method = guiEditorClass.getMethod(name, types);
            return method.invoke(null, args);
        } catch (Throwable t) {
            return null;
        }
    }

    private static SavedElement fallbackElement(String id) {
        Map<String, SavedElement> elements = loadFallback(currentScreen);
        return elements.get(id);
    }

    private static Map<String, SavedElement> loadFallback(String screenId) {
        String safe = safeId(screenId);
        if (FALLBACK_LAYOUTS.containsKey(safe)) {
            return FALLBACK_LAYOUTS.get(safe);
        }
        Map<String, SavedElement> result = new LinkedHashMap<>();
        Path path = CONFIG_DIR.resolve(safe + ".json");
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                if (root != null && root.has("elements") && root.get("elements").isJsonObject()) {
                    JsonObject elements = root.getAsJsonObject("elements");
                    for (String key : elements.keySet()) {
                        JsonObject obj = elements.getAsJsonObject(key);
                        int x = obj.has("x") ? obj.get("x").getAsInt() : Integer.MIN_VALUE;
                        int y = obj.has("y") ? obj.get("y").getAsInt() : Integer.MIN_VALUE;
                        int width = obj.has("width") ? obj.get("width").getAsInt() : Integer.MIN_VALUE;
                        int height = obj.has("height") ? obj.get("height").getAsInt() : Integer.MIN_VALUE;
                        result.put(key, new SavedElement(x, y, width, height));
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        FALLBACK_LAYOUTS.put(safe, result);
        return result;
    }

    private static String safeId(String id) {
        if (id == null || id.isBlank()) {
            return "default";
        }
        String safe = id.replaceAll("[^a-zA-Z0-9_\\-]", "_").toLowerCase();
        if ("waypointlistscreen".equals(safe) || "class_490".equals(safe)) {
            return "waypoints";
        }
        return safe;
    }

    private record SavedElement(int x, int y, int width, int height) {
    }
}
