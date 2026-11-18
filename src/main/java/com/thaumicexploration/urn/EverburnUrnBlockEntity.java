package com.thaumicexploration.urn;

import com.leclowndu93150.thaumaturge.api.aura.AuraHelper;
import com.leclowndu93150.thaumaturge.api.aura.VisRelayHelper;
import com.thaumicexploration.TXDataMaps;
import com.thaumicexploration.TXDataMaps.UrnSource;
import com.thaumicexploration.TXRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * Turns vis into fluid: the Everburn Urn draws Ignis and fills itself with lava, which buckets and pipes take out of
 * the top. Which fluid, which aspect, and the rates all come from the urn_source data map on the block.
 */
public class EverburnUrnBlockEntity extends BlockEntity {
    private final Tank tank = new Tank();

    public EverburnUrnBlockEntity(BlockPos pos, BlockState state) {
        super(TXRegistry.EVERBURN_URN_BE.get(), pos, state);
    }

    public FluidStacksResourceHandler tank() {
        return tank;
    }

    @Nullable UrnSource source() {
        return getBlockState().getBlock().builtInRegistryHolder().getData(TXDataMaps.URN_SOURCE);
    }

    /** Output only: nothing can be poured in, and the capacity follows the data map. */
    private final class Tank extends FluidStacksResourceHandler {
        Tank() {
            super(1, Integer.MAX_VALUE);
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return 0;
        }

        @Override
        protected int getCapacity(int index, FluidResource resource) {
            UrnSource source = source();
            return source == null ? 0 : source.capacity();
        }

        @Override
        protected void onContentsChanged(int index, FluidStack previousContents) {
            setChanged();
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, EverburnUrnBlockEntity be) {
        UrnSource source = be.source();
        if (source == null || level.getGameTime() % source.interval() != 0) {
            return;
        }
        int stored = be.tank.getAmountAsInt(0);
        if (stored + source.mbPerDrain() > source.capacity()) {
            return;
        }
        if (drain((ServerLevel) level, pos, source)) {
            be.tank.set(0, FluidResource.of(source.fluid()), stored + source.mbPerDrain());
            be.setChanged();
        }
    }

    /** Takes the vis from a relay network if one reaches the urn, otherwise straight from the chunk's aura. */
    private static boolean drain(ServerLevel level, BlockPos pos, UrnSource source) {
        try (Transaction tx = Transaction.openRoot()) {
            int got = VisRelayHelper.drainCentivis(level, pos, source.aspect(), source.centivis(), tx);
            if (got < source.centivis()) {
                float want = (source.centivis() - got) / 100f;
                if (AuraHelper.drainVis(level, pos, want, tx) < want) {
                    return false;
                }
            }
            tx.commit();
            return true;
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        tank.serialize(out);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        tank.deserialize(in);
    }
}
