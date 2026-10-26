package com.tacz.client.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/** Resolves the unchanged TACZ data/tacz/index/guns/*.json entries. */
public final class TaczGunIndexRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger("taczclient");
    private static final Gson GSON = new Gson();
    private static final Map<Identifier, Identifier> DISPLAYS = new HashMap<>();

    private TaczGunIndexRegistry() {
    }

    public static void reload(ResourceManager manager) {
        Map<Identifier, Identifier> loaded = new HashMap<>();
        manager.listResources("index/guns", path -> path.getPath().endsWith(".json"))
                .forEach((id, resource) -> read(loaded, id, resource));
        synchronized (DISPLAYS) {
            DISPLAYS.clear();
            DISPLAYS.putAll(loaded);
        }
        LOGGER.info("Loaded {} TACZ gun index entr(y/ies)", loaded.size());
    }

    private static void read(Map<Identifier, Identifier> loaded, Identifier resourceId, Resource resource) {
        try (Reader reader = resource.openAsReader()) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (!json.has("display")) {
                return;
            }
            String path = resourceId.getPath();
            String gunPath = path.substring(path.lastIndexOf('/') + 1, path.length() - ".json".length());
            Identifier gunId = Identifier.fromNamespaceAndPath(resourceId.getNamespace(), gunPath);
            Identifier display = Identifier.tryParse(json.get("display").getAsString());
            if (display != null) {
                loaded.put(gunId, display);
            }
        } catch (Exception e) {
            LOGGER.warn("Unable to load TACZ gun index {}", resourceId, e);
        }
    }

    public static Identifier displayFor(Identifier gunId) {
        synchronized (DISPLAYS) {
            Identifier display = DISPLAYS.get(gunId);
            if (display != null) {
                return display;
            }
            // Client resource packs expose assets/ but not data/. TACZ's
            // stock convention is <gun>_display, so preserve gun-only files
            // even when the original data index is unavailable to the client.
            Identifier conventional = Identifier.fromNamespaceAndPath(gunId.getNamespace(),
                    gunId.getPath() + "_display");
            return TaczDisplayRegistry.contains(conventional) ? conventional : null;
        }
    }
}
