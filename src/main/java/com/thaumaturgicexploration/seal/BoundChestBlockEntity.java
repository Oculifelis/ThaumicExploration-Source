package com.thaumaturgicexploration.seal;

import com.thaumaturgicexploration.TXRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Remembers which network number this bound chest is on; the colour is in the block state. */
public class BoundChestBlockEntity extends BlockEntity {
    private int id;

    public BoundChestBlockEntity(BlockPos pos, BlockState state) {
        super(TXRegistry.BOUND_CHEST_BE.get(), pos, state);
    }

    void bind(int id) {
        this.id = id;
        setChanged();
    }

    public SealLink link() {
        return new SealLink(getBlockState().getValue(BoundChestBlock.COLOR), id);
    }

    /** The loot table copies these onto the linked seal it drops, so the chest can be bound again elsewhere. */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(TXRegistry.SEAL_LINK.get(), link());
        components.set(DataComponents.BASE_COLOR, link().color());
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("network_id", id);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        id = in.getIntOr("network_id", 0);
    }
}
