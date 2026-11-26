package com.thaumaturgicexploration.brazier;

import com.thaumaturgicexploration.ThaumaturgicExploration;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;

/** A lit Soul Brazier keeps its own chunk loaded; tickets of braziers that are no longer lit are dropped on load. */
public final class BrazierTickets {
    static final TicketController CONTROLLER = new TicketController(ThaumaturgicExploration.id("soul_brazier"), (level, helper) ->
            List.copyOf(helper.getBlockTickets().keySet()).forEach(pos -> {
                if (!(level.getBlockEntity(pos) instanceof SoulBrazierBlockEntity be) || !be.active()) {
                    helper.removeAllTickets(pos);
                }
            }));

    public static void onRegister(RegisterTicketControllersEvent event) {
        event.register(CONTROLLER);
    }

    static void force(ServerLevel level, BlockPos pos, boolean on) {
        CONTROLLER.forceChunk(level, pos, pos.getX() >> 4, pos.getZ() >> 4, on, true);
    }

    private BrazierTickets() {
    }
}
