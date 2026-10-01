package com.liskovsoft.smartyoutubetv2.common.utils;

import com.liskovsoft.sharedutils.mylogger.Log;

import java.io.BufferedReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * GRTubeYou: posts a bug report to the receiver.
 *
 * <p>What this class deliberately does not contain is a credential. The only thing the app
 * carries is a URL, and everything that identifies the destination - the Telegram token, the
 * chat id - lives in the receiver. Anything shipped in an APK is extractable, so a token
 * here would be a token handed to whoever downloads the app.
 *
 * <p>Plain {@link HttpURLConnection} rather than Retrofit: the request is a single POST with
 * a JSON body, one-off, and the Retrofit stack in this project lives in {@code youtubeapi}
 * behind interfaces meant for YouTube. Reaching across that boundary for one form post would
 * couple the report path to the service layer, so it would break whenever the service layer
 * does.
 */
public final class BugReportSender {
    private static final String TAG = BugReportSender.class.getSimpleName();

    /**
     * GRTubeYou: the receiver, including its secret path.
     *
     * <p>Left empty on purpose. An endpoint that is not deployed must make the tile say so,
     * not send reports into a void and report success. Fill this in after deploying
     * {@code bug-report-worker.js} - see the header of that file for the steps.
     *
     * <p>It is a URL and nothing else, which is what makes it safe to ship: rotating a
     * compromised path is a rebuild here and a secret change in the worker, and the token
     * itself is never in the app.
     */
    private static final String ENDPOINT = "";

    /**
     * The backslash-u prefix for a control character escape.
     *
     * <p>Split on purpose. The Java lexer expands a backslash-u pair in the source text before
     * it parses anything - inside a string literal and inside a comment alike - so a literal
     * spelling it out in one piece does not compile, and a comment mentioning it plainly does
     * not either. Both cost a build to discover.
     */
    private static final String ESCAPE = "\\" + "u";

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 20_000;

    /** Somewhere for the caller to hear how it went. */
    public interface Callback {
        void onSent(int lines, boolean truncated);

        /**
         * @param reason already phrased for the user. The raw exception text is logged, not
         *               shown: it can be a multi-line stack trace, and a dialog on a TV cannot
         *               render that usefully.
         */
        void onFailed(String reason);
    }

    private BugReportSender() {
    }

    /** @return true when a receiver is configured. The tile checks this before offering. */
    public static boolean isConfigured() {
        return !ENDPOINT.isEmpty();
    }

    /**
     * Sends on a background thread; the callback comes back on the main one.
     *
     * <p>Blocking the caller would freeze the dialog, and on a TV box the log read alone can
     * take a second, so the whole thing is off the main thread.
     */
    public static void sendAsync(final BugReport report, final Callback callback) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Result result = send(report);
                    postSuccess(callback, result.lines, result.truncated);
                } catch (final Throwable t) {
                    // Rule 10: a report that failed must say so out loud. A tile that
                    // swallows the failure leaves the user believing a report was filed
                    // when nothing left the device.
                    Log.e(TAG, "bug report failed: " + t.getMessage());
                    postFailure(callback, describe(t));
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private static class Result {
        int lines;
        boolean truncated;
    }

    private static Result send(BugReport report) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("no receiver configured");
        }

        HttpURLConnection connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        Result result = new Result();
        result.lines = report.getTotalLines();
        result.truncated = report.isTruncated();

        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");

            byte[] payload = buildJson(report).getBytes("UTF-8");
            connection.setFixedLengthStreamingMode(payload.length);

            try (OutputStream out = connection.getOutputStream()) {
                out.write(payload);
                out.flush();
            }

            int status = connection.getResponseCode();

            // Read the body on failures: the receiver explains itself in it, and that
            // explanation is the difference between "try again" and "you are being
            // rate-limited".
            if (status < 200 || status >= 300) {
                String detail = readBody(connection, status);
                Log.e(TAG, "receiver answered " + status + ": " + detail);
                throw new IllegalStateException(explain(status, detail));
            }

            return result;
        } finally {
            connection.disconnect();
        }
    }

    private static String explain(int status, String detail) {
        switch (status) {
            case 429:
                return "too many reports, try later";
            case 400:
                return "the report was rejected as empty";
            case 413:
                return "the report was too large";
            case 502:
                return "the receiver could not forward it";
            case 404:
                // The path was changed in the worker and the app still carries the old one.
                return "the receiver address is stale";
            default:
                return "server said " + status + (detail == null || detail.isEmpty() ? "" : ": " + detail);
        }
    }

    private static String readBody(HttpURLConnection connection, int status) {
        try (BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(
                status >= 400 ? connection.getErrorStream() : connection.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null && sb.length() < 2000) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Hand-rolled JSON.
     *
     * <p>Three string fields, so this is the whole schema - and hand-building it is what
     * keeps the report path free of a JSON dependency in a module that has none. The escaping
     * is the part that has to be right: a log is full of quotes, backslashes, newlines and
     * tabs, and any of them unescaped produces a body the receiver rejects as malformed.
     */
    private static String buildJson(BugReport report) {
        StringBuilder sb = new StringBuilder(64 * 1024);
        sb.append('{');
        sb.append("\"head\":").append(quote(report.getHead())).append(',');
        sb.append("\"description\":").append(quote(report.getDescription())).append(',');
        sb.append("\"log\":").append(quote(report.getLog()));
        sb.append('}');
        return sb.toString();
    }

    static String quote(String value) {
        if (value == null) {
            return "null";
        }

        StringBuilder sb = new StringBuilder(value.length() + 16);
        sb.append('"');

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);

            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                default:
                    // Control characters below 0x20 have to go as a backslash-u escape - a raw
                    // one is invalid JSON and the receiver would reject the whole report. Logs
                    // are full of them, which is exactly how this bites.
                    //
                    // Note for anyone editing this line: the escape is written with a split
                    // string below on purpose. The Java lexer expands backslash-u before it
                    // parses anything - in a literal AND in a comment - so spelling the escape
                    // out in one piece anywhere in this file is a compile error.
                    if (c < 0x20) {
                        sb.append(String.format("%s%04x", ESCAPE, (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }

        sb.append('"');
        return sb.toString();
    }

    private static String describe(Throwable t) {
        String message = t.getMessage();

        if (message == null || message.trim().isEmpty()) {
            return t.getClass().getSimpleName();
        }

        return message;
    }

    private static void postSuccess(final Callback callback, final int lines, final boolean truncated) {
        runOnMain(callback, new Runnable() {
            @Override
            public void run() {
                if (callback != null) {
                    callback.onSent(lines, truncated);
                }
            }
        });
    }

    private static void postFailure(final Callback callback, final String reason) {
        runOnMain(callback, new Runnable() {
            @Override
            public void run() {
                if (callback != null) {
                    callback.onFailed(reason);
                }
            }
        });
    }

    private static void runOnMain(final Callback callback, final Runnable action) {
        if (callback == null) {
            return;
        }

        new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                action.run();
            }
        });
    }
}
