package pl.k8bus.client.mixin;

import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import pl.k8bus.outflow.worldgen.feature.tarn.AlpineTarnLocator;

@Mixin(LevelLoadingScreen.class)
public class LevelLoadingScreenMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    public void onExtractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        boolean isCalculating = (AlpineTarnLocator.calculatingWaterworks != null && AlpineTarnLocator.calculatingWaterworks.get() > 0);
        
        if (isCalculating) {
            net.minecraft.client.gui.screens.Screen screen = (net.minecraft.client.gui.screens.Screen)(Object)this;
            int tasks = AlpineTarnLocator.calculatingWaterworks.get();
            String status = AlpineTarnLocator.currentWaterworksStatus;
            
            // Animacja kolorów i piękny tekst z mieniącą się "Wodą"
            long time = System.currentTimeMillis();
            int r = (int)(Math.sin(time / 200.0) * 20 + 60);
            int g = (int)(Math.sin(time / 250.0 + 1) * 30 + 180);
            int b = (int)(Math.sin(time / 300.0 + 2) * 20 + 230);
            int dynamicColor = (255 << 24) | (r << 16) | (g << 8) | b;
            
            String title = "Alpine Tarns Waterworks [" + tasks + " tasks]";
            
            extractor.centeredText(net.minecraft.client.Minecraft.getInstance().font, title, screen.width / 2, screen.height / 2 + 70, dynamicColor);
            extractor.centeredText(net.minecraft.client.Minecraft.getInstance().font, ">> " + status + " <<", screen.width / 2, screen.height / 2 + 82, 0xFFBBFFFF);
        }
    }
}
