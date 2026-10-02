package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.tv.R;

public class ThumbsUpAction extends TwoStateAction implements CountBadgeAction {
    // GRTubeYou: the number that used to sit in the line under the title, now beside the
    // icon. Null until the video's counts arrive, which is also what it stays when the likes
    // counter setting is off - and the button then looks exactly as it did before.
    private String mBadgeText;

    public ThumbsUpAction(Context context) {
        super(context, R.id.action_thumbs_up, R.drawable.lb_ic_thumb_up, false);

        String[] labels = new String[2];
        // Note, labels denote the action taken when clicked
        labels[INDEX_OFF] = context.getString(R.string.action_like);
        labels[INDEX_ON] = context.getString(R.string.action_like);
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
