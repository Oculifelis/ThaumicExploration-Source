package com.thaumicexploration.mixin;

import com.leclowndu93150.thaumaturge.content.device.BlockEntityEverfullUrn;
import com.thaumicexploration.TXDataMaps;
import com.thaumicexploration.TXDataMaps.UrnSpring;
import com.thaumicexploration.urn.EverfullSpring;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Thaumaturge's Everfull Urn runs the old Thaumic Exploration urn instead of its own vis-fed tick whenever the block has
 * a {@code urn_spring} data map entry. Remove the entry in a datapack to get Thaumaturge's behaviour back.
 */
@Mixin(BlockEntityEverfullUrn.class)
public abstract class EverfullUrnMixin {
    @Unique
    private final EverfullSpring.State thaumicexploration$spring = new EverfullSpring.State();

    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private static void thaumicexploration$oldUrn(Level level, BlockPos pos, BlockState state, BlockEntityEverfullUrn urn, CallbackInfo ci) {
        UrnSpring spring = state.getBlock().builtInRegistryHolder().getData(TXDataMaps.URN_SPRING);
        if (spring != null && level instanceof ServerLevel server) {
            EverfullSpring.tick(server, pos, urn, spring, ((EverfullUrnMixin) (Object) urn).thaumicexploration$spring);
            ci.cancel();
        }
    }
}
