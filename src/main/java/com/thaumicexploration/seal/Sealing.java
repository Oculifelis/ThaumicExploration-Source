package com.thaumicexploration.seal;

import com.leclowndu93150.thaumaturge.content.essentia.jar.AbstractBlockEntityJar;
import com.thaumicexploration.TXRegistry;
import com.thaumicexploration.ThaumicExploration;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * What the binding seals do to the blocks they are used on. Which blocks take a seal is data: the sealable_chests
 * and sealable_jars block tags.
 */
public final class Sealing {
    public static final TagKey<Block> SEALABLE_CHESTS = TagKey.create(Registries.BLOCK, ThaumicExploration.id("sealable_chests"));
    public static final TagKey<Block> SEALABLE_JARS = TagKey.create(Registries.BLOCK, ThaumicExploration.id("sealable_jars"));

    public static boolean isSealableChest(BlockState state) {
        return state.is(SEALABLE_CHESTS) && (!state.hasProperty(ChestBlock.TYPE) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE);
    }

    public static boolean isSealableJar(Level level, BlockPos pos, BlockState state) {
        return state.is(SEALABLE_JARS) && level.getBlockEntity(pos) instanceof AbstractBlockEntityJar jar
                && jar.amount() == 0 && jar.aspectFilterKey() == null;
    }

    /** Turns a chest into a bound chest on the given network; its contents join the network, any overflow drops. */
    public static void bindChest(ServerLevel level, BlockPos pos, SealLink link) {
        BlockState old = level.getBlockState(pos);
        List<ItemStack> items = new ArrayList<>();
        if (level.getBlockEntity(pos) instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                items.add(container.removeItemNoUpdate(i));
            }
        }
        Direction facing = old.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? old.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
        level.setBlockAndUpdate(pos, TXRegistry.BOUND_CHEST.get().defaultBlockState()
                .setValue(BoundChestBlock.FACING, facing).setValue(BoundChestBlock.COLOR, link.color()));
        if (level.getBlockEntity(pos) instanceof BoundChestBlockEntity chest) {
            chest.bind(link.id());
        }
        SealNetworks networks = SealNetworks.get(level.getServer());
        for (ItemStack rest : SealNetworks.fill(networks.chest(link.key()), items)) {
            Block.popResource(level, pos, rest);
        }
        sealSound(level, pos);
    }

    /** Turns an empty warded jar into a bound jar on the network named by the seal (its custom name, or none). */
    public static void bindJar(ServerLevel level, BlockPos pos, DyeColor color, ItemStack seal) {
        Component custom = seal.get(DataComponents.CUSTOM_NAME);
        String name = custom == null ? "" : custom.getString();
        level.setBlockAndUpdate(pos, TXRegistry.BOUND_JAR.get().defaultBlockState().setValue(BoundJarBlock.COLOR, color));
        if (level.getBlockEntity(pos) instanceof BoundJarBlockEntity jar) {
            jar.bind(name);
        }
        sealSound(level, pos);
    }

    private static void sealSound(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.ENDER_EYE_DEATH, SoundSource.BLOCKS, 0.6f, 1.4f);
    }

    private Sealing() {
    }
}
