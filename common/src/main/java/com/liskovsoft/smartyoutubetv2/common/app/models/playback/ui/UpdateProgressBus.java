package com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui;

/**
 * GRTubeYou: one-way bridge from the app update presenter to the progress row of
 * the update panel.
 *
 * <p>It exists because of the module layout: the presenter lives in {@code common}
 * and the row is a {@code Preference} in {@code smarttubetv}, which depends on
 * {@code common} - so the presenter cannot reference the row directly, and the row
 * cannot reach the presenter. The view contract of the updater
 * ({@code AppUpdateView}) is unused and the presenter has no view type, so a tiny
 * bus is the least intrusive way to carry the numbers across.
 *
 * <p>Only one update panel can be open, so a single current target is enough.
 * Progress is best effort: when nothing is listening the value is dropped, which
 * is exactly what should happen if the panel was closed mid download.
 */
public final class UpdateProgressBus {
    public interface Target {
        void onUpdateProgress(int percent, CharSequence status);
    }

    private static Target sTarget;

    private UpdateProgressBus() {
    }

    /** Called by the row when it is created. */
    public static void setTarget(Target target) {
        sTarget = target;
    }

    /** Called when the panel is torn down, so nothing keeps the row alive. */
    public static void clear() {
        sTarget = null;
    }

    /**
     * @param percent 0..100
     */
    public static void push(int percent, CharSequence status) {
        Target target = sTarget;

        if (target != null) {
            target.onUpdateProgress(percent, status);
        }
    }
}
