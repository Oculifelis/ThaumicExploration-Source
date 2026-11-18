package com.thaumicexploration.seal;

import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import com.thaumicexploration.TXRegistry;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * A bound jar: its essentia lives in the jar network, so every jar sealed with the same colour and seal name holds
 * the same essentia. Like a warded jar it draws from a tube above it; tubes and phials can fill and empty it on any
 * side. The client only sees a synced copy of the contents, for goggles and comparators.
 */
public class BoundJarBlockEntity extends BlockEntity implements IEssentiaTransport {
    private static final int SUCTION = 32;

    private String name = "";
    private Optional<AspectInstance> shown = Optional.empty();
    private long seenRevision = -1;

    public BoundJarBlockEntity(BlockPos pos, BlockState state) {
        super(TXRegistry.BOUND_JAR_BE.get(), pos, state);
    }

    void bind(String name) {
        this.name = name;
        setChanged();
    }

    public String key() {
        return SealNetworks.jarKey(getBlockState().getValue(BoundJarBlock.COLOR), name);
    }

    private @Nullable JarNetwork network() {
        return level instanceof ServerLevel server ? SealNetworks.get(server.getServer()).jar(key()) : null;
    }

    public AspectList shownContents() {
        return shown.map(AspectList::of).orElse(AspectList.EMPTY);
    }

    public int shownAmount() {
        return shown.map(AspectInstance::amount).orElse(0);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BoundJarBlockEntity jar) {
        JarNetwork net = jar.network();
        if (net == null) {
            return;
        }
        if (level.getGameTime() % 5 == 0) {
            jar.fillFromAbove(level, pos, net);
        }
        if (net.revision() != jar.seenRevision) {
            jar.seenRevision = net.revision();
            jar.shown = net.amount() > 0 ? Optional.of(new AspectInstance(net.aspect(), net.amount())) : Optional.empty();
            jar.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
            level.updateNeighbourForOutputSignal(pos, state.getBlock());
        }
    }

    /** One point at a time from a tube or container above with weaker suction, as warded jars do. */
    private void fillFromAbove(Level level, BlockPos pos, JarNetwork net) {
        if (net.amount() >= net.capacity()) {
            return;
        }
        IEssentiaTransport above = level.getCapability(EssentiaCapabilities.TRANSPORT, pos.above(), Direction.DOWN);
        if (above == null || !above.canOutputTo(Direction.DOWN) || above.getSuctionAmount(Direction.DOWN) >= SUCTION) {
            return;
        }
        Holder<IAspect> aspect = net.aspect() != null ? net.aspect() : above.getEssentiaType(Direction.DOWN);
        if (aspect != null && above.getEssentiaAmount(Direction.DOWN) > 0) {
            net.add(aspect, above.takeEssentia(aspect, 1, Direction.DOWN));
        }
    }

    // Essentia transport (tubes), server side.
    @Override
    public boolean isConnectable(Direction side) {
        return true;
    }

    @Override
    public boolean canInputFrom(Direction side) {
        return true;
    }

    @Override
    public boolean canOutputTo(Direction side) {
        return true;
    }

    @Override
    public void setSuction(Holder<IAspect> aspect, int amount) {
    }

    @Override
    public @Nullable Holder<IAspect> getSuctionType(Direction side) {
        JarNetwork net = network();
        return net == null ? null : net.aspect();
    }

    @Override
    public int getSuctionAmount(Direction side) {
        JarNetwork net = network();
        return net != null && net.amount() < net.capacity() ? SUCTION : 0;
    }

    @Override
    public int getMinimumSuction() {
        return SUCTION;
    }

    @Override
    public int takeEssentia(Holder<IAspect> aspect, int amount, Direction side) {
        JarNetwork net = network();
        return net == null ? 0 : net.take(aspect, amount);
    }

    @Override
    public int addEssentia(Holder<IAspect> aspect, int amount, Direction side) {
        JarNetwork net = network();
        return net == null ? 0 : net.add(aspect, amount);
    }

    @Override
    public @Nullable Holder<IAspect> getEssentiaType(Direction side) {
        JarNetwork net = network();
        return net == null ? null : net.aspect();
    }

    @Override
    public int getEssentiaAmount(Direction side) {
        JarNetwork net = network();
        return net == null ? 0 : net.amount();
    }

    /** Transactional view of the network for Thaumaturge's essentia storage capability. */
    public @Nullable IEssentiaStorage storage() {
        JarNetwork net = network();
        if (net == null) {
            return null;
        }
        return new IEssentiaStorage() {
            @Override
            public AspectList contents() {
                return net.asList();
            }

            @Override
            public int insert(Holder<IAspect> aspect, int amount, TransactionContext tx) {
                return net.insert(aspect, amount, tx);
            }

            @Override
            public int extract(Holder<IAspect> aspect, int amount, TransactionContext tx) {
                return net.extract(aspect, amount, tx);
            }

            @Override
            public long contentRevision() {
                return net.revision();
            }
        };
    }

    /** The loot table copies the network's name onto the jar seal it drops. */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!name.isEmpty()) {
            components.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putString("network", name);
        shown.ifPresent(s -> out.store("shown", AspectInstance.CODEC, s));
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        name = in.getStringOr("network", "");
        shown = in.read("shown", AspectInstance.CODEC);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
