package com.thaumaturgicexploration;

import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities;
import com.mojang.serialization.Codec;
import com.thaumaturgicexploration.brain.ReplicatorBlock;
import com.thaumaturgicexploration.brain.ReplicatorBlockEntity;
import com.thaumaturgicexploration.brain.SoulCrucibleBlock;
import com.thaumaturgicexploration.brain.SoulCrucibleBlockEntity;
import com.thaumaturgicexploration.brain.ThinkTankBlock;
import com.thaumaturgicexploration.brain.ThinkTankBlockEntity;
import com.thaumaturgicexploration.brazier.SoulBrazierBlock;
import com.thaumaturgicexploration.brazier.SoulBrazierBlockEntity;
import com.thaumaturgicexploration.seal.BindingSealItem;
import com.thaumaturgicexploration.seal.BoundChestBlock;
import com.thaumaturgicexploration.seal.BoundChestBlockEntity;
import com.thaumaturgicexploration.seal.BoundJarBlock;
import com.thaumaturgicexploration.seal.BoundJarBlockEntity;
import com.thaumaturgicexploration.seal.LinkedChestSealItem;
import com.thaumaturgicexploration.seal.SealLink;
import com.thaumaturgicexploration.seal.SealNetworks;
import com.thaumaturgicexploration.seal.TrashJarBlock;
import com.thaumaturgicexploration.seal.TrashJarBlockEntity;
import com.thaumaturgicexploration.trinket.FloatingCandleBlock;
import com.thaumaturgicexploration.trinket.TalismanItem;
import com.thaumaturgicexploration.urn.EverburnUrnBlock;
import com.thaumaturgicexploration.urn.EverburnUrnBlockEntity;
import com.thaumaturgicexploration.wand.TXRods;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.VoidingResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;

