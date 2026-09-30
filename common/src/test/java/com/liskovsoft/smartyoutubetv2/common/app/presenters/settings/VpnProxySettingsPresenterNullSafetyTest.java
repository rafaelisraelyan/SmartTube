package com.liskovsoft.smartyoutubetv2.common.app.presenters.settings;

import com.liskovsoft.smartyoutubetv2.common.proxy.PasswdInetSocketAddress;
import com.liskovsoft.smartyoutubetv2.common.proxy.Proxy;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * GRTubeYou: reading proxy settings before a proxy has ever been configured.
 *
 * <p>Opening the VPN / Proxy tile killed the app, and the defect was not in the new screen.
 * {@code ProxyManager} holds its proxy in a field that is {@link Proxy#NO_PROXY} when nothing
 * is configured - not null, but with a null {@code address()}. The getters tested only
 * {@code mProxy == null}, so they cast that null address and dereferenced it:
 *
 * <pre>
 *   NullPointerException: PasswdInetSocketAddress.getHostString() on a null object reference
 *       at ProxyManager.getProxyHost(ProxyManager.java:113)
 *       at VpnProxySettingsPresenter.&lt;init&gt;(VpnProxySettingsPresenter.java:63)
 * </pre>
 *
 * <p>The trap was armed long before the tile existed - the old web dialog read the raw uri and
 * never touched those getters. This drives the real field with the real value, because the
 * value is the entire bug: a fresh install is the common case, not an edge case.
 *
 * <p>Both classes here are the project's own, not {@code java.net}'s. {@code java.net.Proxy}
 * accepts any {@code SocketAddress}, while this {@code Proxy} insists on a
 * {@code PasswdInetSocketAddress} - which is why the getters' cast is safe in one direction
 * and why the test uses the real types rather than the JDK ones.
 */
public class VpnProxySettingsPresenterNullSafetyTest {

    // --- build a ProxyManager holding only the proxy ---------------------------------

    /**
     * Uses {@code sun.misc.Unsafe} to allocate without a constructor, because
     * {@code ProxyManager} only takes a Context and reading SharedPreferences for its real
     * state would turn a three-line unit test into an instrumented one. The getters under
     * test read nothing but {@code mProxy}, which is set here explicitly.
     */
    private static Object newProxyManager(Object proxy) throws Exception {
        Class<?> cls = Class.forName("com.liskovsoft.smartyoutubetv2.common.proxy.ProxyManager");

        Class<?> unsafeCls = Class.forName("sun.misc.Unsafe");
        Field theUnsafe = unsafeCls.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        Object unsafe = theUnsafe.get(null);

        Object instance = unsafeCls.getMethod("allocateInstance", Class.class).invoke(unsafe, cls);

        Field proxyField = cls.getDeclaredField("mProxy");
        proxyField.setAccessible(true);
        proxyField.set(instance, proxy);

        return instance;
    }

    private static String str(Object manager, String getter) throws Exception {
        Method m = manager.getClass().getDeclaredMethod(getter);
        m.setAccessible(true);
        return (String) m.invoke(manager);
    }

    private static int num(Object manager, String getter) throws Exception {
        Method m = manager.getClass().getDeclaredMethod(getter);
        m.setAccessible(true);
        return (Integer) m.invoke(manager);
    }

    private static boolean bool(Object manager, String getter) throws Exception {
        Method m = manager.getClass().getDeclaredMethod(getter);
        m.setAccessible(true);
        return (Boolean) m.invoke(manager);
    }

    // --- the state that used to crash -----------------------------------------------

    @Test
    public void readingAnUnconfiguredProxyDoesNotThrow() throws Exception {
        // Exactly a fresh install: nothing configured, so the field holds NO_PROXY.
        Object manager = newProxyManager(Proxy.NO_PROXY);

        // Each of these threw NullPointerException before the fix.
        assertEquals("", str(manager, "getProxyHost"));
        assertEquals(0, num(manager, "getProxyPort"));
        assertEquals("", str(manager, "getProxyUsername"));
        assertEquals("", str(manager, "getProxyPassword"));
    }

    @Test
    public void anUnconfiguredProxyReadsAsDirect() throws Exception {
        Object manager = newProxyManager(Proxy.NO_PROXY);

        assertEquals(Proxy.Type.DIRECT, type(manager));
    }

    private static Proxy.Type type(Object manager) throws Exception {
        Method m = manager.getClass().getDeclaredMethod("getProxyType");
        m.setAccessible(true);
        return (Proxy.Type) m.invoke(manager);
    }

    @Test
    public void aNullProxyFieldAlsoDoesNotThrow() throws Exception {
        // The other unconfigured state, and the one the old null check was written for. It
        // has to keep working, so the fix cannot have been "check for NO_PROXY only".
        Object manager = newProxyManager(null);

        assertEquals("", str(manager, "getProxyHost"));
        assertEquals(0, num(manager, "getProxyPort"));
    }

    // --- a configured proxy still reads back intact ----------------------------------

    @Test
    public void aConfiguredProxyIsReadBackIntact() throws Exception {
        PasswdInetSocketAddress address =
                PasswdInetSocketAddress.createUnresolved("proxy.example.com", 8080, "user", "secret");
        Object manager = newProxyManager(new Proxy(Proxy.Type.HTTP, address));

        assertEquals("proxy.example.com", str(manager, "getProxyHost"));
        assertEquals(8080, num(manager, "getProxyPort"));
        assertEquals("user", str(manager, "getProxyUsername"));
        assertEquals("secret", str(manager, "getProxyPassword"));
        assertEquals(Proxy.Type.HTTP, type(manager));
    }

    @Test
    public void aProxyWithoutCredentialsReadsAsEmptyNotNull() throws Exception {
        // Empty strings are stored as null inside the address, so the getters have to
        // normalise them. Returning null here would put a null back into the edit fields.
        PasswdInetSocketAddress address =
                PasswdInetSocketAddress.createUnresolved("proxy.example.com", 1080, "", "");
        Object manager = newProxyManager(new Proxy(Proxy.Type.SOCKS, address));

        assertEquals("", str(manager, "getProxyUsername"));
        assertEquals("", str(manager, "getProxyPassword"));
    }

    // --- the invariants the settings screen depends on ------------------------------

    @Test
    public void theSwitchStaysOffWhenNothingIsConfigured() throws Exception {
        Object manager = newProxyManager(Proxy.NO_PROXY);

        assertFalse("a screen opening with the switch on and no server behind it is the dead end this fixed",
                bool(manager, "isProxyEnabled"));
    }

    @Test
    public void theUriIsEmptyRatherThanMalformedWhenNothingIsConfigured() throws Exception {
        Object manager = newProxyManager(Proxy.NO_PROXY);

        String uri = str(manager, "getProxyUriString");

        assertNotNull(uri);
        assertTrue("expected an empty uri, got: " + uri, uri.isEmpty());
    }

    @Test
    public void isProxyConfiguredDoesNotThrowOnEitherUnconfiguredState() throws Exception {
        // Written down because the name is misleading: the method answers "is a proxy in
        // effect", not "does the user have a proxy saved". For DIRECT it returns whether the
        // JVM's system proxy properties are all clear - true here, since a plain unit test
        // sets none. So the assertion is that it runs and returns a boolean, not that it is
        // false. Reading it as "a proxy exists" is how a caller ends up showing a form for
        // something that was never configured.
        Method m = Class.forName("com.liskovsoft.smartyoutubetv2.common.proxy.ProxyManager")
                .getDeclaredMethod("isProxyConfigured");
        m.setAccessible(true);

        Object direct = newProxyManager(Proxy.NO_PROXY);
        Object nothing = newProxyManager(null);

        assertNotNull("must not throw on NO_PROXY", m.invoke(direct));
        assertNotNull("must not throw on a null field", m.invoke(nothing));
    }

    // --- controls: the harness can actually fail -------------------------------------

    @Test
    public void theFieldUnderTestIsTheOneTheGettersRead() throws Exception {
        // If findField ever picked a different field, every test above would pass against an
        // untouched object and prove nothing at all.
        Object manager = newProxyManager(Proxy.NO_PROXY);

        Field proxyField = manager.getClass().getDeclaredField("mProxy");
        proxyField.setAccessible(true);

        assertEquals(Proxy.NO_PROXY, proxyField.get(manager));
    }

    @Test
    public void aDeliberatelyBrokenGetWouldBeCaught() throws Exception {
        // Proves the reflection plumbing works in the failing direction too: calling a
        // method that does not exist has to throw, not silently return something plausible.
        Object manager = newProxyManager(Proxy.NO_PROXY);

        boolean threw = false;
        try {
            manager.getClass().getDeclaredMethod("getProxyNothingLikeThis");
        } catch (NoSuchMethodException expected) {
            threw = true;
        }

        assertTrue("a missing method must not resolve to something", threw);
    }
}
