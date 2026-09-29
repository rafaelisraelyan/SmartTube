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

        /**
         * GRTubeYou: the file is on disk and can be installed.
         *
         * <p>Deliberately separate from {@link #onUpdateProgress}: the progress
         * row used to stay stuck at 100% because "ready" only ever reached the UI
         * by rebuilding the whole dialog, and that rebuild is conditional. When the
         * condition failed the download had succeeded and the user was left looking
         * at a full bar with nothing to press. The row now turns into the install
         * button itself, so completion never depends on the dialog being rebuilt.
         */
        void onUpdateReady(CharSequence installText);
    }

    private static Target sTarget;
    private static Runnable sInstallAction;

    private UpdateProgressBus() {
    }

    /** Called by the row when it is created. */
    public static void setTarget(Target target) {
        sTarget = target;
    }

    /**
     * GRTubeYou: how the row starts the installer. The row lives in {@code smarttubetv}
     * and the checker that owns the apk in {@code common}, so the action is handed
     * over instead of being called across the module boundary.
     */
    public static void setInstallAction(Runnable action) {
        sInstallAction = action;
    }

    /** Called when the panel is torn down, so nothing keeps the row alive. */
    public static void clear() {
        sTarget = null;
        sInstallAction = null;
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

    public static void pushReady(CharSequence installText) {
        Target target = sTarget;

        if (target != null) {
            target.onUpdateReady(installText);
        }
    }

    public static void runInstallAction() {
        Runnable action = sInstallAction;

        if (action != null) {
            action.run();
        }
    }
}
