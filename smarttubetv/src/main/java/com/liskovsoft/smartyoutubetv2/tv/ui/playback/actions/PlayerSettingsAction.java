package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;
import androidx.leanback.widget.Action;

import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * GRTubeYou: the gear button that replaces the crowded control rows.
 *
 * <p>The player used to show up to 27 buttons across two rows. Most of them
 * (speed, quality, subtitles, repeat, PiP, description, comments, queue, report)
 * are settings that a viewer touches rarely, so they moved into a single dialog
 * behind this action. Play, previous, next, likes and the channel stayed on the
 * rows, where they are reachable in one press.
 */
public class PlayerSettingsAction extends Action {
    public PlayerSettingsAction(Context context) {
        super(R.id.action_player_settings);

        // Reuses the existing gear artwork (the one the quality settings used) so
        // the button matches the rest of the player's icon weight. It is cropped
        // to its glyph, 0% margins, unlike the settings_* icons in the menus.
        Drawable icon = ContextCompat.getDrawable(context, R.drawable.settings_hq);

        setIcon(icon);
        setLabel1(context.getString(R.string.player_settings));
    }
}
