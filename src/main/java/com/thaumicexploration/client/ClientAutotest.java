package com.thaumicexploration.client;

import com.mojang.logging.LogUtils;
import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import com.thaumicexploration.ThaumicExploration;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Development-only automated check of the wand caps and the everburn urn, inert unless the JVM runs with
 * -Dthaumicexploration.autotest (only the Gradle dev run passes it with -Pautotest, see build.gradle and
 * tools/run_client_test.sh). It makes a fresh creative superflat world "txtest", holds a greatwood wand with each cap
 * (Mechanist in the main hand, Sojourner in the off hand), places an everburn urn with the four cap items on item
 * displays above it, bound chests and jars with their seals and a think tank on a bookshelf, takes a first- and a third-person screenshot, logs "TXTEST" lines and stops the client.
 */
@EventBusSubscriber(modid = ThaumicExploration.MODID, value = Dist.CLIENT)
public final class ClientAutotest {
    private static final String MODE = System.getProperty("thaumicexploration.autotest");
    private static final Logger LOG = LogUtils.getLogger();
    private static final String WORLD = "txtest";
    private static final long TIMEOUT_NANOS = 4L * 60L * 1_000_000_000L;

    private static boolean titleSeen;
    private static boolean creating;
    private static long titleAt;
    private static int idleTicks;
    private static int worldTicks;
    private static boolean finished;

    private ClientAutotest() {
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        init();
    }

    public static void init() {
        if (MODE == null) {
            return;
        }
        LOG.info("TXTEST armed ({})", MODE);
        NeoForge.EVENT_BUS.addListener(ClientAutotest::tick);
    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || finished) {
            return;
        }
        if (titleSeen && System.nanoTime() - titleAt > TIMEOUT_NANOS) {
            LOG.info("TXTEST timeout");
            finish(mc);
            return;
        }
        try {
            step(mc);
        } catch (Throwable t) {
            LOG.error("TXTEST step failed", t);
        }
    }

    private static void step(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            if (!creating && mc.screen != null && !(mc.screen instanceof TitleScreen) && mc.getOverlay() == null && ++idleTicks > 200) {
                LOG.info("TXTEST dismissing {}", mc.screen.getClass().getName());
                mc.setScreen(new TitleScreen());
                idleTicks = 0;
            }
            if (!creating && mc.screen instanceof TitleScreen title && mc.getOverlay() == null) {
                creating = true;
                titleSeen = true;
                titleAt = System.nanoTime();
                LOG.info("TXTEST title");
                mc.options.pauseOnLostFocus = false;
                mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0);
                mc.options.framerateLimit().set(30);
                mc.options.renderDistance().set(4);
                mc.options.simulationDistance().set(5);
                deleteOldWorld(mc);
                LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, LevelSettings.DifficultySettings.DEFAULT, true, WorldDataConfiguration.DEFAULT);
                LOG.info("TXTEST creating world {}", WORLD);
                mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(20261101L, false, false), WorldPresets::createFlatWorldDimensions, title);
            }
            return;
        }
        if (mc.screen instanceof PauseScreen) {
            mc.setScreen(null);
        }
        worldTicks++;
        if (worldTicks == 1) {
            LOG.info("TXTEST joined");
        } else if (worldTicks == 60) {
            build(mc);
        } else if (worldTicks == 300) {
            shot(mc, "first_person");
        } else if (worldTicks == 320) {
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
        } else if (worldTicks == 380) {
            shot(mc, "third_person");
        } else if (worldTicks == 420) {
            LOG.info("TXTEST done");
            finish(mc);
        }
    }

    private static void deleteOldWorld(Minecraft mc) {
        LevelStorageSource source = mc.getLevelSource();
        if (!source.levelExists(WORLD)) {
            return;
        }
        try (LevelStorageSource.LevelStorageAccess access = source.createAccess(WORLD)) {
            access.deleteLevel();
            LOG.info("TXTEST deleted old world {}", WORLD);
        } catch (Exception e) {
            LOG.warn("TXTEST could not delete old world {}", WORLD, e);
        }
    }

    /**
     * Superflat: grass at y=-61, the player stands at y=-60 at (0.5, 0.5) facing south. An everburn urn three blocks
     * ahead, the four cap items (active and inert) on item displays above it, a capped wand in each hand.
     */
    private static void build(Minecraft mc) {
        mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
        String[] commands = {
            "time set noon",
            "weather clear",
            "gamerule advance_time false",
            "gamerule advance_weather false",
            "gamerule spawn_mobs false",
            "tp @a 0.5 -60 0.5 0 15",
            "setblock 0 -60 3 thaumicexploration:everburn_urn",
            "setblock -2 -60 3 thaumicexploration:bound_chest[color=red,facing=north]",
            "setblock -3 -60 3 thaumicexploration:bound_chest[color=lime,facing=north]",
            "setblock 2 -60 3 thaumicexploration:bound_jar[color=blue]",
            "setblock 3 -60 3 thaumicexploration:bound_jar[color=yellow]",
            "setblock 1 -60 4 minecraft:bookshelf",
            "setblock 1 -59 4 thaumicexploration:think_tank",
            "item replace entity @a weapon.mainhand with thaumaturge:wand[thaumaturge:wand_parts={cap:\"thaumicexploration:mechanist\",rod:\"thaumaturge:greatwood\"}]",
            "item replace entity @a weapon.offhand with thaumaturge:wand[thaumaturge:wand_parts={cap:\"thaumicexploration:sojourner\",rod:\"thaumaturge:greatwood\"}]",
            "summon minecraft:item_display -0.19999999999999996 -58.8 3.5 {item:{id:\"thaumicexploration:wand_cap_mechanist\",count:1},billboard:\"vertical\",transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.6f,0.6f,0.6f]}}",
            "summon minecraft:item_display 0.4 -58.8 3.5 {item:{id:\"thaumicexploration:wand_cap_mechanist_inert\",count:1},billboard:\"vertical\",transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.6f,0.6f,0.6f]}}",
            "summon minecraft:item_display 1.0 -58.8 3.5 {item:{id:\"thaumicexploration:wand_cap_sojourner\",count:1},billboard:\"vertical\",transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.6f,0.6f,0.6f]}}",
            "summon minecraft:item_display 1.6 -58.8 3.5 {item:{id:\"thaumicexploration:wand_cap_sojourner_inert\",count:1},billboard:\"vertical\",transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.6f,0.6f,0.6f]}}",
        };
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) {
            LOG.error("TXTEST no integrated server");
            return;
        }
        server.execute(() -> {
            for (String c : commands) {
                LOG.info("TXTEST /{}", c);
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), c);
            }
            LOG.info("TXTEST blocks placed");
        });
    }

    private static void shot(Minecraft mc, String what) {
        String name = "txtest_" + what + "_" + (System.currentTimeMillis() / 1000) + ".png";
        File file = new File(new File(mc.gameDirectory, "screenshots"), name);
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), 1, msg -> LOG.info("TXTEST screenshot message: {}", msg.getString()));
        LOG.info("TXTEST shot {}", file.getAbsolutePath());
    }

    private static void finish(Minecraft mc) {
        finished = true;
        LOG.info("TXTEST stopping client");
        mc.stop();
    }
}
