package jp.exnakamura.maikurawaypoints.mixin;

import jp.exnakamura.maikurawaypoints.MaikuraWaypointsMod;
import jp.exnakamura.maikurawaypoints.WaypointGameplayConfig;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.slot.CraftingResultSlot;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public abstract class ReturnCrystalCraftingSlotMixin {
    @Inject(method = "canTakeItems", at = @At("HEAD"), cancellable = true)
    private void maikura_waypoints$disableReturnCrystalCrafting(PlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
        Slot self = (Slot) (Object) this;
        if (self instanceof CraftingResultSlot
                && self.getStack().isOf(MaikuraWaypointsMod.RETURN_CRYSTAL)
                && !WaypointGameplayConfig.get().returnCrystalEnabled) {
            if (player instanceof net.minecraft.server.network.ServerPlayerEntity) {
                player.sendMessage(net.minecraft.text.Text.literal("帰還クリスタルは設定で無効化されています。"), true);
            }
            cir.setReturnValue(false);
        }
    }
}
