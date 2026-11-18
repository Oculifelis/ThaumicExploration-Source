package com.thaumicexploration.wand;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.wands.WandRod;
import com.leclowndu93150.thaumaturge.api.wands.WandVis;
import com.leclowndu93150.thaumaturge.content.wands.WandParts;
import com.leclowndu93150.thaumaturge.content.wands.WandVisHelper;
import com.leclowndu93150.thaumaturge.registry.TTWandParts;
import com.thaumicexploration.TXDataMaps;
import com.thaumicexploration.TXDataMaps.RodChatter;
import com.thaumicexploration.TXDataMaps.VisBalance;
import com.thaumicexploration.TXDataMaps.VisIntake;
import com.thaumicexploration.TXDataMaps.VisTrickle;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Held-wand behaviour shared by every rod, driven by the rod's data map entries (none = does nothing). */
final class RodBehaviour {
    static void update(ItemStack wand, Player player) {
        if (player.level().isClientSide()) {
            return;
        }
        WandParts parts = WandVisHelper.partsOf(wand);
        if (parts == null) {
            return;
        }
        Registry<WandRod> rods = TTWandParts.rods();
        ResourceKey<WandRod> key = rods.getResourceKey(parts.rod()).orElse(null);
        if (key == null) {
            return;
        }
        VisIntake intake = rods.getDataMap(TXDataMaps.VIS_INTAKE).get(key);
        if (intake != null) {
            RodEvents.intake(wand, intake);
        }
        VisTrickle trickle = rods.getDataMap(TXDataMaps.VIS_TRICKLE).get(key);
        if (trickle != null && player.tickCount % trickle.interval() == 0) {
            trickle(wand, parts.maxCentivis(), trickle);
        }
        VisBalance balance = rods.getDataMap(TXDataMaps.VIS_BALANCE).get(key);
        if (balance != null && player.tickCount % balance.interval() == 0) {
            balance(wand, parts.maxCentivis(), balance);
        }
        RodChatter chatter = rods.getDataMap(TXDataMaps.ROD_CHATTER).get(key);
        if (chatter != null && player.getRandom().nextFloat() < chatter.chance()) {
            String message = chatter.messages().get(player.getRandom().nextInt(chatter.messages().size()));
            player.sendSystemMessage(Component.literal("<").append(wand.getHoverName()).append("> ").append(Component.translatable(message)));
        }
    }

    /** Amber: slowly refills every aspect that is short, out of nothing. */
    private static void trickle(ItemStack wand, int max, VisTrickle trickle) {
        WandVis vis = WandVisHelper.chargeOf(wand);
        WandVis next = vis;
        int cutoff = (int) (max * trickle.below());
        for (ResourceKey<IAspect> aspect : trickle.aspects()) {
            int amount = vis.amount(aspect);
            if (amount < cutoff) {
                next = next.with(aspect, Math.min(max, amount + trickle.centivis()));
            }
        }
        if (next != vis) {
            WandVisHelper.writeCharge(wand, next);
        }
    }

    /** Transmutative: drains what is above the threshold in full aspects into the ones that are running low, with loss. */
    private static void balance(ItemStack wand, int max, VisBalance balance) {
        WandVis vis = WandVisHelper.chargeOf(wand);
        int threshold = (int) (max * balance.threshold());
        List<ResourceKey<IAspect>> low = new ArrayList<>();
        for (ResourceKey<IAspect> aspect : balance.aspects()) {
            if (vis.amount(aspect) <= threshold) {
                low.add(aspect);
            }
        }
        if (low.isEmpty() || low.size() == balance.aspects().size()) {
            return;
        }
        int excess = 0;
        WandVis next = vis;
        for (ResourceKey<IAspect> aspect : balance.aspects()) {
            int over = vis.amount(aspect) - threshold;
            if (over > 0) {
                excess += over;
                next = next.with(aspect, threshold);
            }
        }
        int share = (int) (excess * balance.efficiency() / low.size());
        if (share <= 0) {
            return;
        }
        for (ResourceKey<IAspect> aspect : low) {
            next = next.with(aspect, Math.min(max, next.amount(aspect) + share));
        }
        WandVisHelper.writeCharge(wand, next);
    }

    private RodBehaviour() {
    }
}
