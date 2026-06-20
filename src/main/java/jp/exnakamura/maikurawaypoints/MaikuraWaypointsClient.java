package jp.exnakamura.maikurawaypoints;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

import java.util.ArrayList;
import java.util.List;

public class MaikuraWaypointsClient implements ClientModInitializer {
    private static long suppressReturnCrystalInputUntil = 0L;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(WaypointOpenPayload.ID, (payload, context) -> {
            MinecraftClient client = context.client();
            DecodedWaypoints decoded = decodeEntries(payload.data());
            client.execute(() -> client.setScreen(new WaypointListScreen(decoded.current(), decoded.entries())));
        });
        ClientPlayNetworking.registerGlobalReceiver(ReturnCrystalInputPayload.ID, (payload, context) -> {
            MinecraftClient client = context.client();
            client.execute(() -> suppressReturnCrystalInput(client, payload.suppressMillis()));
        });
        ClientTickEvents.END_CLIENT_TICK.register(MaikuraWaypointsClient::tickReturnCrystalInputSuppressor);
    }

    private static void suppressReturnCrystalInput(MinecraftClient client, int suppressMillis) {
        suppressReturnCrystalInputUntil = System.currentTimeMillis() + Math.max(0, suppressMillis);
        applyReturnCrystalInputSuppressor(client);
    }

    private static void tickReturnCrystalInputSuppressor(MinecraftClient client) {
        if (System.currentTimeMillis() > suppressReturnCrystalInputUntil) return;
        applyReturnCrystalInputSuppressor(client);
    }

    private static void applyReturnCrystalInputSuppressor(MinecraftClient client) {
        client.options.useKey.setPressed(false);
        while (client.options.useKey.wasPressed()) {
            // Drain queued use presses produced by the held right-click.
        }
        if (client.player == null) return;
        ItemStack mainHand = client.player.getStackInHand(Hand.MAIN_HAND);
        ItemStack offHand = client.player.getStackInHand(Hand.OFF_HAND);
        if (mainHand.isOf(MaikuraWaypointsMod.RETURN_CRYSTAL)) {
            client.player.clearActiveItem();
            client.player.getItemCooldownManager().set(mainHand, 20);
        }
        if (offHand.isOf(MaikuraWaypointsMod.RETURN_CRYSTAL)) {
            client.player.clearActiveItem();
            client.player.getItemCooldownManager().set(offHand, 20);
        }
    }

    private static DecodedWaypoints decodeEntries(String data) {
        List<WaypointListScreen.Entry> entries = new ArrayList<>();
        WaypointListScreen.Entry current = null;
        if (data == null || data.isBlank()) return new DecodedWaypoints(null, entries);
        String[] lines = data.split("\\n");
        for (String line : lines) {
            List<String> parts = splitEscaped(line);
            if (parts.size() < 11) continue;
            String type = parts.get(0);
            WaypointListScreen.Entry entry = decodeEntry(parts, 1);
            if (entry == null) continue;
            if ("CURRENT".equals(type)) current = entry;
            else entries.add(entry);
        }
        return new DecodedWaypoints(current, entries);
    }

    private static WaypointListScreen.Entry decodeEntry(List<String> parts, int offset) {
        try {
            String key = parts.get(offset);
            String name = parts.get(offset + 1);
            String dimension = parts.get(offset + 2);
            int distance = Integer.parseInt(parts.get(offset + 3));
            boolean ancient = Boolean.parseBoolean(parts.get(offset + 4));
            boolean home = Boolean.parseBoolean(parts.get(offset + 5));
            boolean favorite = Boolean.parseBoolean(parts.get(offset + 6));
            int x = Integer.parseInt(parts.get(offset + 7));
            int y = Integer.parseInt(parts.get(offset + 8));
            int z = Integer.parseInt(parts.get(offset + 9));
            int order = parts.size() > offset + 10 ? Integer.parseInt(parts.get(offset + 10)) : 0;
            return new WaypointListScreen.Entry(key, name, dimension, distance, ancient, home, favorite, x, y, z, order);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static List<String> splitEscaped(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean escaping = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (escaping) {
                if (c == 'p') current.append('|');
                else if (c == 'n') current.append('\n');
                else current.append(c);
                escaping = false;
                continue;
            }
            if (c == '\\') {
                escaping = true;
                continue;
            }
            if (c == '|') {
                result.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        result.add(current.toString());
        return result;
    }

    private record DecodedWaypoints(WaypointListScreen.Entry current, List<WaypointListScreen.Entry> entries) {}
}
