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
        sprintChecks();
        System.out.println("PASS: put-away, rapid switching, switch-back, input guards, reload reset, durability and tool mappings");
    }

    private static void sprintChecks() {
        var t = new ItemAnimationTimeline<String>(String::equals, "gun"::equals, (item, action) -> 3);
        t.tick("gun", true, true);
        check(t.action() == Action.DRAW, "sprint must not interrupt equip");
        for (int i = 0; i < 3; i++) t.tick("gun", true, true);
        check(t.action() == Action.RUN_START, "held sprint starts after draw");
        for (int i = 0; i < 3; i++) t.tick("gun", true, true);
        check(t.action() == Action.RUN, "run_start enters run loop");
        for (int i = 0; i < 10; i++) t.tick("gun", true, true);
        check(t.action() == Action.RUN && Math.abs(t.progress(0.5f) - 0.5f) < 0.001f,
                "run loops across multiple cycles with interpolated progress");
        t.tick("gun", true, false);
        check(t.action() == Action.RUN_HOLD, "airborne sprint holds gun");
        t.tick("gun", true, true);
        check(t.action() == Action.RUN, "landing resumes run");
        t.tick("gun", false, true);
        check(t.action() == Action.RUN_END && t.blend(0) == 0, "stopping plays blended run_end");
        t.tick("gun", true, true);
        check(t.action() == Action.RUN_START, "quick restart interrupts run_end");
        check(t.trigger(Action.ATTACK), "attack interrupts locomotion");
        for (int i = 0; i < 3; i++) t.tick("gun", true, true);
        check(t.action() == Action.RUN_START, "sprint resumes after attack");
        t.tick("empty", true, true);
        check(t.action() == Action.PUT_AWAY, "switching while running retains put-away priority");
        for (int i = 0; i < 4; i++) t.tick("empty", true, true);
        check(t.action() == Action.IDLE, "unmapped selection cannot run");
        t.reset();
        check(t.previousAction() == Action.IDLE && t.blend(0) == 1, "reload clears movement blend");

        var absent = new ItemAnimationTimeline<String>(String::equals, "gun"::equals,
                (item, action) -> action.movement() ? 0 : 1);
        for (int i = 0; i < 20; i++) absent.tick("gun", true, true);
        check(absent.action() == Action.IDLE, "missing/disabled run does not enter movement states");
        var loopOnly = new ItemAnimationTimeline<String>(String::equals, "gun"::equals,
                (item, action) -> action.movement() && action != Action.RUN ? 0 : 2);
        for (int i = 0; i < 3; i++) loopOnly.tick("gun", true, false);
        check(loopOnly.action() == Action.RUN, "run works without optional start or airborne clips");
        loopOnly.tick("gun", false, true);
        check(loopOnly.action() == Action.IDLE, "missing run_end returns to idle");
        var mapping = ClientItemOverride.fromJson(Identifier.parse("minecraft:crossbow"), JsonParser.parseString("""
                {"gun":"tacz:ak47","actions":{"run":"","run_start":"raise","run_end":"lower","run_hold":"hold"}}
                """).getAsJsonObject());
        check(mapping.clip("run", "run").isEmpty(), "run can be disabled explicitly");
        System.out.println("PASS: sprint start/loop/stop, airborne, interruption, missing clips and reset");
    }
}
