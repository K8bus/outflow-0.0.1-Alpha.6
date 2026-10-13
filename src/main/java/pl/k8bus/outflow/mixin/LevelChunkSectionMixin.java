package pl.k8bus.outflow.mixin;

import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import pl.k8bus.outflow.cubic.world.SurfaceSection;

@Mixin(LevelChunkSection.class)
public class LevelChunkSectionMixin implements SurfaceSection {
    
    @Unique
    private boolean outflow$isSurface = false;

    @Override
    public boolean outflow$isSurface() {
        return outflow$isSurface;
    }

    @Override
    public void outflow$setSurface(boolean surface) {
        this.outflow$isSurface = surface;
    }
}
