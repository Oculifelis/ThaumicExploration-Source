package com.thaumaturgicexploration.trinket;

import com.thaumaturgicexploration.TXDataMaps;
import com.thaumaturgicexploration.TXDataMaps.CurioAttribute;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.event.CurioAttributeModifierEvent;

/**
 * Worn trinkets get their stats from the curio_attributes data map: primal rings add Thaumaturge's vis discount,
 * the Thaumostatic Stabilizer adds knockback resistance. Which slot an item fits is the curios:* item tags.
 */
public final class TXCurios {
    public static void onCurioAttributes(CurioAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        List<CurioAttribute> attributes = stack.getItem().builtInRegistryHolder().getData(TXDataMaps.CURIO_ATTRIBUTES);
        if (attributes == null) {
            return;
        }
        for (CurioAttribute attribute : attributes) {
            event.addModifier(attribute.attribute(), attribute.modifier());
        }
    }

    private TXCurios() {
    }
}
