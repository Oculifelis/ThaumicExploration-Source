package com.thaumicexploration.taint;

import com.leclowndu93150.thaumaturge.content.entity.AbstractTaintacle;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A small taintacle called up by the Ring of the Taintacle. It roots where it rises, lashes at hostile mobs (never
 * at players or other minions), needs no tainted ground, and withers away when its time is up.
 */
public final class TaintacleMinion extends AbstractTaintacle {
    private int lifetime = 200;

    public TaintacleMinion(EntityType<? extends TaintacleMinion> type, Level level) {
        super(type, level);
        xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractTaintacle.createTaintacleAttributes(5.0, 2.0);
    }

    public void setLifetime(int ticks) {
        lifetime = ticks;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, true));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Mob.class, 0, true, false,
                (target, level) -> target instanceof Enemy && !(target instanceof TaintacleMinion)));
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return target instanceof Enemy && !(target instanceof TaintacleMinion) && super.canAttack(target);
    }

    /** Replaces the taint-biome check (which starves taintacles off taint) with a plain countdown. */
    @Override
    protected void rootedServerStep(ServerLevel level) {
        if (--lifetime <= 0) {
            discard();
        }
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return true;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("lifetime", lifetime);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        lifetime = input.getIntOr("lifetime", 200);
    }
}
