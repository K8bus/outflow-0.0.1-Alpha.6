package pl.k8bus.outflow.mixin;

import net.minecraft.world.level.levelgen.SurfaceRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.level.levelgen.SurfaceRules$LazyCondition")
public interface LazyXZConditionAccessor {

    @Accessor("context")
    SurfaceRules.Context outflow$getContext();
}
