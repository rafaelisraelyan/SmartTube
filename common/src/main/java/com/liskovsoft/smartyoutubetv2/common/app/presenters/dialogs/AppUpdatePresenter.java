package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs;

import android.annotation.SuppressLint;
import android.content.Context;
import com.liskovsoft.appupdatechecker2.AppUpdateChecker;
import com.liskovsoft.appupdatechecker2.AppUpdateCheckerListener;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UpdateProgressBus;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.utils.LoadingManager;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

import java.util.ArrayList;
import java.util.List;

public class AppUpdatePresenter extends BasePresenter<Void> implements AppUpdateCheckerListener {
    @SuppressLint("StaticFieldLeak")
    private static AppUpdatePresenter sInstance;
    private static final String TAG = AppUpdatePresenter.class.getSimpleName();
    private final AppUpdateChecker mUpdateChecker;
    private final AppDialogPresenter mSettingsPresenter;
    private boolean mIsForceCheck;
    /** GRTubeYou: what the update panel is currently showing. */
    private State mState = State.IDLE;
    private String mVersionName;
    private List<String> mChangelog;

    private enum State {
        IDLE,
        AVAILABLE,
        DOWNLOADING,
        READY
    }

    public AppUpdatePresenter(Context context) {
        super(context);
        mUpdateChecker = new AppUpdateChecker(context, this);
        mSettingsPresenter = AppDialogPresenter.instance(context);
    }

    /**
     * GRTubeYou: stable or beta channel, depending on the "Beta features" switch.
     * Falls back to the stable URLs when the beta array is missing (other flavors).
     */
    private static String[] obtainUpdateManifestUrls(Context context) {
        if (GlobalPreferences.isBetaChannelEnabled(context)) {
            int betaUrls = context.getResources().getIdentifier("update_urls_beta", "array", context.getPackageName());

            if (betaUrls > 0) {
                String[] urls = context.getResources().getStringArray(betaUrls);

                if (urls.length > 0) {
                    return appendCacheBuster(urls);
                }
            }
        }

        return appendCacheBuster(context.getResources().getStringArray(R.array.update_urls));
    }

    /**
     * GRTubeYou: the manifest URL never changes, but its content does on every
     * release. Without a unique URL, any cache in between (CDN, proxy, ISP)
     * happily serves the previous version.json, and the app then reports
     * "you are up to date" while a newer build is already published.
     * A per-check timestamp makes every request unique.
     */
    private static String[] appendCacheBuster(String[] urls) {
        String[] result = new String[urls.length];

        for (int i = 0; i < urls.length; i++) {
            String url = urls[i];
            String separator = url.contains("?") ? "&" : "?";
            result[i] = url + separator + "t=" + System.currentTimeMillis();
        }

        return result;
    }

    public static AppUpdatePresenter instance(Context context) {
        if (sInstance == null) {
            sInstance = new AppUpdatePresenter(context);
        }

        sInstance.setContext(context);

        return sInstance;
    }

    public static void unhold() {
        sInstance = null;
    }

    public void start(boolean forceCheck) {
        mIsForceCheck = forceCheck;

        // GRTubeYou: resolved on every check, not once in the constructor. Building
        // the URL list there froze two things for the whole life of this presenter:
        // the channel (so a beta switch flipped while the app was running kept
        // reading version.json, whose max is below the installed build - the app
        // then correctly reported "you are up to date") and the cache-busting
        // timestamp (so a long-lived process kept re-requesting one fixed URL and
        // could be served a stale copy of it).
        String[] urls = obtainUpdateManifestUrls(getContext());

        Log.d(TAG, "update check, channel: " + (GlobalPreferences.isBetaChannelEnabled(getContext()) ? "beta" : "stable"));
        for (String url : urls) {
            Log.d(TAG, "update check, url: " + url);
        }

        if (forceCheck) {
            LoadingManager.showLoading(getContext(), true);
            mUpdateChecker.forceCheckForUpdates(urls);
        } else {
            mUpdateChecker.checkForUpdates(urls);
        }
    }

    /**
     * GRTubeYou: a newer version is known, but nothing has been downloaded yet.
     * This is the point where the panel can finally show something useful: what
     * changed, and a button that actually fetches the file.
     */
    @Override
    public void onUpdateAvailable(String versionName, List<String> changelog) {
        mVersionName = versionName;
        mChangelog = changelog;

        // The spinner stood in for the whole transfer; the panel takes over now.
        LoadingManager.showLoading(getContext(), false);

        if (canShowUpdateDialog()) {
            showUpdatePanel(State.AVAILABLE);
        } else {
            pinDownloadCard();
        }
    }

    @Override
    public void onDownloadProgress(int percent) {
        // A dropped value is fine - it just means the panel is not on screen.
        UpdateProgressBus.push(percent, getContext().getString(R.string.update_downloading));
    }

    @Override
    public void onUpdateFound(String versionName, List<String> changelog, String apkPath) {
        mVersionName = versionName;
        mChangelog = changelog;

        // GRTubeYou: arm the row BEFORE trying to rebuild the dialog.
        //
        // The rebuild below is conditional (canShowUpdateDialog), and the panel used
        // to have no other way to reach the "install" state. When the condition came
        // back false at the moment the file finished, the code quietly pinned a card
        // on the browse screen instead - invisible behind the dialog the user was
        // actually looking at - and the panel sat on a 100% bar forever with nothing
        // to press. The row turns itself into the install button, so completion no
        // longer depends on the dialog being rebuilt at all.
        UpdateProgressBus.setInstallAction(() -> {
            GeneralData.instance(getContext()).setChangelog(mChangelog);
            mUpdateChecker.installUpdate();
        });
        UpdateProgressBus.pushReady(getContext().getString(R.string.install_update));

        if (canShowUpdateDialog()) {
            showUpdatePanel(State.READY);
        } else {
            pinUpdateSection(versionName, changelog, apkPath);
        }
    }

