package com.thaumicexploration;

import com.thaumicexploration.brain.ReplicatorBlockEntity;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Save/load round trips of the block entities' state machines, run once the dev server has started when the JVM has
 * {@code -Dthaumicexploration.selftest=true} (only the Gradle server run sets it). Each check loads a saved form into a fresh
 * block entity, saves it, loads that into another fresh one and saves again: both saves must hold the expected values,
 * and keys that belong to another state must be gone. Logs one {@code SELFTEST PASS} or {@code SELFTEST FAIL} line each.
 */
final class SelfTest {
    private SelfTest() {
    }

    static void register() {
        if (Boolean.getBoolean("thaumicexploration.selftest")) {
            NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> run(e.getServer().registryAccess()));
        }
    }

    private static void run(HolderLookup.Provider reg) {
        try {
            Supplier<BlockEntity> rep = () -> new ReplicatorBlockEntity(BlockPos.ZERO, TXRegistry.REPLICATOR.get().defaultBlockState());
            check(reg, "replicator crafting keeps ticks", rep, tag("state", "crafting", "ticks", 37, "made", 3),
                    tag("state", "crafting", "ticks", 37, "made", 3));
            check(reg, "replicator idle drops ticks + required", rep, tag("state", "idle", "ticks", 37, "made", 3),
                    tag("state", "idle", "made", 3), "ticks", "required");
            check(reg, "replicator default idle", rep, tag(), tag("state", "idle"), "ticks", "required");
        } catch (Exception ex) {
            ThaumicExploration.LOG.error("SELFTEST FAIL crashed", ex);
        }
    }

    /** Loads {@code in}, saves, loads that save into a fresh one and saves again; both saves must match. */
    private static void check(HolderLookup.Provider reg, String name, Supplier<BlockEntity> make, CompoundTag in, CompoundTag expect,
            String... absent) {
        BlockEntity a = make.get();
        a.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, reg, in));
        CompoundTag first = a.saveCustomOnly(reg);
        BlockEntity b = make.get();
        b.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, reg, first));
        CompoundTag second = b.saveCustomOnly(reg);
        boolean ok = first.equals(second);
        for (String k : expect.keySet()) {
            ok &= expect.get(k).equals(first.get(k));
        }
        for (String k : absent) {
            ok &= !first.contains(k);
        }
        ThaumicExploration.LOG.info("SELFTEST {} {}: {}", ok ? "PASS" : "FAIL", name, second);
    }

    private static CompoundTag tag(Object... kv) {
        CompoundTag t = new CompoundTag();
        for (int i = 0; i < kv.length; i += 2) {
            String k = (String) kv[i];
            switch (kv[i + 1]) {
                case String s -> t.putString(k, s);
                case Integer n -> t.putInt(k, n);
                case Boolean z -> t.putBoolean(k, z);
                default -> throw new IllegalArgumentException(k);
            }
        }
        return t;
    }
}
