package jp.exnakamura.maikurawaypoints;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record WaypointMovePayload(String key, String direction, String currentKey) implements CustomPayload {
    public static final CustomPayload.Id<WaypointMovePayload> ID = new CustomPayload.Id<>(Identifier.of(MaikuraWaypointsMod.MOD_ID, "move_waypoint"));
    public static final PacketCodec<RegistryByteBuf, WaypointMovePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING,
            WaypointMovePayload::key,
            PacketCodecs.STRING,
            WaypointMovePayload::direction,
            PacketCodecs.STRING,
            WaypointMovePayload::currentKey,
            WaypointMovePayload::new
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
