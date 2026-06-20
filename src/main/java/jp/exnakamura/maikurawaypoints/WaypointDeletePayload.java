package jp.exnakamura.maikurawaypoints;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record WaypointDeletePayload(String key, String currentKey) implements CustomPayload {
    public static final CustomPayload.Id<WaypointDeletePayload> ID = new CustomPayload.Id<>(Identifier.of(MaikuraWaypointsMod.MOD_ID, "delete_waypoint"));
    public static final PacketCodec<RegistryByteBuf, WaypointDeletePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING,
            WaypointDeletePayload::key,
            PacketCodecs.STRING,
            WaypointDeletePayload::currentKey,
            WaypointDeletePayload::new
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
