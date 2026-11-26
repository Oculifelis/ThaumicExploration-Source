package com.thaumaturgicexploration.brain;

import com.mojang.serialization.MapCodec;
import com.thaumaturgicexploration.TXRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Use a replicable block on it to set the template. A redstone pulse (or sneak-use with an empty hand) starts a copy;
 * use it empty-handed to take the copies, or, with none made, to cancel the copy or clear the template.
 */
public class ReplicatorBlock extends BaseEntityBlock {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public ReplicatorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(ReplicatorBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ReplicatorBlockEntity(pos, state);
    }

    @Override
    public <E extends BlockEntity> @Nullable BlockEntityTicker<E> getTicker(Level level, BlockState state, BlockEntityType<E> type) {
        return level.isClientSide() ? null : createTickerHelper(type, TXRegistry.REPLICATOR_BE.get(), ReplicatorBlockEntity::serverTick);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            if (powered && level.getBlockEntity(pos) instanceof ReplicatorBlockEntity rep) {
                rep.start();
            }
            level.setBlock(pos, state.setValue(POWERED, powered), 2);
        }
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ReplicatorBlockEntity rep) || !rep.template().isEmpty()
                || !ReplicatorBlockEntity.canReplicate(stack)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide()) {
            rep.setTemplate(stack);
            player.sendOverlayMessage(Component.translatable("block.thaumaturgicexploration.replicator.template", stack.getHoverName()));
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.6f, 1.0f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof ReplicatorBlockEntity rep)) {
            return InteractionResult.SUCCESS;
        }
        if (rep.made() > 0) {
            ItemStack out = rep.takeMade();
            if (!player.getInventory().add(out)) {
                player.drop(out, false);
            }
        } else if (player.isSecondaryUseActive()) {
            rep.start();
        } else if (rep.crafting()) {
            rep.cancel();
            level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.4f, 1.6f);
        } else if (!rep.template().isEmpty()) {
            rep.setTemplate(ItemStack.EMPTY);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.6f, 1.0f);
        }
        return InteractionResult.SUCCESS;
    }
}
