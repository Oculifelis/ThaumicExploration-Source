package com.thaumaturgicexploration;

import com.mojang.logging.LogUtils;
import com.thaumaturgicexploration.boots.BootEvents;
import com.thaumaturgicexploration.brazier.BrazierTickets;
import com.thaumaturgicexploration.brazier.PendingWarp;
import com.thaumaturgicexploration.wand.RodEvents;
import com.thaumaturgicexploration.boots.TXBoots;
import com.thaumaturgicexploration.taint.TXTaint;
import com.thaumaturgicexploration.trinket.TXCurios;
import com.thaumaturgicexploration.wand.TXCaps;
import com.thaumaturgicexploration.wand.TXRods;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/** Thaumic Exploration (GTNH 1.5.29), ported from the Thaumcraft 4 addon to Thaumaturge (NeoForge 26.1.2). */
@Mod(ThaumaturgicExploration.MODID)
public final class ThaumaturgicExploration {
    public static final String MODID = "thaumaturgicexploration";
    public static final Logger LOG = LogUtils.getLogger();

    public ThaumaturgicExploration(IEventBus modBus) {
        TXRegistry.register(modBus);
        SelfTest.register();
        TXRods.register(modBus);
        TXCaps.register(modBus);
        TXBoots.register(modBus);
        TXTaint.register(modBus);
        RodEvents.register();
        modBus.addListener(BrazierTickets::onRegister);
        modBus.addListener(TXDataMaps::register);
        NeoForge.EVENT_BUS.addListener(TXCurios::onCurioAttributes);
        NeoForge.EVENT_BUS.addListener(BootEvents::onAttributes);
        NeoForge.EVENT_BUS.addListener(BootEvents::onJump);
        NeoForge.EVENT_BUS.addListener(BootEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(TXCaps::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(TXTaint::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(TXTaint::onEnderTeleport);
        NeoForge.EVENT_BUS.addListener(TXTaint::onEntityTick);
        NeoForge.EVENT_BUS.addListener(RodEvents::onDeath);
        NeoForge.EVENT_BUS.addListener(RodEvents::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(PendingWarp::onLogin);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
