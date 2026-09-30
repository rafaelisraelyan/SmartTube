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

    /**
     * GRTubeYou: the file is on disk, whatever happens to the row.
     *
     * <p>Held here rather than only being pushed, because a single live target is
     * not enough: the dialog is rebuilt between states, and its onFinish calls
     * {@link #clear()}. The download finishing could therefore arrive after the
     * row had been torn down, and the signal was simply dropped - which is how
     * the panel managed to sit on a 100% bar with nothing to press. Keeping the
     * fact here makes the order irrelevant: a row that appears later is told on
     * registration, and one that is already there is told immediately.
     */
    private static boolean sReady;
    private static CharSequence sInstallText;

    private UpdateProgressBus() {
    }

    /**
     * Called by the row when it is created.
     *
     * <p>Re-binding the same row is a no-op. A preference row is bound again on every
     * RecyclerView pass, so a naive implementation re-sent {@code onUpdateReady} each time.
     * That is harmless in itself - the row just re-reads the same text - but it means the
     * "finished" signal is delivered an unbounded number of times, and anything added to that
     * callback later (a sound, a focus request, a log line) inherits the repetition without
     * anyone deciding it should. Identity is checked here so the replay is exactly once per
     * distinct row, which is what the contract says it means.
     */
    public static void setTarget(Target target) {
        if (sTarget == target) {
            return;
        }

        sTarget = target;

        if (sReady && sInstallText != null) {
            target.onUpdateReady(sInstallText);
        }
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
        // sReady deliberately survives: the apk is on disk and stays installable
        // however often the panel is rebuilt.
    }

    /**
     * A new transfer started, so nothing is installable yet.
     */
    public static void reset() {
        sReady = false;
        sInstallText = null;
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

    public static void pushReady(CharSequence installText) {
        sReady = true;
        sInstallText = installText;

        Target target = sTarget;

        if (target != null) {
            target.onUpdateReady(installText);
        }
    }

    public static void runInstallAction() {
        Runnable action = sInstallAction;

        if (action != null) {
            action.run();
            return;
        }

        // Not a silent no-op. A press that quietly does nothing is worse than a
        // crash: the user sees a button that works and an app that does not. The
        // action is missing whenever the download finished without the presenter
        // having armed it, and that is a bug worth seeing in the log.
        com.liskovsoft.sharedutils.mylogger.Log.e(
                UpdateProgressBus.class.getSimpleName(),
                "install pressed but no action was registered");
    }
}
