package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;
import android.os.Build;

import com.liskovsoft.sharedutils.helpers.AppInfoHelpers;
import com.liskovsoft.sharedutils.helpers.Helpers;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;

/**
 * GRTubeYou: assembles what a bug report carries.
 *
 * <p>Three things go in, and the third is the one that matters most:
 *
 * <ul>
 *   <li><b>the log</b> - the app's own lines plus a logcat dump;
 *   <li><b>the device</b> - because the user does not know, and cannot tell us, that the
 *       box is Android 9 on an arm64 board with 1 GB of RAM, which is usually half the
 *       answer;
 *   <li><b>what the user typed</b> - supplied separately, never invented here.
 * </ul>
 *
 * <p>No channel, no account, no video id, no anything about what the user watched. A report
 * about a crash does not need the user's history, and attaching it would be collecting
 * something nobody asked for.
 *
 * <p>The log is <b>capped, and the cap is on the tail</b>. A crash is at the end, so the
 * tail is what gets kept, and the count is reported alongside rather than silently applied -
 * a truncated report that does not say it was truncated is worse than a short one.
 */
public final class BugReport {
    /**
     * Characters of log kept. The receiver ships the log as a .txt attachment, so this can be
     * generous: 600k is roughly 6000-8000 lines, which is a whole session rather than a
     * glimpse of one. It was 60k when the log travelled as chat messages, where that number
     * became eighteen unreadable bubbles - the right size for neither, and too small for the
     * crash to be in it.
     */
    private static final int MAX_LOG_CHARS = 600_000;

    /** logcat lines to read. Bounded so a chatty device cannot stall the report. */
    private static final int MAX_LOGCAT_LINES = 4000;

    /** Hard stop on the logcat read, so a hung process cannot hang the report. */
    private static final int LOGCAT_TIMEOUT_MS = 8_000;

    private final String mDescription;
    private final String mHead;
    private final String mLog;
    private final boolean mTruncated;
    private final int mTotalLines;

    private BugReport(String description, String head, String log, boolean truncated, int totalLines) {
        mDescription = description == null ? "" : description;
        mHead = head;
        mLog = log;
        mTruncated = truncated;
        mTotalLines = totalLines;
    }

    /** What the user wrote, verbatim, or empty when they wrote nothing. */
    public String getDescription() {
        return mDescription;
    }

    public String getHead() {
        return mHead;
    }

    public String getLog() {
        return mLog;
    }

    /** @return true when lines were dropped, so the report can say so rather than lie. */
    public boolean isTruncated() {
        return mTruncated;
    }

    public int getTotalLines() {
        return mTotalLines;
    }

    /**
     * @param description what the user wrote. May be empty - the log alone is worth
     *                    sending - but the report is rejected later if both this and the
     *                    log are empty.
     */
    public static BugReport collect(Context context, String description) {
        List<String> lines = new ArrayList<>();

        String clean = description == null ? "" : description.trim();

        lines.addAll(deviceLines(context));
        lines.addAll(appLines(context));

        String logcat = readLogcat();
        if (logcat != null && !logcat.isEmpty()) {
            lines.add("");
            lines.add("----- logcat -----");
            lines.add(logcat);
        }

        int total = lines.size();
        String log = join(lines);
        boolean truncated = log.length() > MAX_LOG_CHARS;

        if (truncated) {
            log = log.substring(log.length() - MAX_LOG_CHARS);
            // Say where the cut is, otherwise the first line in the report looks like the
            // start of the session when it is actually the middle of it.
            log = "(earlier lines dropped, " + log.length() + " characters of a longer log shown)"
                    + "\n" + log;
        }

        // The description is NOT written into the log lines as well: the receiver prints it as
        // its own section, so duplicating it here would make the report read twice.
        return new BugReport(clean, head(context), log, truncated, total);
    }

