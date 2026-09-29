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

    @Override
    public void onUpdateProgress(int percent, CharSequence status) {
        setProgress(percent, status);
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
        mPercent = percent;
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
            // The row is informative only, so keep it out of the focus chain -
            // otherwise the D-pad stops on a row that does nothing when pressed.
            summary.setVisibility(mPercent >= 0 ? android.view.View.VISIBLE : android.view.View.GONE);
        }

        ProgressBar bar = (ProgressBar) holder.findViewById(R.id.update_progress_bar);
        if (bar != null) {
            bar.setVisibility(mPercent >= 0 ? android.view.View.VISIBLE : android.view.View.GONE);

            if (mPercent >= 0) {
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
