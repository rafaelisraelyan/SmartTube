package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

/**
 * GRTubeYou: a control that can carry a number next to its icon.
 *
 * <p>Implemented only by the two thumb actions. Deliberately not put on {@link TwoStateAction}:
 * that base has seventeen subclasses - captions, repeat, rotate, AFR and so on - and a badge
 * capability there would be available to fifteen buttons that have no count to show, and
 * would sit there looking like it belonged to them.
 *
 * <p>The text is stored, never rendered, here. Whoever draws the control composes the icon and
 * the text into one image, so that a state change - the thumb filling in when liked - swaps
 * the icon and the new number is composed from it automatically, with nothing to keep in sync
 * between the action and the view.
 *
 * <p>The value is whatever the video already holds, verbatim. There is no second source and no
 * second formatter: {@code Video.likeCount} is already the compact string the interface shows
 * ("14K", "402"), so displaying it as it arrives is both the existing format and the existing
 * value.
 */
public interface CountBadgeAction {
    /**
     * @return the text to show, or null/empty for "no number, draw the icon alone". Empty is
     *         the normal case before the counts arrive, and while the likes counter setting is
     *         off - the button must look exactly as it did before, not like a button with a
     *         gap in it.
     */
    String getBadgeText();

    void setBadgeText(String text);
}