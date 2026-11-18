package com.thaumicexploration.brazier;

import com.leclowndu93150.thaumaturge.api.warp.WarpHelper;
import com.leclowndu93150.thaumaturge.api.warp.WarpType;
import com.mojang.serialization.Codec;
import com.thaumicexploration.ThaumicExploration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Warp a Soul Brazier gave back while its owner was offline, handed over when they next log in. */
public final class PendingWarp extends SavedData {
    private static final Codec<PendingWarp> CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT)
            .xmap(PendingWarp::new, p -> p.owed);
    private static final SavedDataType<PendingWarp> TYPE =
            new SavedDataType<>(ThaumicExploration.id("pending_warp"), PendingWarp::new, CODEC);

    private final Map<UUID, Integer> owed;

    private PendingWarp() {
        this(Map.of());
    }

    private PendingWarp(Map<UUID, Integer> owed) {
        this.owed = new HashMap<>(owed);
    }

    static PendingWarp get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    void add(UUID player, int warp) {
        owed.merge(player, warp, Integer::sum);
        setDirty();
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PendingWarp pending = get(player.level().getServer());
            Integer warp = pending.owed.remove(player.getUUID());
            if (warp != null) {
                pending.setDirty();
                WarpHelper.addWarp(player, warp, WarpType.PERMANENT);
                player.sendSystemMessage(Component.translatable("block.thaumicexploration.soul_brazier.returned", warp));
            }
        }
    }
}
