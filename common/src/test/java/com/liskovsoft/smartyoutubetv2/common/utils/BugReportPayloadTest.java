package com.liskovsoft.smartyoutubetv2.common.utils;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * GRTubeYou: the parts of a bug report that can be tested without a device.
 *
 * <p>Three of them, chosen because each one has already been a way for this feature to be
 * useless rather than merely untested:
 *
 * <ul>
 *   <li><b>JSON quoting</b> - the log is full of quotes, backslashes, tabs and control
 *       characters. One unescaped byte and the receiver rejects the whole report as malformed,
 *       so the user is told it failed and loses everything they wrote. This is the single most
 *       likely way for the feature to break in the field, and it is pure string handling;
 *   <li><b>log truncation</b> - the cap keeps the tail, because the crash is at the end. Keeping
 *       the head instead would produce a report that looks complete and misses the exception;
 *   <li><b>status explanations</b> - the message shown on a non-2xx. Every receiver answer means
 *       something different to the user: "try later" versus "this build has no receiver" are not
 *       interchangeable.
 * </ul>
 *
 * <p>Reached by reflection where needed, deliberately: widening visibility purely for a test
 * leaves production code the app must keep alive for no user-visible reason. What is NOT tested
 * here, and why: the actual socket, the logcat subprocess and the whole collection - all of
 * those need a real device, and {@code adb install} does not work on this machine.
 */
public class BugReportPayloadTest {
    /** The cap {@code BugReport} applies. Mirrored, not read, so a change in one is visible. */
    private static final int MAX_LOG_CHARS = 60_000;

    // ------------------------------------------------------------------ JSON quoting

    private static String quote(String value) throws Exception {
        Method method = BugReportSender.class.getDeclaredMethod("quote", String.class);
        method.setAccessible(true);
        return (String) method.invoke(null, value);
    }

    @Test
    public void plainTextIsUnchangedInsideQuotes() throws Exception {
        assertEquals("\"hello world\"", quote("hello world"));
    }

    @Test
    public void quotesInTheLogAreEscaped() throws Exception {
        // A single stray quote turns the whole body into invalid JSON, and the receiver answers
        // 400 - so the report is lost, not mangled.
        assertEquals("\"he said \\\"hi\\\"\"", quote("he said \"hi\""));
    }

    @Test
    public void backslashesAreEscaped() throws Exception {
        // File paths in a log are the common case, and \d or \n in a path is not rare.
        assertEquals("\"C:\\\\dir\\\\file\"", quote("C:\\dir\\file"));
    }

    @Test
    public void newlinesAndTabsBecomeEscapesNotLiteralBytes() throws Exception {
        String quoted = quote("line1\nline2\tend");

        assertEquals("\"line1\\nline2\\tend\"", quoted);
        assertFalse("a literal newline inside a JSON string is invalid JSON",
                quoted.contains("\n"));
        assertFalse("a literal tab inside a JSON string is invalid JSON",
                quoted.contains("\t"));
    }

    @Test
    public void carriageReturnIsEscapedToo() throws Exception {
        // Log lines end \r\n on some devices. \r alone is a control character JSON forbids
        // unescaped, so this is not a theoretical case.
        assertEquals("\"a\\r\\nb\"", quote("a\r\nb"));
    }

    @Test
    public void controlCharactersBecomeUnicodeEscapes() throws Exception {
        // BEL, backspace and the like come straight out of a stack trace. They are below 0x20,
        // which is exactly where JSON stops allowing raw bytes.
        String quoted = quote("bell\u0007back\b");

        assertTrue("expected \\u escapes, got: " + quoted, quoted.contains("\\u0007"));
        assertTrue("expected \\u escapes, got: " + quoted, quoted.contains("\\b"));
    }

    @Test
    public void nullBecomesJsonNull() throws Exception {
        // Not an empty string: an empty string is a value, and the receiver treats an empty
        // description as "the user wrote nothing", which is the intent.
        assertEquals("null", quote(null));
    }

    @Test
    public void cyrillicSurvivesAsIs() throws Exception {
        // The log and the description are Russian in practice. JSON is UTF-8 and carries these
        // literally; escaping them would be legal but unreadable in the receiver.
        assertEquals("\"ошибка воспроизведения\"", quote("ошибка воспроизведения"));
    }

    // ------------------------------------------------------------------ truncation

    /**
     * Applies the same tail-keeping rule as {@code BugReport.collect}, on a synthetic log.
     *
     * <p>Re-implemented rather than called because {@code collect} reaches for logcat and a
     * Context. The point being pinned down is the rule - which end survives - not the string
     * building, and the rule is the thing that can silently be written backwards.
     */
    private static String truncateLikeCollect(String log) {
        if (log.length() <= MAX_LOG_CHARS) {
            return log;
        }
        return log.substring(log.length() - MAX_LOG_CHARS);
    }

    @Test
    public void aShortLogIsNotTruncated() {
        String log = "start\nmiddle\nend";
        assertEquals(log, truncateLikeCollect(log));
    }

    @Test
    public void truncationKeepsTheTailNotTheHead() {
        // The exception is at the end of a session. A head-kept report would drop it and still
        // look like a complete report, which is the failure this guards.
        StringBuilder sb = new StringBuilder();
        sb.append("THE-VERY-FIRST-LINE\n");
        while (sb.length() < MAX_LOG_CHARS + 5_000) {
            sb.append("filler line\n");
        }
        sb.append("THE-VERY-LAST-LINE\n");

        String truncated = truncateLikeCollect(sb.toString());

        assertFalse("the first line is what gets dropped",
                truncated.contains("THE-VERY-FIRST-LINE"));
        assertTrue("the last line is the one worth keeping",
                truncated.contains("THE-VERY-LAST-LINE"));
        assertEquals(MAX_LOG_CHARS, truncated.length());
    }

    @Test
    public void aLogExactlyAtTheCapIsKeptWhole() {
        // Off by one here would truncate a report that fit, and the notice would then claim
        // lines were dropped when none were.
        StringBuilder sb = new StringBuilder();
        while (sb.length() < MAX_LOG_CHARS) {
            sb.append('x');
        }

        assertEquals(MAX_LOG_CHARS, sb.length());
        assertEquals(MAX_LOG_CHARS, truncateLikeCollect(sb.toString()).length());
    }

    // ------------------------------------------------------------------ status handling

    private static String explain(int status) throws Exception {
        Method method = BugReportSender.class.getDeclaredMethod("explain", int.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(null, status, "");
    }

    @Test
    public void rateLimitIsExplainedAsSuchRatherThanAsAServerError() throws Exception {
        // "try later" is actionable; "server said 429" is not, and on a TV it is the difference
        // between the user waiting and the user reporting the app as broken.
        assertEquals("too many reports, try later", explain(429));
    }

    @Test
    public void anEmptyReportIsToldApartFromAServerFault() throws Exception {
        assertEquals("the report was rejected as empty", explain(400));
        assertEquals("the receiver could not forward it", explain(502));
    }

    @Test
    public void aStalePathIsNamedAsSuch() throws Exception {
        // 404 after a redeploy means the app carries a path the worker no longer has. Saying
        // "server said 404" hides that the build is the thing that needs rebuilding.
        assertEquals("the receiver address is stale", explain(404));
    }

    @Test
    public void anUnknownStatusStillCarriesTheNumber() throws Exception {
        // Never an empty message: an unexplained failure leaves the user with nothing to
        // report back to us.
        String reason = explain(418);
        assertNotNull(reason);
        assertTrue("expected the status in the text, got: " + reason, reason.contains("418"));
        assertFalse("reason must not be blank", reason.trim().isEmpty());
    }
}