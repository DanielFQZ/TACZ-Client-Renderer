package com.tacz.client.render;

import com.google.gson.*;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;

import java.lang.reflect.*;
import java.nio.ByteOrder;
import java.nio.file.*;
import java.util.*;

/** Exercises the real mesh emitter without opening a world or touching saves. */
public final class RendererRegressionTest {
    private static final Class<?> OWNER = TaczGunRenderer.class;
    private static Class<?> nested(String name) throws Exception {
        return Class.forName(OWNER.getName() + "$" + name);
    }
    private static Object call(Object receiver, Class<?> owner, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = owner.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(receiver, args);
    }
    private static Object construct(String name, Class<?>[] types, Object... args) throws Exception {
        Constructor<?> ctor = nested(name).getDeclaredConstructor(types);
        ctor.setAccessible(true);
        return ctor.newInstance(args);
    }
    private static Object model(JsonObject geometry) throws Exception {
        JsonObject desc = geometry.getAsJsonObject("description");
        Object bones = call(null, OWNER, "parseBones",
                new Class<?>[]{JsonArray.class, nested("Bone"), int.class, int.class},
                geometry.getAsJsonArray("bones"), null,
                desc.get("texture_width").getAsInt(), desc.get("texture_height").getAsInt());
        return construct("Model", new Class<?>[]{List.class}, bones);
    }
    private static void near(float actual, float expected) {
        if (!Float.isFinite(actual) || Math.abs(actual - expected) > 0.0001f)
            throw new AssertionError(actual + " != " + expected);
    }
    private static Object clip(JsonObject json) throws Exception {
        Map<String, Object> bones = new HashMap<>();
        for (var entry : json.getAsJsonObject("bones").entrySet()) {
            bones.put(entry.getKey(), call(null, nested("BoneAnimation"), "parse",
                    new Class<?>[]{JsonObject.class}, entry.getValue().getAsJsonObject()));
        }
        return construct("AnimationClip", new Class<?>[]{float.class, Map.class}, 1f, bones);
    }
    private static float[] mesh(Object model, Object clip, float time) throws Exception {
        PoseStack pose = new PoseStack();
        pose.scale(1f / 16, 1f / 16, 1f / 16);
        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(180));
        call(model, nested("Model"), "applyView", new Class<?>[]{PoseStack.class}, pose);
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(1024 * 1024)) {
            BufferBuilder buffer = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.ENTITY);
            call(model, nested("Model"), "emit", new Class<?>[]{PoseStack.Pose.class, VertexConsumer.class,
                    int.class, nested("AnimationClip"), float.class}, pose.last(), buffer, 0xF000F0, clip, time);
            try (MeshData mesh = buffer.buildOrThrow()) {
                var data = mesh.vertexBuffer().order(ByteOrder.nativeOrder());
                int stride = DefaultVertexFormat.ENTITY.getVertexSize();
                int uv = DefaultVertexFormat.ENTITY.getElement("UV0").offset();
                for (int offset = 0; offset < data.remaining(); offset += stride) {
                    for (int axis = 0; axis < 3; axis++) {
                        float value = data.getFloat(offset + axis * 4);
                        if (!Float.isFinite(value) || Math.abs(value) > 100) throw new AssertionError("Invalid model vertex: " + value);
                    }
                }
                return new float[]{data.getFloat(0), data.getFloat(4), data.getFloat(8),
                        data.getFloat(uv), data.getFloat(uv + 4), data.remaining() / stride};
            }
        }
    }
    private static Object layered(Object action, Object idle, boolean additive) throws Exception {
        return call(action, nested("AnimationClip"), "withBase", new Class<?>[]{nested("AnimationClip"), boolean.class}, idle, additive);
    }
    private static float[] channel(Object clip, String bone, String channel, float time) throws Exception {
        Object transform = call(clip, nested("AnimationClip"), "transform", new Class<?>[]{String.class, float.class}, bone, time);
        return (float[]) call(transform, nested("BoneTransform"), channel, new Class<?>[]{});
    }
    private static void same(float[] actual, float[] expected) {
        for (int i = 0; i < 3; i++) near(actual[i], expected[i]);
    }
    public static void main(String[] args) throws Exception {
        ItemAnimationRegressionTest.run();
        Object idleLayer = clip(JsonParser.parseString("""
                {"bones":{"hand":{"rotation":[90,0,10],"position":[1,2,3],"scale":[1,1.5,1]}}}
                """).getAsJsonObject());
        Object partialLayer = clip(JsonParser.parseString("""
                {"bones":{"hand":{"position":[4,5,6]}}}
                """).getAsJsonObject());
        Object replaced = layered(partialLayer, idleLayer, false);
        same(channel(replaced,"hand","rotation",0), new float[]{90,0,10});
        same(channel(replaced,"hand","scale",0), new float[]{1,1.5f,1});
        same(channel(replaced,"hand","position",0), new float[]{4,5,6});
        Object added = layered(partialLayer, idleLayer, true);
        same(channel(added,"hand","position",0), new float[]{5,7,9});
        Object blended = call(added, nested("AnimationClip"), "blendFrom",
                new Class<?>[]{nested("AnimationClip"), float.class, float.class}, idleLayer, 0f, 0.5f);
        same(channel(blended,"hand","position",0), new float[]{3,4.5f,6});
        same(channel(blended,"hand","rotation",0), new float[]{90,0,10});
        Object defaults = construct("Animation", new Class<?>[]{Map.class}, Map.of("run", added, "shoot", added));
        Object primary = construct("Animation", new Class<?>[]{Map.class, nested("Animation")},
                Map.of("shoot", partialLayer), defaults);
        if (call(primary, nested("Animation"), "clip", new Class<?>[]{String.class}, "run") != added
                || call(primary, nested("Animation"), "clip", new Class<?>[]{String.class}, "shoot") != partialLayer
                || call(primary, nested("Animation"), "clip", new Class<?>[]{String.class}, "") != null)
            throw new AssertionError("fallback must fill missing clips only and respect disabled actions");
        if (!TaczGunRenderer.defaultAnimationId(JsonParser.parseString("""
                {"use_default_animation":"rifle"}
                """).getAsJsonObject()).equals(Identifier.parse("tacz:rifle_default"))
                || !TaczGunRenderer.defaultAnimationId(JsonParser.parseString("""
                {"use_default_animation":"rifle","default_animation":"custom:movement"}
                """).getAsJsonObject()).equals(Identifier.parse("custom:movement")))
            throw new AssertionError("TACZ default animation paths and explicit precedence");
        System.out.println("PASS: default animation precedence, missing clip fallback and movement blending");
        System.out.println("PASS: channel-wise idle inheritance and additive recoil");
        if (!TaczGunRenderer.textureFile(Identifier.parse("tacz:gun/uv/ak47")).toString()
                .equals("tacz:textures/gun/uv/ak47.png")) throw new AssertionError("TACZ texture path");
        // A child pivot must remain fixed when scaling its local geometry.
        Object fixture = model(JsonParser.parseString("""
                {"description":{"texture_width":64,"texture_height":64},"bones":[
                  {"name":"root","pivot":[10,20,30]},
                  {"name":"child","parent":"root","pivot":[12,23,34],"cubes":[
                    {"origin":[12,23,34],"size":[2,3,4],"uv":{"north":{"uv":[8,16],"uv_size":[2,3]}}}]},
                  {"name":"views","pivot":[5,6,7]},
                  {"name":"idle_view","parent":"views","pivot":[12,23,40]}
                ]}
                """).getAsJsonObject());
        float[] v = mesh(fixture, null, 0);
        near(v[0], -2f / 16); near(v[1], 3f / 16); near(v[2], -6f / 16);
        near(v[3], 10f / 64); near(v[4], 16f / 64); near(v[5], 4);
        Object scale = clip(JsonParser.parseString("""
                {"bones":{"child":{"scale":[2,3,4]}}}
                """).getAsJsonObject());
        v = mesh(fixture, scale, 0);
        near(v[0], -4f / 16); near(v[1], 9f / 16); near(v[2], -6f / 16);
        // Multi-axis locator inversion, independently checked against its forward transform.
        Object view = call(fixture, nested("Model"), "find", new Class<?>[]{String.class}, "idle_view");
        Field rotation = nested("Bone").getDeclaredField("rotation"); rotation.setAccessible(true);
        float[] angles = (float[]) rotation.get(view); angles[0] = 25; angles[1] = 40; angles[2] = -15;
        PoseStack forward = new PoseStack();
        call(view, nested("Bone"), "applyPath", new Class<?>[]{PoseStack.class, nested("AnimationClip"), float.class}, forward, null, 0f);
        PoseStack inverse = new PoseStack();
        call(fixture, nested("Model"), "applyView", new Class<?>[]{PoseStack.class}, inverse);
        var identity = new org.joml.Matrix4f(inverse.last().pose()).mul(forward.last().pose());
        Vector3f point = identity.transformPosition(new Vector3f(2, 3, 4));
        near(point.x, 2); near(point.y, 3); near(point.z, 4);
        System.out.println("PASS: texture resolution, UV orientation, complete ENTITY vertices, nested pivot scaling and camera inversion");
        if (args.length > 0) {
            Path assets = Path.of(args[0]).resolve("assets/tacz");
            if (!Files.isRegularFile(assets.resolve("textures/gun/uv/ak47.png"))) throw new AssertionError("Missing AK-47 texture");
            JsonObject geo = JsonParser.parseString(Files.readString(assets.resolve("geo_models/gun/ak47_geo.json")))
                    .getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
            Object ak = model(geo);
            JsonObject animations = JsonParser.parseString(Files.readString(assets.resolve("animations/ak47.animation.json")))
                    .getAsJsonObject().getAsJsonObject("animations");
            Object idle = clip(animations.getAsJsonObject("static_idle"));
            Object shoot = layered(clip(animations.getAsJsonObject("shoot")), idle, true);
            for (String hand : new String[]{"lefthand", "righthand"}) {
                for (String name : new String[]{"rotation", "position", "scale"}) {
                    for (float time : new float[]{0, 0.02f, 0.15f, 0.4f, 0.7667f}) {
                        same(channel(shoot, hand, name, time), channel(idle, hand, name, 0));
                    }
                }
            }
            System.out.println("PASS: actual AK-47 hands retain static_idle throughout shoot");
            JsonObject movement = JsonParser.parseString(Files.readString(assets.resolve("animations/rifle_default.animation.json")))
                    .getAsJsonObject().getAsJsonObject("animations");
            for (String name : new String[]{"run_start", "run", "run_hold", "run_end"}) {
                JsonObject json = movement.getAsJsonObject(name);
                Object running = layered(clip(json), idle, true);
                float length = json.get("animation_length").getAsFloat();
                for (int i = 0; i <= 20; i++) {
                    float time = length * i / 20;
                    mesh(ak, running, time);
                    for (String hand : new String[]{"lefthand", "righthand"}) {
                        same(channel(running, hand, "rotation", time), channel(idle, hand, "rotation", 0));
                    }
                }
            }
            System.out.println("PASS: AK-47 with external rifle defaults, 84 sprint mesh samples and preserved hand poses");
            int count = 0;
            for (var entry : animations.entrySet()) {
                JsonObject definition = entry.getValue().getAsJsonObject();
                if (!definition.has("bones")) continue;
                Object animation = layered(clip(definition), idle, entry.getKey().equals("shoot"));
                float length = definition.has("animation_length") ? definition.get("animation_length").getAsFloat() : 1;
                for (float time : new float[]{0, length / 2, length}) { mesh(ak, animation, time); count++; }
            }
            System.out.println("PASS: deployed AK-47 pack, " + count + " animation samples built into real vertex buffers");
        }
        Path tools = Path.of("examples/tacz_vanilla_tools/assets/tacz_tools");
        JsonObject mappings = JsonParser.parseString(Files.readString(tools.resolve("config/client_item_overrides.json")))
                .getAsJsonObject().getAsJsonObject("items");
        JsonObject actions = JsonParser.parseString(Files.readString(tools.resolve("animations/tools.animation.json")))
                .getAsJsonObject().getAsJsonObject("animations");
        if (mappings.size() != 35) throw new AssertionError("Expected all 35 vanilla tools");
        int toolSamples = 0;
        for (var mappingEntry : mappings.entrySet()) {
            var mapping = com.tacz.client.config.ClientItemOverride.fromJson(Identifier.parse(mappingEntry.getKey()),
                    mappingEntry.getValue().getAsJsonObject());
            if (mapping.enabled()) throw new AssertionError("Tool examples must be opt-in");
            JsonObject display = JsonParser.parseString(Files.readString(tools.resolve("display/guns/" + mapping.display().getPath() + ".json"))).getAsJsonObject();
            Identifier modelId = Identifier.parse(display.get("model").getAsString());
            JsonObject geo = JsonParser.parseString(Files.readString(tools.resolve("geo_models/" + modelId.getPath() + ".json")))
                    .getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
            Object tool = model(geo);
            for (String actionName : mapping.actions().values()) {
                if (actionName.isEmpty()) continue;
                JsonObject animation = actions.getAsJsonObject(actionName);
                if (animation == null) throw new AssertionError("Missing tool clip: " + actionName);
                float length = animation.has("animation_length") ? animation.get("animation_length").getAsFloat() : 1;
                Object animated = clip(animation);
                for (float time : new float[]{0, length / 2, length}) { mesh(tool, animated, time); toolSamples++; }
            }
        }
        System.out.println("PASS: all 35 tool mappings and " + toolSamples + " animation mesh samples");
    }
}
