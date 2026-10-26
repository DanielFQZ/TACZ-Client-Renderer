package com.tacz.client.input;

import com.tacz.client.config.ClientItemOverrideRegistry;
import com.tacz.client.render.TaczGunRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** Local-only action state. Never changes inventory selection or sends packets. */
public final class ClientActionController {
    private static final ItemAnimationTimeline<ItemStack> TIMELINE = new ItemAnimationTimeline<>(
            (a, b) -> a.isEmpty() ? b.isEmpty() : !b.isEmpty() && a.getItem() == b.getItem(),
            stack -> ClientItemOverrideRegistry.INSTANCE.get(stack).isPresent(),
            TaczGunRenderer::actionDurationTicks);
    private static boolean wasSwinging;
    private static int lastSwingTime;
    private static boolean wasUsing;
    private static Object lastLevel;

    private ClientActionController() {}

    public static void tick(Minecraft client) {
        var player = client.player;
        if (player == null || client.level != lastLevel) {
            reset();
            lastLevel = client.level;
            if (player == null) return;
        }
        TIMELINE.tick(player.getMainHandItem().copy());
        boolean swing = player.swinging && player.swingingArm == InteractionHand.MAIN_HAND;
        if (swing && (!wasSwinging || player.swingTime < lastSwingTime)) {
            boolean use = client.options.keyUse.isDown() && !client.options.keyAttack.isDown();
            boolean mining = client.gameMode != null && (client.gameMode.isDestroying()
                    || client.options.keyAttack.isDown() && client.hitResult != null
                    && client.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK);
            // Instant tool uses (strip, till, make paths) swing but do not set isUsingItem.
            begin(use ? Action.USE : mining ? Action.MINE : Action.ATTACK);
        }
        boolean using = player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND;
        if (using && !wasUsing) begin(Action.USE);
        wasSwinging = swing;
        lastSwingTime = player.swingTime;
        wasUsing = using;
    }

    public static void begin(Action requested) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack shown = visualItem(ItemStack.EMPTY);
        // Do not animate the departing weapon in response to the newly selected item.
        if (shown.isEmpty() || shown.getItem() != player.getMainHandItem().getItem()) return;
        TIMELINE.trigger(requested);
    }

    public static ItemStack visualItem(ItemStack vanillaStack) { return TIMELINE.shownOr(vanillaStack); }
    public static Action action() { return TIMELINE.action(); }
    public static float progress(float tickDelta) { return TIMELINE.progress(tickDelta); }
    public static void reset() {
        TIMELINE.reset();
        wasSwinging = false;
        lastSwingTime = 0;
        wasUsing = false;
        lastLevel = null;
    }

    public enum Action {
        IDLE("idle", "static_idle", 1),
        DRAW("draw", "draw", 10),
        PUT_AWAY("put_away", "put_away", 8),
        ATTACK("attack", "shoot", 10),
        MINE("mine", "shoot", 10),
        USE("use", "", 10),
        INSPECT("inspect", "inspect", 40),
        RELOAD("reload", "reload_tactical", 40);

        public final String key;
        public final String defaultClip;
        public final int defaultTicks;
        Action(String key, String defaultClip, int defaultTicks) {
            this.key = key; this.defaultClip = defaultClip; this.defaultTicks = defaultTicks;
        }
    }
}
