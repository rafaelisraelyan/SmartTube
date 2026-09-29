package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.base.BasePresenter;
import com.liskovsoft.smartyoutubetv2.common.proxy.PasswdInetSocketAddress;
import com.liskovsoft.smartyoutubetv2.common.proxy.Proxy;
import com.liskovsoft.smartyoutubetv2.common.proxy.ProxyManager;
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * GRTubeYou: the "VPN / Proxy" tool.
 *
 * <p>What this is, plainly: the app sends its own traffic through a proxy server
 * you name here. That is every request reaching YouTube - the video and the API
 * calls alike - because they all leave from this one process.
 *
 * <p>What this is not: a system-wide tunnel. That needs a {@code VpnService}, a
 * TUN interface and a userspace IP stack speaking SOCKS (tun2socks), which
 * cannot be verified on a machine whose adb cannot install an apk. An untested
 * TCP/IP stack would look finished and fail on the first connect, so it is
 * deliberately not here yet; this is the tile it would go behind.
 *
 * <p>No new plumbing either: {@link ProxyManager} already kept a SOCKS5/HTTP
 * proxy with credentials and applied it to the system properties. This presenter
 * gives it a proper screen instead of the web dialog buried in general settings.
 */
public class VpnProxySettingsPresenter extends BasePresenter<Void> {
    /** Always up and accepting TCP; used only by the connection check. */
    private static final String TEST_HOST = "1.1.1.1";
    private static final int TEST_PORT = 443;
    private static final int TIMEOUT_MS = 8000;

    private final ProxyManager mProxyManager;
    private final Handler mUi = new Handler(Looper.getMainLooper());

    private String mHost = "";
    private int mPort;
    private String mLogin = "";
    private String mPassword = "";
    private Proxy.Type mType = Proxy.Type.SOCKS;
    private boolean mEnabled;

    public VpnProxySettingsPresenter(Context context) {
        super(context);
        mProxyManager = new ProxyManager(context);

        mHost = nullSafe(mProxyManager.getProxyHost());
        mPort = mProxyManager.getProxyPort();
        mLogin = nullSafe(mProxyManager.getProxyUsername());
        mPassword = nullSafe(mProxyManager.getProxyPassword());
        mType = mProxyManager.getProxyType() == Proxy.Type.HTTP ? Proxy.Type.HTTP : Proxy.Type.SOCKS;
        mEnabled = mProxyManager.isProxyEnabled();
    }

    public static VpnProxySettingsPresenter instance(Context context) {
        return new VpnProxySettingsPresenter(context);
    }

    public void show() {
        if (!mProxyManager.isProxySupported()) {
            MessageHelpers.showMessage(getContext(), R.string.proxy_unsupported_device);
            return;
        }

        AppDialogPresenter presenter = AppDialogPresenter.instance(getContext());

        presenter.appendSingleSwitch(UiOptionItem.from(
                getContext().getString(R.string.proxy_enabled),
                option -> {
                    mEnabled = option.isSelected();
                    apply();
                },
                mEnabled));

        presenter.appendRadioCategory(
                getContext().getString(R.string.proxy_type), buildTypeOptions());

        // One row per field. This dialog cannot render several editable values
        // inside one category: the only multi-row types it knows open a second
        // dialog of checkboxes - which is exactly how the player menu ended up
        // dead. A single-button category per field renders as a plain row.
        presenter.appendSingleButton(UiOptionItem.from(
                label(R.string.proxy_host, mHost), option -> editHost()));
        presenter.appendSingleButton(UiOptionItem.from(
                label(R.string.proxy_port, mPort > 0 ? String.valueOf(mPort) : ""), option -> editPort()));
        presenter.appendSingleButton(UiOptionItem.from(
                label(R.string.proxy_login, mLogin), option -> editLogin()));
        presenter.appendSingleButton(UiOptionItem.from(
                label(R.string.proxy_password, mask(mPassword)), option -> editPassword()));

        presenter.appendSingleButton(UiOptionItem.from(
                getContext().getString(R.string.proxy_test), option -> testConnection()));

        presenter.showDialog(getContext().getString(R.string.settings_vpn), null);
    }

    private List<OptionItem> buildTypeOptions() {
        List<OptionItem> options = new ArrayList<>();

        options.add(UiOptionItem.from(getContext().getString(R.string.proxy_type_socks5),
                option -> {
                    mType = Proxy.Type.SOCKS;
                    apply();
                },
                mType == Proxy.Type.SOCKS));

        options.add(UiOptionItem.from(getContext().getString(R.string.proxy_type_http),
                option -> {
                    mType = Proxy.Type.HTTP;
                    apply();
                },
                mType == Proxy.Type.HTTP));

        return options;
    }

    private String label(int titleRes, String value) {
        String text = value == null || value.isEmpty()
                ? getContext().getString(R.string.proxy_not_set)
                : value;

        return getContext().getString(titleRes) + ": " + text;
    }

    private String mask(String value) {
        if (value == null || value.isEmpty()) {
            return getContext().getString(R.string.proxy_not_set);
        }

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < value.length(); i++) {
            sb.append('*');
        }

