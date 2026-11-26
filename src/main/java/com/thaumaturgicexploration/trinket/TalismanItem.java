package com.thaumaturgicexploration.trinket;

import com.thaumaturgicexploration.TXDataMaps;
import com.thaumaturgicexploration.TXDataMaps.Talisman;
import com.thaumaturgicexploration.TXRegistry;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.TooltipDisplay;
import com.thaumaturgicexploration.ThaumaturgicExploration;
import org.jspecify.annotations.Nullable;

/**
 * Talisman of Nourishment: carried anywhere in the inventory, it eats harmless food from the hotbar into a store of
 * nourishment and feeds it back whenever you are hungry. Points live in the nourishment data component; capacity
 * and pace in the talisman data map; foods it must never eat in the talisman_blacklist item tag.
 */
public class TalismanItem extends Item {
    public static final TagKey<Item> BLACKLIST = TagKey.create(Registries.ITEM, ThaumaturgicExploration.id("talisman_blacklist"));

    public TalismanItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    private @Nullable Talisman settings(ItemStack stack) {
        return stack.getItem().builtInRegistryHolder().getData(TXDataMaps.TALISMAN);
    }

    public static int points(ItemStack stack) {
        return stack.getOrDefault(TXRegistry.NOURISHMENT.get(), 0);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        Talisman settings = settings(stack);
        if (!(owner instanceof Player player) || settings == null || player.tickCount % settings.interval() != 0) {
            return;
        }
        int points = absorb(stack, player, level, points(stack), settings.capacity());
        points = feed(player, points);
        stack.set(TXRegistry.NOURISHMENT.get(), points);
    }

    /** Eats one food from the hotbar per tick of the talisman, if there is room for it. */
    private int absorb(ItemStack talisman, Player player, ServerLevel level, int points, int capacity) {
        if (points >= capacity) {
            return points;
        }
        for (int i = 0; i < 9; i++) {
            ItemStack food = player.getInventory().getItem(i);
            FoodProperties props = food.get(DataComponents.FOOD);
            if (props == null || food == talisman || food.is(BLACKLIST) || props.nutrition() <= 0 || harmful(food)) {
                continue;
            }
            int gained = Math.round((props.nutrition() + props.saturation()) / 2f);
            food.shrink(1);
            level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS,
                    0.5f + 0.5f * level.getRandom().nextInt(2), (level.getRandom().nextFloat() - level.getRandom().nextFloat()) * 0.2f + 1f);
            return Math.min(capacity, points + Math.max(1, gained));
        }
        return points;
    }

    /** Foods with any on-eat effect (poison, teleporting, clearing effects) are skipped, like the original's potion test. */
    private static boolean harmful(ItemStack food) {
        Consumable consumable = food.get(DataComponents.CONSUMABLE);
        return consumable != null && !consumable.onConsumeEffects().isEmpty();
    }

    /** Tops up hunger first, then saturation up to the hunger level, one point of nourishment per point restored. */
    private static int feed(Player player, int points) {
        FoodData food = player.getFoodData();
        int hunger = Math.min(points, 20 - food.getFoodLevel());
        if (hunger > 0) {
            food.setFoodLevel(food.getFoodLevel() + hunger);
            points -= hunger;
        }
        float saturation = Math.min(points, food.getFoodLevel() - food.getSaturationLevel());
        if (saturation > 0) {
            food.setSaturation(food.getSaturationLevel() + saturation);
            points -= Math.round(saturation);
        }
        return Math.max(points, 0);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return points(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        Talisman settings = settings(stack);
        return settings == null ? 0 : Math.round(13f * points(stack) / settings.capacity());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xC8A040;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.thaumaturgicexploration.talisman_food.points", points(stack)));
    }
}
