package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.utils.BugReport;
import com.liskovsoft.smartyoutubetv2.common.utils.BugReportSender;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;

/**
 * GRTubeYou: the bug report tool.
 *
 * <p>The user describes what went wrong, and a log plus the device details go with it. All of
 * the collection lives in {@link BugReport}, all of the sending in {@link BugReportSender}, and
 * this class is only the flow between them: ask, collect, send, report the outcome.
 *
 * <p>The one thing worth reading here is the refusal to pretend. With no receiver deployed the
 * tile says so and stops, instead of appearing to accept a report and dropping it - a report
 * the user believes they filed is the worst outcome available here, worse than an error, because
 * they will not file a second one.
 */
public class BugReportPresenter extends BasePresenter<Void> {
    public BugReportPresenter(Context context) {
        super(context);
    }

    public static BugReportPresenter instance(Context context) {
        return new BugReportPresenter(context);
    }

    public void show() {
        if (!BugReportSender.isConfigured()) {
            MessageHelpers.showMessage(getContext(), R.string.bug_report_no_receiver);
            return;
        }

        SimpleEditDialog.showMultiline(
                getContext(),
                getContext().getString(R.string.bug_report),
                getContext().getString(R.string.bug_report_hint),
                "",
                text -> {
                    send(text);
                    // The dialog has done its job. Kept open on nothing - there is no failure
                    // the user could fix in place, and the report is already on its way.
                    return true;
                },
                null);
    }

    /**
     * Collects and sends off the main thread.
     *
     * <p>Collection is not instant and not cheap: it reads a logcat dump, which is a subprocess
     * start and a read with an 8 second deadline. Doing it on the main thread would freeze the
     * UI for the whole of that.
     */
    private void send(String description) {
        MessageHelpers.showMessage(getContext(), R.string.bug_report_sending);

        new Thread(() -> {
            try {
                BugReport report = BugReport.collect(getContext(), description);
                BugReportSender.sendAsync(report, new BugReportSender.Callback() {
                    @Override
                    public void onSent(int lines, boolean truncated) {
                        MessageHelpers.showMessage(getContext(), truncated
                                ? R.string.bug_report_sent_truncated
                                : R.string.bug_report_sent);
                    }

                    @Override
                    public void onFailed(String reason) {
                        // Rule 10: the failure is stated. A silent failure here means the user
                        // walks away believing a report was filed.
                        MessageHelpers.showMessage(getContext(),
                                getContext().getString(R.string.bug_report_failed, reason));
                    }
                });
            } catch (Throwable t) {
                // Collection itself can fail - no logcat on the device, a Context that has gone.
                // The sender's own try/catch does not cover this, so it is covered here.
                // Hopped to the main thread because a toast cannot be posted from here, and
                // this catch is reached on the collector thread.
                final String reason = String.valueOf(t.getMessage());
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                        MessageHelpers.showMessage(getContext(),
                                getContext().getString(R.string.bug_report_failed, reason)));
            }
        }, "bug-report").start();
    }
}