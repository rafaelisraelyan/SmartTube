package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import android.os.Handler;
import android.os.Looper;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.BasePlayerController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.listener.PlayerEventListener;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;

import java.util.ArrayList;
import java.util.List;

/**
 * GRTubeYou: the menu behind the player's gear button.
 *
 * <p>The control rows used to carry 19 buttons at once, which on a TV means two
 * full screens of icons with the ones people actually press buried between them.
 * The gear collects what is touched rarely and leaves the rows with play,
 * previous, next, channel, like, dislike and subscribe.
 *
 * <p>Each entry dispatches the same button click the removed button used to send,
 * so no behaviour is reinvented here - only the way in moved. Quality keeps its
 * own dialog: HQDialogController builds the full one, and its button flag is now
 * off by default rather than removed.
 */
public class PlayerSettingsMenuController extends BasePlayerController {
    private static final String TAG = PlayerSettingsMenuController.class.getSimpleName();

    /**
     * GRTubeYou: how long to wait after closing this menu before firing the entry.
     * Long enough for the dialog to actually go away, short enough not to feel
     * like a lag.
     */
    private static final long DIALOG_SWAP_DELAY_MS = 200L;

    private final List<OptionItem> mItems = new ArrayList<>();
    private AppDialogPresenter mAppDialogPresenter;

    @Override
    public void onInit() {
        mAppDialogPresenter = AppDialogPresenter.instance(getContext());
    }

    @Override
    public void onButtonClicked(int buttonId, int buttonState) {
        if (buttonId == R.id.action_player_settings) {
            onSettingsClicked();
        }
    }

    private void onSettingsClicked() {
        if (getContext() == null) {
            return;
        }

        mItems.clear();

        // Labels are the ones the removed buttons already used, so the menu reads
        // exactly like the rows did - no second wording to learn.
        //
        // GRTubeYou: every entry is dispatched the way a real transport row button
        // is - a broadcast to all controllers, not to one guessed owner. Handing
        // everything to PlayerUIController was wrong and it showed: the owners are
        // scattered (speed lives in VideoStateController, chat in ChatController,
        // quality in HQDialogController, repeat in VideoLoaderController), so every
        // entry except the plain UI ones silently did nothing. PlaybackPresenter's
        // onButtonClicked already fans out to all listeners, which is the same path
        // the row buttons take, so behaviour cannot drift from it.
        addItem(R.string.action_video_speed, R.id.action_video_speed, true);
        addItem(R.string.lb_playback_controls_closed_captioning_enable, R.id.lb_control_closed_captioning, false);
        addItem(R.string.repeat_mode_all, R.id.action_repeat, false);
        addItem(R.string.action_video_info, R.id.action_info, false);
        addItem(R.string.screen_dimming, R.id.action_screen_dimming, false);
        addItem(R.string.action_search, R.id.action_search, false);
        addItem(R.string.playback_settings, R.id.lb_control_high_quality, false);
        addItem(R.string.run_in_background, R.id.action_pip, false);
        addItem(R.string.open_chat, R.id.action_chat, false);
        addItem(R.string.action_playback_queue, R.id.action_playback_queue, false);
        addItem(R.string.player_tweaks, R.id.action_video_stats, false);

        // One category per row, TYPE_SINGLE_BUTTON: this dialog cannot render
        // several values inside one category (see the button-list note in
        // AppPreferenceManager), and a separate category per row renders inline.
        for (OptionItem item : mItems) {
            mAppDialogPresenter.appendCategory(OptionCategory.singleButton(item));
        }

        // Same as HQDialogController: shrink the video so the dialog does not cover it.
        fitVideoIntoDialog();
        mAppDialogPresenter.showDialog(getContext().getString(R.string.player_settings), this::onDialogHide);
    }

    /**
     * @param longClick the entry means "open the picker for this" rather than
     *                  "press the button once". Only the speed button needs it: a
     *                  short press on the row toggles the stored speed, while the
     *                  long press opens the list - and from a menu, the list is
     *                  what the entry is obviously asking for.
     */
    private void addItem(int titleRes, int buttonId, boolean longClick) {
        mItems.add(UiOptionItem.from(getContext().getString(titleRes), option -> {
            // Close first. Entries that open a dialog of their own would otherwise
            // stack on top of this menu and leave both fighting for the D-pad.
            if (mAppDialogPresenter != null) {
                mAppDialogPresenter.closeDialog();
            }

            dispatch(buttonId, longClick);
        }));
    }

    private void dispatch(int buttonId, boolean longClick) {
        PlayerEventListener mainController = getMainController();

        if (mainController == null) {
            Log.e(TAG, "no main controller, " + buttonId + " dropped");
            return;
        }

        // Posted rather than run inline: the handlers open a dialog of their own,
        // and doing that in the same frame as closing this one left two dialogs
        // fighting for the D-pad - the new one was swallowed and the screen looked
        // like nothing had happened.
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (longClick) {
                mainController.onButtonLongClicked(buttonId, 0);
            } else {
                mainController.onButtonClicked(buttonId, 0);
            }
        }, DIALOG_SWAP_DELAY_MS);
    }

    private void onDialogHide() {
        mItems.clear();
    }
}
