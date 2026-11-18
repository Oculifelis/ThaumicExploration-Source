package com.thaumicexploration;

import com.mojang.logging.LogUtils;
import com.thaumicexploration.boots.BootEvents;
import com.thaumicexploration.brazier.BrazierTickets;
import com.thaumicexploration.brazier.PendingWarp;
import com.thaumicexploration.wand.RodEvents;
import com.thaumicexploration.boots.TXBoots;
import com.thaumicexploration.taint.TXTaint;
import com.thaumicexploration.trinket.TXCurios;
import com.thaumicexploration.wand.TXCaps;
import com.thaumicexploration.wand.TXRods;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/** Thaumic Exploration (GTNH 1.5.29), ported from the Thaumcraft 4 addon to Thaumaturge (NeoForge 26.1.2). */
@Mod(ThaumicExploration.MODID)
public final class ThaumicExploration {
    public static final String MODID = "thaumicexploration";
    public static final Logger LOG = LogUtils.getLogger();

    public ThaumicExploration(IEventBus modBus) {
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
