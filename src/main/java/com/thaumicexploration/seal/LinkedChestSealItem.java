package com.thaumicexploration.seal;

import com.thaumicexploration.TXRegistry;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

/**
 * A chest seal already tied to a network (the seal_link component, plus the vanilla base_color component that picks
 * its texture). Used on a chest, it binds that chest to the same network as the chest it came from.
 */
public class LinkedChestSealItem extends Item {
    public LinkedChestSealItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(SealLink link) {
        ItemStack stack = new ItemStack(TXRegistry.LINKED_CHEST_SEAL.get());
        stack.set(TXRegistry.SEAL_LINK.get(), link);
        stack.set(DataComponents.BASE_COLOR, link.color());
        return stack;
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        SealLink link = stack.get(TXRegistry.SEAL_LINK.get());
        if (link == null || !Sealing.isSealableChest(context.getLevel().getBlockState(context.getClickedPos()))) {
            return InteractionResult.PASS;
        }
        if (context.getLevel() instanceof ServerLevel server) {
            Sealing.bindChest(server, context.getClickedPos(), link);
            stack.consume(1, context.getPlayer());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        SealLink link = stack.get(TXRegistry.SEAL_LINK.get());
        if (link != null) {
            builder.accept(Component.translatable("item.thaumicexploration.linked_chest_seal.network",
                    Component.translatable("color.minecraft." + link.color().getSerializedName()), link.id()));
        }
    }
}
