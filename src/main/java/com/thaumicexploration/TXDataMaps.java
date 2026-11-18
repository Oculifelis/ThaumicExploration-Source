package com.thaumicexploration;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.wands.WandCap;
import com.leclowndu93150.thaumaturge.api.wands.WandRod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/** Every tunable lives in a data map; the JSON comes from tools/gen_data.py and packs can override it. */
public final class TXDataMaps {
    private static final Codec<ResourceKey<IAspect>> ASPECT = ResourceKey.codec(IAspect.REGISTRY_KEY);

    /** Amber core: every {@code interval} ticks, each listed aspect below {@code below} of the wand's capacity gains {@code centivis}. */
    public record VisTrickle(int interval, int centivis, float below, List<ResourceKey<IAspect>> aspects) {
        public static final Codec<VisTrickle> CODEC = RecordCodecBuilder.create(i -> i.group(
                ExtraCodecs.POSITIVE_INT.fieldOf("interval").forGetter(VisTrickle::interval),
                ExtraCodecs.POSITIVE_INT.fieldOf("centivis").forGetter(VisTrickle::centivis),
                Codec.floatRange(0, 1).fieldOf("below").forGetter(VisTrickle::below),
                ASPECT.listOf().fieldOf("aspects").forGetter(VisTrickle::aspects)
        ).apply(i, VisTrickle::new));
    }

    /**
     * Transmutative core: when any listed aspect is at or under {@code threshold} of capacity, vis above the threshold in
     * the others is shared out among the low ones. {@code efficiency} is the fraction that survives the transfer.
     */
    public record VisBalance(int interval, float threshold, float efficiency, List<ResourceKey<IAspect>> aspects) {
        public static final Codec<VisBalance> CODEC = RecordCodecBuilder.create(i -> i.group(
                ExtraCodecs.POSITIVE_INT.fieldOf("interval").forGetter(VisBalance::interval),
                Codec.floatRange(0, 1).fieldOf("threshold").forGetter(VisBalance::threshold),
                Codec.floatRange(0, 1).fieldOf("efficiency").forGetter(VisBalance::efficiency),
                ASPECT.listOf().fieldOf("aspects").forGetter(VisBalance::aspects)
        ).apply(i, VisBalance::new));
    }