public final class TXRegistry {
    private static final String MODID = ThaumaturgicExploration.MODID;

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID);

    private static final List<Supplier<? extends Item>> TAB_ITEMS = new ArrayList<>();

    public static final DeferredBlock<EverburnUrnBlock> EVERBURN_URN = block("everburn_urn", EverburnUrnBlock::new,
            p -> p.mapColor(MapColor.COLOR_RED).strength(2.0f).sound(SoundType.DECORATED_POT).noOcclusion().lightLevel(s -> 9));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EverburnUrnBlockEntity>> EVERBURN_URN_BE = BLOCK_ENTITIES.register("everburn_urn",
            () -> new BlockEntityType<>(EverburnUrnBlockEntity::new, EVERBURN_URN.get()));

    /** Nourishment points stored in a Talisman of Nourishment. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> NOURISHMENT = COMPONENTS.registerComponentType("nourishment",
            b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    // Phase 2: trinkets (Curios) and floating candles.
    public static final List<String> PRIMALS = List.of("aer", "terra", "ignis", "aqua", "ordo", "perditio");
    public static final Map<String, DeferredItem<Item>> DISCOUNT_RINGS = new java.util.LinkedHashMap<>();
    static {
        for (String primal : PRIMALS) {
            DISCOUNT_RINGS.put(primal, item("ring_" + primal, p -> new Item(p.stacksTo(1))));
        }
    }
    public static final DeferredItem<Item> STABILIZER_BELT = item("stabilizer_belt", p -> new Item(p.stacksTo(1)));
    public static final DeferredItem<TalismanItem> TALISMAN_FOOD = item("talisman_food", TalismanItem::new);
    public static final Map<DyeColor, DeferredBlock<FloatingCandleBlock>> FLOATING_CANDLES = new EnumMap<>(DyeColor.class);
    static {
        for (DyeColor color : DyeColor.values()) {
            FLOATING_CANDLES.put(color, block("floating_candle_" + color.getSerializedName(), p -> new FloatingCandleBlock(color, p),
                    p -> p.mapColor(color.getMapColor()).strength(0.1f).sound(SoundType.CANDLE).noOcclusion().noCollision().lightLevel(s -> 14)));
        }
    }

    // Phase 3: binding seals, bound chests and jars, Jar of Oblivion.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SealLink>> SEAL_LINK = COMPONENTS.registerComponentType("seal_link",
            b -> b.persistent(SealLink.CODEC).networkSynchronized(SealLink.STREAM_CODEC));
    public static final Map<DyeColor, DeferredItem<Item>> BLANK_SEALS = new EnumMap<>(DyeColor.class);
    public static final Map<DyeColor, DeferredItem<BindingSealItem>> CHEST_SEALS = new EnumMap<>(DyeColor.class);
    public static final Map<DyeColor, DeferredItem<BindingSealItem>> JAR_SEALS = new EnumMap<>(DyeColor.class);
    static {
        for (DyeColor color : DyeColor.values()) {
            BLANK_SEALS.put(color, item("blank_seal_" + color.getSerializedName(), Item::new));
        }
        for (DyeColor color : DyeColor.values()) {
            CHEST_SEALS.put(color, item("chest_seal_" + color.getSerializedName(), p -> new BindingSealItem(color, BindingSealItem.Kind.CHEST, p)));
        }
        for (DyeColor color : DyeColor.values()) {
            JAR_SEALS.put(color, item("jar_seal_" + color.getSerializedName(), p -> new BindingSealItem(color, BindingSealItem.Kind.JAR, p)));
        }
    }
    /** Only made by using a chest seal on a bound chest, so it stays out of the creative tab. */
    public static final DeferredItem<LinkedChestSealItem> LINKED_CHEST_SEAL = ITEMS.registerItem("linked_chest_seal", LinkedChestSealItem::new);
    public static final DeferredBlock<BoundChestBlock> BOUND_CHEST = BLOCKS.registerBlock("bound_chest", BoundChestBlock::new,
            p -> p.mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD).noOcclusion());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BoundChestBlockEntity>> BOUND_CHEST_BE = BLOCK_ENTITIES.register("bound_chest",
            () -> new BlockEntityType<>(BoundChestBlockEntity::new, BOUND_CHEST.get()));
    public static final DeferredBlock<BoundJarBlock> BOUND_JAR = BLOCKS.registerBlock("bound_jar", BoundJarBlock::new,
            p -> p.mapColor(MapColor.NONE).strength(0.3f).sound(SoundType.GLASS).noOcclusion());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BoundJarBlockEntity>> BOUND_JAR_BE = BLOCK_ENTITIES.register("bound_jar",
            () -> new BlockEntityType<>(BoundJarBlockEntity::new, BOUND_JAR.get()));
    public static final DeferredBlock<TrashJarBlock> TRASH_JAR = block("trash_jar", TrashJarBlock::new,
            p -> p.mapColor(MapColor.COLOR_BLACK).strength(0.3f).sound(SoundType.GLASS).noOcclusion());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TrashJarBlockEntity>> TRASH_JAR_BE = BLOCK_ENTITIES.register("trash_jar",
            () -> new BlockEntityType<>(TrashJarBlockEntity::new, TRASH_JAR.get()));
    private static final VoidingResourceHandler<ItemResource> VOID_ITEMS = new VoidingResourceHandler<>(ItemResource.EMPTY);
    private static final VoidingResourceHandler<FluidResource> VOID_FLUIDS = new VoidingResourceHandler<>(FluidResource.EMPTY);

    // Phase 4: purified brain, Think Tank, Thaumic Replicator, Crucible of Souls.
    public static final DeferredItem<Item> PURE_BRAIN = item("pure_brain", Item::new);
    public static final DeferredBlock<ThinkTankBlock> THINK_TANK = block("think_tank", ThinkTankBlock::new,
            p -> p.mapColor(MapColor.NONE).strength(0.3f).sound(SoundType.GLASS).noOcclusion().lightLevel(s -> s.getValue(ThinkTankBlock.LIT) ? 7 : 0));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ThinkTankBlockEntity>> THINK_TANK_BE = BLOCK_ENTITIES.register("think_tank",
            () -> new BlockEntityType<>(ThinkTankBlockEntity::new, THINK_TANK.get()));
    public static final DeferredBlock<ReplicatorBlock> REPLICATOR = block("replicator", ReplicatorBlock::new,
            p -> p.mapColor(MapColor.COLOR_PURPLE).strength(3.0f).sound(SoundType.STONE).requiresCorrectToolForDrops());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ReplicatorBlockEntity>> REPLICATOR_BE = BLOCK_ENTITIES.register("replicator",
            () -> new BlockEntityType<>(ReplicatorBlockEntity::new, REPLICATOR.get()));
    public static final DeferredBlock<SoulCrucibleBlock> SOUL_CRUCIBLE = block("soul_crucible", SoulCrucibleBlock::new,
            p -> p.mapColor(MapColor.COLOR_BLACK).strength(3.0f).sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SoulCrucibleBlockEntity>> SOUL_CRUCIBLE_BE = BLOCK_ENTITIES.register("soul_crucible",
            () -> new BlockEntityType<>(SoulCrucibleBlockEntity::new, SOUL_CRUCIBLE.get()));

    // Phase 7: Soul Brazier.
    public static final DeferredBlock<SoulBrazierBlock> SOUL_BRAZIER = block("soul_brazier", SoulBrazierBlock::new,
            p -> p.mapColor(MapColor.COLOR_BLACK).strength(3.0f).sound(SoundType.STONE).noOcclusion().requiresCorrectToolForDrops()
                    .lightLevel(s -> s.getValue(SoulBrazierBlock.LIT) ? 10 : 0));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SoulBrazierBlockEntity>> SOUL_BRAZIER_BE = BLOCK_ENTITIES.register("soul_brazier",
            () -> new BlockEntityType<>(SoulBrazierBlockEntity::new, SOUL_BRAZIER.get()));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.thaumaturgicexploration"))
            .icon(() -> new ItemStack(TXRods.AMBER_ITEM.get()))
            .displayItems((params, out) -> TAB_ITEMS.forEach(i -> out.accept(i.get())))
            .build());

    private TXRegistry() {
    }

    private static <B extends Block> DeferredBlock<B> block(String name, Function<Block.Properties, B> factory,
                                                           UnaryOperator<Block.Properties> props) {
        DeferredBlock<B> block = BLOCKS.registerBlock(name, factory, props);
        TAB_ITEMS.add(ITEMS.registerSimpleBlockItem(block));
        return block;
    }

    public static <I extends Item> DeferredItem<I> item(String name, Function<Item.Properties, I> factory) {
        DeferredItem<I> item = ITEMS.registerItem(name, factory);
        TAB_ITEMS.add(item);
        return item;
    }

    static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        TABS.register(bus);
        COMPONENTS.register(bus);
        bus.addListener(TXRegistry::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Like the original, the urn only gives its fluid up through the top.
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, EVERBURN_URN_BE.get(),
                (be, side) -> side == null || side == Direction.UP ? be.tank() : null);
        // Every bound chest of a network is the same 27 slots to hoppers and pipes.
        event.registerBlockEntity(Capabilities.Item.BLOCK, BOUND_CHEST_BE.get(), (be, side) -> be.getLevel() instanceof ServerLevel level
                ? VanillaContainerWrapper.of(SealNetworks.get(level.getServer()).chest(be.link().key())) : null);
        event.registerBlockEntity(EssentiaCapabilities.TRANSPORT, BOUND_JAR_BE.get(), (be, side) -> be);
        event.registerBlockEntity(EssentiaCapabilities.STORAGE, BOUND_JAR_BE.get(), (be, side) -> be.storage());
        event.registerBlockEntity(EssentiaCapabilities.ASPECT_QUERY, BOUND_JAR_BE.get(), (be, side) -> be::shownContents);
        event.registerBlockEntity(EssentiaCapabilities.TRANSPORT, TRASH_JAR_BE.get(), (be, side) -> be);
        event.registerBlockEntity(EssentiaCapabilities.STORAGE, TRASH_JAR_BE.get(), (be, side) -> TrashJarBlockEntity.VOID_STORAGE);
        event.registerBlockEntity(Capabilities.Item.BLOCK, TRASH_JAR_BE.get(), (be, side) -> VOID_ITEMS);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, TRASH_JAR_BE.get(), (be, side) -> VOID_FLUIDS);
        event.registerBlockEntity(EssentiaCapabilities.TRANSPORT, SOUL_BRAZIER_BE.get(), (be, side) -> be);
        event.registerBlockEntity(Capabilities.Item.BLOCK, THINK_TANK_BE.get(), (be, side) -> be.slots());
        event.registerBlockEntity(Capabilities.Item.BLOCK, REPLICATOR_BE.get(), (be, side) -> be.output());
        event.registerBlockEntity(EssentiaCapabilities.TRANSPORT, SOUL_CRUCIBLE_BE.get(), (be, side) -> be);
        event.registerBlockEntity(EssentiaCapabilities.ASPECT_QUERY, SOUL_CRUCIBLE_BE.get(), (be, side) -> be::contents);
    }
}
