package com.thaumaturgicexploration.taint;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.item.enchantment.effects.EnchantmentEntityEffect;
import net.minecraft.world.phys.Vec3;

/**
 * Disarming: with a level-based chance, the struck mob drops what it holds in its main hand. Players are never
 * disarmed (the enchantment's JSON also restricts it to non-players).
 */
public record DisarmEffect(LevelBasedValue chance) implements EnchantmentEntityEffect {
    public static final MapCodec<DisarmEffect> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            LevelBasedValue.CODEC.fieldOf("chance").forGetter(DisarmEffect::chance)
    ).apply(i, DisarmEffect::new));

    @Override
    public void apply(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item, Entity entity, Vec3 position) {
        if (!(entity instanceof Mob mob) || level.getRandom().nextFloat() >= chance.calculate(enchantmentLevel)) {
            return;
        }
        ItemStack held = mob.getItemBySlot(EquipmentSlot.MAINHAND);
        if (held.isEmpty()) {
            return;
        }
        mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        mob.spawnAtLocation(level, held);
    }

    @Override
    public MapCodec<DisarmEffect> codec() {
        return CODEC;
    }
}