    /**
     * A vis-fed fluid source block: every {@code interval} ticks, while below {@code capacity} mB, it draws
     * {@code centivis} of {@code aspect} from the vis relay network (or the chunk's aura when no relay reaches it)
     * and turns it into {@code mb_per_drain} mB of {@code fluid}.
     */
    public record UrnSource(Fluid fluid, ResourceKey<IAspect> aspect, int interval, int centivis, int mbPerDrain, int capacity) {
        public static final Codec<UrnSource> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.FLUID.byNameCodec().fieldOf("fluid").forGetter(UrnSource::fluid),
                ASPECT.fieldOf("aspect").forGetter(UrnSource::aspect),
                ExtraCodecs.POSITIVE_INT.fieldOf("interval").forGetter(UrnSource::interval),
                ExtraCodecs.POSITIVE_INT.fieldOf("centivis").forGetter(UrnSource::centivis),
                ExtraCodecs.POSITIVE_INT.fieldOf("mb_per_drain").forGetter(UrnSource::mbPerDrain),
                ExtraCodecs.POSITIVE_INT.fieldOf("capacity").forGetter(UrnSource::capacity)
        ).apply(i, UrnSource::new));
    }

    /** One attribute modifier a curio grants while worn (e.g. Thaumaturge's vis discount, knockback resistance). */
    public record CurioAttribute(Holder<Attribute> attribute, AttributeModifier modifier) {
        public static final Codec<CurioAttribute> CODEC = RecordCodecBuilder.create(i -> i.group(
                Attribute.CODEC.fieldOf("attribute").forGetter(CurioAttribute::attribute),
                AttributeModifier.MAP_CODEC.forGetter(CurioAttribute::modifier)
        ).apply(i, CurioAttribute::new));
    }

    /** Talisman of Nourishment: how many points it holds and how often (ticks) it eats and feeds. */
    public record Talisman(int capacity, int interval) {
        public static final Codec<Talisman> CODEC = RecordCodecBuilder.create(i -> i.group(
                ExtraCodecs.POSITIVE_INT.fieldOf("capacity").forGetter(Talisman::capacity),
                ExtraCodecs.POSITIVE_INT.fieldOf("interval").forGetter(Talisman::interval)
        ).apply(i, Talisman::new));
    }

    /** Think Tank: what a book becomes, how long it takes (ticks), and the odds of each count of result. */
    public record ThinkTankInput(Item result, int time, List<Outcome> outcomes) {
        public record Outcome(float chance, int count) {
            public static final Codec<Outcome> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.floatRange(0, 1).fieldOf("chance").forGetter(Outcome::chance),
                    ExtraCodecs.POSITIVE_INT.fieldOf("count").forGetter(Outcome::count)
            ).apply(i, Outcome::new));
        }

        public static final Codec<ThinkTankInput> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("result").forGetter(ThinkTankInput::result),
                ExtraCodecs.POSITIVE_INT.fieldOf("time").forGetter(ThinkTankInput::time),
                Outcome.CODEC.listOf().fieldOf("outcomes").forGetter(ThinkTankInput::outcomes)
        ).apply(i, ThinkTankInput::new));
    }

    /** Thaumic Replicator: how far it looks for essentia, ticks per point drawn, and ticks to form the copy. */
    public record Replicator(int range, int essentiaInterval, int finishTicks) {
        public static final Codec<Replicator> CODEC = RecordCodecBuilder.create(i -> i.group(
                ExtraCodecs.POSITIVE_INT.fieldOf("range").forGetter(Replicator::range),
                ExtraCodecs.POSITIVE_INT.fieldOf("essentia_interval").forGetter(Replicator::essentiaInterval),
                ExtraCodecs.POSITIVE_INT.fieldOf("finish_ticks").forGetter(Replicator::finishTicks)
        ).apply(i, Replicator::new));
    }

    /**
     * Crucible of Souls: reach, ticks between drains, damage per drain, and for each aspect point of a slain creature
     * the chance it is kept whole or split into a component (the rest becomes {@code flux_per_point} flux).
     */
    public record SoulCrucible(int range, int drainInterval, float damage, float keepChance, float splitChance, float fluxPerPoint) {
        public static final Codec<SoulCrucible> CODEC = RecordCodecBuilder.create(i -> i.group(
                ExtraCodecs.POSITIVE_INT.fieldOf("range").forGetter(SoulCrucible::range),
                ExtraCodecs.POSITIVE_INT.fieldOf("drain_interval").forGetter(SoulCrucible::drainInterval),
                ExtraCodecs.POSITIVE_FLOAT.fieldOf("damage").forGetter(SoulCrucible::damage),
                Codec.floatRange(0, 1).fieldOf("keep_chance").forGetter(SoulCrucible::keepChance),
                Codec.floatRange(0, 1).fieldOf("split_chance").forGetter(SoulCrucible::splitChance),
                Codec.floatRange(0, 10).fieldOf("flux_per_point").forGetter(SoulCrucible::fluxPerPoint)
        ).apply(i, SoulCrucible::new));
    }

    /** A wand cap that refills a held wand: from the chunk's aura or a vis relay, every interval, for these aspects. */
    public record CapRecharge(Source source, int interval, int centivis, List<ResourceKey<IAspect>> aspects) {
        public enum Source implements StringRepresentable {
            AURA, RELAY;

            public static final Codec<Source> CODEC = StringRepresentable.fromEnum(Source::values);

            @Override
            public String getSerializedName() {
                return name().toLowerCase(Locale.ROOT);
            }
        }

        public static final Codec<CapRecharge> CODEC = RecordCodecBuilder.create(i -> i.group(
                Source.CODEC.fieldOf("source").forGetter(CapRecharge::source),
                ExtraCodecs.POSITIVE_INT.fieldOf("interval").forGetter(CapRecharge::interval),
                ExtraCodecs.POSITIVE_INT.fieldOf("centivis").forGetter(CapRecharge::centivis),
                ASPECT.listOf().fieldOf("aspects").forGetter(CapRecharge::aspects)
        ).apply(i, CapRecharge::new));
    }

    /** A rod that talks: the chance per held tick, and the translation keys it picks from. */
    public record RodChatter(float chance, List<String> messages) {
        public static final Codec<RodChatter> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0, 1).fieldOf("chance").forGetter(RodChatter::chance),
                Codec.STRING.listOf(1, 64).fieldOf("messages").forGetter(RodChatter::messages)
        ).apply(i, RodChatter::new));
    }

    /** One attribute modifier an item grants while worn in its slot group (boots: speed, step height, fall). */
    public record WornAttribute(Holder<Attribute> attribute, AttributeModifier modifier, EquipmentSlotGroup slot) {
        public static final Codec<WornAttribute> CODEC = RecordCodecBuilder.create(i -> i.group(
                Attribute.CODEC.fieldOf("attribute").forGetter(WornAttribute::attribute),
                AttributeModifier.MAP_CODEC.forGetter(WornAttribute::modifier),
                EquipmentSlotGroup.CODEC.fieldOf("slot").forGetter(WornAttribute::slot)
        ).apply(i, WornAttribute::new));
    }

    /**
     * What worn boots do: a crouch-jump dash (0 = none), the midair crouch slam, frost-walking radius (0 = none),
     * and a sprint speed that builds by {@code ramp_per_second} up to {@code ramp_max}.
     */
    public record BootEffects(float dash, boolean smash, int frostWalk, float rampPerSecond, float rampMax) {
        public static final Codec<BootEffects> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0, 4).optionalFieldOf("dash", 0f).forGetter(BootEffects::dash),
                Codec.BOOL.optionalFieldOf("smash", false).forGetter(BootEffects::smash),
                Codec.intRange(0, 8).optionalFieldOf("frost_walk", 0).forGetter(BootEffects::frostWalk),
                Codec.floatRange(0, 4).optionalFieldOf("ramp_per_second", 0f).forGetter(BootEffects::rampPerSecond),
                Codec.floatRange(0, 4).optionalFieldOf("ramp_max", 0f).forGetter(BootEffects::rampMax)
        ).apply(i, BootEffects::new));
    }

    /** A charm that turns aside, with this chance, damage of these types or from these attackers. */
    public record Ward(float chance, TagKey<DamageType> damageTypes, TagKey<EntityType<?>> attackers) {
        public static final Codec<Ward> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0, 1).fieldOf("chance").forGetter(Ward::chance),
                TagKey.hashedCodec(Registries.DAMAGE_TYPE).fieldOf("damage_types").forGetter(Ward::damageTypes),
                TagKey.hashedCodec(Registries.ENTITY_TYPE).fieldOf("attackers").forGetter(Ward::attackers)
        ).apply(i, Ward::new));
    }

    /** A ring that raises a taintacle minion within {@code radius} blocks when its wearer is hurt. */
    public record HurtSummon(int radius, int lifetime) {
        public static final Codec<HurtSummon> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 8).fieldOf("radius").forGetter(HurtSummon::radius),
                ExtraCodecs.POSITIVE_INT.fieldOf("lifetime").forGetter(HurtSummon::lifetime)
        ).apply(i, HurtSummon::new));
    }

    /** A rod that takes in only this share of the vis it gains by any means but kill_vis. */
    public record VisIntake(float multiplier) {
        public static final Codec<VisIntake> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.floatRange(0, 4).fieldOf("multiplier").forGetter(VisIntake::multiplier)
        ).apply(i, VisIntake::new));
    }

    /** A rod fed by the monsters its holder slays: centivis per point of the victim's max health, to a short aspect. */
    public record KillVis(float centivisPerHealth, List<ResourceKey<IAspect>> aspects) {
        public static final Codec<KillVis> CODEC = RecordCodecBuilder.create(i -> i.group(
                ExtraCodecs.POSITIVE_FLOAT.fieldOf("centivis_per_health").forGetter(KillVis::centivisPerHealth),
                ASPECT.listOf().fieldOf("aspects").forGetter(KillVis::aspects)
        ).apply(i, KillVis::new));
    }

    /** A rod whose melee blows afflict monsters with an effect. */
    public record HitEffect(Holder<MobEffect> effect, int duration, int amplifier) {
        public static final Codec<HitEffect> CODEC = RecordCodecBuilder.create(i -> i.group(
                MobEffect.CODEC.fieldOf("effect").forGetter(HitEffect::effect),
                ExtraCodecs.POSITIVE_INT.fieldOf("duration").forGetter(HitEffect::duration),
                Codec.intRange(0, 255).optionalFieldOf("amplifier", 0).forGetter(HitEffect::amplifier)
        ).apply(i, HitEffect::new));
    }

    /**
     * Soul Brazier: the vis it burns and the essentia it takes from below (amounts in centivis and points), how often
     * it burns a cycle's worth, and how its taint spreads.
     */
    public record Brazier(ResourceKey<IAspect> vis, ResourceKey<IAspect> essentia, int visCapacity, int essentiaCapacity,
                          int visPerCycle, int essentiaPerCycle, int cycle, int taintInterval, int taintRadius,
                          float taintPressure) {
        public static final Codec<Brazier> CODEC = RecordCodecBuilder.create(i -> i.group(
                ASPECT.fieldOf("vis").forGetter(Brazier::vis),
                ASPECT.fieldOf("essentia").forGetter(Brazier::essentia),
                ExtraCodecs.POSITIVE_INT.fieldOf("vis_capacity").forGetter(Brazier::visCapacity),
                ExtraCodecs.POSITIVE_INT.fieldOf("essentia_capacity").forGetter(Brazier::essentiaCapacity),
                ExtraCodecs.POSITIVE_INT.fieldOf("vis_per_cycle").forGetter(Brazier::visPerCycle),
                ExtraCodecs.POSITIVE_INT.fieldOf("essentia_per_cycle").forGetter(Brazier::essentiaPerCycle),
                ExtraCodecs.POSITIVE_INT.fieldOf("cycle").forGetter(Brazier::cycle),
                ExtraCodecs.POSITIVE_INT.fieldOf("taint_interval").forGetter(Brazier::taintInterval),
                Codec.intRange(1, 64).fieldOf("taint_radius").forGetter(Brazier::taintRadius),
                Codec.floatRange(0, 10).fieldOf("taint_pressure").forGetter(Brazier::taintPressure)
        ).apply(i, Brazier::new));
    }

    public static final DataMapType<Item, List<CurioAttribute>> CURIO_ATTRIBUTES =
            DataMapType.builder(ThaumicExploration.id("curio_attributes"), Registries.ITEM, CurioAttribute.CODEC.listOf())
                    .synced(CurioAttribute.CODEC.listOf(), false).build();
    public static final DataMapType<Item, Talisman> TALISMAN =
            DataMapType.builder(ThaumicExploration.id("talisman"), Registries.ITEM, Talisman.CODEC)
                    .synced(Talisman.CODEC, false).build();

    public static final DataMapType<Item, ThinkTankInput> THINK_TANK =
            DataMapType.builder(ThaumicExploration.id("think_tank"), Registries.ITEM, ThinkTankInput.CODEC)
                    .synced(ThinkTankInput.CODEC, false).build();
    public static final DataMapType<Block, Replicator> REPLICATOR =
            DataMapType.builder(ThaumicExploration.id("replicator"), Registries.BLOCK, Replicator.CODEC).build();
    public static final DataMapType<Block, SoulCrucible> SOUL_CRUCIBLE =
            DataMapType.builder(ThaumicExploration.id("soul_crucible"), Registries.BLOCK, SoulCrucible.CODEC).build();

    public static final DataMapType<WandCap, CapRecharge> CAP_RECHARGE =
            DataMapType.builder(ThaumicExploration.id("cap_recharge"), WandCap.REGISTRY_KEY, CapRecharge.CODEC).build();
    public static final DataMapType<WandRod, RodChatter> ROD_CHATTER =
            DataMapType.builder(ThaumicExploration.id("rod_chatter"), WandRod.REGISTRY_KEY, RodChatter.CODEC).build();
    // Boots move the player, which the client computes, so both maps are synced to clients.
    public static final DataMapType<Item, List<WornAttribute>> WORN_ATTRIBUTES =
            DataMapType.builder(ThaumicExploration.id("worn_attributes"), Registries.ITEM, WornAttribute.CODEC.listOf())
                    .synced(WornAttribute.CODEC.listOf(), false).build();
    public static final DataMapType<Item, BootEffects> BOOT_EFFECTS =
            DataMapType.builder(ThaumicExploration.id("boot_effects"), Registries.ITEM, BootEffects.CODEC)
                    .synced(BootEffects.CODEC, false).build();

    public static final DataMapType<Item, Ward> WARD =
            DataMapType.builder(ThaumicExploration.id("ward"), Registries.ITEM, Ward.CODEC).build();
    public static final DataMapType<Item, HurtSummon> HURT_SUMMON =
            DataMapType.builder(ThaumicExploration.id("hurt_summon"), Registries.ITEM, HurtSummon.CODEC).build();

    public static final DataMapType<WandRod, VisIntake> VIS_INTAKE =
            DataMapType.builder(ThaumicExploration.id("vis_intake"), WandRod.REGISTRY_KEY, VisIntake.CODEC).build();
    public static final DataMapType<WandRod, KillVis> KILL_VIS =
            DataMapType.builder(ThaumicExploration.id("kill_vis"), WandRod.REGISTRY_KEY, KillVis.CODEC).build();
    public static final DataMapType<WandRod, HitEffect> HIT_EFFECT =
            DataMapType.builder(ThaumicExploration.id("hit_effect"), WandRod.REGISTRY_KEY, HitEffect.CODEC).build();
    public static final DataMapType<Block, Brazier> SOUL_BRAZIER =
            DataMapType.builder(ThaumicExploration.id("soul_brazier"), Registries.BLOCK, Brazier.CODEC).build();

    public static final DataMapType<WandRod, VisTrickle> VIS_TRICKLE =
            DataMapType.builder(ThaumicExploration.id("vis_trickle"), WandRod.REGISTRY_KEY, VisTrickle.CODEC).build();
    public static final DataMapType<WandRod, VisBalance> VIS_BALANCE =
            DataMapType.builder(ThaumicExploration.id("vis_balance"), WandRod.REGISTRY_KEY, VisBalance.CODEC).build();
    /**
     * The old Thaumic Exploration Everfull Urn, laid over Thaumaturge's: free water, and every {@code interval} ticks it
     * waters the nearest block within {@code range} (sideways) and {@code y_range} that takes water, {@code mb_per_push} mB
     * at a time, or one cauldron level per {@code cauldron_pushes} pushes. It can put out burning players in the same box
     * and shows a fountain while idle. Blocks in {@code thaumicexploration:everfull_urn_ignores} are never watered.
     */
    public record UrnSpring(int range, int yRange, int interval, int mbPerPush, int cauldronPushes, boolean needsAirAbove,
                            boolean extinguish, boolean fountain) {
        public static final Codec<UrnSpring> CODEC = RecordCodecBuilder.create(i -> i.group(
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("range").forGetter(UrnSpring::range),
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("y_range").forGetter(UrnSpring::yRange),
                ExtraCodecs.POSITIVE_INT.fieldOf("interval").forGetter(UrnSpring::interval),
                ExtraCodecs.POSITIVE_INT.fieldOf("mb_per_push").forGetter(UrnSpring::mbPerPush),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("cauldron_pushes", 4).forGetter(UrnSpring::cauldronPushes),
                Codec.BOOL.optionalFieldOf("needs_air_above", true).forGetter(UrnSpring::needsAirAbove),
                Codec.BOOL.optionalFieldOf("extinguish", true).forGetter(UrnSpring::extinguish),
                Codec.BOOL.optionalFieldOf("fountain", true).forGetter(UrnSpring::fountain)
        ).apply(i, UrnSpring::new));
    }

    public static final DataMapType<Block, UrnSpring> URN_SPRING =
            DataMapType.builder(ThaumicExploration.id("urn_spring"), Registries.BLOCK, UrnSpring.CODEC).build();

    public static final DataMapType<Block, UrnSource> URN_SOURCE =
            DataMapType.builder(ThaumicExploration.id("urn_source"), Registries.BLOCK, UrnSource.CODEC).build();

    static void register(RegisterDataMapTypesEvent event) {
        event.register(VIS_TRICKLE);
        event.register(VIS_BALANCE);
        event.register(URN_SOURCE);
        event.register(URN_SPRING);
        event.register(CURIO_ATTRIBUTES);
        event.register(TALISMAN);
        event.register(VIS_INTAKE);
        event.register(KILL_VIS);
        event.register(HIT_EFFECT);
        event.register(SOUL_BRAZIER);
        event.register(WARD);
        event.register(HURT_SUMMON);
        event.register(CAP_RECHARGE);
        event.register(ROD_CHATTER);
        event.register(WORN_ATTRIBUTES);
        event.register(BOOT_EFFECTS);
        event.register(THINK_TANK);
        event.register(REPLICATOR);
        event.register(SOUL_CRUCIBLE);
    }

    private TXDataMaps() {
    }
}
