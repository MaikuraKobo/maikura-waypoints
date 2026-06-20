package jp.exnakamura.maikurawaypoints;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record WaypointHomePayload(String key, String currentKey) implements CustomPayload {
    public static final CustomPayload.Id<WaypointHomePayload> ID = new CustomPayload.Id<>(Identifier.of(MaikuraWaypointsMod.MOD_ID, "set_home_waypoint"));
    public static final PacketCodec<RegistryByteBuf, WaypointHomePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING,
            WaypointHomePayload::key,
            PacketCodecs.STRING,
            WaypointHomePayload::currentKey,
            WaypointHomePayload::new
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
