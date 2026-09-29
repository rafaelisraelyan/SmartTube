package com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.other;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UpdateProgressBus;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * GRTubeYou: an inline, non-interactive row that shows how far the app update
 * download has got.
 *
 * <p>It is a {@link Preference} rather than a plain view so it can live inside the
 * app's existing settings dialog, next to the "Download" button and the changelog,
 * instead of introducing a second screen. The updater updates it through
 * {@link #setProgress(int, CharSequence)}, which re-binds only this row.
 */
public class UpdateProgressPreference extends Preference implements UpdateProgressBus.Target {
    /** Same scale as {@code update_progress_bar} max, see the layout. */
    private static final int MAX_STEPS = 1000;
    private int mPercent = -1;
    private CharSequence mStatus;
    /** GRTubeYou: the file is on disk, the row acts as the install button. */
    private boolean mReady;

    @Override
    public void onUpdateProgress(int percent, CharSequence status) {
        setProgress(percent, status);
    }

    @Override
    public void onUpdateReady(CharSequence installText) {
        mReady = true;
        mPercent = -1;
        mStatus = installText;

        setTitle(installText);
        setSelectable(true);
        // performClick() runs this listener first and returns early when it returns
        // true, so the install starts here and never reaches onPreferenceDisplayDialog.
        setOnPreferenceClickListener(pref -> {
            UpdateProgressBus.runInstallAction();
            return true;
        });

        notifyChanged();
    }

    public UpdateProgressPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public UpdateProgressPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public UpdateProgressPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public UpdateProgressPreference(Context context) {
        super(context);
        // Registered at creation, not at the first progress tick: the presenter
        // may push a value for a panel that has not moved yet.
        UpdateProgressBus.setTarget(this);
    }

    /**
     * @param percent 0..100, negative hides the bar
     * @param status  line under the bar, e.g. the transferred/total size
     */
    public void setProgress(int percent, CharSequence status) {
        // A late progress tick must not undo a finished download: the ordering of
        // the last tick and the completion callback is not guaranteed, and losing
        // the ready state is what strands the user on a full bar.
        if (mReady) {
            return;
        }

        mPercent = percent;
        mStatus = status;
        // Re-binds this single row instead of redrawing the whole dialog, so the
        // bar animates without the changelog below it flickering.
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        TextView title = (TextView) holder.findViewById(android.R.id.title);
        if (title != null) {
            title.setText(mStatus);
        }

        TextView summary = (TextView) holder.findViewById(android.R.id.summary);
        if (summary != null) {
            // The row is informative only while it is a progress bar, so keep it out
            // of the focus chain - otherwise the D-pad stops on a row that does
            // nothing when pressed. Once the file is ready the row is the install
            // button and has to be focusable again.
            summary.setVisibility(mPercent >= 0 && !mReady ? android.view.View.VISIBLE : android.view.View.GONE);
        }

        ProgressBar bar = (ProgressBar) holder.findViewById(R.id.update_progress_bar);
        if (bar != null) {
            bar.setVisibility(mPercent >= 0 && !mReady ? android.view.View.VISIBLE : android.view.View.GONE);

            if (mPercent >= 0 && !mReady) {
                int steps = mPercent * MAX_STEPS / 100;
                // animate=true: without it the bar snaps between values, which
                // looks broken when the transfer only ticks a few times a second.
                bar.setProgress(steps, true);

                TextView percentView = (TextView) holder.findViewById(R.id.update_progress_percent);
                if (percentView != null) {
                    percentView.setText(mPercent + "%");
                }
            }
        }
    }
}