        return sb.toString();
    }

    // ------------------------------------------------------------------ editing

    // SimpleEditDialog.OnChange returns whether the edit dialog may close, so a
    // rejected value keeps it open and the user can fix it in place. The settings
    // panel is redrawn from onDismiss, not from here: reopening a dialog from
    // inside its own dismiss callback is a race.

    public void editHost() {
        SimpleEditDialog.show(getContext(), getContext().getString(R.string.proxy_host),
                getContext().getString(R.string.proxy_host), mHost,
                text -> {
                    mHost = text == null ? "" : text.trim();
                    apply();
                    return true;
                },
                this::show);
    }

    public void editPort() {
        SimpleEditDialog.show(getContext(), getContext().getString(R.string.proxy_port),
                getContext().getString(R.string.proxy_port),
                mPort > 0 ? String.valueOf(mPort) : "",
                text -> {
                    int parsed;

                    try {
                        parsed = Integer.parseInt(text == null ? "" : text.trim());
                    } catch (NumberFormatException e) {
                        MessageHelpers.showMessage(getContext(), R.string.proxy_bad_port);
                        return false; // keep the editor open
                    }

                    if (parsed < 1 || parsed > 65535) {
                        MessageHelpers.showMessage(getContext(), R.string.proxy_bad_port);
                        return false;
                    }

                    mPort = parsed;
                    apply();
                    return true;
                },
                this::show);
    }

    public void editLogin() {
        SimpleEditDialog.show(getContext(), getContext().getString(R.string.proxy_login),
                getContext().getString(R.string.proxy_login), mLogin,
                text -> {
                    mLogin = text == null ? "" : text.trim();
                    apply();
                    return true;
                },
                this::show);
    }

    public void editPassword() {
        SimpleEditDialog.showPassword(getContext(), getContext().getString(R.string.proxy_password), mPassword,
                text -> {
                    mPassword = text == null ? "" : text;
                    apply();
                    return true;
                },
                this::show);
    }

    // ------------------------------------------------------------------ applying

    private void apply() {
        if (mHost.isEmpty() || mPort <= 0) {
            // Nothing to point at yet. A switch that reads "on" with no server
            // behind it is the same dead end the old web dialog was, so the stored
            // state is kept but not enabled.
            mProxyManager.saveProxyInfoToPrefs(null, false);
            return;
        }

        Proxy proxy = new Proxy(mType, PasswdInetSocketAddress.createUnresolved(
                mHost, mPort, mLogin, mPassword));

        mProxyManager.saveProxyInfoToPrefs(proxy, mEnabled);

        if (mEnabled) {
            mProxyManager.configureSystemProxy();
        }
    }

    // ------------------------------------------------------------------ checking

    /**
     * Speaks the proxy's own handshake instead of only opening a socket, so a
     * wrong password or a server that is up but refusing is reported as a failure
     * rather than looking like success.
     */
    private void testConnection() {
        if (mHost.isEmpty() || mPort <= 0) {
            MessageHelpers.showMessage(getContext(), R.string.proxy_not_set);
            return;
        }

        new Thread(() -> {
            String detail;
            boolean ok;

            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(mHost, mPort), TIMEOUT_MS);
                socket.setSoTimeout(TIMEOUT_MS);

                ok = mType == Proxy.Type.SOCKS ? socksConnect(socket) : httpConnect(socket);
                detail = ok ? "" : getContext().getString(R.string.proxy_test_refused);
            } catch (Exception e) {
                ok = false;
                String message = e.getMessage();
                detail = message == null || message.isEmpty()
                        ? e.getClass().getSimpleName()
                        : message;
            }

            final boolean success = ok;
            final String result = detail;

            mUi.post(() -> MessageHelpers.showMessage(getContext(),
                    getContext().getString(success
                            ? R.string.proxy_test_ok
                            : R.string.proxy_test_fail) + ": " + result));
        }, "proxy-test").start();
    }

    /** SOCKS5: greeting, optional auth, then CONNECT to a known-open target. */
    private boolean socksConnect(Socket socket) throws Exception {
        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();

        boolean hasAuth = !mLogin.isEmpty();

        out.write(new byte[]{0x05, (byte) (hasAuth ? 0x02 : 0x01), 0x00});
        out.flush();

        int version = in.read();
        int method = in.read();

        if (version != 0x05) {
            return false;
        }

        if (method == 0xFF) {
            return false; // server rejected every offered auth method
        }

        if (method == 0x02) {
            out.write(new byte[]{0x01, (byte) mLogin.length()});
            out.write(mLogin.getBytes("UTF-8"));
            out.write(new byte[]{(byte) mPassword.length()});
            out.write(mPassword.getBytes("UTF-8"));
            out.flush();

            if (in.read() != 0x01) {
                return false; // bad credentials
            }
        }

        out.write(new byte[]{
                0x05, 0x01, 0x00, 0x01,           // CONNECT, reserved, IPv4
                1, 1, 1, 1,                        // 1.1.1.1
                (byte) (TEST_PORT >> 8), (byte) TEST_PORT});
        out.flush();

        int reply = in.read();

        if (reply < 0) {
            return false;
        }

        in.read(); // reserved

        return reply == 0x00;
    }

    private boolean httpConnect(Socket socket) throws Exception {
        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();

        String auth = "";

        if (!mLogin.isEmpty()) {
            String raw = mLogin + ":" + mPassword;
            auth = "Proxy-Authorization: Basic "
                    + Base64.encodeToString(raw.getBytes("UTF-8"), Base64.NO_WRAP) + "\r\n";
        }

        String request = "CONNECT " + TEST_HOST + ":" + TEST_PORT + " HTTP/1.1\r\n"
                + "Host: " + TEST_HOST + ":" + TEST_PORT + "\r\n"
                + auth
                + "\r\n";

        out.write(request.getBytes("UTF-8"));
        out.flush();

        StringBuilder status = new StringBuilder();
        int c;

        while ((c = in.read()) >= 0 && status.length() < 200) {
            status.append((char) c);

            if (status.length() >= 4 && status.lastIndexOf("\r\n\r\n") > 0) {
                break;
            }
        }

        return status.toString().startsWith("HTTP/1.1 200")
                || status.toString().startsWith("HTTP/1.0 200");
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
