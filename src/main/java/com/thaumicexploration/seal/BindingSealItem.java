package com.thaumicexploration.seal;

import com.thaumicexploration.TXRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Chest and jar binding seals. A chest seal on a chest makes a bound chest on a new network; another seal of the same
 * colour used on that bound chest becomes a linked seal, which binds a second chest to it. A jar seal on an empty
 * warded jar makes a bound jar; jars sealed with the same colour (and the same seal name, if renamed) share essentia.
 * Runs before the block's own use, so the chest does not open.
 */
public class BindingSealItem extends Item {
    public enum Kind { CHEST, JAR }

    private final DyeColor color;
    private final Kind kind;

    public BindingSealItem(DyeColor color, Kind kind, Properties properties) {
        super(properties);
        this.color = color;
        this.kind = kind;
    }

    public DyeColor color() {
        return color;
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = context.getPlayer();
        if (kind == Kind.CHEST) {
            if (Sealing.isSealableChest(state)) {
                if (level instanceof ServerLevel server) {
                    Sealing.bindChest(server, pos, new SealLink(color, SealNetworks.get(server.getServer()).newChestId(color)));
                    stack.consume(1, player);
                }
                return InteractionResult.SUCCESS;
            }
            if (state.is(TXRegistry.BOUND_CHEST.get()) && state.getValue(BoundChestBlock.COLOR) == color
                    && level.getBlockEntity(pos) instanceof BoundChestBlockEntity chest) {
                if (!level.isClientSide()) {
                    ItemStack linked = LinkedChestSealItem.of(chest.link());
                    stack.consume(1, player);
                    if (player == null || !player.getInventory().add(linked)) {
                        net.minecraft.world.level.block.Block.popResource(level, pos.above(), linked);
                    }
                    if (player != null) {
                        player.sendOverlayMessage(Component.translatable("item.thaumicexploration.linked_chest_seal.made"));
                    }
                }
                return InteractionResult.SUCCESS;
            }
        } else if (Sealing.isSealableJar(level, pos, state)) {
            if (level instanceof ServerLevel server) {
                Sealing.bindJar(server, pos, color, stack);
                stack.consume(1, player);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
