package com.tacz.client.render;

import com.google.gson.JsonParser;
import com.tacz.client.config.ClientItemOverride;
import com.tacz.client.input.ClientActionController.Action;
import com.tacz.client.input.ItemAnimationTimeline;
import net.minecraft.resources.Identifier;

final class ItemAnimationRegressionTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void run() {
        var timeline = new ItemAnimationTimeline<String>(String::equals,
                item -> item.equals("gun") || item.equals("sword") || item.equals("pickaxe"),
                (item, action) -> action == Action.USE ? 0 : 3);
        timeline.tick("empty");
        timeline.tick("gun");
        check(timeline.action() == Action.DRAW, "empty -> gun draws");
        for (int i = 0; i < 3; i++) timeline.tick("gun");
        check(timeline.action() == Action.IDLE, "draw completes");
        check(timeline.trigger(Action.ATTACK), "attack starts");
        timeline.tick("empty");
        check(timeline.action() == Action.PUT_AWAY, "selection change interrupts attack with put-away");
        check(timeline.shownOr("").equals("gun"), "retain departing gun after actual slot is empty");
        check(!timeline.trigger(Action.INSPECT), "cannot interrupt put-away with input");
        timeline.tick("sword");
        timeline.tick("pickaxe");
        check(timeline.shownOr("").equals("gun"), "rapid selection changes do not truncate put-away");
        timeline.tick("sword");
        check(timeline.shownOr("").equals("sword") && timeline.action() == Action.DRAW, "draw latest target only");
        for (int i = 0; i < 3; i++) timeline.tick("sword");
        check(!timeline.trigger(Action.USE), "undefined use action is ignored");
        timeline.tick("empty");
        timeline.tick("sword");
        check(timeline.action() == Action.DRAW, "switching back recovers displayed item");
        timeline.tick("empty");
        for (int i = 0; i < 3; i++) timeline.tick("empty");
        check(timeline.action() == Action.IDLE && timeline.shownOr("").equals("empty"), "put-away completes to empty");
        timeline.tick("stone");
        check(timeline.action() == Action.IDLE && timeline.shownOr("").equals("stone"), "unmapped item is not animated");
        timeline.reset();
        check(timeline.shownOr("fallback").equals("fallback"), "disconnect/reload clears retained item");

        record Tool(String kind, int damage) {}
        var durable = new ItemAnimationTimeline<Tool>((a,b) -> a.kind.equals(b.kind), item -> true, (item, action) -> 2);
        durable.tick(new Tool("pickaxe", 0));
        durable.tick(new Tool("pickaxe", 1));
        durable.tick(new Tool("pickaxe", 2));
        check(durable.action() == Action.IDLE, "durability changes never restart draw or put-away");

        var mapping = ClientItemOverride.fromJson(Identifier.parse("minecraft:diamond_pickaxe"), JsonParser.parseString("""
                {"display":"tacz_tools:diamond_pickaxe_display", "actions":{"attack":"swing","mine":"dig","use":""}}
                """).getAsJsonObject());
        check(mapping.clip("attack", "shoot").equals("swing"), "tool action mapping");
        check(mapping.clip("mine", "shoot").equals("dig"), "mining action mapping");
        check(mapping.clip("use", "use").isEmpty(), "explicitly disabled clip");
        var gun = ClientItemOverride.fromJson(Identifier.parse("minecraft:crossbow"), JsonParser.parseString("""
                {"gun":"tacz:ak47"}
                """).getAsJsonObject());
        check(gun.clip("attack", "shoot").equals("shoot"), "existing gun mappings remain compatible");
        System.out.println("PASS: put-away, rapid switching, switch-back, input guards, reload reset, durability and tool mappings");
    }
}
