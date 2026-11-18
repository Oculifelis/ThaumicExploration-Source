package com.thaumicexploration.brazier;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aura.AuraHelper;
import com.leclowndu93150.thaumaturge.api.aura.VisRelayHelper;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import com.leclowndu93150.thaumaturge.api.taint.TaintApi;
import com.leclowndu93150.thaumaturge.api.warp.IPlayerWarp;
import com.leclowndu93150.thaumaturge.api.warp.WarpHelper;
import com.leclowndu93150.thaumaturge.api.warp.WarpType;
import com.thaumicexploration.TXDataMaps;
import com.thaumicexploration.TXDataMaps.Brazier;
import com.thaumicexploration.TXRegistry;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * Soul Brazier: holds its owner's permanent warp while it burns. It burns on vis drawn from a relay (or the aura) and
 * essentia piped in from below; while lit it keeps its chunk loaded and the land around it slowly sickens with taint.
 * When it goes out or is broken the warp goes back to its owner, at once if online or at their next login.
 * All numbers are the soul_brazier block data map.
 */
public final class SoulBrazierBlockEntity extends BlockEntity implements IEssentiaTransport {
    private static final int SUCTION = 128;

    private @Nullable UUID owner;
    private int storedWarp;
    private int vis;
    private int essentia;
    private boolean active;
    private int tick;

    public SoulBrazierBlockEntity(BlockPos pos, BlockState state) {
        super(TXRegistry.SOUL_BRAZIER_BE.get(), pos, state);
    }

    private @Nullable Brazier settings() {
        return getBlockState().getBlock().builtInRegistryHolder().getData(TXDataMaps.SOUL_BRAZIER);
    }

    void setOwner(UUID owner) {
        this.owner = owner;
        setChanged();
    }

    public boolean active() {
        return active;
    }

    public int storedWarp() {
        return storedWarp;
    }

    /** The owner hands over their permanent warp. */
    void light(ServerPlayer player) {
        Brazier b = settings();
        if (b == null) {
            return;
        }
        if (owner != null && !owner.equals(player.getUUID())) {
            player.sendOverlayMessage(Component.translatable("block.thaumicexploration.soul_brazier.not_owner"));
        } else if (!powered(b)) {
            player.sendOverlayMessage(Component.translatable("block.thaumicexploration.soul_brazier.no_power"));
        } else {
            IPlayerWarp warp = WarpHelper.getWarp(player);
            int permanent = warp == null ? 0 : warp.get(WarpType.PERMANENT);
            if (permanent <= 0) {
                player.sendOverlayMessage(Component.translatable("block.thaumicexploration.soul_brazier.no_warp"));
                return;
            }
            owner = player.getUUID();
            warp.reduce(WarpType.PERMANENT, permanent);
            storedWarp += permanent;
            setActive(true);
            player.sendOverlayMessage(Component.translatable("block.thaumicexploration.soul_brazier.stored", permanent));
        }
    }

    private boolean powered(Brazier b) {
        return vis >= b.visPerCycle() && essentia >= b.essentiaPerCycle();
    }

    private void setActive(boolean on) {
        active = on;
        if (level instanceof ServerLevel server) {
            BrazierTickets.force(server, worldPosition, on);
            server.setBlockAndUpdate(worldPosition, getBlockState().setValue(SoulBrazierBlock.LIT, on));
        }
        setChanged();
    }

