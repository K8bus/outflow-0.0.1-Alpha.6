package pl.k8bus.outflow;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.k8bus.outflow.registry.OutflowWorldGen;

public class Outflow implements ModInitializer {
    public static final String MOD_ID = "outflow";
    public static final Logger LOGGER = LoggerFactory.getLogger("outflow");

    @Override
    public void onInitialize() {
        LOGGER.info("[Outflow] Inicjalizacja silnika hydrologicznego...");
        OutflowWorldGen.registerAll();
    }
}
