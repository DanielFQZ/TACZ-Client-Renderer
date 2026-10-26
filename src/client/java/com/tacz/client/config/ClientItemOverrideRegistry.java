package com.tacz.client.config;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.tacz.client.TaczClient;
import com.tacz.client.render.TaczGunRenderer;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Reads the new mapping layer while leaving all existing TACZ display, model,
 * animation, and script files in their original locations.
 *
 * Supported client-resource locations:
 * - assets/tacz/config/client_item_overrides.json
 * - assets/tacz/config/client_items/*.json
 * Embedded packs may place the same paths below their custom pack namespace.
 */
public enum ClientItemOverrideRegistry implements SimpleSynchronousResourceReloadListener {
    INSTANCE;

    private static final Logger LOGGER = LoggerFactory.getLogger(TaczClient.MOD_ID);
    private static final Gson GSON = new Gson();
    private static final Identifier RELOAD_ID = Identifier.fromNamespaceAndPath(TaczClient.MOD_ID, "client_item_overrides");
    private final Map<Identifier, ClientItemOverride> overrides = new HashMap<>();

    @Override
    public Identifier getFabricId() {
        return RELOAD_ID;
    }

    @Override
    public synchronized void onResourceManagerReload(ResourceManager manager) {
        Map<Identifier, ClientItemOverride> loaded = new HashMap<>();
        TaczGunIndexRegistry.reload(manager);
        TaczDisplayRegistry.reload(manager);
        TaczGunRenderer.reload(manager);
        com.tacz.client.input.ClientActionController.reset();
        loadDirectory(manager, loaded, "config/client_items");
        loadDirectory(manager, loaded, "custom");
        loadSingleFiles(manager, loaded, "config/client_item_overrides.json");
        loadSingleFiles(manager, loaded, "custom");
        overrides.clear();
        overrides.putAll(loaded);
        LOGGER.info("Loaded {} TACZ vanilla item override(s)", overrides.size());
    }

    private void loadDirectory(ResourceManager manager, Map<Identifier, ClientItemOverride> loaded, String prefix) {
        manager.listResources(prefix, path -> path.getPath().endsWith(".json") && path.getPath().contains("client_items/"))
                .forEach((resourceId, resource) -> loadItemFile(loaded, resourceId, resource));
    }

    private void loadSingleFiles(ResourceManager manager, Map<Identifier, ClientItemOverride> loaded, String prefix) {
        manager.listResources(prefix, path -> path.getPath().endsWith("client_item_overrides.json"))
                .forEach((resourceId, resource) -> loadMappingFile(loaded, resourceId, resource));
    }

    private void loadItemFile(Map<Identifier, ClientItemOverride> loaded, Identifier resourceId, Resource resource) {
        try (Reader reader = resource.openAsReader()) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            String itemName = json.has("item") ? json.get("item").getAsString()
                    : resourceId.getPath().substring(resourceId.getPath().lastIndexOf('/') + 1,
                    resourceId.getPath().length() - ".json".length());
            Identifier item = Identifier.tryParse(itemName);
            if (item == null) {
                throw new JsonParseException("invalid item id: " + itemName);
            }
            loaded.put(item, ClientItemOverride.fromJson(item, json));
        } catch (Exception e) {
            LOGGER.warn("Unable to load TACZ item override {}", resourceId, e);
        }
    }

    private void loadMappingFile(Map<Identifier, ClientItemOverride> loaded, Identifier resourceId, Resource resource) {
        try (Reader reader = resource.openAsReader()) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            JsonObject items = root.has("items") ? root.getAsJsonObject("items") : root;
            for (Map.Entry<String, JsonElement> entry : items.entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }
                Identifier item = Identifier.tryParse(entry.getKey());
                if (item == null) {
                    LOGGER.warn("Invalid vanilla item id '{}' in {}", entry.getKey(), resourceId);
                    continue;
                }
                loaded.put(item, ClientItemOverride.fromJson(item, entry.getValue().getAsJsonObject()));
            }
        } catch (Exception e) {
            LOGGER.warn("Unable to load TACZ item override file {}", resourceId, e);
        }
    }

    public synchronized Optional<ClientItemOverride> get(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        Identifier item = BuiltInRegistries.ITEM.getKey(stack.getItem());
        ClientItemOverride override = overrides.get(item);
        return override == null || !override.enabled() ? Optional.empty() : Optional.of(override);
    }

    /** Returns the display JSON selected by an override, resolving a TACZ gun index when needed. */
    public synchronized Optional<com.google.gson.JsonObject> display(ItemStack stack) {
        return get(stack)
                .map(override -> {
                    Identifier display = override.display();
                    if (display == null && override.gun() != null) {
                        display = TaczGunIndexRegistry.displayFor(override.gun());
                    }
                    return display == null ? null : TaczDisplayRegistry.get(display);
                })
                .filter(java.util.Objects::nonNull);
    }

    public synchronized Map<Identifier, ClientItemOverride> snapshot() {
        return Map.copyOf(overrides);
    }
}
