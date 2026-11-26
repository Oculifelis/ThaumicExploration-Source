package com.thaumaturgicexploration.wand;

import com.leclowndu93150.thaumaturge.api.wands.WandRod;
import com.leclowndu93150.thaumaturge.content.wands.ItemWandRod;
import com.thaumaturgicexploration.TXRegistry;
import com.thaumaturgicexploration.ThaumaturgicExploration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Thaumic Exploration's wand and staff cores. Capacity and craft cost are the original's; what each core does while
 * held comes from the vis_trickle / vis_balance data maps ({@link RodBehaviour}). Thaumaturge generates the arcane
 * workbench assembly recipes for every {@link ItemWandRod}, gated by each rod's assembly research.
 */
public final class TXRods {
    private static final DeferredRegister<WandRod> RODS = DeferredRegister.create(WandRod.REGISTRY_KEY, ThaumaturgicExploration.MODID);

    public static final DeferredHolder<WandRod, WandRod> AMBER = rod("amber", 10, 8, false, "rod_amber");
    public static final DeferredHolder<WandRod, WandRod> AMBER_STAFF = rod("amber_staff", 25, 18, true, "rod_amber_staff");
    public static final DeferredHolder<WandRod, WandRod> TRANSMUTATIVE = rod("transmutative", 75, 6, false, "rod_transmutative");
    public static final DeferredHolder<WandRod, WandRod> TRANSMUTATIVE_STAFF = rod("transmutative_staff", 175, 14, true, "rod_transmutative_staff");

    public static final DeferredHolder<WandRod, WandRod> BREAD = rod("bread", 39, 8, false, "rod_bread");

    public static final DeferredHolder<WandRod, WandRod> NECROMANCER_STAFF = rod("necromancer_staff", 200, 15, true, "rod_necromancer_staff");

    public static final DeferredItem<ItemWandRod> AMBER_ITEM = item("wand_rod_amber", AMBER);
    public static final DeferredItem<ItemWandRod> AMBER_STAFF_ITEM = item("staff_rod_amber", AMBER_STAFF);
    public static final DeferredItem<ItemWandRod> TRANSMUTATIVE_ITEM = item("wand_rod_transmutative", TRANSMUTATIVE);
    public static final DeferredItem<ItemWandRod> TRANSMUTATIVE_STAFF_ITEM = item("staff_rod_transmutative", TRANSMUTATIVE_STAFF);

    public static final DeferredItem<ItemWandRod> BREAD_ITEM = item("wand_rod_bread", BREAD);

    public static final DeferredItem<ItemWandRod> NECROMANCER_STAFF_ITEM = item("staff_rod_necromancer", NECROMANCER_STAFF);

    private static DeferredHolder<WandRod, WandRod> rod(String name, int capacity, int craftCost, boolean staff, String research) {
        String texture = "textures/models/" + (staff ? "staff_rod_" + name.replace("_staff", "") : "wand_rod_" + name) + ".png";
        return RODS.register(name, () -> new WandRod(capacity, craftCost, ThaumaturgicExploration.id(texture), RodBehaviour::update,
                false, staff, false, ThaumaturgicExploration.id(research)));
    }

    private static DeferredItem<ItemWandRod> item(String name, DeferredHolder<WandRod, WandRod> rod) {
        return TXRegistry.item(name, p -> new ItemWandRod(p, rod));
    }

    public static void register(IEventBus bus) {
        RODS.register(bus);
    }

    private TXRods() {
    }
}
