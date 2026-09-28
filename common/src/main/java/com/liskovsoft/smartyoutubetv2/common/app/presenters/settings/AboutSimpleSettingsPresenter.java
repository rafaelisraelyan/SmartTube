package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import com.liskovsoft.appupdatechecker2.AppUpdateChecker;
import com.liskovsoft.sharedutils.helpers.AppInfoHelpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.ATVBridgePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AmazonBridgePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AppUpdatePresenter;

public class AboutSimpleSettingsPresenter extends BasePresenter<Void> {
    private final AppUpdateChecker mUpdateChecker;

    public AboutSimpleSettingsPresenter(Context context) {
        super(context);

        mUpdateChecker = new AppUpdateChecker(getContext(), null);
    }

    public static AboutSimpleSettingsPresenter instance(Context context) {
        return new AboutSimpleSettingsPresenter(context);
    }

    public void show() {
        String mainTitle = String.format("%s %s",
                getContext().getString(R.string.app_name) + " MOD",
                AppInfoHelpers.getAppVersionName(getContext()));

        AppDialogPresenter settingsPresenter = AppDialogPresenter.instance(getContext());

        // GRTubeYou: order requested by the user -
        // check for updates, changelog, beta channel, then how to be notified.
        appendUpdateCheckButton(settingsPresenter);

        appendBetaChannelSwitch(settingsPresenter);

        appendAutoUpdateSwitch(settingsPresenter);

        // GRTubeYou: the "Install ATV/Amazon bridge" entry is hidden. It installs a
        // helper APK that bridges to the official YouTube TV app for voice search -
        // GRTubeYou already has its own search, and the bridge pointed at the
        // upstream SmartTubeNext release URL. The method is kept for reference.
        //appendInstallBridge(settingsPresenter);

        settingsPresenter.showDialog(mainTitle);
    }

    /**
     * GRTubeYou: switch between the stable and the beta update channel.
     */
    private void appendBetaChannelSwitch(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleSwitch(UiOptionItem.from(
                getContext().getString(R.string.beta_features),
                optionItem -> {
                    GlobalPreferences.setBetaChannelEnabled(getContext(), optionItem.isSelected());
                    MessageHelpers.showMessage(getContext(), getContext().getString(R.string.beta_features_switched));
                    // The manifest list is baked in the constructor, so a new
                    // AppUpdatePresenter is needed for the channel to take effect.
                    AppUpdatePresenter.unhold();
                },
                GlobalPreferences.isBetaChannelEnabled(getContext())));
    }

    private void appendAutoUpdateSwitch(AppDialogPresenter settingsPresenter) {
        settingsPresenter.appendSingleSwitch(UiOptionItem.from(getContext().getString(R.string.check_updates_auto), optionItem -> {
            mUpdateChecker.setUpdateCheckEnabled(optionItem.isSelected());
        }, mUpdateChecker.isUpdateCheckEnabled()));
    }

    private void appendUpdateCheckButton(AppDialogPresenter settingsPresenter) {
        OptionItem updateCheckOption = UiOptionItem.from(
                getContext().getString(R.string.check_for_updates),
                option -> AppUpdatePresenter.instance(getContext()).start(true));

        settingsPresenter.appendSingleButton(updateCheckOption);
    }

    private void appendInstallBridge(AppDialogPresenter settingsPresenter) {
        OptionItem installBridgeOption = UiOptionItem.from(
                getContext().getString(R.string.enable_voice_search),
                option -> startBridgePresenter());

        settingsPresenter.appendSingleButton(installBridgeOption);
    }

    private void startBridgePresenter() {
        MessageHelpers.showLongMessage(getContext(), R.string.enable_voice_search_desc);

        ATVBridgePresenter atvPresenter = ATVBridgePresenter.instance(getContext());
        atvPresenter.runBridgeInstaller(true);
        atvPresenter.unhold();

        AmazonBridgePresenter amazonPresenter = AmazonBridgePresenter.instance(getContext());
        amazonPresenter.runBridgeInstaller(true);
        amazonPresenter.unhold();
    }
}