    /**
     * Reads logcat from inside the app.
     *
     * <p>{@code FileLogger} already shells out to {@code logcat -d} the same way, so the
     * pattern is proven in this codebase rather than assumed. On modern Android this shows
     * the app's own lines, which are the ones that matter; other apps' lines are not
     * readable and are not missed.
     *
     * @return the dump, or null if it could not be read. A report without it is still worth
     *         sending, so a failure here must not sink the whole report.
     */
    private static String readLogcat() {
        Process process = null;

        try {
            process = new ProcessBuilder("logcat", "-d", "-t", String.valueOf(MAX_LOGCAT_LINES))
                    .redirectErrorStream(true)
                    .start();

            // Read on a deadline. A logcat that produces no output and never closes would
            // otherwise block the report thread until the user force-quits the app.
            final Process proc = process;
            final StringBuilder out = new StringBuilder();
            final boolean[] done = {false};

            Thread readerThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try (BufferedReader r = new BufferedReader(
                            new java.io.InputStreamReader(proc.getInputStream()))) {
                        String line;
                        while ((line = r.readLine()) != null) {
                            out.append(line).append('\n');
                        }
                    } catch (Exception ignored) {
                        // Nothing useful to do; the deadline below ends the wait.
                    } finally {
                        done[0] = true;
                    }
                }
            });
            readerThread.setDaemon(true);
            readerThread.start();

            readerThread.join(LOGCAT_TIMEOUT_MS);

            if (!done[0]) {
                process.destroy();
                return out.length() > 0 ? out.toString() : null;
            }

            return out.toString();
        } catch (Exception e) {
            return null;
        } finally {
            // The reader thread closes its own stream. All that is left is the process, and it
            // must be destroyed even on the success path - a logcat left running would sit on
            // the collector thread until its own deadline.
            if (process != null) {
                process.destroy();
            }
        }
    }

    /**
     * The device, as much as can be told without asking for anything.
     *
     * <p>All of it is readable by any app, so no permission is involved and nothing here can
     * fail for lack of access.
     */
    private static List<String> deviceLines(Context context) {
        List<String> lines = new ArrayList<>();

        lines.add("");
        lines.add("----- device -----");

        if (context != null) {
            lines.add("app version: " + AppInfoHelpers.getAppVersionName(context));
            lines.add("app code:    " + AppInfoHelpers.getAppVersionCode(context));
        }

        lines.add("device:      " + Helpers.getDeviceName());
        lines.add("android:     " + Helpers.getAndroidVersion() + " (SDK " + Build.VERSION.SDK_INT + ")");
        lines.add("build:       " + Build.DISPLAY);
        lines.add("abi:         " + JoinAbis());
        lines.add("dpi:         " + Helpers.getDeviceDpi(context));
        lines.add("reported at: " + Helpers.getCurrentTime());

        if (context != null) {
            Runtime runtime = Runtime.getRuntime();
            long totalMb = runtime.totalMemory() / (1024 * 1024);
            long freeMb = runtime.freeMemory() / (1024 * 1024);
            lines.add("heap:        " + freeMb + " MB free of " + totalMb + " MB");
        }

        return lines;
    }

    /** What the app can say about itself that is relevant to a crash. */
    private static List<String> appLines(Context context) {
        List<String> lines = new ArrayList<>();

        lines.add("");
        lines.add("----- app -----");

        if (context != null) {
            lines.add("package:     " + context.getPackageName());
        }

        lines.add("screen:      " + screenSize(context));

        return lines;
    }

    private static String JoinAbis() {
        try {
            String[] abis = Build.SUPPORTED_ABIS;
            if (abis == null || abis.length == 0) {
                return "(none reported)";
            }

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < abis.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(abis[i]);
            }

            return sb.toString();
        } catch (Throwable t) {
            return "(unavailable)";
        }
    }

    private static String screenSize(Context context) {
        if (context == null) {
            return "(unknown)";
        }

        try {
            android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            return metrics.widthPixels + "x" + metrics.heightPixels + " px, density "
                    + metrics.density;
        } catch (Throwable t) {
            return "(unavailable)";
        }
    }

    /** The short block shown at the top of the report, where a reader looks first. */
    private static String head(Context context) {
        StringBuilder sb = new StringBuilder();
        sb.append("version:     ").append(context == null ? "?" : AppInfoHelpers.getAppVersionName(context))
                .append(" (").append(context == null ? "?" : AppInfoHelpers.getAppVersionCode(context)).append(')');
        sb.append("\ndevice:      ").append(Helpers.getDeviceName());
        sb.append("\nandroid:     ").append(Helpers.getAndroidVersion());
        return sb.toString();
    }

    private static String join(List<String> lines) {
        StringBuilder sb = new StringBuilder();

        for (String line : lines) {
            sb.append(line == null ? "" : line).append('\n');
        }

        return sb.toString();
    }
}
