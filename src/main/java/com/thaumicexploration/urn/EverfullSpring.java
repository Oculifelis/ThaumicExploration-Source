package com.thaumicexploration.urn;

import com.leclowndu93150.thaumaturge.content.device.BlockEntityEverfullUrn;
import com.thaumicexploration.TXDataMaps.UrnSpring;
import com.thaumicexploration.ThaumicExploration;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * The original Thaumic Exploration Everfull Urn (TileEntityEverfullUrn), run in place of Thaumaturge's urn tick when the
 * urn block has a {@code thaumicexploration:urn_spring} data map entry: never runs dry, waters nearby crucibles, cauldrons,
 * apothecaries and tanks, douses burning players and spouts a fountain while idle.
 */
public final class EverfullSpring {
    /** Blocks the urn never waters: other urns, and sinks like the Jar of Oblivion that would drink forever. */
    public static final TagKey<Block> IGNORES = TagKey.create(Registries.BLOCK, ThaumicExploration.id("everfull_urn_ignores"));
    private static final FluidResource WATER = FluidResource.of(Fluids.WATER);

    private EverfullSpring() {}

    /** Per-urn state, kept on the block entity by the mixin. */
    public static final class State {
        int ticks;
        int pushes;
        @Nullable BlockPos target;
    }

    public static void tick(ServerLevel level, BlockPos pos, BlockEntityEverfullUrn urn, UrnSpring spring, State state) {
        state.ticks++;
        if (spring.needsAirAbove() && !level.getBlockState(pos.above()).isAir()) {
            return;
        }
        topUp(urn);
        if (spring.fountain() && state.target == null && state.ticks % 4 == 0) {
            level.sendParticles(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 3, 0.12, 0.05, 0.12, 0.05);
        }
        if (state.ticks % spring.interval() != 0) {
            return;
        }
        AABB box = new AABB(pos).inflate(spring.range(), spring.yRange(), spring.range());
        if (spring.extinguish()) {
            douse(level, pos, box);
        }
        if (state.target == null || !wants(level, state.target)) {
            state.target = nearestTarget(level, pos, spring);
            state.pushes = 0;
        }
        if (state.target != null) {
            water(level, state.target, spring, state);
            stream(level, pos, state.target);
        }
    }

    /** The urn never runs dry: keep Thaumaturge's 1000 mB tank brim full, so buckets, bottles and pipes always get water. */
    private static void topUp(BlockEntityEverfullUrn urn) {
        int missing = BlockEntityEverfullUrn.CAPACITY - urn.waterAmount();
        if (missing <= 0) {
            return;
        }
        try (Transaction tx = Transaction.openRoot()) {
            urn.getTank().insert(WATER, missing, tx);
            tx.commit();
        }
    }

    private static void douse(ServerLevel level, BlockPos pos, AABB box) {
        List<Player> burning = level.getEntitiesOfClass(Player.class, box, Player::isOnFire);
        for (Player player : burning) {
            player.clearFire();
            stream(level, pos, player.blockPosition());
            level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 0.7F, 1.6F);
            for (BlockPos p : BlockPos.betweenClosed(player.blockPosition().offset(-1, 0, -1), player.blockPosition().offset(1, 0, 1))) {
                if (level.getBlockState(p).is(Blocks.FIRE)) {
                    level.removeBlock(p, false);
                }
            }
        }
    }

    private static @Nullable BlockPos nearestTarget(ServerLevel level, BlockPos pos, UrnSpring spring) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-spring.range(), -spring.yRange(), -spring.range()),
                pos.offset(spring.range(), spring.yRange(), spring.range()))) {
            if (p.equals(pos)) {
                continue;
            }
            double d = p.distSqr(pos);
            if (d < bestDist && wants(level, p)) {
                best = p.immutable();
                bestDist = d;
            }
        }
        return best;
    }

    /** True when the block at {@code p} can take water now: a cauldron below full, or a fluid handler with room for water. */
    private static boolean wants(ServerLevel level, BlockPos p) {
        BlockState state = level.getBlockState(p);
        if (state.is(IGNORES)) {
            return false;
        }
        if (state.is(Blocks.CAULDRON)) {
            return true;
        }
        if (state.is(Blocks.WATER_CAULDRON)) {
            return state.getValue(LayeredCauldronBlock.LEVEL) < LayeredCauldronBlock.MAX_FILL_LEVEL;
        }
        ResourceHandler<FluidResource> handler = level.getCapability(Capabilities.Fluid.BLOCK, p, Direction.UP);
        if (handler == null) {
            return false;
        }
        try (Transaction tx = Transaction.openRoot()) {
            return handler.insert(WATER, 1, tx) > 0;
        }
    }

    private static void water(ServerLevel level, BlockPos p, UrnSpring spring, State state) {
        BlockState block = level.getBlockState(p);
        if (block.is(Blocks.CAULDRON) || block.is(Blocks.WATER_CAULDRON)) {
            if (++state.pushes < spring.cauldronPushes()) {
                return;
            }
            state.pushes = 0;
            BlockState next = block.is(Blocks.CAULDRON) ? Blocks.WATER_CAULDRON.defaultBlockState() : block.cycle(LayeredCauldronBlock.LEVEL);
            level.setBlockAndUpdate(p, next);
            return;
        }
        ResourceHandler<FluidResource> handler = level.getCapability(Capabilities.Fluid.BLOCK, p, Direction.UP);
        if (handler != null) {
            try (Transaction tx = Transaction.openRoot()) {
                if (handler.insert(WATER, spring.mbPerPush(), tx) > 0) {
                    tx.commit();
                }
            }
        }
    }

    /** The arc of water from the urn's mouth to what it is filling (the original's spawnWaterAtLocation). */
    private static void stream(ServerLevel level, BlockPos from, BlockPos to) {
        Vec3 a = new Vec3(from.getX() + 0.5, from.getY() + 1.1, from.getZ() + 0.5);
        Vec3 b = new Vec3(to.getX() + 0.5, to.getY() + 1.1, to.getZ() + 0.5);
        int steps = Math.max(4, (int) (a.distanceTo(b) * 3));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            Vec3 p = a.lerp(b, t).add(0, Math.sin(Math.PI * t) * 0.8, 0);
            level.sendParticles(ParticleTypes.FALLING_WATER, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
        }
    }
}