    @Override
    public void onUpdateError(Exception error) {
        if (mIsForceCheck) {
            LoadingManager.showLoading(getContext(), false);
        }

        if (mState == State.DOWNLOADING) {
            // A failed transfer should leave the user able to try again, not stuck
            // on a bar that stopped moving.
            mState = State.AVAILABLE;
            MessageHelpers.showMessage(getContext(), R.string.update_download_failed);
            showUpdatePanel(State.AVAILABLE);
            onFinish();

            return;
        }

        if (mIsForceCheck) {
            if (AppUpdateCheckerListener.LATEST_VERSION.equals(error.getMessage())) {
                MessageHelpers.showMessage(getContext(), R.string.update_not_found);
            } else {
                MessageHelpers.showMessage(getContext(), String.format("%s: %s", getContext().getString(R.string.update_error),
                        error.getCause() != null ? error.getCause().getMessage() : error.getMessage()));
            }
        }

        onFinish();
    }

    private boolean canShowUpdateDialog() {
        // Don't show update dialog if the player opened or the app is collapsed
        if (getContext() == null || getViewManager().isPlayerInForeground() || !Utils.isAppInForegroundFixed()) {
            return false;
        }

        // A silent boot check only interrupts the user when they asked to hear about
        // updates; otherwise the update waits as a card on the browse screen.
        return mIsForceCheck || GeneralData.instance(getContext()).isOldUpdateNotificationsEnabled();
    }

    /**
     * GRTubeYou: the update panel. Same dialog as before, but the single button
     * now reflects the state, and while the file is on its way it is replaced by a
     * progress row instead of leaving the user with a spinner.
     */
    private void showUpdatePanel(State state) {
        mState = state;

        if (getContext() == null) {
            return;
        }

        switch (state) {
            case DOWNLOADING:
                mSettingsPresenter.appendCategory(OptionCategory.from(-1, OptionCategory.TYPE_UPDATE_PROGRESS,
                        getContext().getString(R.string.update_downloading), UiOptionItem.from("")));
                break;
            case READY:
                mSettingsPresenter.appendSingleButton(
                        UiOptionItem.from(getContext().getString(R.string.install_update), optionItem -> {
                            GeneralData.instance(getContext()).setChangelog(mChangelog);
                            mUpdateChecker.installUpdate();
                        }, false));
                break;
            case AVAILABLE:
            default:
                mSettingsPresenter.appendSingleButton(
                        UiOptionItem.from(getContext().getString(R.string.download_update), optionItem -> {
                            mState = State.DOWNLOADING;
                            mUpdateChecker.startDownload();
                            showUpdatePanel(State.DOWNLOADING);
                        }, false));
                break;
        }

        mSettingsPresenter.appendStringsCategory(getContext().getString(R.string.update_changelog), createChangelogOptions(mChangelog));

        mSettingsPresenter.showDialog(
                String.format("%s %s", getContext().getString(R.string.app_name), mVersionName),
                () -> {
                    UpdateProgressBus.clear();
                    AppUpdatePresenter.unhold();
                });
    }

    /**
     * GRTubeYou: silent check, update notices off. The card opens the panel instead
     * of installing straight away, so the download is never invisible.
     */
    private void pinDownloadCard() {
        if (getContext() == null) {
            return;
        }

        BrowsePresenter.instance(getContext()).pinItem(getContext().getString(R.string.update_found), R.drawable.action_info, new ErrorFragmentData() {
            @Override
            public void onAction() {
                mState = State.DOWNLOADING;
                mUpdateChecker.startDownload();
                showUpdatePanel(State.DOWNLOADING);
            }

            @Override
            public String getMessage() {
                return String.format("%s %s", getContext().getString(R.string.app_name), mVersionName) + " " +
                        getContext().getString(R.string.update_changelog) + ":\n" +
                        createChangelog(mChangelog);
            }

            @Override
            public String getActionText() {
                return getContext().getString(R.string.download_update);
            }
        });
    }

    private void pinUpdateSection(String versionName, List<String> changelog, String apkPath) {
        // Don't show update dialog if the player opened or the app is collapsed
        if (getContext() == null) {
            return;
        }

        BrowsePresenter.instance(getContext()).pinItem(getContext().getString(R.string.update_found), R.drawable.action_info, new ErrorFragmentData() {
            @Override
            public void onAction() {
                GeneralData.instance(getContext()).setChangelog(changelog);
                mUpdateChecker.installUpdate();
            }

            @Override
            public String getMessage() {
                return String.format("%s %s", getContext().getString(R.string.app_name), versionName) + " " +
                        getContext().getString(R.string.update_changelog) + ":\n" +
                        createChangelog(changelog);
            }

            @Override
            public String getActionText() {
                return getContext().getString(R.string.install_update);
            }
        });
    }

    private List<OptionItem> createChangelogOptions(List<String> changelog) {
        List<OptionItem> options = new ArrayList<>();

        for (String change : changelog) {
            options.add(UiOptionItem.from(change));
        }

        return options;
    }

    private String createChangelog(List<String> changelog) {
        StringBuilder builder = new StringBuilder();

        int maxLines = 30;
        int lineNum = 0;

        for (String change : changelog) {
            if (lineNum > maxLines) {
                break;
            }

            builder.append("- ");
            builder.append(change);
            builder.append("\n");

            lineNum++;
        }

        return builder.toString();
    }
}