    /** Gives the stored warp back to the owner and goes out. */
    void returnWarp(ServerLevel level) {
        if (owner != null && storedWarp > 0) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
            if (player != null) {
                WarpHelper.addWarp(player, storedWarp, WarpType.PERMANENT);
                player.sendSystemMessage(Component.translatable("block.thaumicexploration.soul_brazier.returned", storedWarp));
            } else {
                PendingWarp.get(level.getServer()).add(owner, storedWarp);
            }
        }
        storedWarp = 0;
        if (active) {
            setActive(false);
        }
    }

    static void serverTick(net.minecraft.world.level.Level lvl, BlockPos pos, BlockState state, SoulBrazierBlockEntity be) {
        if (lvl instanceof ServerLevel level) {
            be.tick(level);
        }
    }

    private void tick(ServerLevel level) {
        Brazier b = settings();
        if (b == null) {
            return;
        }
        tick++;
        if (tick % 5 == 0) {
            refuel(level, b);
        }
        if (!active) {
            return;
        }
        if (tick % 10 == 0) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, worldPosition.getX() + 0.5, worldPosition.getY() + 1.1,
                    worldPosition.getZ() + 0.5, 3, 0.15, 0.1, 0.15, 0.01);
        }
        if (tick % b.taintInterval() == 0) {
            sicken(level, b);
        }
        if (tick % b.cycle() == 0) {
            if (!powered(b)) {
                returnWarp(level);
                return;
            }
            vis -= b.visPerCycle();
            essentia -= b.essentiaPerCycle();
            setChanged();
        }
    }

    private void refuel(ServerLevel level, Brazier b) {
        Holder<IAspect> aspect = aspect(level, b);
        if (vis < b.visCapacity()) {
            int want = Math.min(100, b.visCapacity() - vis);
            try (Transaction tx = Transaction.openRoot()) {
                int got = VisRelayHelper.drainCentivis(level, worldPosition, b.vis(), want, tx);
                if (got <= 0) {
                    got = Math.round(AuraHelper.drainVis(level, worldPosition, want / 100f, tx) * 100);
                }
                tx.commit();
                vis += got;
            }
        }
        if (essentia < b.essentiaCapacity()) {
            IEssentiaTransport below = level.getCapability(EssentiaCapabilities.TRANSPORT, worldPosition.below(), Direction.UP);
            if (below != null && below.canOutputTo(Direction.UP) && below.getSuctionAmount(Direction.UP) < SUCTION) {
                essentia += below.takeEssentia(aspect, 1, Direction.UP);
            }
        }
        setChanged();
    }

    /** Warp released into the land: taint fibres creep in at a random spot around the brazier. */
    private void sicken(ServerLevel level, Brazier b) {
        int r = b.taintRadius();
        BlockPos at = worldPosition.offset(level.getRandom().nextInt(2 * r + 1) - r, 0, level.getRandom().nextInt(2 * r + 1) - r);
        at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
        TaintApi.addEcologicalPressure(level, at, b.taintPressure());
        TaintApi.spreadFibres(level, at, false);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel server) {
            returnWarp(server);
        }
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("owner", UUIDUtil.CODEC, owner);
        output.putInt("stored_warp", storedWarp);
        output.putInt("vis", vis);
        output.putInt("essentia", essentia);
        output.putBoolean("active", active);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
        storedWarp = input.getIntOr("stored_warp", 0);
        vis = input.getIntOr("vis", 0);
        essentia = input.getIntOr("essentia", 0);
        active = input.getBooleanOr("active", false);
    }

    private static Holder<IAspect> aspect(net.minecraft.world.level.Level level, Brazier b) {
        return level.registryAccess().lookupOrThrow(IAspect.REGISTRY_KEY).getOrThrow(b.essentia());
    }

    // Essentia comes in from a tube underneath only, so tubes route its aspect toward the brazier.
    @Override
    public boolean isConnectable(Direction side) {
        return side == Direction.DOWN;
    }

    @Override
    public boolean canInputFrom(Direction side) {
        return side == Direction.DOWN;
    }

    @Override
    public boolean canOutputTo(Direction side) {
        return false;
    }

    @Override
    public void setSuction(Holder<IAspect> aspect, int amount) {
    }

    @Override
    public @Nullable Holder<IAspect> getSuctionType(Direction side) {
        Brazier b = settings();
        return side == Direction.DOWN && b != null && level != null ? aspect(level, b) : null;
    }

    @Override
    public int getSuctionAmount(Direction side) {
        Brazier b = settings();
        return side == Direction.DOWN && b != null && essentia < b.essentiaCapacity() ? SUCTION : 0;
    }

    @Override
    public int takeEssentia(Holder<IAspect> aspect, int amount, Direction side) {
        return 0;
    }

    @Override
    public int addEssentia(Holder<IAspect> aspect, int amount, Direction side) {
        Brazier b = settings();
        if (b == null || side != Direction.DOWN || !aspect.is(b.essentia())) {
            return 0;
        }
        int added = Math.min(amount, b.essentiaCapacity() - essentia);
        essentia += added;
        setChanged();
        return added;
    }

    @Override
    public @Nullable Holder<IAspect> getEssentiaType(Direction side) {
        return null;
    }

    @Override
    public int getEssentiaAmount(Direction side) {
        return 0;
    }

    @Override
    public int getMinimumSuction() {
        return 0;
    }

    boolean ownedBy(Player player) {
        return owner == null || owner.equals(player.getUUID());
    }

    Optional<UUID> owner() {
        return Optional.ofNullable(owner);
    }
}
