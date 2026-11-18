package com.thaumicexploration.brain;

import com.leclowndu93150.thaumaturge.content.essentia.jar.BlockJar;
import com.mojang.serialization.MapCodec;
import com.thaumicexploration.TXRegistry;
import com.thaumicexploration.ThaumicExploration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** Use a book on it to feed it; use it empty-handed to take out what it has made (or the books, if it made nothing). */
public class ThinkTankBlock extends BaseEntityBlock {
    public static final TagKey<Block> BASE = TagKey.create(Registries.BLOCK, ThaumicExploration.id("think_tank_base"));
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public ThinkTankBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(ThinkTankBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return BlockJar.SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ThinkTankBlockEntity(pos, state);
    }

    @Override
    public <E extends BlockEntity> @Nullable BlockEntityTicker<E> getTicker(Level level, BlockState state, BlockEntityType<E> type) {
        return level.isClientSide() ? null : createTickerHelper(type, TXRegistry.THINK_TANK_BE.get(), ThinkTankBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (ThinkTankBlockEntity.recipe(stack) == null || !(level.getBlockEntity(pos) instanceof ThinkTankBlockEntity tank)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide()) {
            ItemStack in = tank.stackIn(0);
            if (in.isEmpty() || ItemStack.isSameItemSameComponents(in, stack)) {
                int moved = Math.min(stack.getCount(), stack.getMaxStackSize() - in.getCount());
                tank.setStack(0, in.isEmpty() ? stack.copyWithCount(moved) : in.copyWithCount(in.getCount() + moved));
                stack.shrink(moved);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ThinkTankBlockEntity tank) {
            int slot = tank.stackIn(1).isEmpty() ? 0 : 1;
            ItemStack taken = tank.stackIn(slot);
            tank.setStack(slot, ItemStack.EMPTY);
            if (!player.getInventory().add(taken)) {
                player.drop(taken, false);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** The brain glows and sheds enchanting glyphs while it reads. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT) && random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.ENCHANT, pos.getX() + 0.5 + random.nextGaussian() * 0.6, pos.getY() + 1.2,
                    pos.getZ() + 0.5 + random.nextGaussian() * 0.6, 0, -0.6, 0);
        }
    }
}
