package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.BasePlayerController;
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
        addItem(R.string.action_video_speed, R.id.action_video_speed, null);
        addItem(R.string.lb_playback_controls_closed_captioning_enable, R.id.lb_control_closed_captioning, null);
        addItem(R.string.repeat_mode_all, R.id.action_repeat, null);
        addItem(R.string.action_video_info, R.id.action_info, null);
        addItem(R.string.screen_dimming, R.id.action_screen_dimming, null);
        addItem(R.string.action_search, R.id.action_search, null);

        // Routed to another controller, same as the original row button did.
        addItem(R.string.playback_settings, R.id.lb_control_high_quality, HQDialogController.class);
        addItem(R.string.run_in_background, R.id.action_pip, null);
        addItem(R.string.open_chat, R.id.action_chat, null);
        addItem(R.string.action_playback_queue, R.id.action_playback_queue, null);
        addItem(R.string.player_tweaks, R.id.action_video_stats, null);

        mAppDialogPresenter.appendStringsCategory(
                getContext().getString(R.string.player_settings), mItems);

        // Same as HQDialogController: shrink the video so the dialog does not cover it.
        fitVideoIntoDialog();
        mAppDialogPresenter.showDialog(getContext().getString(R.string.player_settings), this::onDialogHide);
    }

    /**
     * @param targetController controller that owns the action, or null for the
     *                        general one (PlayerUIController)
     */
    private void addItem(int titleRes, int buttonId, Class<? extends BasePlayerController> targetController) {
        mItems.add(UiOptionItem.from(getContext().getString(titleRes), option -> {
            // Close first. Entries that open a dialog of their own would otherwise
            // stack on top of this menu and leave both fighting for the D-pad.
            if (mAppDialogPresenter != null) {
                mAppDialogPresenter.closeDialog();
            }

            dispatch(buttonId, targetController);
        }));
    }

    private void dispatch(int buttonId, Class<? extends BasePlayerController> targetController) {
        BasePlayerController controller = targetController != null
                ? getController(targetController)
                : getController(PlayerUIController.class);

        if (controller != null) {
            controller.onButtonClicked(buttonId, 0);
        }
    }

    private void onDialogHide() {
        mItems.clear();
    }
}
