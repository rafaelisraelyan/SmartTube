package com.liskovsoft.smartyoutubetv2.tv.ui.dialogs.other;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.liskovsoft.sharedutils.mylogger.Log;
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
    private static final String TAG = UpdateProgressPreference.class.getSimpleName();
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
        Log.d(TAG, "download finished, this row becomes the install button: " + installText);

        mReady = true;
        mPercent = -1;
        mStatus = installText;

        setTitle(installText);
        // No setSelectable() here on purpose. It reads like the way to make the row
        // reachable, but leanback's preference list never consults isSelectable();
        // focusability is set on the view in onBindViewHolder instead.
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
        // may push a value for a panel that has not moved yet. The bus replays the
        // ready state on registration, so a row built after the download finished
        // also comes up as the install button.
        UpdateProgressBus.setTarget(this);
        Log.d(TAG, "progress row created, ready=" + mReady);
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

        // GRTubeYou: focusability lives on the view, not on the Preference.
        // setSelectable() looks like it would do this, but leanback's preference
        // list never reads isSelectable() - verified against the AAR - so the row
        // stayed unreachable by the D-pad even after it had become the install
        // button. That is a third, independent way the panel could sit on a full
        // bar with no actionable row, and it survived the two earlier fixes.
        //
        // The layout ships focusable="false" because while the transfer runs there
        // genuinely is nothing to press. Flip it here on every bind: RecyclerView
        // recycles holders, so the state has to follow the data, not the instance.
        // androidx.preference 1.1.0 has no public PreferenceViewHolder.itemView
        // (it only became public in 1.2.0), so the row's root is reached through
        // the id the layout already puts on it - the same id androidx itself uses
        // for a preference item container.
        View itemView = holder.findViewById(R.id.container);

        if (itemView != null) {
            itemView.setFocusable(mReady);
            itemView.setClickable(mReady);
            itemView.setFocusableInTouchMode(false);

            // descendantFocusability lives on ViewGroup, and the row's root is the
            // LinearLayout from the layout - so the cast is safe, and without it the
            // row would keep swallowing focus from its own children.
            if (itemView instanceof ViewGroup) {
                ((ViewGroup) itemView).setDescendantFocusability(mReady
                        ? ViewGroup.FOCUS_BEFORE_DESCENDANTS
                        : ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            }
        }
    }
}
