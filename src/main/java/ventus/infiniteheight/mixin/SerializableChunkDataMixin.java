package ventus.infiniteheight.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;

@Mixin(SerializableChunkData.class)
public abstract class SerializableChunkDataMixin {
    @Redirect(
        method = "parse",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/CompoundTag;getByteOr(Ljava/lang/String;B)B")
    )
    private static byte infiniteheight$readSectionY(CompoundTag tag, String key, byte fallback) {
        if ("Y".equals(key)) {
            return (byte) tag.getIntOr("Y", fallback);
        }
        return tag.getByteOr(key, fallback);
    }

    @Redirect(
        method = "write",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/CompoundTag;putByte(Ljava/lang/String;B)V")
    )
    private void infiniteheight$writeSectionY(CompoundTag tag, String key, byte value) {
        if ("Y".equals(key)) {
            tag.putInt("Y", value);
        } else {
            tag.putByte(key, value);
        }
    }
}
