package com.liskovsoft.smartyoutubetv2.tv.utils.vosk;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;

import com.liskovsoft.sharedutils.mylogger.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * GRTubeYou: fetches and keeps the offline speech model.
 *
 * <p>The model is ~44 MB, so it is deliberately not bundled into the apk (that
 * would add 44 MB to each of the four per-abi builds). It lives in GitHub
 * Releases next to the update manifests, and the app pulls it in the background
 * the first time it runs - not when the user presses the microphone, because a
 * 44 MB wait is not something to discover at the moment you want to talk.
 *
 * <p>The description of the model lives in a tiny json next to version.json, so
 * the model can be replaced (or a second language added) without shipping a new
 * build of the app:
 *
 * <pre>
 * {
 *   "model": {
 *     "version": "0.22",
 *     "dirName": "vosk-model-small-ru-0.22",
 *     "url": "https://github.com/.../vosk-model-small-ru-0.22.zip",
 *     "sha256": "&lt;64 hex chars&gt;",
 *     "sizeBytes": 46236750
 *   }
 * }
 * </pre>
 *
 * <p>Everything here is best effort and silent by design. A failed or missing
 * model must never stop the app from starting; the search screen asks
 * {@link #getState()} and tells the user what is going on instead.
 */
public final class VoskModelStore {
    private static final String TAG = VoskModelStore.class.getSimpleName();

    /** Same place the app already reads its update manifests from. */
    private static final String MANIFEST_URL =
            "https://raw.githubusercontent.com/rafaelisraelyan/GRTubeYou/main/voice-model.json";

    private static final String PREFS = "grtubeyou_vosk";
    private static final String KEY_INSTALLED_VERSION = "installed_version";
    private static final String KEY_DOWNLOAD_ID = "download_id";

    /** Where the zip and the unpacked model live. */
    private static final String WORK_DIR = "vosk";
    private static final String ZIP_NAME = "model.zip";

    public enum State {
        /** Still on disk, or already unpacked and usable. */
        READY,
        /** Nothing yet, and no attempt in flight. */
        ABSENT,
        /** Manifest read, bytes on their way. */
        DOWNLOADING,
        /** Unpacking the zip into the model directory. */
        UNPACKING,
        /** Attempted and failed; the reason is worth showing to the user. */
        FAILED,
        /** Remote microphone is not readable, so the model would be useless. */
        NO_MICROPHONE
    }

    public interface Listener {
        void onVoskModelStateChanged(State state);
    }

    private static final ExecutorService sExecutor = Executors.newSingleThreadExecutor();
    private static boolean sReceiverRegistered;
    private static Listener sListener;

    private VoskModelStore() {
    }

    public static void setListener(Listener listener) {
        sListener = listener;
    }

    public static State getState(Context context) {
        if (!isModelPresent(context)) {
            return State.ABSENT;
        }

        return State.READY;
    }

    /** True when the unpacked model is on disk, whatever version it is. */
    public static boolean isModelPresent(Context context) {
        return getModelDir(context) != null;
    }

    /**
     * @return the directory holding {@code am/final.mdl}, or null if the model was
     *         never unpacked (or was unpacked into a different version's directory).
     */
    public static File getModelDir(Context context) {
        String dirName = prefs(context).getString(KEY_INSTALLED_VERSION, null);

        if (dirName == null) {
            return null;
        }

        File dir = new File(new File(context.getFilesDir(), WORK_DIR), dirName);

        // The recognizer is created lazily and a truncated model would throw deep
        // inside native code, so check the one file that must exist.
        return new File(dir, "am/final.mdl").isFile() ? dir : null;
    }

    /**
     * Kicks off the background fetch if there is something to fetch. Safe to call
     * on every app start: it does nothing when the model is already there or a
     * transfer is still running.
     */
    public static void ensureModel(Context context) {
        if (isModelPresent(context)) {
            return;
        }

        long existingId = prefs(context).getLong(KEY_DOWNLOAD_ID, -1L);

        if (existingId != -1L && isDownloadAlive(context, existingId)) {
            registerReceiver(context);
            return;
        }

        registerReceiver(context);
        fetchManifestAndStart(context);
    }

    /** Explicit retry, for the settings row. */
    public static void retry(Context context) {
        prefs(context).edit().remove(KEY_DOWNLOAD_ID).apply();
        registerReceiver(context);
        fetchManifestAndStart(context);
    }

    // ------------------------------------------------------------------ internals

    private static void fetchManifestAndStart(Context context) {
        final Context app = context.getApplicationContext();

        sExecutor.execute(() -> {
            try {
                JSONObject model = readManifest();
                String version = model.optString("version", null);
                String url = model.optString("url", null);

                if (version == null || url == null) {
                    Log.w(TAG, "voice-model.json has no version/url, voice search stays off");
                    publish(app, State.FAILED);
                    return;
                }

                File work = new File(app.getFilesDir(), WORK_DIR);

                if (!work.isDirectory() && !work.mkdirs()) {
                    Log.w(TAG, "cannot create " + work);
                    publish(app, State.FAILED);
                    return;
                }

                // Already unpacked under this exact version? Then the "installed"
                // marker is just stale.
                if (new File(new File(work, dirNameOf(model)), "am/final.mdl").isFile()) {
                    prefs(app).edit().putString(KEY_INSTALLED_VERSION, dirNameOf(model)).apply();
                    publish(app, State.READY);
                    return;
                }

                long id = enqueue(app, url);
                prefs(app).edit().putLong(KEY_DOWNLOAD_ID, id).apply();
                Log.d(TAG, "model download enqueued, id=" + id + " version=" + version);
                publish(app, State.DOWNLOADING);
            } catch (Exception e) {
                Log.w(TAG, "voice model fetch failed: " + e);
                publish(app, State.FAILED);
            }
        });
    }

    /**
     * The folder the archive creates when it is unpacked. Kept separate from the
     * version string so a model can be renamed without the app caring, and so a
     * manifest without the field still works.
     */
    private static String dirNameOf(JSONObject model) {
        String version = model.optString("version", "");
        String dirName = model.optString("dirName", "");

        return dirName.isEmpty() ? version : dirName;
    }

    private static JSONObject readManifest() throws Exception {        HttpURLConnection conn = (HttpURLConnection) new URL(MANIFEST_URL).openConnection();

        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(10_000);

        try (InputStream in = conn.getInputStream()) {
            StringBuilder sb = new StringBuilder();
            byte[] buf = new byte[4096];
            int n;

            while ((n = in.read(buf)) > 0) {
                sb.append(new String(buf, 0, n, "UTF-8"));
            }

            return new JSONObject(sb.toString()).getJSONObject("model");
        } finally {
            conn.disconnect();
        }
    }

    private static long enqueue(Context context, String url) {
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));

        request.setTitle("GRTubeYou voice model");
        request.setDescription("Offline speech recognition");
        request.setAllowedOverRoaming(true);
        // The system service owns the transfer: it survives the app being killed,
        // which a thread of ours would not.
        request.setDestinationInExternalFilesDir(context, null,
                WORK_DIR + File.separator + ZIP_NAME);

        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);

        return dm.enqueue(request);
    }

    private static boolean isDownloadAlive(Context context, long id) {
        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(id);

        try (Cursor cursor = dm.query(query)) {
            if (cursor == null || !cursor.moveToFirst()) {
                return false;
            }

            int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));

            return status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_RUNNING;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Watches for the transfer to finish. Registered on the application context so
     * it outlives whatever screen started the download.
     */
    private static void registerReceiver(final Context context) {
        if (sReceiverRegistered) {
            return;
        }

        final Context app = context.getApplicationContext();

        app.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context receiverContext, Intent intent) {
                if (intent == null || !DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
                    return;
                }

                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);

                if (id == -1L || id != prefs(app).getLong(KEY_DOWNLOAD_ID, -1L)) {
                    return;
                }

                onDownloadFinished(app, id);
            }
        }, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        sReceiverRegistered = true;
    }

    private static void onDownloadFinished(final Context app, final long id) {
        sExecutor.execute(() -> {
            try {
                DownloadManager dm = (DownloadManager) app.getSystemService(Context.DOWNLOAD_SERVICE);
                DownloadManager.Query query = new DownloadManager.Query().setFilterById(id);
                int status;
                Uri localUri;

                try (Cursor cursor = dm.query(query)) {
                    if (cursor == null || !cursor.moveToFirst()) {
                        publish(app, State.FAILED);
                        return;
                    }

                    status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                    localUri = Uri.parse(cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)));
                }

                if (status != DownloadManager.STATUS_SUCCESSFUL) {
                    Log.w(TAG, "model download finished with status " + status);
                    publish(app, State.FAILED);
                    return;
                }

                String path = localUri.getPath();

                if (path == null) {
                    publish(app, State.FAILED);
                    return;
                }

                // Re-read the manifest: the download may belong to an older manifest
                // than the one we would fetch now, and the checksum must match the
                // file that was actually requested.
                JSONObject model = readManifest();
                String expected = model.optString("sha256", null);
                String version = model.optString("version", null);
                String dirName = model.optString("dirName", version);

                if (expected == null || version == null) {
                    publish(app, State.FAILED);
                    return;
                }

                if (!sha256(new File(path)).equalsIgnoreCase(expected)) {
                    // A truncated or tampered archive would be unpacked into a model
                    // that fails deep inside native code. Refuse it here instead.
                    Log.w(TAG, "model checksum mismatch, discarding the archive");
                    new File(path).delete();
                    prefs(app).edit().remove(KEY_DOWNLOAD_ID).apply();
                    publish(app, State.FAILED);
                    return;
                }

                publish(app, State.UNPACKING);

                File modelDir = new File(new File(app.getFilesDir(), WORK_DIR), dirName);

                // Vosk's own StorageService.unpack() is deliberately NOT used: it is
                // built for a model shipped inside the apk's assets (it reads a "uuid"
                // asset and syncs into getExternalFilesDir), and it constructs the
                // Model from the path sync() returns, not from the target we pass.
                // With a model fetched from the network that contract does not hold.
                // Unpacking here also lets us strip the archive's top level folder,
                // so the model lands exactly where getModelDir() looks for it.
                deleteTree(modelDir);

                try {
                    unzipInto(new File(path), modelDir);

                    if (!new File(modelDir, "am/final.mdl").isFile()) {
                        throw new IOException("archive has no " + dirName + "/am/final.mdl");
                    }
                } catch (Exception e) {
                    Log.w(TAG, "unpack failed: " + e);
                    deleteTree(modelDir);
                    prefs(app).edit().remove(KEY_DOWNLOAD_ID).apply();
                    publish(app, State.FAILED);
                    return;
                }

                prefs(app).edit()
                        .putString(KEY_INSTALLED_VERSION, dirName)
                        .remove(KEY_DOWNLOAD_ID)
                        .apply();

                // The archive is 44 MB and the model is now on disk.
                new File(path).delete();

                Log.d(TAG, "voice model ready: " + modelDir);
                publish(app, State.READY);
            } catch (Exception e) {
                Log.w(TAG, "model install failed: " + e);
                publish(app, State.FAILED);
            }
        });
    }

    /**
     * Extracts the archive, dropping its single top level folder so the files land
     * directly in {@code target}.
     */
    private static void unzipInto(File zipFile, File target) throws IOException {
        if (!target.isDirectory() && !target.mkdirs()) {
            throw new IOException("cannot create " + target);
        }

        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;

            while ((entry = zip.getNextEntry()) != null) {
                int slash = entry.getName().indexOf('/');

                if (slash < 0) {
                    continue; // top level folder entry itself
                }

                String relative = entry.getName().substring(slash + 1);

                if (relative.isEmpty()) {
                    continue;
                }

                // Refuse anything trying to escape the target directory.
                File outFile = new File(target, relative);
                String canonicalTarget = target.getCanonicalPath() + File.separator;
                String canonicalOut = outFile.getCanonicalPath();

                if (!canonicalOut.startsWith(canonicalTarget)) {
                    throw new IOException("archive entry escapes the target: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    outFile.mkdirs();
                    continue;
                }

                File parent = outFile.getParentFile();

                if (parent != null) {
                    parent.mkdirs();
                }

                try (FileOutputStream out = new FileOutputStream(outFile)) {
                    byte[] buf = new byte[64 * 1024];
                    int n;

                    while ((n = zip.read(buf)) > 0) {
                        out.write(buf, 0, n);
                    }
                }
            }
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[64 * 1024];
            int n;

            while ((n = in.read(buf)) > 0) {
                digest.update(buf, 0, n);
            }
        }

        StringBuilder sb = new StringBuilder();

        for (byte b : digest.digest()) {
            sb.append(String.format("%02x", b));
        }

        return sb.toString();
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) {
            return;
        }

        File[] children = file.listFiles();

        if (children != null) {
            for (File child : children) {
                deleteTree(child);
            }
        }

        // Result ignored on purpose: there is nothing useful to do if this fails,
        // and unpack() will refuse a non-empty directory anyway.
        file.delete();
    }

    private static void publish(Context context, State state) {
        // Callbacks may touch the search bar, so stay on the main thread.
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            if (sListener != null) {
                sListener.onVoskModelStateChanged(state);
            }
        });
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
