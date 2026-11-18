package com.thaumicexploration.wand;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.wands.WandRod;
import com.leclowndu93150.thaumaturge.api.wands.WandVis;
import com.leclowndu93150.thaumaturge.content.wands.WandParts;
import com.leclowndu93150.thaumaturge.content.wands.WandVisHelper;
import com.leclowndu93150.thaumaturge.registry.TTWandParts;
import com.thaumicexploration.TXDataMaps;
import com.thaumicexploration.TXDataMaps.HitEffect;
import com.thaumicexploration.TXDataMaps.KillVis;
import com.thaumicexploration.TXDataMaps.VisIntake;
import com.thaumicexploration.TXRegistry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.jspecify.annotations.Nullable;

/**
 * Rods that care how their vis arrives (the Necromancer's staff core): vis_intake scales every gain the wand sees,
 * kill_vis feeds it from slain monsters, hit_effect afflicts what it strikes.
 */
public final class RodEvents {
    /** What the wand held last time its intake was checked, so new gains can be told apart. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WandVis>> LAST_VIS =
            TXRegistry.COMPONENTS.registerComponentType("last_vis", b -> b.persistent(WandVis.CODEC).networkSynchronized(WandVis.STREAM_CODEC));

    static @Nullable ResourceKey<WandRod> rodKey(ItemStack wand) {
        WandParts parts = wand.isEmpty() ? null : WandVisHelper.partsOf(wand);
        return parts == null ? null : TTWandParts.rods().getResourceKey(parts.rod()).orElse(null);
    }

    /** Called from the rod's update: every gain since the last check is cut to the intake multiplier. */
    static void intake(ItemStack wand, VisIntake intake) {
        WandVis now = WandVisHelper.chargeOf(wand);
        WandVis last = wand.get(LAST_VIS.get());
        if (last != null && !last.equals(now)) {
            Set<ResourceKey<IAspect>> keys = new HashSet<>(now.centivis().keySet());
            keys.addAll(last.centivis().keySet());
            WandVis next = now;
            for (ResourceKey<IAspect> aspect : keys) {
                int gain = now.amount(aspect) - last.amount(aspect);
                if (gain > 0) {
                    next = next.with(aspect, last.amount(aspect) + (int) (gain * intake.multiplier()));
                }
            }
            if (!next.equals(now)) {
                WandVisHelper.writeCharge(wand, next);
                now = next;
            }
        }
        wand.set(LAST_VIS.get(), now);
    }

    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player) || !(event.getEntity() instanceof Enemy)) {
            return;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack wand = player.getItemInHand(hand);
            ResourceKey<WandRod> key = rodKey(wand);
            KillVis kill = key == null ? null : TTWandParts.rods().getDataMap(TXDataMaps.KILL_VIS).get(key);
            if (kill != null) {
                feed(player, wand, kill, event.getEntity().getMaxHealth());
                return;
            }
        }
    }

    private static void feed(Player player, ItemStack wand, KillVis kill, float health) {
        WandParts parts = WandVisHelper.partsOf(wand);
        WandVis vis = WandVisHelper.chargeOf(wand);
        List<ResourceKey<IAspect>> low = new ArrayList<>();
        for (ResourceKey<IAspect> aspect : kill.aspects()) {
            if (vis.amount(aspect) < parts.maxCentivis()) {
                low.add(aspect);
            }
        }
        if (low.isEmpty()) {
            return;
        }
        ResourceKey<IAspect> aspect = low.get(player.getRandom().nextInt(low.size()));
        WandVis next = vis.with(aspect, Math.min(parts.maxCentivis(), vis.amount(aspect) + (int) (health * kill.centivisPerHealth())));
        WandVisHelper.writeCharge(wand, next);
        wand.set(LAST_VIS.get(), next);  // a kill is not throttled by the intake
    }

    /** A melee blow with the wand in the main hand applies the rod's hit effect to the victim. */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getDirectEntity() instanceof Player player) || event.getSource().getEntity() != player) {
            return;
        }
        ResourceKey<WandRod> key = rodKey(player.getMainHandItem());
        HitEffect hit = key == null ? null : TTWandParts.rods().getDataMap(TXDataMaps.HIT_EFFECT).get(key);
        LivingEntity victim = event.getEntity();
        if (hit != null && victim instanceof Enemy) {
            victim.addEffect(new MobEffectInstance(hit.effect(), hit.duration(), hit.amplifier()), player);
        }
    }

    public static void register() {
        // Class load registers LAST_VIS on TXRegistry.COMPONENTS before the register events fire.
    }

    private RodEvents() {
    }
}
