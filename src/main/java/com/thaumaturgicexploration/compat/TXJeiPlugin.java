package com.thaumaturgicexploration.compat;

import com.leclowndu93150.thaumaturge.api.aspect.AspectIndexAccess;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCrystalAccess;
import com.thaumaturgicexploration.TXDataMaps;
import com.thaumaturgicexploration.TXRegistry;
import com.thaumaturgicexploration.ThaumaturgicExploration;
import com.thaumaturgicexploration.brain.ReplicatorBlockEntity;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * JEI: a Think Tank category from the synced {@code think_tank} data map (the book in, what it is read into, the
 * chance and time), a Thaumic Replicator category for every replicable block that has aspects (the template, its
 * copy and the essentia it costs), and information pages for the machines and the linked chest seal (the original had
 * NEI handlers for the Replicator and the Crucible of Souls). Loaded only with JEI.
 */
@JeiPlugin
public final class TXJeiPlugin implements IModPlugin {
    record Reading(Item book, TXDataMaps.ThinkTankInput spec) {
    }

    record Copy(ItemStack template) {
    }

    static final IRecipeType<Reading> THINK_TANK = IRecipeType.create(ThaumaturgicExploration.id("think_tank"), Reading.class);
    static final IRecipeType<Copy> REPLICATOR = IRecipeType.create(ThaumaturgicExploration.id("replicator"), Copy.class);

    @Override
    public Identifier getPluginUid() {
        return ThaumaturgicExploration.id("jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new ThinkTankCategory(gui), new ReplicatorCategory(gui));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(THINK_TANK, TXRegistry.THINK_TANK.get());
        registration.addCraftingStation(REPLICATOR, TXRegistry.REPLICATOR.get());
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<Reading> readings = new ArrayList<>();
        BuiltInRegistries.ITEM.getDataMap(TXDataMaps.THINK_TANK).forEach((key, spec) ->
                BuiltInRegistries.ITEM.getOptional(key).ifPresent(book -> readings.add(new Reading(book, spec))));
        registration.addRecipes(THINK_TANK, readings);

        List<Copy> copies = new ArrayList<>();
        BuiltInRegistries.ITEM.getTagOrEmpty(ReplicatorBlockEntity.REPLICABLE).forEach(h -> {
            ItemStack stack = new ItemStack(h);
            if (!AspectIndexAccess.of(stack).isEmpty()) {
                copies.add(new Copy(stack));
            }
        });
        registration.addRecipes(REPLICATOR, copies);

        info(registration, TXRegistry.REPLICATOR.get(), "replicator");
        info(registration, TXRegistry.SOUL_CRUCIBLE.get(), "soul_crucible");
        info(registration, TXRegistry.THINK_TANK.get(), "think_tank");
        info(registration, TXRegistry.SOUL_BRAZIER.get(), "soul_brazier");
        info(registration, TXRegistry.TRASH_JAR.get(), "trash_jar");
        info(registration, TXRegistry.LINKED_CHEST_SEAL.get(), "linked_chest_seal");
    }

    private static void info(IRecipeRegistration registration, ItemLike item, String name) {
        registration.addIngredientInfo(item, Component.translatable("jei.thaumaturgicexploration." + name));
    }

    private static final class ThinkTankCategory extends AbstractRecipeCategory<Reading> {
        ThinkTankCategory(IGuiHelper gui) {
            super(THINK_TANK, Component.translatable("jei.thaumaturgicexploration.think_tank.category"),
                    gui.createDrawableItemLike(TXRegistry.THINK_TANK.get()), 120, 54);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, Reading recipe, IFocusGroup focuses) {
            builder.addInputSlot(4, 4).setStandardSlotBackground().add(new ItemStack(recipe.book()));
            int count = recipe.spec().outcomes().stream().mapToInt(TXDataMaps.ThinkTankInput.Outcome::count).max().orElse(1);
            builder.addOutputSlot(60, 4).setOutputSlotBackground().add(new ItemStack(recipe.spec().result(), count));
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, Reading recipe, IFocusGroup focuses) {
            builder.addAnimatedRecipeArrowWidget(recipe.spec().time()).setPosition(28, 4);
            int y = 24;
            for (TXDataMaps.ThinkTankInput.Outcome o : recipe.spec().outcomes()) {
                builder.addText(Component.translatable("jei.thaumaturgicexploration.think_tank.chance",
                        Math.round(o.chance() * 100), recipe.spec().time() / 20f).append(" x" + o.count()), 120, 10)
                        .setPosition(0, y).setColor(0xFF707070);
                y += 10;
            }
        }
    }

    private static final class ReplicatorCategory extends AbstractRecipeCategory<Copy> {
        ReplicatorCategory(IGuiHelper gui) {
            super(REPLICATOR, Component.translatable("jei.thaumaturgicexploration.replicator.category"),
                    gui.createDrawableItemLike(TXRegistry.REPLICATOR.get()), 140, 44);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, Copy recipe, IFocusGroup focuses) {
            builder.addInputSlot(4, 4).setStandardSlotBackground().add(recipe.template());
            builder.addOutputSlot(118, 4).setOutputSlotBackground().add(recipe.template());
            int x = 4;
            for (AspectInstance a : AspectIndexAccess.of(recipe.template()).entries()) {
                if (x > 120) {
                    break;
                }
                builder.addInputSlot(x, 24).setStandardSlotBackground().add(EssentiaCrystalAccess.create(a.aspect(), a.amount()));
                x += 18;
            }
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, Copy recipe, IFocusGroup focuses) {
            builder.addRecipeArrowWidget().setPosition(60, 4);
        }
    }
}
