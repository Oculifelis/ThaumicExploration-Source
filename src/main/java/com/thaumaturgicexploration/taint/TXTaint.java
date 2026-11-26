package com.thaumaturgicexploration.taint;

import com.thaumaturgicexploration.TXDataMaps;
import com.thaumaturgicexploration.TXDataMaps.HurtSummon;
import com.thaumaturgicexploration.TXDataMaps.Ward;
import com.thaumaturgicexploration.TXRegistry;
import com.thaumaturgicexploration.ThaumaturgicExploration;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.effects.EnchantmentEntityEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * The taint corner of the mod: the Wispy Dreamcatcher (wards off taint damage, the ward data map), the Ring of the
 * Taintacle (raises a {@link TaintacleMinion} when you are hurt, the hurt_summon data map), and the pieces behind the
 * Binding and Disarming enchantments (the Bound effect and the disarm enchantment effect type).
 */
public final class TXTaint {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, ThaumaturgicExploration.MODID);
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, ThaumaturgicExploration.MODID);
    private static final DeferredRegister<MapCodec<? extends EnchantmentEntityEffect>> ENCHANT_EFFECTS =
            DeferredRegister.create(Registries.ENCHANTMENT_ENTITY_EFFECT_TYPE, ThaumaturgicExploration.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<TaintacleMinion>> MINION = ENTITIES.register("taintacle_minion",
            key -> EntityType.Builder.<TaintacleMinion>of(TaintacleMinion::new, MobCategory.MISC).sized(0.6f, 1.6f)
                    .clientTrackingRange(8).build(net.minecraft.resources.ResourceKey.create(Registries.ENTITY_TYPE, key)));
    /** Bound: an enderman cannot teleport and a creeper's fuse will not burn. */
    public static final DeferredHolder<MobEffect, MobEffect> BOUND = EFFECTS.register("bound", () -> new BoundEffect());
    public static final DeferredHolder<MapCodec<? extends EnchantmentEntityEffect>, MapCodec<DisarmEffect>> DISARM =
            ENCHANT_EFFECTS.register("disarm", () -> DisarmEffect.CODEC);

    public static final DeferredItem<Item> DREAMCATCHER = TXRegistry.item("dreamcatcher", p -> new Item(p.stacksTo(1)));
    public static final DeferredItem<Item> TENTACLE_RING = TXRegistry.item("tentacle_ring", p -> new Item(p.stacksTo(1)));

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
        EFFECTS.register(bus);
        ENCHANT_EFFECTS.register(bus);
        bus.addListener(TXTaint::onAttributes);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(MINION.get(), TaintacleMinion.createAttributes().build());
    }

    /** Charms work worn as curios or carried in the hotbar, as the original's did. */
    private static @Nullable ItemStack carried(Player player, java.util.function.Predicate<ItemStack> test) {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (test.test(stack)) {
                return stack;
            }
        }
        return CuriosApi.getCuriosInventory(player).flatMap(c -> c.findFirstCurio(test)).map(r -> r.stack()).orElse(null);
    }

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        ItemStack ward = carried(player, s -> !s.isEmpty() && s.typeHolder().getData(TXDataMaps.WARD) != null);
        if (ward != null) {
            Ward w = ward.typeHolder().getData(TXDataMaps.WARD);
            Entity attacker = event.getSource().getEntity();
            boolean warded = event.getSource().is(w.damageTypes()) || attacker != null && attacker.getType().builtInRegistryHolder().is(w.attackers());
            if (warded && player.getRandom().nextFloat() < w.chance()) {
                event.setCanceled(true);
                return;
            }
        }
        ItemStack ring = carried(player, s -> !s.isEmpty() && s.typeHolder().getData(TXDataMaps.HURT_SUMMON) != null);
        if (ring != null && player.onGround()) {
            summon(level, player, ring.typeHolder().getData(TXDataMaps.HURT_SUMMON), event.getSource().getEntity());
        }
    }

    /** Raise a minion on a free spot of solid ground near the player and set it on whoever struck. */
    private static void summon(ServerLevel level, Player player, HurtSummon summon, @Nullable Entity attacker) {
        List<BlockPos> spots = new ArrayList<>();
        BlockPos at = player.blockPosition();
        int r = summon.radius();
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-r, -1, -r), at.offset(r, 1, r))) {
            if (!p.equals(at) && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP)
                    && level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()) {
                spots.add(p.immutable());
            }
        }
        if (spots.isEmpty()) {
            return;
        }
        BlockPos spot = spots.get(player.getRandom().nextInt(spots.size()));
        TaintacleMinion minion = MINION.get().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (minion == null) {
            return;
        }
        minion.setLifetime(summon.lifetime());
        minion.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, player.getRandom().nextFloat() * 360, 0);
        if (attacker instanceof LivingEntity living && minion.canAttack(living)) {
            minion.setTarget(living);
        }
        level.addFreshEntity(minion);
        level.playSound(null, spot, SoundEvents.SLIME_SQUISH, minion.getSoundSource(), 1.0f, 0.6f);
    }

    public static void onEnderTeleport(EntityTeleportEvent.EnderEntity event) {
        if (event.getEntityLiving().hasEffect(BOUND)) {
            event.setCanceled(true);
        }
    }

    /** Runs before the creeper's own tick, so its fuse winds back down every tick it stays bound. */
    public static void onEntityTick(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof Creeper creeper && creeper.hasEffect(BOUND) && !creeper.isIgnited()) {
            creeper.setSwellDir(-1);
        }
    }

    private static final class BoundEffect extends MobEffect {
        BoundEffect() {
            super(MobEffectCategory.HARMFUL, 0x6A3D9A);
        }
    }

    private TXTaint() {
    }
}
