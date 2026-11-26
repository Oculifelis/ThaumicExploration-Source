package com.thaumaturgicexploration.boots;

import com.thaumaturgicexploration.TXRegistry;
import com.thaumaturgicexploration.ThaumaturgicExploration;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Boots of the Meteor and of the Comet: infused traveller's boots, the same armour values as the original. */
public final class TXBoots {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ThaumaturgicExploration.MODID);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<BootState>> STATE = ATTACHMENTS.register("boot_state",
            () -> AttachmentType.builder(BootState::new).build());

    private static final TagKey<Item> REPAIR = TagKey.create(Registries.ITEM, ThaumaturgicExploration.id("repairs_infused_boots"));

    private static ArmorMaterial material(String asset) {
        ResourceKey<EquipmentAsset> id = ResourceKey.create(EquipmentAssets.ROOT_ID, ThaumaturgicExploration.id(asset));
        return new ArmorMaterial(25, Map.of(ArmorType.BOOTS, 3), 25, SoundEvents.ARMOR_EQUIP_LEATHER, 0, 0, REPAIR, id);
    }

    public static final DeferredItem<Item> METEOR = TXRegistry.item("meteor_boots",
            p -> new Item(p.humanoidArmor(material("meteor"), ArmorType.BOOTS).rarity(Rarity.RARE)));
    public static final DeferredItem<Item> COMET = TXRegistry.item("comet_boots",
            p -> new Item(p.humanoidArmor(material("comet"), ArmorType.BOOTS).rarity(Rarity.RARE)));

    public static void register(IEventBus bus) {
        ATTACHMENTS.register(bus);
    }

    private TXBoots() {
    }
}
