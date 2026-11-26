package com.thaumaturgicexploration.seal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.DyeColor;

/** Which bound chest network a linked chest seal (or a bound chest) belongs to. */
public record SealLink(DyeColor color, int id) {
    public static final Codec<SealLink> CODEC = RecordCodecBuilder.create(i -> i.group(
            DyeColor.CODEC.fieldOf("color").forGetter(SealLink::color),
            Codec.INT.fieldOf("id").forGetter(SealLink::id)
    ).apply(i, SealLink::new));
    public static final StreamCodec<ByteBuf, SealLink> STREAM_CODEC = StreamCodec.composite(
            DyeColor.STREAM_CODEC, SealLink::color, ByteBufCodecs.VAR_INT, SealLink::id, SealLink::new);

    public String key() {
        return SealNetworks.chestKey(color, id);
    }
}
