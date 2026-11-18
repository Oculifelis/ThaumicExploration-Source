package com.thaumicexploration.brain;

import com.leclowndu93150.thaumaturge.api.aspect.AspectIndexAccess;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import com.thaumicexploration.TXDataMaps;
import com.thaumicexploration.TXDataMaps.Replicator;
import com.thaumicexploration.TXRegistry;
import com.thaumicexploration.ThaumicExploration;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * Thaumic Replicator: holds a template of a plain building block (the replicable item tag). A redstone pulse starts
 * a copy; it draws the block's aspects, one point at a time, from any essentia storage (jars) in range, then forms
 * the copy. Range and pace are the replicator block data map; the costs are Thaumaturge's own item aspects.
 */
public class ReplicatorBlockEntity extends BlockEntity {
    public static final TagKey<Item> REPLICABLE = TagKey.create(Registries.ITEM, ThaumicExploration.id("replicable"));

    /**
     * Idle, or crafting one copy. The aspects still required, the tick counter and the found sources belong to
     * {@link State#CRAFTING} only: every change of state goes through {@link #enter}, which sets them all, and they
     * are saved only while crafting. The finished copies ({@code made}) are output, not state.
     */
    enum State {
        IDLE, CRAFTING;

        String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        static State of(String id) {
            for (State s : values()) {
                if (s.id().equals(id)) {
                    return s;
                }
            }
            return IDLE;
        }
    }

    private ItemStack template = ItemStack.EMPTY;
    private int made;
    private State state = State.IDLE;
    private AspectList required = AspectList.EMPTY;
    private int ticks;
    private final List<BlockPos> sources = new ArrayList<>();
    private final Output output = new Output();

    public ReplicatorBlockEntity(BlockPos pos, BlockState state) {
        super(TXRegistry.REPLICATOR_BE.get(), pos, state);
    }

    public static boolean canReplicate(ItemStack stack) {
        return !stack.isEmpty() && stack.is(REPLICABLE) && !AspectIndexAccess.of(stack.copyWithCount(1)).isEmpty();
    }

    private @Nullable Replicator settings() {
        return getBlockState().getBlock().builtInRegistryHolder().getData(TXDataMaps.REPLICATOR);
    }

    public ItemStack template() {
        return template;
    }

    public int made() {
        return made;
    }

    public boolean crafting() {
        return state == State.CRAFTING;
    }

    /** The one place the state changes; a new craft needs the template's aspects, idle needs nothing. */
    private void enter(State next) {
        state = next;
        required = next == State.CRAFTING ? AspectIndexAccess.of(template.copyWithCount(1)) : AspectList.EMPTY;
        ticks = 0;
        sources.clear();
        setChanged();
    }

    void setTemplate(ItemStack stack) {
        template = stack.copyWithCount(1);
        made = 0;
        setChanged();
    }

    /** Hands out every finished copy, keeping the template. */
    ItemStack takeMade() {
        ItemStack out = template.copyWithCount(made);
        made = 0;
        setChanged();
        return out;
    }

    void start() {
        if (state == State.CRAFTING || template.isEmpty() || made >= template.getMaxStackSize()) {
            return;
        }
        enter(State.CRAFTING);
        level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.4f, 1.6f);
    }

    void cancel() {
        enter(State.IDLE);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ReplicatorBlockEntity rep) {
        Replicator settings = rep.settings();
        if (rep.state != State.CRAFTING || settings == null) {
            return;
        }
        rep.ticks++;
        if (!rep.required.isEmpty()) {
            if (rep.ticks >= settings.essentiaInterval()) {
                rep.ticks = 0;
                rep.drainOne((ServerLevel) level, settings.range());
            }
            return;
        }
        if (level instanceof ServerLevel server && rep.ticks % 4 == 0) {
            server.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.4, pos.getZ() + 0.5, 4, 0.4, 0.3, 0.4, 0.2);
        }
        if (rep.ticks >= settings.finishTicks()) {
            rep.made++;
            rep.enter(State.IDLE);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8f, 1.0f);
        }
    }

    /** Takes one point of any still-needed aspect from the first storage in range that has it. */
    private void drainOne(ServerLevel level, int range) {
        if (sources.isEmpty()) {
            findSources(level, range);
        }
        for (BlockPos source : sources) {
            IEssentiaStorage storage = level.getCapability(EssentiaCapabilities.STORAGE, source, null);
            if (storage == null) {
                continue;
            }
            for (AspectInstance need : required.entries()) {
                try (Transaction tx = Transaction.openRoot()) {
                    if (storage.extract(need.aspect(), 1, tx) == 1) {
                        tx.commit();
                        required = required.reduce(need.aspect(), 1);
                        level.sendParticles(ParticleTypes.WITCH, source.getX() + 0.5, source.getY() + 1, source.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0);
                        setChanged();
                        return;
                    }
                }
            }
        }
        sources.clear(); // nothing had what we need: look again next time
    }

    private void findSources(ServerLevel level, int range) {
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-range, -range, -range), worldPosition.offset(range, range, range))) {
            if (!p.equals(worldPosition) && level.getBlockEntity(p) != null
                    && level.getCapability(EssentiaCapabilities.STORAGE, p, null) != null) {
                sources.add(p.immutable());
            }
        }
    }

    public ResourceHandler<ItemResource> output() {
        return output;
    }

    /** Hoppers can pull the finished copies (never the template). */
    private final class Output extends SnapshotJournal<Integer> implements ResourceHandler<ItemResource> {
        @Override
        public int size() {
            return 1;
        }

        @Override
        public ItemResource getResource(int index) {
            return made > 0 ? ItemResource.of(template) : ItemResource.EMPTY;
        }

        @Override
        public long getAmountAsLong(int index) {
            return made;
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            return template.getMaxStackSize();
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return false;
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext tx) {
            return 0;
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext tx) {
            if (made <= 0 || !resource.matches(template)) {
                return 0;
            }
            int got = Math.min(amount, made);
            updateSnapshots(tx);
            made -= got;
            return got;
        }

        @Override
        protected Integer createSnapshot() {
            return made;
        }

        @Override
        protected void revertToSnapshot(Integer snapshot) {
            made = snapshot;
        }

        @Override
        protected void onRootCommit(Integer original) {
            setChanged();
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        if (!template.isEmpty()) {
            out.store("template", ItemStack.CODEC, template);
        }
        out.putInt("made", made);
        out.putString("state", state.id());
        if (state == State.CRAFTING) {
            out.store("required", AspectList.CODEC, required);
            out.putInt("ticks", ticks);
        }
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        template = in.read("template", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        made = in.getIntOr("made", 0);
        state = State.of(in.getStringOr("state", ""));
        boolean on = state == State.CRAFTING;
        required = on ? in.read("required", AspectList.CODEC).orElse(AspectList.EMPTY) : AspectList.EMPTY;
        ticks = on ? in.getIntOr("ticks", 0) : 0;
        sources.clear();
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null && made > 0) {
            net.minecraft.world.level.block.Block.popResource(level, pos, template.copyWithCount(made));
        }
    }
}
