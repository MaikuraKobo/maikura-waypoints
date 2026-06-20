package jp.exnakamura.maikurawaypoints;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record WaypointFavoritePayload(String key, String currentKey) implements CustomPayload {
    public static final CustomPayload.Id<WaypointFavoritePayload> ID = new CustomPayload.Id<>(Identifier.of(MaikuraWaypointsMod.MOD_ID, "toggle_favorite_waypoint"));
    public static final PacketCodec<RegistryByteBuf, WaypointFavoritePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING,
            WaypointFavoritePayload::key,
            PacketCodecs.STRING,
            WaypointFavoritePayload::currentKey,
            WaypointFavoritePayload::new
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
