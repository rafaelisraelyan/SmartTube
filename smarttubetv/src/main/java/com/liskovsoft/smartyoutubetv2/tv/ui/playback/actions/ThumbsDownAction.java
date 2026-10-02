package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.tv.R;

public class ThumbsDownAction extends TwoStateAction implements CountBadgeAction {
    // GRTubeYou: the count that used to sit in the line under the title. Same rules as the
    // like button: null when there is nothing to show, so the icon stands alone.
    private String mBadgeText;

    public ThumbsDownAction(Context context) {
        super(context, R.id.action_thumbs_down, R.drawable.lb_ic_thumb_down, false);

        String[] labels = new String[2];
        // Note, labels denote the action taken when clicked
        labels[INDEX_OFF] = context.getString(R.string.action_dislike);
        labels[INDEX_ON] = context.getString(R.string.action_dislike);
        setLabels(labels);
    }

    @Override
    public String getBadgeText() {
        return mBadgeText;
    }

    @Override
    public void setBadgeText(String text) {
        String clean = text == null || text.trim().isEmpty() ? null : text.trim();
        if (clean == null ? mBadgeText == null : clean.equals(mBadgeText)) {
            return;
        }

        mBadgeText = clean;
    }
}
