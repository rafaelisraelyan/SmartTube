package com.liskovsoft.smartyoutubetv2.tv.utils.vosk;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;

import androidx.leanback.widget.SpeechRecognitionCallback;

import com.liskovsoft.sharedutils.mylogger.Log;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * GRTubeYou: offline Russian voice search, driven by the search bar's microphone
 * orb.
 *
 * <p>Plugged into leanback through {@link SpeechRecognitionCallback#recognizeSpeech()},
 * which is the one hook the vendored {@code SearchBar} offers when no
 * {@code android.speech.SpeechRecognizer} is set. Results are pushed back with
 * {@code SearchBar.setSearchQuery()}, since that callback carries no result of
 * its own (it was designed for launching an external activity, which leanback has
 * since deprecated).
 *
 * <p>Why not the system recognizer: it needs a {@code com.google.*} package, and a
 * TV box without Google Services has none, so the button is simply dead there.
 * Vosk runs on the device and needs nothing but the microphone.
 */
public class VoskVoiceSearch implements SpeechRecognitionCallback, VoskModelStore.Listener {
    private static final String TAG = VoskVoiceSearch.class.getSimpleName();

    /** The small Russian model is 16 kHz mono. */
    private static final int SAMPLE_RATE = 16_000;

    /** Longest single utterance, so a stuck mic cannot listen forever. */
    private static final long MAX_UTTERANCE_MS = 12_000L;

    /** Silence after which the utterance is considered finished. */
    private static final long SILENCE_END_MS = 1_200L;

    /** Below this RMS the chunk counts as silence (roughly -45 dBFS). */
    private static final int SILENCE_RMS = 400;

    public interface Callback {
        /** Heard so far. Not submitted - shown as feedback only. */
        void onVoskPartial(String text);

        /** Utterance finished; this is what should be searched for. */
        void onVoskFinal(String text);

        /** Nothing usable was heard. */
        void onVoskNothingHeard();

        /** The remote's microphone is not readable by the app. */
        void onVoskNoMicrophone();

        /** RECORD_AUDIO has not been granted yet. */
        void onVoskNeedsPermission();

        void onVoskModelStateChanged(VoskModelStore.State state);
    }

    /** Cached because building a Model takes seconds and holds native memory. */
    private static Model sModel;
    private static String sModelPath;

    private final Context mContext;
    private final Callback mCallback;
    private final Handler mUi = new Handler(Looper.getMainLooper());
    private final AtomicBoolean mRunning = new AtomicBoolean();

    private Thread mWorker;
    private AudioRecord mRecord;
    private Recognizer mRecognizer;
    private boolean mHeardSomething;

    public VoskVoiceSearch(Context context, Callback callback) {
        mContext = context.getApplicationContext();
        mCallback = callback;
    }

    @Override
    public void onVoskModelStateChanged(VoskModelStore.State state) {
        mCallback.onVoskModelStateChanged(state);
    }

    @Override
    public void recognizeSpeech() {
        if (mRunning.get()) {
            stop();
            return;
        }

        if (mContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            mCallback.onVoskNeedsPermission();
            return;
        }

        File modelDir = VoskModelStore.getModelDir(mContext);

        if (modelDir == null) {
            // Nothing to recognise with. The search screen tells the user the model
            // is on its way instead of leaving the button dead.
            mCallback.onVoskModelStateChanged(VoskModelStore.getState(mContext));
            return;
        }

        start(modelDir);
    }

    private void start(final File modelDir) {
        mRunning.set(true);
        mHeardSomething = false;

        mWorker = new Thread(() -> {
            try {
                Model model = loadModel(modelDir);

                if (model == null) {
                    post(() -> mCallback.onVoskNothingHeard());
                    return;
                }

                mRecognizer = new Recognizer(model, SAMPLE_RATE);

                AudioRecord record = openMicrophone();

                if (record == null) {
                    post(() -> mCallback.onVoskNoMicrophone());
                    return;
                }

                mRecord = record;
                record.startRecording();
                listen(modelDir);
            } catch (Exception e) {
                Log.w(TAG, "recognition failed: " + e);
                post(() -> mCallback.onVoskNothingHeard());
            } finally {
                release();
                mRunning.set(false);
            }
        }, "vosk-recognizer");

        mWorker.start();
    }

    private void listen(File modelDir) {
        int bufferSize = Math.max(4096, AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 2);
        short[] buffer = new short[bufferSize / 2];

        long startedAt = System.currentTimeMillis();
        long lastVoiceAt = startedAt;

        while (mRunning.get() && System.currentTimeMillis() - startedAt < MAX_UTTERANCE_MS) {
            int read = mRecord.read(buffer, 0, buffer.length);

            if (read <= 0) {
                continue;
            }

            boolean voiced = isVoiced(buffer, read);

            if (voiced) {
                mHeardSomething = true;
                lastVoiceAt = System.currentTimeMillis();
            }

            if (mRecognizer.acceptWaveForm(buffer, read)) {
                // A whole utterance was recognised inside this chunk.
                String text = readResult(mRecognizer.getResult());

                if (!text.isEmpty()) {
                    post(() -> mCallback.onVoskFinal(text));
                    return;
                }
            } else {
                String partial = readResult(mRecognizer.getPartialResult());

                if (!partial.isEmpty() && mHeardSomething) {
                    post(() -> mCallback.onVoskPartial(partial));
                }
            }

            if (mHeardSomething && System.currentTimeMillis() - lastVoiceAt > SILENCE_END_MS) {
                // Trailing silence: flush whatever was left in the decoder.
                String text = readResult(mRecognizer.getFinalResult());

                if (!text.isEmpty()) {
                    post(() -> mCallback.onVoskFinal(text));
                } else {
                    post(() -> mCallback.onVoskNothingHeard());
                }

                return;
            }
        }

        // Timed out. Still flush, the model often has a complete phrase by now.
        if (mRecognizer != null) {
            String text = readResult(mRecognizer.getFinalResult());

            if (!text.isEmpty()) {
                post(() -> mCallback.onVoskFinal(text));
            } else if (mHeardSomething) {
                post(() -> mCallback.onVoskNothingHeard());
            }
        } else if (!mHeardSomething) {
            post(() -> mCallback.onVoskNothingHeard());
        }
    }

    private static Model loadModel(File modelDir) {
        String path = modelDir.getAbsolutePath();

        if (sModel != null && path.equals(sModelPath)) {
            return sModel;
        }

        releaseModel();

        try {
            sModel = new Model(path);
            sModelPath = path;
            Log.d(TAG, "model loaded from " + path);
        } catch (Exception e) {
            Log.w(TAG, "cannot load model at " + path + ": " + e);
            sModel = null;
            sModelPath = null;
        }

        return sModel;
    }

    private static synchronized void releaseModel() {
        if (sModel != null) {
            try {
                sModel.close();
            } catch (Exception e) {
                // Nothing to do: the process is going down with it.
            }

            sModel = null;
            sModelPath = null;
        }
    }

    /**
     * TV boxes expose wildly different capture sources, and a remote control
     * microphone often only appears on {@code MIC} or {@code DEFAULT} while
     * {@code VOICE_RECOGNITION} - the one Android recommends - stays unavailable.
     * Try them in order rather than picking one and hoping.
     */
    private AudioRecord openMicrophone() {
        int[] sources = {
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.DEFAULT,
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.VOICE_CALL
        };

        int bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);

        if (bufferSize <= 0) {
            Log.w(TAG, "no usable capture buffer size: " + bufferSize);
            return null;
        }

        for (int source : sources) {
            try {
                AudioRecord record = new AudioRecord(source, SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize * 2);

                if (record.getState() == AudioRecord.STATE_INITIALIZED) {
                    Log.d(TAG, "microphone opened on source " + source);
                    return record;
                }

                record.release();
            } catch (Exception e) {
                Log.d(TAG, "source " + source + " unavailable: " + e);
            }
        }

        return null;
    }

    private static boolean isVoiced(short[] buffer, int length) {
        double sum = 0d;

        for (int i = 0; i < length; i++) {
            double v = buffer[i] / 32768d;
            sum += v * v;
        }

        double rms = Math.sqrt(sum / Math.max(1, length));

        return rms * 32768d > SILENCE_RMS;
    }

    /** Vosk answers in json; the app only ever wants the text. */
    private static String readResult(String json) {
        if (json == null) {
            return "";
        }

        try {
            JSONObject obj = new JSONObject(json);
            String text = obj.optString("text", "");

            return text == null ? "" : text.trim();
        } catch (Exception e) {
            return "";
        }
    }

    private void post(Runnable runnable) {
        mUi.post(runnable);
    }

    /** Stop listening. Safe to call when not listening. */
    public void stop() {
        mRunning.set(false);
    }

    /** Stop and drop the cached native model (the app is going away). */
    public void release() {
        stop();

        if (mWorker != null) {
            mWorker.interrupt();
            mWorker = null;
        }

        releaseRecognizer();
    }

    private synchronized void releaseRecognizer() {
        if (mRecord != null) {
            try {
                if (mRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    mRecord.stop();
                }
            } catch (Exception e) {
                // Already gone.
            }

            mRecord.release();
            mRecord = null;
        }

        if (mRecognizer != null) {
            mRecognizer.close();
            mRecognizer = null;
        }
    }
}
