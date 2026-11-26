package com.thaumaturgicexploration.seal;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** One player's view of a bound chest network: the shared inventory, valid only while that chest stands in reach. */
record BoundChestView(Container shared, BoundChestBlockEntity chest) implements Container {
    @Override public int getContainerSize() { return shared.getContainerSize(); }
    @Override public boolean isEmpty() { return shared.isEmpty(); }
    @Override public ItemStack getItem(int slot) { return shared.getItem(slot); }
    @Override public ItemStack removeItem(int slot, int amount) { return shared.removeItem(slot, amount); }
    @Override public ItemStack removeItemNoUpdate(int slot) { return shared.removeItemNoUpdate(slot); }
    @Override public void setItem(int slot, ItemStack stack) { shared.setItem(slot, stack); }
    @Override public int getMaxStackSize() { return shared.getMaxStackSize(); }
    @Override public void setChanged() { shared.setChanged(); }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return shared.canPlaceItem(slot, stack); }
    @Override public void clearContent() { shared.clearContent(); }
    @Override public boolean stillValid(Player player) { return Container.stillValidBlockEntity(chest, player); }
}
