package com.thaumaturgicexploration.trinket;

import com.leclowndu93150.thaumaturge.api.infusion.IInfusionStabiliser;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A tallow candle that hangs in midair: no support needed, no collision, lit forever, and like Thaumaturge's own
 * candles it steadies a nearby infusion. Adapted: the original bobbed through a special renderer; this one floats
 * still, halfway up its block.
 */
public class FloatingCandleBlock extends Block implements IInfusionStabiliser {
    private static final VoxelShape SHAPE = box(6, 4, 6, 10, 13, 10);
    private final DyeColor color;

    public FloatingCandleBlock(DyeColor color, Properties properties) {
        super(properties);
        this.color = color;
    }

    public DyeColor color() {
        return color;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(p -> new FloatingCandleBlock(color, p));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + 0.5, y = pos.getY() + 0.86, z = pos.getZ() + 0.5;
        level.addParticle(ParticleTypes.SMALL_FLAME, x, y, z, 0, 0, 0);
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.SMOKE, x, y + 0.05, z, 0, 0, 0);
        }
    }

    @Override
    public boolean canStabiliseInfusion(Level level, BlockPos pos) {
        return true;
    }
}
