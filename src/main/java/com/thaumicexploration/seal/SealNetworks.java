package com.thaumicexploration.seal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thaumicexploration.ThaumicExploration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.NonNullList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Server-wide contents of every bound chest and bound jar network, saved with the world. A chest network is keyed
 * "colour/number" (a new number each time a chest is sealed); a jar network is keyed "colour/name", the name being
 * the jar seal's custom name or empty.
 */
public final class SealNetworks extends SavedData {
    public static final int CHEST_SLOTS = 27;

    private static final Codec<SimpleContainer> CONTAINER_CODEC = ItemStack.OPTIONAL_CODEC.listOf().xmap(
            items -> {
                SimpleContainer container = new SimpleContainer(CHEST_SLOTS);
                for (int i = 0; i < Math.min(items.size(), CHEST_SLOTS); i++) {
                    container.setItem(i, items.get(i));
                }
                return container;
            },
            container -> List.copyOf(container.getItems()));

    private static final Codec<SealNetworks> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, CONTAINER_CODEC).optionalFieldOf("chests", Map.of()).forGetter(d -> d.chests),
            Codec.unboundedMap(Codec.STRING, JarNetwork.CODEC).optionalFieldOf("jars", Map.of()).forGetter(d -> d.jars),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("next_ids", Map.of()).forGetter(d -> d.nextIds)
    ).apply(i, SealNetworks::new));

    public static final SavedDataType<SealNetworks> TYPE = new SavedDataType<>(ThaumicExploration.id("seal_networks"),
            SealNetworks::new, CODEC);

    private final Map<String, SimpleContainer> chests = new HashMap<>();
    private final Map<String, JarNetwork> jars = new HashMap<>();
    private final Map<String, Integer> nextIds = new HashMap<>();

    private SealNetworks() {
    }

    private SealNetworks(Map<String, SimpleContainer> chests, Map<String, JarNetwork> jars, Map<String, Integer> nextIds) {
        chests.forEach((k, v) -> this.chests.put(k, watch(v)));
        jars.forEach((k, v) -> this.jars.put(k, v.attach(this)));
        this.nextIds.putAll(nextIds);
    }

    public static SealNetworks get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public static String chestKey(DyeColor color, int id) {
        return color.getSerializedName() + "/" + id;
    }

    public static String jarKey(DyeColor color, String name) {
        return color.getSerializedName() + "/" + name;
    }

    /** A fresh chest network number for this colour. */
    public int newChestId(DyeColor color) {
        int id = nextIds.getOrDefault(color.getSerializedName(), 0) + 1;
        nextIds.put(color.getSerializedName(), id);
        setDirty();
        return id;
    }

    public SimpleContainer chest(String key) {
        return chests.computeIfAbsent(key, k -> {
            setDirty();
            return watch(new SimpleContainer(CHEST_SLOTS));
        });
    }

    public JarNetwork jar(String key) {
        return jars.computeIfAbsent(key, k -> {
            setDirty();
            return new JarNetwork(Optional.empty()).attach(this);
        });
    }

    /** A copy of the container that marks this saved data dirty whenever its contents change. */
    private SimpleContainer watch(SimpleContainer container) {
        SimpleContainer watched = new SimpleContainer(container.getContainerSize()) {
            @Override
            public void setChanged() {
                super.setChanged();
                SealNetworks.this.setDirty();
            }
        };
        for (int i = 0; i < container.getContainerSize(); i++) {
            watched.setItem(i, container.getItem(i));
        }
        return watched;
    }

    /** Copies a list of stacks into a network's container, returning whatever did not fit. */
    public static NonNullList<ItemStack> fill(SimpleContainer container, List<ItemStack> stacks) {
        NonNullList<ItemStack> leftover = NonNullList.create();
        for (ItemStack stack : stacks) {
            ItemStack rest = container.addItem(stack);
            if (!rest.isEmpty()) {
                leftover.add(rest);
            }
        }
        return leftover;
    }
}
