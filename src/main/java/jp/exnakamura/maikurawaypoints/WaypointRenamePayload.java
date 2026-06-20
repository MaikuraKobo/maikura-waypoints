package jp.exnakamura.maikurawaypoints;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record WaypointRenamePayload(String key, String name) implements CustomPayload {
    public static final CustomPayload.Id<WaypointRenamePayload> ID = new CustomPayload.Id<>(Identifier.of(MaikuraWaypointsMod.MOD_ID, "rename_waypoint"));
    public static final PacketCodec<RegistryByteBuf, WaypointRenamePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING,
            WaypointRenamePayload::key,
            PacketCodecs.STRING,
            WaypointRenamePayload::name,
            WaypointRenamePayload::new
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
