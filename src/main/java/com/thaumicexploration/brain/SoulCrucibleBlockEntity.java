package com.thaumicexploration.brain;

import com.leclowndu93150.thaumaturge.api.aspect.AspectDataMaps;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aura.AuraHelper;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import com.thaumicexploration.TXDataMaps;
import com.thaumicexploration.TXDataMaps.SoulCrucible;
import com.thaumicexploration.TXRegistry;
import com.thaumicexploration.ThaumicExploration;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Crucible of Souls: latches on to the nearest creature in range and slowly drains it; when it dies the crucible
 * keeps essentia from Thaumaturge's entity_aspects data map. Each point survives whole, breaks down into one of its
 * components, or turns to flux that pollutes the aura. Tubes take the essentia out. Range, pace and the odds are the
 * soul_crucible block data map; creatures it will not touch are the soul_crucible_immune entity tag.
 */
public class SoulCrucibleBlockEntity extends BlockEntity implements IEssentiaTransport {
    public static final TagKey<EntityType<?>> IMMUNE = TagKey.create(Registries.ENTITY_TYPE, ThaumicExploration.id("soul_crucible_immune"));
    public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, ThaumicExploration.id("soul_crucible"));

    private AspectList contents = AspectList.EMPTY;
    private float flux;
    private @Nullable UUID target;
    private int drainTicks;

    public SoulCrucibleBlockEntity(BlockPos pos, BlockState state) {
        super(TXRegistry.SOUL_CRUCIBLE_BE.get(), pos, state);
    }

    public AspectList contents() {
        return contents;
    }

    private @Nullable SoulCrucible settings() {
        return getBlockState().getBlock().builtInRegistryHolder().getData(TXDataMaps.SOUL_CRUCIBLE);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SoulCrucibleBlockEntity crucible) {
        SoulCrucible settings = crucible.settings();
        if (settings == null || !(level instanceof ServerLevel server)) {
            return;
        }
        if (!level.getBlockState(pos.above()).isAir()) { // like the original, it only works open to the air
            crucible.target = null;
            return;
        }
        if (crucible.flux >= 1) {
            AuraHelper.polluteAura(level, pos.above(), 1, true);
            crucible.flux -= 1;
            crucible.setChanged();
        }
        LivingEntity mob = crucible.target == null ? null : server.getEntity(crucible.target) instanceof LivingEntity l ? l : null;
        if (mob == null || !mob.isAlive() || mob.distanceToSqr(pos.getCenter()) > (double) settings.range() * settings.range()) {
            crucible.target = null;
            if (level.getGameTime() % 5 == 0) {
                crucible.findTarget(server, pos, settings.range());
            }
            return;
        }
        if (level.getGameTime() % 2 == 0) {
            soulWisp(server, mob, pos);
        }
        if (++crucible.drainTicks % settings.drainInterval() != 0) {
            return;
        }
        if (mob.getHealth() <= 1) {
            crucible.harvest(mob.getType(), settings, level.getRandom());
            mob.hurtServer(server, level.damageSources().source(DAMAGE), Float.MAX_VALUE);
            crucible.target = null;
            crucible.syncToClient();
        } else {
            mob.hurtServer(server, level.damageSources().source(DAMAGE), settings.damage());
        }
    }

    /** A wisp of soul drifts from the victim towards the crucible. */
    private static void soulWisp(ServerLevel server, LivingEntity mob, BlockPos pos) {
        double t = server.getRandom().nextDouble();
        double x = mob.getX() + (pos.getX() + 0.5 - mob.getX()) * t;
        double y = mob.getEyeY() + (pos.getY() + 0.9 - mob.getEyeY()) * t;
        double z = mob.getZ() + (pos.getZ() + 0.5 - mob.getZ()) * t;
        server.sendParticles(ParticleTypes.SOUL, x, y, z, 1, 0.05, 0.05, 0.05, 0.01);
    }

    private void findTarget(ServerLevel level, BlockPos pos, int range) {
        List<LivingEntity> mobs = level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(range),
                e -> e.isAlive() && !(e instanceof Player) && !e.getType().builtInRegistryHolder().is(IMMUNE));
        LivingEntity nearest = null;
        double best = (double) range * range;
        for (LivingEntity e : mobs) {
            double d = e.distanceToSqr(pos.getCenter());
            if (d < best) {
                best = d;
                nearest = e;
            }
        }
        if (nearest != null) {
            target = nearest.getUUID();
            drainTicks = 0;
        }
    }

    private void harvest(EntityType<?> type, SoulCrucible settings, RandomSource random) {
        AspectList souls = type.builtInRegistryHolder().getData(AspectDataMaps.ENTITY_ASPECTS);
        if (souls == null) {
            return;
        }
        for (AspectInstance part : souls.entries()) {
            for (int i = 0; i < part.amount(); i++) {
                float roll = random.nextFloat();
                if (roll < settings.keepChance()) {
                    contents = contents.add(part.aspect(), 1);
                } else if (roll < settings.keepChance() + settings.splitChance()) {
                    List<Holder<IAspect>> parts = part.aspect().value().components();
                    contents = contents.add(parts.isEmpty() ? part.aspect() : parts.get(random.nextInt(parts.size())), 1);
                } else {
                    flux += settings.fluxPerPoint();
                }
            }
        }
        setChanged();
    }

    private void syncToClient() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // Essentia transport: output only, to tubes on any side.
    @Override
    public boolean isConnectable(Direction side) {
        return true;
    }

    @Override
    public boolean canInputFrom(Direction side) {
        return false;
    }

    @Override
    public boolean canOutputTo(Direction side) {
        return true;
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
        return 0;
    }

    @Override
    public int getMinimumSuction() {
        return 0;
    }

    @Override
    public int takeEssentia(Holder<IAspect> aspect, int amount, Direction side) {
        int got = Math.min(amount, contents.amountOf(aspect));
        if (got > 0) {
            contents = contents.reduce(aspect, got);
            syncToClient();
        }
        return got;
    }

    @Override
    public int addEssentia(Holder<IAspect> aspect, int amount, Direction side) {
        return 0;
    }

    @Override
    public @Nullable Holder<IAspect> getEssentiaType(Direction side) {
        return contents.isEmpty() ? null : contents.entries().getFirst().aspect();
    }

    @Override
    public int getEssentiaAmount(Direction side) {
        return contents.isEmpty() ? 0 : contents.entries().getFirst().amount();
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.store("contents", AspectList.CODEC, contents);
        out.putFloat("flux", flux);
        if (target != null) {
            out.store("target", UUIDUtil.CODEC, target);
        }
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        contents = in.read("contents", AspectList.CODEC).orElse(AspectList.EMPTY);
        flux = in.getFloatOr("flux", 0);
        target = in.read("target", UUIDUtil.CODEC).orElse(null);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
