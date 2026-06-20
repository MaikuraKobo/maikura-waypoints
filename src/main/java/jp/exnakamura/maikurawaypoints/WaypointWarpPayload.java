package jp.exnakamura.maikurawaypoints;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record WaypointWarpPayload(String key) implements CustomPayload {
    public static final CustomPayload.Id<WaypointWarpPayload> ID = new CustomPayload.Id<>(Identifier.of(MaikuraWaypointsMod.MOD_ID, "warp_to_waypoint"));
    public static final PacketCodec<RegistryByteBuf, WaypointWarpPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING,
            WaypointWarpPayload::key,
            WaypointWarpPayload::new
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
