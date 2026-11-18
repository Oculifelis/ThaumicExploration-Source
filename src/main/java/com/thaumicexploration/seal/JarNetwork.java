package com.thaumicexploration.seal;

import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.content.essentia.jar.BlockEntityJar;
import com.mojang.serialization.Codec;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * The essentia shared by every bound jar on one network: one aspect at a time, as much as one warded jar holds.
 * Plain add/take serve Thaumaturge's tubes; insert/extract are the transactional storage side (phials, golems).
 */
public final class JarNetwork extends SnapshotJournal<Optional<AspectInstance>> {
    static final Codec<JarNetwork> CODEC = AspectInstance.CODEC.optionalFieldOf("contents").codec()
            .xmap(JarNetwork::new, j -> j.contents);

    private Optional<AspectInstance> contents;
    private @Nullable SealNetworks owner;
    private long revision;

    JarNetwork(Optional<AspectInstance> contents) {
        this.contents = contents;
    }

    JarNetwork attach(SealNetworks owner) {
        this.owner = owner;
        return this;
    }

    public int capacity() {
        return BlockEntityJar.CAPACITY;
    }

    public @Nullable Holder<IAspect> aspect() {
        return contents.map(AspectInstance::aspect).orElse(null);
    }

    public int amount() {
        return contents.map(AspectInstance::amount).orElse(0);
    }

    public long revision() {
        return revision;
    }

    public AspectList asList() {
        return contents.map(AspectList::of).orElse(AspectList.EMPTY);
    }

    static boolean same(Holder<IAspect> a, Holder<IAspect> b) {
        return a == b || a.unwrapKey().equals(b.unwrapKey());
    }

    private int room(Holder<IAspect> aspect) {
        Holder<IAspect> held = aspect();
        return held != null && !same(held, aspect) ? 0 : capacity() - amount();
    }

    private int available(Holder<IAspect> aspect) {
        Holder<IAspect> held = aspect();
        return held == null || !same(held, aspect) ? 0 : amount();
    }

    private void set(Holder<IAspect> aspect, int amount) {
        contents = amount <= 0 ? Optional.empty() : Optional.of(new AspectInstance(aspect, amount));
    }

    private void changed() {
        revision++;
        if (owner != null) {
            owner.setDirty();
        }
    }

    public int add(Holder<IAspect> aspect, int amount) {
        int put = Math.min(amount, room(aspect));
        if (put > 0) {
            set(aspect, amount() + put);
            changed();
        }
        return put;
    }

    public int take(Holder<IAspect> aspect, int amount) {
        int got = Math.min(amount, available(aspect));
        if (got > 0) {
            set(aspect, amount() - got);
            changed();
        }
        return got;
    }

    public int insert(Holder<IAspect> aspect, int amount, TransactionContext tx) {
        int put = Math.min(amount, room(aspect));
        if (put > 0) {
            updateSnapshots(tx);
            set(aspect, amount() + put);
        }
        return put;
    }

    public int extract(Holder<IAspect> aspect, int amount, TransactionContext tx) {
        int got = Math.min(amount, available(aspect));
        if (got > 0) {
            updateSnapshots(tx);
            set(aspect, amount() - got);
        }
        return got;
    }

    @Override
    protected Optional<AspectInstance> createSnapshot() {
        return contents;
    }

    @Override
    protected void revertToSnapshot(Optional<AspectInstance> snapshot) {
        contents = snapshot;
    }

    @Override
    protected void onRootCommit(Optional<AspectInstance> originalState) {
        changed();
    }
}
