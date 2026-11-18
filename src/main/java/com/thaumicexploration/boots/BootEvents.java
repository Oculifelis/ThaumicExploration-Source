package com.thaumicexploration.boots;

import com.thaumicexploration.TXDataMaps;
import com.thaumicexploration.TXDataMaps.BootEffects;
import com.thaumicexploration.TXDataMaps.WornAttribute;
import com.thaumicexploration.ThaumicExploration;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Boots of the Meteor and of the Comet. Their stats are the worn_attributes item data map; what they do is the
 * boot_effects data map: a crouch-jump dash, the midair crouch slam that bursts on landing, freezing water underfoot,
 * and a speed that builds while you sprint.
 */
public final class BootEvents {
    private static final Identifier RAMP_ID = ThaumicExploration.id("boot_ramp");

    private static @Nullable BootEffects effects(ItemStack boots) {
        return boots.isEmpty() ? null : boots.getItem().builtInRegistryHolder().getData(TXDataMaps.BOOT_EFFECTS);
    }

    /** Worn stats (speed, step height, fall protection) come from data, so packs can retune them. */
    public static void onAttributes(ItemAttributeModifierEvent event) {
        List<WornAttribute> worn = event.getItemStack().getItem().builtInRegistryHolder().getData(TXDataMaps.WORN_ATTRIBUTES);
        if (worn != null) {
            for (WornAttribute w : worn) {
                event.addModifier(w.attribute(), w.modifier(), w.slot());
            }
        }
    }

    /** Crouch-jump with dash boots: you are flung the way you are looking. */
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof Player player) || !player.isShiftKeyDown()) {
            return;
        }
        BootEffects fx = effects(player.getItemBySlot(EquipmentSlot.FEET));
        if (fx == null || fx.dash() <= 0) {
            return;
        }
        Vec3 look = player.getLookAngle();
        double up = Math.max(look.y, Math.abs(look.x + look.z));
        player.setDeltaMovement(player.getDeltaMovement().add(look.x * fx.dash() * 4, up * fx.dash() / 1.5, look.z * fx.dash() * 4));
        player.hurtMarked = true;
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        BootState state = player.getData(TXBoots.STATE);
        BootEffects fx = effects(player.getItemBySlot(EquipmentSlot.FEET));
        if (fx == null) {
            state.reset();
            ramp(player, 0);
            return;
        }
        if (fx.smash()) {
            smash(player, state);
        }
        if (fx.rampMax() > 0) {
            state.runTicks = player.isSprinting() && player.onGround() ? state.runTicks + 1 : 0;
            ramp(player, Math.min(fx.rampMax(), state.runTicks / 20f * fx.rampPerSecond()));
        }
        if (fx.frostWalk() > 0 && !player.level().isClientSide() && player.onGround()) {
            freeze(player.level(), player.blockPosition(), fx.frostWalk(), player);
        }
    }

    /** Midair crouch: dive; landing after a long enough dive bursts like a small, harmless-to-blocks explosion. */
    private static void smash(Player player, BootState state) {
        if (player.onGround() || player.onClimbable() || player.getAbilities().flying) {
            int size = state.smashTicks > 15 ? 3 : state.smashTicks > 10 ? 2 : state.smashTicks > 5 ? 1 : 0;
            if (size > 0 && player.level() instanceof ServerLevel level) {
                level.explode(player, player.getX(), player.getY(), player.getZ(), size, Level.ExplosionInteraction.NONE);
            }
            state.reset();
            return;
        }
        if (player.isInWater()) {
            return;
        }
        if (!player.isShiftKeyDown()) {
            state.airTicks++;
        } else if (state.airTicks > 5) {
            state.smashing = true;
        }
        if (state.smashing) {
            state.smashTicks++;
            player.setDeltaMovement(player.getDeltaMovement().add(0, -0.1, 0));
            player.fallDistance = 0;
            if (player.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY(), player.getZ(), 2, 0.3, 0.3, 0.3, 0);
                level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY(), player.getZ(), 1, 0.3, 0.3, 0.3, 0);
            }
        }
    }

    /** The speed that builds while sprinting is a transient modifier, replaced every tick. */
    private static void ramp(Player player, float amount) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        if (amount <= 0) {
            speed.removeModifier(RAMP_ID);
        } else {
            speed.addOrUpdateTransientModifier(new AttributeModifier(RAMP_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    /** Vanilla frost walking: still water under and around you turns to frosted ice, which melts back on its own. */
    private static void freeze(Level level, BlockPos pos, int radius, Player player) {
        BlockState ice = Blocks.FROSTED_ICE.defaultBlockState();
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-radius, -1, -radius), pos.offset(radius, -1, radius))) {
            if (p.closerToCenterThan(player.position(), radius) && level.getBlockState(p.above()).isAir()) {
                BlockState here = level.getBlockState(p);
                if (here.getBlock() instanceof LiquidBlock && here.getFluidState().is(Fluids.WATER) && here.getFluidState().isSource()
                        && ice.canSurvive(level, p) && level.isUnobstructed(ice, p, CollisionContext.empty())) {
                    level.setBlockAndUpdate(p, ice);
                    level.scheduleTick(p, Blocks.FROSTED_ICE, Mth.nextInt(player.getRandom(), 60, 120));
                }
            }
        }
    }

    private BootEvents() {
    }
}
