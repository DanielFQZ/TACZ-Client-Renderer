package com.tacz.client.config;

import net.minecraft.resources.Identifier;

/**
 * Maps a vanilla item to one TACZ display/animation definition.
 * The TACZ display JSON and animation files remain unchanged.
 */
public record ClientItemOverride(
        Identifier item,
        Identifier display,
        Identifier gun,
        Identifier animation,
        Identifier stateMachine,
        boolean enabled,
        java.util.Map<String, String> actions
) {
    public static ClientItemOverride fromJson(Identifier item, com.google.gson.JsonObject json) {
        Identifier display = id(json, "display");
        Identifier gun = id(json, "gun");
        Identifier animation = id(json, "animation");
        Identifier stateMachine = id(json, "state_machine");
        boolean enabled = !json.has("enabled") || json.get("enabled").getAsBoolean();
        if (display == null && gun == null) {
            throw new IllegalArgumentException("an item override needs either 'display' or 'gun'");
        }
        java.util.Map<String, String> actions = new java.util.HashMap<>();
        if (json.has("actions")) {
            for (var entry : json.getAsJsonObject("actions").entrySet()) {
                if (!java.util.Set.of("idle", "draw", "put_away", "attack", "mine", "use", "inspect", "reload",
                        "run_start", "run", "run_hold", "run_end").contains(entry.getKey())) {
                    throw new IllegalArgumentException("unknown local action: " + entry.getKey());
                }
                actions.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        return new ClientItemOverride(item, display, gun, animation, stateMachine, enabled, java.util.Map.copyOf(actions));
    }

    public String clip(String action, String fallback) {
        if (action.equals("mine") && !actions.containsKey("mine")) {
            return actions.getOrDefault("attack", fallback);
        }
        return actions.getOrDefault(action, fallback);
    }

    private static Identifier id(com.google.gson.JsonObject json, String name) {
        if (!json.has(name) || json.get(name).isJsonNull()) {
            return null;
        }
        return Identifier.tryParse(json.get(name).getAsString());
    }
}
