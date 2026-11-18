package com.thaumicexploration.compat;

import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.thaumicexploration.ThaumicExploration;
import com.thaumicexploration.brazier.SoulBrazierBlock;
import com.thaumicexploration.brazier.SoulBrazierBlockEntity;
import com.thaumicexploration.seal.BoundJarBlock;
import com.thaumicexploration.seal.BoundJarBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/** Jade tooltips: a bound jar's network and contents, a Soul Brazier's state and the warp it holds. Loaded only with Jade. */
@WailaPlugin
public final class TXJadePlugin implements IWailaPlugin {
    private static final Identifier JAR = ThaumicExploration.id("bound_jar");
    private static final Identifier BRAZIER = ThaumicExploration.id("soul_brazier");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(BrazierData.INSTANCE, SoulBrazierBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(JarTooltip.INSTANCE, BoundJarBlock.class);
        registration.registerBlockComponent(BrazierData.INSTANCE, SoulBrazierBlock.class);
    }

    /** The jar's network name and contents are already synced to the client with the block entity. */
    enum JarTooltip implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!(accessor.getBlockEntity() instanceof BoundJarBlockEntity jar)) {
                return;
            }
            String key = jar.key();
            tooltip.add(Component.translatable("jade.thaumicexploration.network", key.substring(key.indexOf('/') + 1)));
            for (AspectInstance aspect : jar.shownContents().entries()) {
                aspect.aspect().unwrapKey().ifPresent(k -> tooltip.add(Component.translatable("jade.thaumicexploration.essentia",
                        Component.translatable("aspect." + k.identifier().getNamespace() + "." + k.identifier().getPath()), aspect.amount())));
            }
        }

        @Override
        public Identifier getUid() {
            return JAR;
        }
    }

    record BrazierInfo(boolean active, int warp) {
        static final StreamCodec<ByteBuf, BrazierInfo> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, BrazierInfo::active, ByteBufCodecs.VAR_INT, BrazierInfo::warp, BrazierInfo::new);
    }

    /** Stored warp lives only on the server, so it travels with Jade's own data request. */
    enum BrazierData implements StreamServerDataProvider<BlockAccessor, BrazierInfo>, IBlockComponentProvider {
        INSTANCE;

        @Override
        public BrazierInfo streamData(BlockAccessor accessor) {
            SoulBrazierBlockEntity be = (SoulBrazierBlockEntity) accessor.getBlockEntity();
            return new BrazierInfo(be.active(), be.storedWarp());
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, BrazierInfo> streamCodec() {
            return BrazierInfo.CODEC.cast();
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            decodeFromData(accessor).ifPresent(info -> {
                tooltip.add(Component.translatable(info.active() ? "jade.thaumicexploration.brazier.lit" : "jade.thaumicexploration.brazier.out"));
                if (info.warp() > 0) {
                    tooltip.add(Component.translatable("jade.thaumicexploration.brazier.warp", info.warp()));
                }
            });
        }

        @Override
        public Identifier getUid() {
            return BRAZIER;
        }
    }
}
