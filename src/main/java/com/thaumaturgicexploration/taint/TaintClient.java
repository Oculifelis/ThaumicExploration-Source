package com.thaumaturgicexploration.taint;

import com.leclowndu93150.thaumaturge.client.entity.TaintacleRenderer;
import com.thaumaturgicexploration.ThaumaturgicExploration;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** The taintacle minion borrows Thaumaturge's taintacle renderer, at a small taintacle's size. */
@EventBusSubscriber(modid = ThaumaturgicExploration.MODID, value = Dist.CLIENT)
public final class TaintClient {
    @SubscribeEvent
    static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(TXTaint.MINION.get(), ctx -> new TaintacleRenderer(ctx, 0.5f));
    }

    private TaintClient() {
    }
}
