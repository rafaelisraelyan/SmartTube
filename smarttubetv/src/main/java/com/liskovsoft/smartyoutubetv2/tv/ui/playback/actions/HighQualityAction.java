package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;
import androidx.leanback.widget.Action;

import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * The video quality button: opens the resolution picker.
 *
 * <p>GRTubeYou: three changes over the upstream version, and the third was the one that
 * actually mattered.
 *
 * <ul>
 *   <li><b>The icon.</b> Upstream took it from {@code lbPlaybackControlsActionIcons}, which
 *       is a Leanback default that sits with the rest of the library's control glyphs. The
 *       buttons next to this one are not library glyphs any more - the gear was redrawn to
 *       match the row - so a borrowed icon was the odd one out. {@code player_quality} is
 *       drawn on the same 192x192 canvas as {@code player_settings_gear} with a 133px glyph
 *       against the gear's 128px, which is what makes the two read as a pair rather than
 *       as one foreign object between the neighbours.
 *
 *       The existing {@code settings_hq} was not usable: it is a solid white tile with the
 *       letters cut out of it. On the settings grid that passes for a category icon, but on
 *       a control row it is a white block next to two line-drawn glyphs, and the weight
 *       difference is the first thing the eye catches.
 *
 *   <li><b>The label.</b> It was {@code playback_settings} - the gear's own label. A viewer
 *       who long-pressed this was told "player settings", which is the button an inch to the
 *       right. {@code action_high_quality} already exists in every translation; it was never
 *       wired up.
 *
 *   <li><b>Where it sits.</b> Not here - in {@code VideoPlayerGlue}, which owns the order of
 *       the row. It is registered from the constructor and placed there.
 * </ul>
 */
public class HighQualityAction extends Action {
    public HighQualityAction(Context context) {
        super(R.id.action_video_quality);

        Drawable icon = ContextCompat.getDrawable(context, R.drawable.player_quality);

        setIcon(icon);
        setLabel1(context.getString(R.string.action_high_quality));
    }
}