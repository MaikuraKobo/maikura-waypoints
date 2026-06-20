package jp.exnakamura.maikurawaypoints;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ReturnCrystalInputPayload(int suppressMillis) implements CustomPayload {
    public static final CustomPayload.Id<ReturnCrystalInputPayload> ID = new CustomPayload.Id<>(Identifier.of(MaikuraWaypointsMod.MOD_ID, "return_crystal_input"));
    public static final PacketCodec<RegistryByteBuf, ReturnCrystalInputPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER,
            ReturnCrystalInputPayload::suppressMillis,
            ReturnCrystalInputPayload::new
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
