package com.thaumicexploration.wand;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aura.AuraHelper;
import com.leclowndu93150.thaumaturge.api.aura.VisRelayHelper;
import com.leclowndu93150.thaumaturge.api.wands.WandCap;
import com.leclowndu93150.thaumaturge.api.wands.WandVis;
import com.leclowndu93150.thaumaturge.content.wands.ItemWandCap;
import com.leclowndu93150.thaumaturge.content.wands.WandParts;
import com.leclowndu93150.thaumaturge.content.wands.WandVisHelper;
import com.leclowndu93150.thaumaturge.registry.TTWandParts;
import com.thaumicexploration.TXDataMaps;
import com.thaumicexploration.TXDataMaps.CapRecharge;
import com.thaumicexploration.TXRegistry;
import com.thaumicexploration.ThaumicExploration;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Sojourner's and Mechanist's caps: channel vis a little better than gold (cost 0.95) and refill a held wand. How
 * is the cap_recharge data map: Sojourner's draws from the chunk's aura, Mechanist's from a vis relay network.
 */
public final class TXCaps {
    private static final DeferredRegister<WandCap> CAPS = DeferredRegister.create(WandCap.REGISTRY_KEY, ThaumicExploration.MODID);

    public static final DeferredHolder<WandCap, WandCap> SOJOURNER = cap("sojourner");
    public static final DeferredHolder<WandCap, WandCap> MECHANIST = cap("mechanist");

    public static final DeferredItem<Item> SOJOURNER_INERT = TXRegistry.item("wand_cap_sojourner_inert", Item::new);
    public static final DeferredItem<ItemWandCap> SOJOURNER_ITEM = TXRegistry.item("wand_cap_sojourner", p -> new ItemWandCap(p, SOJOURNER));
    public static final DeferredItem<Item> MECHANIST_INERT = TXRegistry.item("wand_cap_mechanist_inert", Item::new);
    public static final DeferredItem<ItemWandCap> MECHANIST_ITEM = TXRegistry.item("wand_cap_mechanist", p -> new ItemWandCap(p, MECHANIST));

    private static DeferredHolder<WandCap, WandCap> cap(String name) {
        return CAPS.register(name, () -> new WandCap(0.95f, List.of(), 0.95f, 6,
                ThaumicExploration.id("textures/models/wand_cap_" + name + ".png")));
    }

    public static void register(IEventBus bus) {
        CAPS.register(bus);
    }

    /** Server side, either hand: a wand whose cap has a cap_recharge entry tops up one short aspect per interval. */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack wand = player.getItemInHand(hand);
            WandParts parts = wand.isEmpty() ? null : WandVisHelper.partsOf(wand);
            if (parts == null) {
                continue;
            }
            ResourceKey<WandCap> key = TTWandParts.caps().getResourceKey(parts.cap()).orElse(null);
            CapRecharge recharge = key == null ? null : TTWandParts.caps().getDataMap(TXDataMaps.CAP_RECHARGE).get(key);
            if (recharge != null && player.tickCount % recharge.interval() == 0) {
                recharge(level, player, wand, parts.maxCentivis(), recharge);
            }
        }
    }

    private static void recharge(ServerLevel level, Player player, ItemStack wand, int max, CapRecharge recharge) {
        WandVis vis = WandVisHelper.chargeOf(wand);
        List<ResourceKey<IAspect>> low = new ArrayList<>();
        for (ResourceKey<IAspect> aspect : recharge.aspects()) {
            if (vis.amount(aspect) < max) {
                low.add(aspect);
            }
        }
        if (low.isEmpty()) {
            return;
        }
        ResourceKey<IAspect> aspect = low.get(level.getRandom().nextInt(low.size()));
        int want = Math.min(recharge.centivis(), max - vis.amount(aspect));
        int got;
        try (Transaction tx = Transaction.openRoot()) {
            if (recharge.source() == CapRecharge.Source.RELAY) {
                got = VisRelayHelper.drainCentivis(level, player.blockPosition(), aspect, want, tx);
            } else {
                got = Math.round(AuraHelper.drainVis(level, player.blockPosition(), want / 100f, tx) * 100);
            }
            tx.commit();
        }
        if (got > 0) {
            WandVisHelper.writeCharge(wand, vis.with(aspect, Math.min(max, vis.amount(aspect) + got)));
        }
    }

    private TXCaps() {
    }
}
