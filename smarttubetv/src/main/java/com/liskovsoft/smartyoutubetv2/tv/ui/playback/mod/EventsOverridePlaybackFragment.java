package com.liskovsoft.smartyoutubetv2.tv.ui.playback.mod;

import android.os.Bundle;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.View;
import androidx.leanback.app.PlaybackSupportFragment;
import androidx.leanback.widget.VerticalGridView;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.mod.surface.SurfacePlaybackFragment;

import java.util.List;

/**
 *  Every successfully handled event invokes {@link PlaybackSupportFragment#tickle} that makes ui to appear.
 *  Fixing that for keys.
 */
public class EventsOverridePlaybackFragment extends SurfacePlaybackFragment {
    /**
     * GRTubeYou: whether up/down wraps around at the ends of the Shorts list.
     * The official player stops, so this defaults to false.
     */
    private static final boolean SHORTS_WRAP_AROUND = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Object onTouchInterceptListener = Helpers.getField(this, "mOnTouchInterceptListener");

        if (onTouchInterceptListener != null) {
            Helpers.setField(this, "mOnTouchInterceptListener", (VerticalGridView.OnTouchInterceptListener) this::onInterceptInputEvent);
        }

        Object onKeyInterceptListener = Helpers.getField(this, "mOnKeyInterceptListener");

        if (onKeyInterceptListener != null) {
            Helpers.setField(this, "mOnKeyInterceptListener", (VerticalGridView.OnKeyInterceptListener) this::onInterceptInputEvent);
        }
    }

    boolean onInterceptInputEvent(InputEvent event) {
        final boolean controlsHidden = !isControlsOverlayVisible();
        //if (DEBUG) Log.v(TAG, "onInterceptInputEvent hidden " + controlsHidden + " " + event);
        boolean consumeEvent = false;
        int keyCode = KeyEvent.KEYCODE_UNKNOWN;
        int keyAction = 0;

        // GRTubeYou: step through Shorts vertically. Only when the controls are
        // hidden, so up/down still moves focus inside the player UI when it is shown.
        if (event instanceof KeyEvent && controlsHidden) {
            KeyEvent keyEvent = (KeyEvent) event;
            int shortsKey = keyEvent.getKeyCode();

            if ((shortsKey == KeyEvent.KEYCODE_DPAD_DOWN || shortsKey == KeyEvent.KEYCODE_DPAD_UP)
                    && keyEvent.getAction() == KeyEvent.ACTION_DOWN
                    && navigateShorts(shortsKey)) {
                return true;
            }
        }

        if (event instanceof KeyEvent) {
            keyCode = ((KeyEvent) event).getKeyCode();
            keyAction = ((KeyEvent) event).getAction();
            if (getInputEventHandler() != null) {
                // VideoPlayerGlue handler
                consumeEvent = getInputEventHandler().onKey(getView(), keyCode, (KeyEvent) event);
            }
        }

        if (consumeEvent) {
            return true;
        }

        switch (keyCode) {
            // Confirm key
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_SPACE:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
            // Navigation key
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                // Event may be consumed; regardless, if controls are hidden then these keys will
                // bring up the controls.

                // MOD: show ui and apply key immediately
                //if (controlsHidden) {
                //    consumeEvent = true;
                //}

                if (keyAction == KeyEvent.ACTION_DOWN) {
                    tickle();
                }
                break;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                if (isInSeek()) {
                    // when in seek, the SeekUi will handle the BACK.
                    return false;
                }
                // If controls are not hidden, back will be consumed to fade
                // them out (even if the key was consumed by the handler).
                if (!controlsHidden) {
                    consumeEvent = true;

                    if (((KeyEvent) event).getAction() == KeyEvent.ACTION_UP) {
                        hideControlsOverlay(true);
                    }
                }
                break;
            default:
                if (consumeEvent) {
                    if (keyAction == KeyEvent.ACTION_DOWN) {
                        tickle();
                    }
                }
        }
        return consumeEvent;
    }

    private View.OnKeyListener getInputEventHandler() {
        return (View.OnKeyListener) Helpers.getField(this, "mInputEventHandler");
    }

    /**
     * GRTubeYou: move to the neighbouring Shorts in the same group.
     * <p>
     * DPAD_DOWN goes to the next one and DPAD_UP to the previous one, which is the
     * direction the official Shorts player uses. Everything else is left untouched:
     * left/right keep their usual next/previous meaning.
     * <p>
     * Deliberately not applied when the player controls are visible - up/down has to
     * keep moving the focus there.
     *
     * @return true if a Shorts switch happened and the event must be consumed
     */
    private boolean navigateShorts(int keyCode) {
        if (getActivity() == null) {
            return false;
        }

        PlaybackPresenter presenter = PlaybackPresenter.instance(getActivity());
        Video current = presenter.getVideo();

        if (current == null || !current.isShorts) {
            return false;
        }

        // NOTE: the group is kept through a WeakReference, so it may already be gone
        // (a Shorts opened from search or from a channel, for example).
        VideoGroup group = current.getGroup();

        // isShorts() only returns true when the whole group is made of Shorts, so a
        // regular row that happens to contain a short video is not affected.
        if (group == null || !group.isShorts() || group.isEmpty()) {
            return false;
        }

        int index = group.indexOf(current);

        if (index == -1) {
            return false;
        }

        List<Video> videos = group.getVideos();
        int size = videos.size();
        int nextIndex = index + (keyCode == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1);

        if (nextIndex < 0 || nextIndex >= size) {
            if (!SHORTS_WRAP_AROUND) {
                return false;
            }

            nextIndex = (nextIndex + size) % size;
        }

        presenter.openVideo(videos.get(nextIndex));

        // Keep the feed clean instead of leaving the controls on top of the next Shorts.
        hideControlsOverlay(true);

        return true;
    }

    private boolean isInSeek() {
        Object mInSeek = Helpers.getField(this, "mInSeek");
        return mInSeek != null && (boolean) mInSeek;
    }
}
