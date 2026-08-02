package dev.micx.micxfabric;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MicxFabric implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("MICx-Fabric");

    @Override
    public void onInitializeClient() {
        FabricRuntime.initialize();
        LOGGER.info("MICx Fabric initialized for Minecraft 26.2");
    }
}
