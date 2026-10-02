package com.liskovsoft.smartyoutubetv2.tv.utils.vosk;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.leanback.widget.SearchBar;
import androidx.leanback.widget.SpeechRecognitionCallback;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.prefs.SearchData;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * GRTubeYou: one place that wires the offline recognizer into a leanback
 * {@link SearchBar}.
 *
 * <p>There are three search bars in the app (the global search screen, the channel
 * header and the tag search), and each of them switches on the selected engine.
 * The wiring is identical in all three, so it lives here instead of being copied -
 * a fourth search bar should not mean a fourth copy of the microphone handling.
 *
 * <p>leanback's bar has a single "you start listening yourself" hook, the
 * deprecated {@link SpeechRecognitionCallback}, and it refuses to hold a callback
 * while a system recognizer is set. That is why the recognizer is nulled first.
 */
public final class VoskSearchBinder {
    private static final int REQUEST_AUDIO = 4712;

    /** How a recognised phrase reaches the host's result list. */
    public interface Submitter {
        void submit(String query);
    }

    private VoskSearchBinder() {
    }

    /**
     * @return the recognizer, so the caller can stop it when its screen goes away.
     */
    public static VoskVoiceSearch attach(Context context, SearchBar searchBar, Submitter submitter) {
        final Context app = context.getApplicationContext();

        VoskVoiceSearch vosk = new VoskVoiceSearch(app, new VoskVoiceSearch.Callback() {
            @Override
            public void onVoskPartial(String text) {
                // Feedback only. Submitting every partial would fire a search per
                // syllable.
                if (searchBar != null) {
                    searchBar.setSearchQuery(text);
                }
            }

            @Override
            public void onVoskFinal(String text) {
                if (searchBar != null) {
                    searchBar.setSearchQuery(text);
                    searchBar.stopRecognition();
                }

                submitter.submit(text);
            }

            @Override
            public void onVoskNothingHeard() {
                if (searchBar != null) {
                    searchBar.stopRecognition();
                }

                message(app, R.string.voice_search_nothing_heard);
            }

            @Override
            public void onVoskNoMicrophone() {
                if (searchBar != null) {
                    searchBar.stopRecognition();
                }

                // The likeliest failure on a TV box: the remote's mic never reaches
                // the app. Say so instead of leaving a button that does nothing.
                message(app, R.string.voice_search_no_microphone);
            }

            @Override
            public void onVoskNeedsPermission() {
                // NOTE: `context`, not `app`. requestPermissions only exists on an Activity,
                // so handing it the application context made the instanceof check below always
                // fail - the permission was never actually requested, and the user was told it
                // was needed on a button that could never work without it. Silent, permanent,
                // and only on the first run: the permission is never granted, so every later
                // press took this same dead branch.
                if (!requestAudio(context)) {
                    message(app, R.string.voice_search_need_permission);
                }
            }

            @Override
            public void onVoskModelStateChanged(VoskModelStore.State state) {
                switch (state) {
                    case READY:
                        // Do not start listening on our own - the user still has to
                        // press the orb.
                        break;
                    case DOWNLOADING:
                    case UNPACKING:
                        message(app, R.string.voice_search_downloading);
                        break;
                    case FAILED:
                        message(app, R.string.voice_search_model_failed);
                        break;
                    default:
                        // "Preparing" would be a lie when the user switched the
                        // automatic download off - nothing is coming.
                        if (SearchData.instance(app).isVoiceModelAutoDownloadEnabled()) {
                            message(app, R.string.voice_search_preparing);
                        } else {
                            message(app, R.string.voice_search_model_disabled);
                        }
                        break;
                }
            }
        });

        searchBar.setSpeechRecognizer(null);
        searchBar.setSpeechRecognitionCallback(vosk);

        return vosk;
    }

    private static boolean requestAudio(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true; // Granted at install time.
        }

        if (context instanceof Activity) {
            ((Activity) context).requestPermissions(
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO);
            return true;
        }

        return false;
    }

    private static void message(Context context, int resId) {
        MessageHelpers.showMessage(context, context.getString(resId));
    }
}
