package com.tacz.client.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/** Loads the original TACZ display JSON without changing its schema. */
public final class TaczDisplayRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger("taczclient");
    private static final Gson GSON = new Gson();
    private static final Map<Identifier, JsonObject> DISPLAYS = new HashMap<>();

    private TaczDisplayRegistry() {
    }

    public static void reload(ResourceManager manager) {
        Map<Identifier, JsonObject> loaded = new HashMap<>();
        manager.listResources("display/guns", path -> path.getPath().endsWith(".json"))
                .forEach((id, resource) -> read(loaded, id, resource, "display/guns/"));
        manager.listResources("custom", path -> path.getPath().startsWith("custom/")
                        && path.getPath().contains("/assets/tacz/display/guns/")
                        && path.getPath().endsWith(".json"))
                .forEach((id, resource) -> read(loaded, id, resource, "/assets/tacz/display/guns/"));
        synchronized (DISPLAYS) {
            DISPLAYS.clear();
            DISPLAYS.putAll(loaded);
        }
        LOGGER.info("Loaded {} TACZ gun display definition(s)", loaded.size());
    }

    private static void read(Map<Identifier, JsonObject> loaded, Identifier resourceId, Resource resource, String marker) {
        try (var reader = resource.openAsReader()) {
            String path = resourceId.getPath();
            int markerStart = path.lastIndexOf(marker);
            String file = markerStart >= 0 ? path.substring(markerStart + marker.length()) : path;
            if (!file.endsWith(".json")) {
                return;
            }
            String key = file.substring(0, file.length() - ".json".length());
            loaded.put(Identifier.fromNamespaceAndPath(resourceId.getNamespace(), key),
                    GSON.fromJson(reader, JsonObject.class));
        } catch (Exception e) {
            LOGGER.warn("Unable to load TACZ display {}", resourceId, e);
        }
    }

    public static JsonObject get(Identifier id) {
        synchronized (DISPLAYS) {
            return DISPLAYS.get(id);
        }
    }

    public static boolean contains(Identifier id) {
        synchronized (DISPLAYS) {
            return DISPLAYS.containsKey(id);
        }
    }
}
