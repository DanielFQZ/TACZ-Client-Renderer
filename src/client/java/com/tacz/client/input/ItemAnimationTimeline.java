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

    public ItemAnimationTimeline(BiPredicate<T, T> same, Predicate<T> mapped,
                                 ToIntBiFunction<T, Action> duration) {
        this.same = same;
        this.mapped = mapped;
        this.duration = duration;
    }

    public void tick(T selected) {
        if (!initialized) {
            initialized = true;
            show(selected);
            return;
        }
        elapsed++;
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
        if (action != Action.IDLE && elapsed >= length) start(Action.IDLE);
    }

    private void show(T selected) {
        shown = selected;
        start(mapped.test(shown) ? Action.DRAW : Action.IDLE);
    }

    private void start(Action next) {
        action = next;
        elapsed = 0;
        length = Math.max(1, duration.applyAsInt(shown, next));
    }

    public boolean trigger(Action next) {
        if (!initialized || !mapped.test(shown) || action == Action.PUT_AWAY || action == Action.DRAW) return false;
        if (duration.applyAsInt(shown, next) <= 0) return false;
        start(next);
        return true;
    }

    public T shownOr(T fallback) { return initialized ? shown : fallback; }
    public Action action() { return action; }
    public float progress(float delta) { return Math.min(1f, (elapsed + delta) / length); }
    public void reset() { initialized = false; shown = null; action = Action.IDLE; elapsed = 0; length = 1; }
}
