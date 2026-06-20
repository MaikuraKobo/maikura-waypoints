package jp.exnakamura.maikurawaypoints;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record WaypointOpenPayload(String data) implements CustomPayload {
    public static final CustomPayload.Id<WaypointOpenPayload> ID = new CustomPayload.Id<>(Identifier.of(MaikuraWaypointsMod.MOD_ID, "open_waypoint_screen"));
    public static final PacketCodec<RegistryByteBuf, WaypointOpenPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING,
            WaypointOpenPayload::data,
            WaypointOpenPayload::new
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
