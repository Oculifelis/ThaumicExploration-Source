package com.thaumicexploration.seal;

import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import com.thaumicexploration.TXRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * Jar of Oblivion: a jar that is never full. It draws essentia from a tube above with weak suction, as the original
 * did, and anything that reaches it (essentia, items, fluids) is destroyed.
 */
public class TrashJarBlockEntity extends BlockEntity implements IEssentiaTransport {
    private static final int SUCTION = 8;

    /** Storage view that accepts any essentia and keeps none. */
    public static final IEssentiaStorage VOID_STORAGE = new IEssentiaStorage() {
        @Override
        public AspectList contents() {
            return AspectList.EMPTY;
        }

        @Override
        public int insert(Holder<IAspect> aspect, int amount, TransactionContext tx) {
            return amount;
        }

        @Override
        public int extract(Holder<IAspect> aspect, int amount, TransactionContext tx) {
            return 0;
        }

        @Override
        public long contentRevision() {
            return 0;
        }
    };

    public TrashJarBlockEntity(BlockPos pos, BlockState state) {
        super(TXRegistry.TRASH_JAR_BE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TrashJarBlockEntity jar) {
        if (level.getGameTime() % 5 != 0) {
            return;
        }
        IEssentiaTransport above = level.getCapability(EssentiaCapabilities.TRANSPORT, pos.above(), Direction.DOWN);
        if (above == null || !above.canOutputTo(Direction.DOWN) || above.getSuctionAmount(Direction.DOWN) >= SUCTION) {
            return;
        }
        Holder<IAspect> aspect = above.getEssentiaType(Direction.DOWN);
        if (aspect != null && above.getEssentiaAmount(Direction.DOWN) > 0) {
            above.takeEssentia(aspect, 1, Direction.DOWN);
        }
    }

    @Override
    public boolean isConnectable(Direction side) {
        return true;
    }

    @Override
    public boolean canInputFrom(Direction side) {
        return true;
    }

    @Override
    public boolean canOutputTo(Direction side) {
        return false;
    }

    @Override
    public void setSuction(Holder<IAspect> aspect, int amount) {
    }

    @Override
    public @Nullable Holder<IAspect> getSuctionType(Direction side) {
        return null;
    }

    @Override
    public int getSuctionAmount(Direction side) {
        return SUCTION;
    }

    @Override
    public int getMinimumSuction() {
        return SUCTION;
    }

    @Override
    public int takeEssentia(Holder<IAspect> aspect, int amount, Direction side) {
        return 0;
    }

    @Override
    public int addEssentia(Holder<IAspect> aspect, int amount, Direction side) {
        return amount;
    }

    @Override
    public @Nullable Holder<IAspect> getEssentiaType(Direction side) {
        return null;
    }

    @Override
    public int getEssentiaAmount(Direction side) {
        return 0;
    }
}
