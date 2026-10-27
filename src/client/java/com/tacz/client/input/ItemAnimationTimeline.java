package com.tacz.client.input;

import com.tacz.client.input.ClientActionController.Action;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.ToIntBiFunction;

/** Presentation only: selection always changes immediately in the real inventory. */
public final class ItemAnimationTimeline<T> {
    private final BiPredicate<T, T> same;
    private final Predicate<T> mapped;
    private final ToIntBiFunction<T, Action> duration;
    private T shown;
    private boolean initialized;
    private Action action = Action.IDLE;
    private int elapsed;
    private int length = 1;
    private Action previousAction = Action.IDLE;
    private float previousProgress;
    private int blendElapsed = 4;
    private int blendLength = 4;

    public ItemAnimationTimeline(BiPredicate<T, T> same, Predicate<T> mapped,
                                 ToIntBiFunction<T, Action> duration) {
        this.same = same;
        this.mapped = mapped;
        this.duration = duration;
    }

    public void tick(T selected) {
        tick(selected, false, true);
    }

    public void tick(T selected, boolean sprinting, boolean grounded) {
        if (!initialized) {
            initialized = true;
            show(selected);
            return;
        }
        elapsed++;
        blendElapsed++;
        if (action == Action.PUT_AWAY) {
            if (same.test(shown, selected)) {
                // The user switched back before the old item disappeared.
                shown = selected;
                start(Action.DRAW);
            } else if (elapsed >= length) {
                show(selected); // Latest selection wins during rapid hotbar changes.
            }
            return;
        }
        if (!same.test(shown, selected)) {
            if (mapped.test(shown)) start(Action.PUT_AWAY);
            else show(selected);
            return;
        }
        shown = selected; // Count, durability and other component changes are not equips.
        if (action.movement()) {
            if (!sprinting && action != Action.RUN_END) {
                start(available(Action.RUN_END) ? Action.RUN_END : Action.IDLE);
            } else if (sprinting && action == Action.RUN_END) {
                startRun(grounded);
            } else if (action == Action.RUN_START && elapsed >= length) {
                start(runLoop(grounded));
            } else if (action == Action.RUN_END && elapsed >= length) {
                start(Action.IDLE);
            } else if (action == Action.RUN || action == Action.RUN_HOLD) {
                Action loop = runLoop(grounded);
                if (loop != action) start(loop);
                else elapsed %= length;
            }
        } else {
            if (action != Action.IDLE && elapsed >= length) start(Action.IDLE);
            if (action == Action.IDLE && mapped.test(shown) && sprinting && available(Action.RUN)) startRun(grounded);
        }
    }

    private boolean available(Action next) { return duration.applyAsInt(shown, next) > 0; }
    private Action runLoop(boolean grounded) {
        return !grounded && available(Action.RUN_HOLD) ? Action.RUN_HOLD : Action.RUN;
    }
    private void startRun(boolean grounded) {
        start(available(Action.RUN_START) ? Action.RUN_START : runLoop(grounded));
    }

    private void show(T selected) {
        shown = selected;
        start(mapped.test(shown) ? Action.DRAW : Action.IDLE);
    }

    private void start(Action next) {
        previousAction = action;
        previousProgress = progress(0);
        action = next;
        elapsed = 0;
        length = Math.max(1, duration.applyAsInt(shown, next));
        blendElapsed = 0;
        blendLength = transitionTicks(next);
        if (next == Action.DRAW || (next == Action.PUT_AWAY && !previousAction.movement())) {
            blendElapsed = blendLength;
        }
    }

    private int transitionTicks(Action next) {
        if (next == Action.PUT_AWAY) return Math.max(1, (int) Math.ceil(length * 0.75));
        if (next == Action.RUN_END) return 6; // TACZ default_state_machine uses 0.3 s.
        if (next == Action.RUN_START || next == Action.RUN_HOLD) return 4; // 0.2 s.
        return 4;
    }

    public boolean trigger(Action next) {
        if (!initialized || !mapped.test(shown) || action == Action.PUT_AWAY || action == Action.DRAW) return false;
        if (duration.applyAsInt(shown, next) <= 0) return false;
        start(next);
        return true;
    }

    public T shownOr(T fallback) { return initialized ? shown : fallback; }
    public Action action() { return action; }
    public Action previousAction() { return previousAction; }
    public float previousProgress() { return previousProgress; }
    public float blend(float delta) {
        float linear = Math.min(1f, (blendElapsed + delta) / blendLength);
        // ObjectAnimationRunner uses easeOutCubic for channel transitions.
        float inverse = 1f - linear;
        return 1f - inverse * inverse * inverse;
    }
    public float progress(float delta) {
        float time = elapsed + delta;
        return action == Action.RUN || action == Action.RUN_HOLD ? (time % length) / length : Math.min(1f, time / length);
    }
    public void reset() {
        initialized = false; shown = null; action = Action.IDLE; elapsed = 0; length = 1;
        previousAction = Action.IDLE; previousProgress = 0; blendElapsed = blendLength = 4;
    }
}
