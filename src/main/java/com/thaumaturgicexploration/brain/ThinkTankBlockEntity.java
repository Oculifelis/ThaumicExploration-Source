package com.thaumaturgicexploration.brain;

import com.thaumaturgicexploration.TXDataMaps;
import com.thaumaturgicexploration.TXDataMaps.ThinkTankInput;
import com.thaumaturgicexploration.TXRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * Think Tank: a purified brain in a jar, sat on a bookshelf with room to think, reads books into Illuminating
 * Curiosities. Slot 0 takes anything in the think_tank item data map (books, enchanted books), slot 1 holds the
 * results; hoppers fill the first and empty the second.
 */
public class ThinkTankBlockEntity extends BlockEntity {
    private final Slots slots = new Slots();
    private int progress;

    public ThinkTankBlockEntity(BlockPos pos, BlockState state) {
        super(TXRegistry.THINK_TANK_BE.get(), pos, state);
    }

    public ItemStacksResourceHandler slots() {
        return slots;
    }

    static @Nullable ThinkTankInput recipe(ItemStack stack) {
        return stack.isEmpty() ? null : stack.getItem().builtInRegistryHolder().getData(TXDataMaps.THINK_TANK);
    }

    private final class Slots extends ItemStacksResourceHandler {
        Slots() {
            super(2);
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext tx) {
            return index == 0 ? super.insert(index, resource, amount, tx) : 0;
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext tx) {
            return index == 1 ? super.extract(index, resource, amount, tx) : 0;
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return index == 1 || recipe(resource.toStack()) != null;
        }

        @Override
        protected void onContentsChanged(int index, ItemStack previousContents) {
            setChanged();
        }
    }

    ItemStack stackIn(int index) {
        return slots.getResource(index).toStack(slots.getAmountAsInt(index));
    }

    void setStack(int index, ItemStack stack) {
        slots.set(index, ItemResource.of(stack), stack.getCount());
    }

    /** It only thinks on a bookshelf (the think_tank_base block tag) with a clear 3x3x3 space around it. */
    public boolean canThink(Level level, BlockPos pos) {
        if (!level.getBlockState(pos.below()).is(ThinkTankBlock.BASE)) {
            return false;
        }
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
            if (p.equals(pos) || p.equals(pos.below())) {
                continue;
            }
            BlockState state = level.getBlockState(p);
            if (!state.isAir() && !state.canBeReplaced()) {
                return false;
            }
        }
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ThinkTankBlockEntity tank) {
        ItemStack input = tank.stackIn(0);
        ThinkTankInput recipe = recipe(input);
        boolean working = recipe != null && tank.roomFor(recipe) && tank.canThink(level, pos);
        if (state.getValue(ThinkTankBlock.LIT) != working) {
            level.setBlock(pos, state.setValue(ThinkTankBlock.LIT, working), 3);
        }
        if (!working) {
            tank.progress = 0;
            return;
        }
        if (++tank.progress < recipe.time()) {
            return;
        }
        tank.progress = 0;
        int made = roll(recipe, level.getRandom().nextFloat());
        if (made > 0) {
            ItemStack out = tank.stackIn(1);
            tank.setStack(1, out.isEmpty() ? new ItemStack(recipe.result(), made) : out.copyWithCount(out.getCount() + made));
            level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.5f, 1.2f);
        }
        input.shrink(1);
        tank.setStack(0, input);
        if (level instanceof ServerLevel server) {
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 0.9,
                    pos.getZ() + 0.5, 8, 0.3, 0.2, 0.3, 0.5);
        }
    }

    /** Outcomes are tried in order against one roll: each takes its own slice of the 0-1 range. */
    static int roll(ThinkTankInput recipe, float roll) {
        float edge = 0;
        for (ThinkTankInput.Outcome outcome : recipe.outcomes()) {
            edge += outcome.chance();
            if (roll < edge) {
                return outcome.count();
            }
        }
        return 0;
    }

    private boolean roomFor(ThinkTankInput recipe) {
        ItemStack out = stackIn(1);
        int most = recipe.outcomes().stream().mapToInt(ThinkTankInput.Outcome::count).max().orElse(0);
        return out.isEmpty() || out.is(recipe.result()) && out.getCount() + most <= out.getMaxStackSize();
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        slots.serialize(out);
        out.putInt("progress", progress);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        slots.deserialize(in);
        progress = in.getIntOr("progress", 0);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) {
            for (int i = 0; i < 2; i++) {
                net.minecraft.world.level.block.Block.popResource(level, pos, stackIn(i));
            }
        }
    }
}
