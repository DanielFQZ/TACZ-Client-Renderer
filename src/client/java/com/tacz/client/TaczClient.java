package com.tacz.client;

import com.tacz.client.config.ClientItemOverrideRegistry;
import com.tacz.client.input.ClientActionController;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.server.packs.PackType;

/**
 * Client-only entrypoint. No items, blocks, entities, packets, or server entrypoint
 * are registered by this project.
 */
public final class TaczClient implements ClientModInitializer {
    public static final String MOD_ID = "taczclient";
    private static final KeyMapping INSPECT_KEY = new KeyMapping(
            "key.taczclient.inspect", InputConstants.Type.KEYSYM, 73, KeyMapping.Category.MISC);
    private static final KeyMapping RELOAD_KEY = new KeyMapping(
            "key.taczclient.reload", InputConstants.Type.KEYSYM, 82, KeyMapping.Category.MISC);

    @Override
    public void onInitializeClient() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)
                .registerReloadListener(ClientItemOverrideRegistry.INSTANCE);
        KeyMappingHelper.registerKeyMapping(INSPECT_KEY);
        KeyMappingHelper.registerKeyMapping(RELOAD_KEY);
        ClientTickEvents.END_CLIENT_TICK.register(ClientActionController::tick);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (INSPECT_KEY.consumeClick()) {
                ClientActionController.begin(ClientActionController.Action.INSPECT);
            }
            while (RELOAD_KEY.consumeClick()) {
                ClientActionController.begin(ClientActionController.Action.RELOAD);
            }
        });
    }
}
