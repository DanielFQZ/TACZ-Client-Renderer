package com.tacz.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.client.config.ClientItemOverrideRegistry;
import com.tacz.client.config.ClientItemOverride;
import com.tacz.client.input.ClientActionController;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Small client-side Bedrock geometry bridge. It deliberately consumes the same
 * display and geo_models JSON used by TACZ packs; no item or gun is registered.
 */
public final class TaczGunRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("taczclient");
    private static final Map<Identifier, Model> MODELS = new HashMap<>();
    private static final Map<Identifier, Animation> ANIMATIONS = new HashMap<>();
    private static final Map<Identifier, RenderType> RENDER_TYPES = new HashMap<>();
    private static ResourceManager resources;

    private TaczGunRenderer() {
    }

    public static synchronized void reload(ResourceManager manager) {
        resources = manager;
        MODELS.clear();
        ANIMATIONS.clear();
        RENDER_TYPES.clear();
    }

    public static boolean render(ItemStack stack, PoseStack poseStack, SubmitNodeCollector collector,
                                 int light, float tickDelta) {
        Optional<JsonObject> display = ClientItemOverrideRegistry.INSTANCE.display(stack);
        if (display.isEmpty()) {
            return false;
        }
        JsonObject json = display.get();
        Identifier modelId = id(json, "model");
        Identifier textureId = id(json, "texture");
        if (modelId == null || textureId == null || resources == null) {
            return false;
        }
        Model model = MODELS.computeIfAbsent(modelId, TaczGunRenderer::loadModel);
        if (model == null) {
            return false;
        }
        RenderType renderType = RENDER_TYPES.computeIfAbsent(textureId, TaczGunRenderer::createRenderType);
        ClientActionController.Action action = ClientActionController.action();
        Animation definition = definition(stack, json);
        AnimationClip base = definition == null ? null : definition.clip(clipName(stack, ClientActionController.Action.IDLE));
        AnimationClip selected = definition == null || action == ClientActionController.Action.IDLE
                ? null : definition.clip(clipName(stack, action));
        // TACZ keeps static_idle on a base track. Shoot is an additive track;
        // draw/reload/inspect replace only the channels they actually animate.
        AnimationClip animation = layer(stack, selected, base, action);
        float animationTime = selected == null ? 0 : ClientActionController.progress(tickDelta) * selected.length;
        if (definition != null && animation != null && ClientActionController.blend(tickDelta) < 1
                && !(action == ClientActionController.Action.PUT_AWAY
                && ClientActionController.previousAction().movement())) {
            var previousAction = ClientActionController.previousAction();
            AnimationClip previous = previousAction == ClientActionController.Action.IDLE
                    ? null : definition.clip(clipName(stack, previousAction));
            float previousTime = previous == null ? 0 : ClientActionController.previousProgress() * previous.length;
            animation = animation.blendFrom(layer(stack, previous, base, previousAction), previousTime,
                    ClientActionController.blend(tickDelta));
        }
        // TACZ keeps the movement track alive while the main track plays put_away.
        // Keep that track additive so a sprinting gun lowers without snapping out of
        // its running pose. The main put-away animation remains the authoritative
        // transition and is sampled as the overlay base at the current time.
        if (definition != null && animation != null && action == ClientActionController.Action.PUT_AWAY
                && ClientActionController.previousAction().movement()) {
            AnimationClip movement = definition.clip(clipName(stack, ClientActionController.previousAction()));
            if (movement != null) {
                float movementTime = (ClientActionController.previousProgress() * movement.length
                        + animationTime) % movement.length;
                animation = movement.withBaseAt(animation, true, animationTime);
                animationTime = movementTime;
            }
        }
        float scale = firstPersonScale(json);
        poseStack.pushPose();
        // Convert TACZ's Y-down model space to the first-person camera space,
        // then place the pack's idle_view locator at the eye (all units are pixels).
        poseStack.scale(scale / 16.0F, scale / 16.0F, scale / 16.0F);
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(180.0F));
        model.applyView(poseStack);
        if (selected == null && (action == ClientActionController.Action.DRAW
                || action == ClientActionController.Action.PUT_AWAY)) {
            float amount = action == ClientActionController.Action.DRAW
                    ? 1 - ClientActionController.progress(tickDelta) : ClientActionController.progress(tickDelta);
            // Y points down here. Apply to the gun and its skin arms together.
            poseStack.translate(0, 12 * amount, 0);
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-25 * amount));
        }
        AnimationClip activeAnimation = animation;
        float activeAnimationTime = animationTime;
        collector.submitCustomGeometry(poseStack, renderType,
                (pose, vertices) -> model.emit(pose, vertices, light, activeAnimation, activeAnimationTime));
        model.submitHands(poseStack, collector, light, activeAnimation, activeAnimationTime);
        poseStack.popPose();
        return true;
    }

    private static String clipName(ItemStack stack, ClientActionController.Action action) {
        return ClientItemOverrideRegistry.INSTANCE.get(stack)
                .map(mapping -> mapping.clip(action.key, action.defaultClip)).orElse(action.defaultClip);
    }

    private static AnimationClip layer(ItemStack stack, AnimationClip clip, AnimationClip base,
                                       ClientActionController.Action action) {
        return clip == null ? base : clip.withBase(base, action.movement()
                || (action == ClientActionController.Action.ATTACK || action == ClientActionController.Action.MINE)
                && clipName(stack, action).equals("shoot"));
    }

    private static Animation definition(ItemStack stack, JsonObject display) {
        Identifier animation = ClientItemOverrideRegistry.INSTANCE.get(stack).map(ClientItemOverride::animation)
                .orElseGet(() -> id(display, "animation"));
        if (resources == null) return null;
        Animation primary = animation == null ? null : ANIMATIONS.computeIfAbsent(animation, TaczGunRenderer::loadAnimation);
        Identifier fallbackId = defaultAnimationId(display);
        Animation fallback = fallbackId == null ? null : ANIMATIONS.computeIfAbsent(fallbackId, TaczGunRenderer::loadAnimation);
        return primary == null ? fallback : new Animation(primary.clips, fallback);
    }

    static Identifier defaultAnimationId(JsonObject display) {
        Identifier explicit = id(display, "default_animation");
        if (explicit != null) return explicit;
        String type = display.has("use_default_animation") && !display.get("use_default_animation").isJsonNull()
                ? display.get("use_default_animation").getAsString() : "";
        return type.equals("rifle") || type.equals("pistol")
                ? Identifier.fromNamespaceAndPath("tacz", type + "_default") : null;
    }

    /** Resolve before the action starts, even when the item is no longer held. */
    public static int actionDurationTicks(ItemStack stack, ClientActionController.Action action) {
        if (action == ClientActionController.Action.IDLE) return 1;
        Optional<JsonObject> display = ClientItemOverrideRegistry.INSTANCE.display(stack);
        Animation definition = display.isEmpty() ? null : definition(stack, display.get());
        AnimationClip clip = definition == null ? null : definition.clip(clipName(stack, action));
        if (clip != null && Float.isFinite(clip.length) && clip.length > 0) {
            return Math.max(1, (int) Math.ceil(clip.length * 20));
        }
        return action == ClientActionController.Action.DRAW || action == ClientActionController.Action.PUT_AWAY
                ? action.defaultTicks : 0;
    }

    private static RenderType createRenderType(Identifier texture) {
        return RenderTypes.entityCutout(textureFile(texture));
    }

    static Identifier textureFile(Identifier texture) {
        return Identifier.fromNamespaceAndPath(texture.getNamespace(),
                "textures/" + texture.getPath() + ".png");
    }

    private static Model loadModel(Identifier modelId) {
        Identifier file = Identifier.fromNamespaceAndPath(modelId.getNamespace(),
                "geo_models/" + modelId.getPath() + ".json");
        try {
            Optional<Resource> resource = resources.getResource(file);
            if (resource.isEmpty()) {
                LOGGER.warn("TACZ model {} was selected but {} is missing", modelId, file);
                return null;
            }
            try (Reader reader = resource.get().openAsReader()) {
                JsonObject root = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                JsonArray geometries = root.getAsJsonArray("minecraft:geometry");
                if (geometries == null || geometries.isEmpty()) {
                    return null;
                }
                JsonObject geometry = geometries.get(0).getAsJsonObject();
                JsonObject description = geometry.getAsJsonObject("description");
                int width = description != null && description.has("texture_width")
                        ? description.get("texture_width").getAsInt() : 64;
                int height = description != null && description.has("texture_height")
                        ? description.get("texture_height").getAsInt() : 64;
                List<Bone> bones = parseBones(geometry.getAsJsonArray("bones"), null, width, height);
                return new Model(bones);
            }
        } catch (Exception e) {
            LOGGER.warn("Unable to load TACZ Bedrock model {}", modelId, e);
            return null;
        }
    }

    private static Animation loadAnimation(Identifier animationId) {
        Identifier file = Identifier.fromNamespaceAndPath(animationId.getNamespace(),
                "animations/" + animationId.getPath() + ".animation.json");
        try {
            Optional<Resource> resource = resources.getResource(file);
            if (resource.isEmpty()) {
                LOGGER.warn("TACZ animation {} is missing; supply {} in a resource pack", animationId, file);
                return new Animation(Map.of());
            }
            try (Reader reader = resource.get().openAsReader()) {
                JsonObject root = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                JsonObject animations = root.getAsJsonObject("animations");
                if (animations == null) {
                    return new Animation(Map.of());
                }
                Map<String, AnimationClip> clips = new HashMap<>();
                for (Map.Entry<String, JsonElement> animationEntry : animations.entrySet()) {
                    if (!animationEntry.getValue().isJsonObject()) {
                        continue;
                    }
                    JsonObject clipJson = animationEntry.getValue().getAsJsonObject();
                    JsonObject boneObject = clipJson.getAsJsonObject("bones");
                    if (boneObject == null) {
                        continue;
                    }
                    Map<String, BoneAnimation> bones = new HashMap<>();
                    for (Map.Entry<String, JsonElement> entry : boneObject.entrySet()) {
                        if (entry.getValue().isJsonObject()) {
                            bones.put(entry.getKey(), BoneAnimation.parse(entry.getValue().getAsJsonObject()));
                        }
                    }
                    float length = clipJson.has("animation_length")
                            ? clipJson.get("animation_length").getAsFloat() : 0.5F;
                    clips.put(animationEntry.getKey(), new AnimationClip(length, bones));
                }
                return new Animation(clips);
            }
        } catch (Exception e) {
            LOGGER.warn("Unable to load TACZ animation {}", animationId, e);
            return new Animation(Map.of());
        }
    }

    private static List<Bone> parseBones(JsonArray array, Bone parent, int width, int height) {
        List<Bone> result = new ArrayList<>();
        if (array == null) {
            return result;
        }
        Map<String, Bone> byName = new HashMap<>();
        for (JsonElement element : array) {
            JsonObject json = element.getAsJsonObject();
            Bone bone = new Bone(json.get("name").getAsString(), parent,
                    vector(json, "pivot"), vector(json, "rotation"), width, height,
                    parseCubes(json.getAsJsonArray("cubes"), vector(json, "pivot"), width, height));
            byName.put(bone.name, bone);
            result.add(bone);
        }
        // Bedrock lists bones flat; connect parents after all entries exist.
        for (Bone bone : result) {
            JsonObject json = array.get(result.indexOf(bone)).getAsJsonObject();
            if (json.has("parent")) {
                Bone actualParent = byName.get(json.get("parent").getAsString());
                if (actualParent != null) {
                    bone.parent = actualParent;
                    actualParent.children.add(bone);
                }
            }
        }
        result.removeIf(bone -> bone.parent != parent);
        return result;
    }

    private static List<Cube> parseCubes(JsonArray array, float[] bonePivot, int width, int height) {
        List<Cube> result = new ArrayList<>();
        if (array == null) {
            return result;
        }
        for (JsonElement element : array) {
            JsonObject json = element.getAsJsonObject();
            float[] origin = vector(json, "origin"), size = vector(json, "size");
            float[] pivot = json.has("rotation") ? vector(json, "pivot") : bonePivot;
            float[] localOrigin = {origin[0] - pivot[0], pivot[1] - origin[1] - size[1], origin[2] - pivot[2]};
            float[] localPivot = {pivot[0] - bonePivot[0], bonePivot[1] - pivot[1], pivot[2] - bonePivot[2]};
            result.add(new Cube(localOrigin, size,
                    json.has("inflate") ? json.get("inflate").getAsFloat() : 0.0F,
                    localPivot, vector(json, "rotation"), json.getAsJsonObject("uv"), width, height));
        }
        return result;
    }

    private static float[] vector(JsonObject json, String key) {
        JsonArray array = json.has(key) ? json.getAsJsonArray(key) : null;
        if (array == null || array.size() < 3) {
            return new float[]{0.0F, 0.0F, 0.0F};
        }
        return new float[]{array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat()};
    }

    private static Identifier id(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull()
                ? Identifier.tryParse(json.get(key).getAsString()) : null;
    }

    private static float firstPersonScale(JsonObject display) {
        JsonObject transform = display.getAsJsonObject("transform");
        JsonObject scale = transform == null ? null : transform.getAsJsonObject("scale");
        JsonArray firstPerson = scale == null ? null : scale.getAsJsonArray("firstperson");
        return firstPerson == null || firstPerson.isEmpty() ? 1.0F : firstPerson.get(0).getAsFloat();
    }

    private static final class Model {
        private final List<Bone> roots;

        private Model(List<Bone> roots) {
            this.roots = roots;
        }

        private Bone find(String name) {
            for (Bone root : roots) {
                Bone result = find(root, name);
                if (result != null) return result;
            }
            return null;
        }

        private Bone find(Bone bone, String name) {
            if (bone.name.equals(name)) return bone;
            for (Bone child : bone.children) {
                Bone result = find(child, name);
                if (result != null) return result;
            }
            return null;
        }

        private void applyView(PoseStack stack) {
            Bone view = find("idle_view");
            if (view == null) view = find("camera");
            if (view != null) {
                PoseStack locator = new PoseStack();
                view.applyPath(locator, null, 0);
                stack.mulPose(new org.joml.Matrix4f(locator.last().pose()).invert());
            }
        }

        private void submitHands(PoseStack stack, SubmitNodeCollector collector, int light,
                                 AnimationClip animation, float time) {
            var client = Minecraft.getInstance();
            var player = client.player;
            if (player == null || player.isInvisible()) return;
            var renderer = client.getEntityRenderDispatcher().getPlayerRenderer(player);
            var skin = player.getSkin().body().texturePath();
            for (boolean left : new boolean[]{true, false}) {
                Bone hand = find(left ? "lefthand_pos" : "righthand_pos");
                if (hand == null) continue;
                stack.pushPose();
                hand.applyPath(stack, animation, time);
                stack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(180));
                // Vanilla arms use blocks; the surrounding Bedrock transforms use pixels.
                stack.scale(16, 16, 16);
                if (left) {
                    renderer.renderLeftHand(stack, collector, light, skin,
                            player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE));
                } else {
                    renderer.renderRightHand(stack, collector, light, skin,
                            player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE));
                }
                stack.popPose();
            }
        }

        private void emit(PoseStack.Pose pose, VertexConsumer vertices, int light,
                          AnimationClip animation, float animationTime) {
            for (Bone root : roots) {
                root.emit(pose, vertices, light, animation, animationTime);
            }
        }
    }

    private static final class Bone {
        private final String name;
        private Bone parent;
        private final float[] pivot;
        private final float[] rotation;
        private final List<Cube> cubes;
        private final List<Bone> children = new ArrayList<>();

        private Bone(String name, Bone parent, float[] pivot, float[] rotation, int width, int height,
                     List<Cube> cubes) {
            this.name = name;
            this.parent = parent;
            this.pivot = pivot;
            this.rotation = rotation;
            this.cubes = cubes;
        }

        private void applyRest(PoseStack stack) {
            float[] base = parent == null ? new float[3] : parent.pivot;
            stack.translate(pivot[0] - base[0], base[1] - pivot[1], pivot[2] - base[2]);
            stack.mulPose(rotation(rotation));
        }

        private void apply(PoseStack stack, AnimationClip animation, float time) {
            BoneTransform transform = animation == null ? null : animation.transform(name, time);
            if (transform != null) {
                stack.translate(transform.position[0], -transform.position[1], transform.position[2]);
            }
            applyRest(stack);
            if (transform != null) {
                stack.mulPose(rotation(transform.rotation));
                stack.scale(transform.scale[0], transform.scale[1], transform.scale[2]);
            }
        }

        private void applyPath(PoseStack stack, AnimationClip animation, float time) {
            if (parent != null) parent.applyPath(stack, animation, time);
            apply(stack, animation, time);
        }

        private boolean hiddenWithoutAttachments() {
            return name.equals("attachment_adapter") || name.equals("mount")
                    || name.equals("sight_folded") || name.equals("handguard_tactical")
                    || name.matches("mag_extended_[123]");
        }

        private void emit(PoseStack.Pose parentPose, VertexConsumer vertices, int light,
                          AnimationClip animation, float animationTime) {
            if (hiddenWithoutAttachments()) return;
            PoseStack stack = new PoseStack();
            stack.last().set(parentPose);
            apply(stack, animation, animationTime);
            PoseStack.Pose pose = stack.last();
            // These cubes are editor placeholders. TACZ replaces them with player arms.
            if (!name.equals("lefthand_pos") && !name.equals("righthand_pos")) {
                for (Cube cube : cubes) cube.emit(pose, vertices, light);
            }
            for (Bone child : children) {
                child.emit(pose, vertices, light, animation, animationTime);
            }
        }
    }

    private static Quaternionf rotation(float[] degrees) {
        return new Quaternionf().rotationZYX((float) Math.toRadians(degrees[2]),
                (float) Math.toRadians(degrees[1]), (float) Math.toRadians(degrees[0]));
    }

    private record Animation(Map<String, AnimationClip> clips, Animation fallback) {
        private Animation(Map<String, AnimationClip> clips) { this(clips, null); }
        private AnimationClip clip(String name) {
            // An empty action explicitly disables it, including fallback lookup.
            if (name.isEmpty()) return null;
            AnimationClip clip = clips.get(name);
            return clip != null || fallback == null ? clip : fallback.clip(name);
        }
    }

    private record AnimationClip(float length, Map<String, BoneAnimation> bones,
                                 AnimationClip base, boolean additive,
                                 float baseTime,
                                 AnimationClip previous, float previousTime, float weight) {
        private AnimationClip(float length, Map<String, BoneAnimation> bones) {
            this(length, bones, null, false, 0, null, 0, 1);
        }

        private AnimationClip withBase(AnimationClip base, boolean additive) {
            return new AnimationClip(length, bones, base, additive, 0, null, 0, 1);
        }

        private AnimationClip withBaseAt(AnimationClip base, boolean additive, float baseTime) {
            return new AnimationClip(length, bones, base, additive, baseTime, null, 0, 1);
        }

        private AnimationClip blendFrom(AnimationClip previous, float time, float weight) {
            return new AnimationClip(length, bones, base, additive, baseTime, previous, time, weight);
        }

        private BoneTransform transform(String bone, float time) {
            BoneAnimation animation = bones.get(bone);
            BoneTransform rest = base == null ? null : base.transform(bone, baseTime);
            BoneTransform result = animation == null ? rest : animation.sample(time, rest, additive);
            if (weight >= 1) return result;
            BoneTransform from = previous == null ? null : previous.transform(bone, previousTime);
            if (from == null && result == null) return null;
            if (from == null) from = BoneTransform.identity();
            if (result == null) result = BoneTransform.identity();
            return new BoneTransform(mix(from.rotation, result.rotation, weight),
                    mix(from.position, result.position, weight), mix(from.scale, result.scale, weight));
        }

        private static float[] mix(float[] from, float[] to, float weight) {
            return new float[]{from[0] + (to[0] - from[0]) * weight,
                    from[1] + (to[1] - from[1]) * weight, from[2] + (to[2] - from[2]) * weight};
        }
    }

    private record BoneAnimation(Channel rotation, Channel position, Channel scale) {
        private static BoneAnimation parse(JsonObject json) {
            return new BoneAnimation(Channel.parse(json.get("rotation"), new float[]{0, 0, 0}),
                    Channel.parse(json.get("position"), new float[]{0, 0, 0}),
                    Channel.parse(json.get("scale"), new float[]{1, 1, 1}));
        }

        private BoneTransform sample(float time, BoneTransform base, boolean additive) {
            float[] baseRotation = base == null ? new float[]{0, 0, 0} : base.rotation;
            float[] basePosition = base == null ? new float[]{0, 0, 0} : base.position;
            float[] baseScale = base == null ? new float[]{1, 1, 1} : base.scale;
            return new BoneTransform(combine(rotation, time, baseRotation, additive, false),
                    combine(position, time, basePosition, additive, false),
                    combine(scale, time, baseScale, additive, true));
        }

        private static float[] combine(Channel channel, float time, float[] base, boolean additive, boolean scale) {
            if (channel == null) return base.clone();
            float[] sampled = channel.sample(time);
            if (additive) for (int i = 0; i < 3; i++) sampled[i] = scale ? sampled[i] * base[i] : sampled[i] + base[i];
            return sampled;
        }
    }

    private record BoneTransform(float[] rotation, float[] position, float[] scale) {
        private static BoneTransform identity() {
            return new BoneTransform(new float[3], new float[3], new float[]{1, 1, 1});
        }
    }

    private static final class Channel {
        private final float[] constant;
        private final NavigableMap<Float, float[]> keyframes;

        private Channel(float[] constant, NavigableMap<Float, float[]> keyframes) {
            this.constant = constant;
            this.keyframes = keyframes;
        }

        private static Channel parse(JsonElement element, float[] fallback) {
            if (element == null || element.isJsonNull()) {
                return null;
            }
            if (element.isJsonArray() || element.isJsonPrimitive()) {
                return new Channel(value(element, fallback), new TreeMap<>());
            }
            NavigableMap<Float, float[]> frames = new TreeMap<>();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                try {
                    float time = Float.parseFloat(entry.getKey());
                    frames.put(time, value(entry.getValue(), fallback));
                } catch (NumberFormatException ignored) {
                    // Metadata such as lerp_mode is not a keyframe.
                }
            }
            return new Channel(frames.isEmpty() ? fallback : null, frames);
        }

        private float[] sample(float time) {
            if (keyframes.isEmpty()) {
                return constant.clone();
            }
            Map.Entry<Float, float[]> floor = keyframes.floorEntry(time);
            Map.Entry<Float, float[]> ceiling = keyframes.ceilingEntry(time);
            if (floor == null) {
                return keyframes.firstEntry().getValue().clone();
            }
            if (ceiling == null || ceiling.getKey().equals(floor.getKey())) {
                return floor.getValue().clone();
            }
            float alpha = (time - floor.getKey()) / (ceiling.getKey() - floor.getKey());
            float[] result = new float[3];
            for (int i = 0; i < 3; i++) {
                result[i] = floor.getValue()[i] + (ceiling.getValue()[i] - floor.getValue()[i]) * alpha;
            }
            return result;
        }

        private static float[] value(JsonElement element, float[] fallback) {
            if (element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                if (object.has("post")) {
                    return value(object.get("post"), fallback);
                }
                if (object.has("pre")) {
                    return value(object.get("pre"), fallback);
                }
                return fallback.clone();
            }
            if (element.isJsonPrimitive()) {
                float scalar = element.getAsFloat();
                return new float[]{scalar, scalar, scalar};
            }
            JsonArray array = element.getAsJsonArray();
            float x = array.size() > 0 ? array.get(0).getAsFloat() : fallback[0];
            float y = array.size() > 1 ? array.get(1).getAsFloat() : fallback[1];
            float z = array.size() > 2 ? array.get(2).getAsFloat() : fallback[2];
            return new float[]{x, y, z};
        }
    }

    private record Cube(float[] origin, float[] size, float inflate, float[] pivot, float[] rotation,
                        JsonObject uv, int textureWidth, int textureHeight) {
        private void emit(PoseStack.Pose pose, VertexConsumer vertices, int light) {
            PoseStack rotated = new PoseStack();
            rotated.last().set(pose);
            rotated.translate(pivot[0], pivot[1], pivot[2]);
            rotated.mulPose(TaczGunRenderer.rotation(rotation));
            pose = rotated.last();
            float x0 = origin[0] - inflate, y0 = origin[1] - inflate, z0 = origin[2] - inflate;
            float x1 = origin[0] + size[0] + inflate, y1 = origin[1] + size[1] + inflate,
                    z1 = origin[2] + size[2] + inflate;
            // Same corner order and Bedrock face mapping as TACZ's BedrockCubePerFace.
            emitFace(pose, vertices, light, new float[][]{{x1,y0,z0},{x0,y0,z0},{x0,y1,z0},{x1,y1,z0}}, "north");
            emitFace(pose, vertices, light, new float[][]{{x0,y0,z1},{x1,y0,z1},{x1,y1,z1},{x0,y1,z1}}, "south");
            emitFace(pose, vertices, light, new float[][]{{x0,y0,z0},{x0,y0,z1},{x0,y1,z1},{x0,y1,z0}}, "east");
            emitFace(pose, vertices, light, new float[][]{{x1,y0,z1},{x1,y0,z0},{x1,y1,z0},{x1,y1,z1}}, "west");
            emitFace(pose, vertices, light, new float[][]{{x1,y0,z1},{x0,y0,z1},{x0,y0,z0},{x1,y0,z0}}, "up");
            emitFace(pose, vertices, light, new float[][]{{x1,y1,z0},{x0,y1,z0},{x0,y1,z1},{x1,y1,z1}}, "down");
        }

        private void emitFace(PoseStack.Pose pose, VertexConsumer vertices, int light, float[][] points, String face) {
            JsonObject side = uv == null ? null : uv.getAsJsonObject(face);
            if (side == null || !side.has("uv")) {
                // TACZ/Bedrock omits faces that should not be submitted. Drawing
                // a fallback texel here creates visible artifacts on thin parts.
                return;
            }
            float[] uv0 = uv(face, 0.0F, 0.0F), uv1 = uv(face, 1.0F, 1.0F);
            float[][] tex = {{uv1[0], uv0[1]}, {uv0[0], uv0[1]}, {uv0[0], uv1[1]}, {uv1[0], uv1[1]}};
            float[] normal = normal(points);
            for (int i = 0; i < 4; i++) {
                vertices.addVertex(pose, points[i][0], points[i][1], points[i][2])
                        .setColor(255, 255, 255, 255)
                        .setUv(tex[i][0], tex[i][1])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(pose, normal[0], normal[1], normal[2]);
            }
        }

        private float[] uv(String face, float px, float py) {
            JsonObject side = uv == null ? null : uv.getAsJsonObject(face);
            if (side == null || !side.has("uv")) {
                return new float[]{px, py};
            }
            JsonArray base = side.getAsJsonArray("uv");
            JsonArray size = side.has("uv_size") ? side.getAsJsonArray("uv_size") : null;
            float u = base.get(0).getAsFloat() + px * (size == null ? 1.0F : size.get(0).getAsFloat());
            float v = base.get(1).getAsFloat() + py * (size == null ? 1.0F : size.get(1).getAsFloat());
            return new float[]{u / textureWidth, v / textureHeight};
        }

        private static float[] normal(float[][] p) {
            float ax = p[1][0] - p[0][0], ay = p[1][1] - p[0][1], az = p[1][2] - p[0][2];
            float bx = p[2][0] - p[0][0], by = p[2][1] - p[0][1], bz = p[2][2] - p[0][2];
            float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
            float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            return length == 0.0F ? new float[]{0, 1, 0} : new float[]{nx / length, ny / length, nz / length};
        }
    }
}
