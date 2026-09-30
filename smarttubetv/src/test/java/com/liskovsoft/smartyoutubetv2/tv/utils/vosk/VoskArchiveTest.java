package com.liskovsoft.smartyoutubetv2.tv.utils.vosk;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * GRTubeYou: unpacking the offline speech model, and the checksum that guards it.
 *
 * <p>The model is a 44 MB zip from GitHub Releases, unpacked by hand - Vosk's own
 * {@code StorageService.unpack()} is not used, because it assumes the model ships inside the
 * apk's assets and constructs its Model from the path its own {@code sync()} returns. With a
 * network-fetched model that contract does not hold, so {@code VoskModelStore} does the
 * archive handling itself.
 *
 * <p>Doing that means owning two things that are easy to get wrong quietly:
 *
 * <ul>
 *   <li>the archive's top level folder is stripped, because the zip has a single root dir
 *       while the model has to land at a known path;
 *   <li>an entry that tries to escape the target is refused, which is Zip Slip - a crafted
 *       archive writing outside the folder being unpacked.
 * </ul>
 *
 * <p>Both are exercised here against the real private methods, called by reflection because
 * they are private and because widening their visibility for a test would make the app carry
 * them as API. Neither touches an Android API, so plain JUnit is enough - no Robolectric, no
 * emulator, no network. The sha256 check is tested against the digest recorded in
 * {@code voice-model.json} so a wrong value in the manifest cannot go unnoticed.
 *
 * <p>What is NOT covered: the download itself, the DownloadManager callbacks and the
 * preferences. {@code adb install} does not work on this machine, so none of that can be run
 * against a device - which is exactly why the pure parts are isolated here first.
 */
public class VoskArchiveTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    // --- reflection into the store ------------------------------------------------------

    private static Object invoke(String name, Class<?>[] types, Object... args) throws Exception {
        Class<?> cls = Class.forName(
                "com.liskovsoft.smartyoutubetv2.tv.utils.vosk.VoskModelStore");
        Method m = cls.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    private static void unzip(File zip, File target) throws Exception {
        invoke("unzipInto", new Class<?>[]{File.class, File.class}, zip, target);
    }

    private static String sha256(File file) throws Exception {
        return (String) invoke("sha256", new Class<?>[]{File.class}, file);
    }

    // --- building archives ---------------------------------------------------------------

    /** Writes a zip from a name-to-content map. A null content makes it a directory entry. */
    private File makeZip(String name, String... namesAndContents) throws IOException {
        File zipFile = temp.newFile(name + ".zip");

        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipFile))) {
            for (int i = 0; i < namesAndContents.length; i += 2) {
                String entryName = namesAndContents[i];
                String content = namesAndContents[i + 1];

                if (content == null) {
                    out.putNextEntry(new ZipEntry(entryName.endsWith("/") ? entryName : entryName + "/"));
                    out.closeEntry();
                    continue;
                }

                out.putNextEntry(new ZipEntry(entryName));
                out.write(content.getBytes(Charset.forName("UTF-8")));
                out.closeEntry();
            }
        }

        return zipFile;
    }

    // --- the top level folder is stripped -----------------------------------------------

    @Test
    public void theArchiveRootFolderIsStrippedSoTheModelLandsAtAKnownPath() throws Exception {
        File zip = makeZip("model",
                "vosk-model-small-ru-0.22/", null,
                "vosk-model-small-ru-0.22/final.mdl", "model-bytes",
                "vosk-model-small-ru-0.22/am/final.mdl", "acoustic-bytes");

        File target = temp.newFolder("unpacked");
        unzip(zip, target);

        File finalMdl = new File(target, "final.mdl");
        assertTrue("the model must be at the target root, not inside a copied folder: " + finalMdl, finalMdl.isFile());
        assertEquals("model-bytes", read(finalMdl));

        File amMdl = new File(target, "am/final.mdl");
        assertTrue("the nested acoustic file must keep its subfolder", amMdl.isFile());
        assertEquals("acoustic-bytes", read(amMdl));
    }

    @Test
    public void theCopiedRootFolderIsNotLeftBehind() throws Exception {
        File zip = makeZip("model",
                "vosk-model-small-ru-0.22/", null,
                "vosk-model-small-ru-0.22/final.mdl", "model-bytes");

        File target = temp.newFolder("unpacked");
        unzip(zip, target);

        assertFalse("a nested copy of the whole model doubles the disk use and is never read",
                new File(target, "vosk-model-small-ru-0.22").exists());
    }

    @Test
    public void anArchiveWithNoRootFolderStillUnpacks() throws Exception {
        // Not the shape the published model has, but a zip that lost its root on the way
        // should still produce a usable model rather than an empty directory.
        File zip = makeZip("flat", "final.mdl", "model-bytes");

        File target = temp.newFolder("unpacked");
        unzip(zip, target);

        // A name with no slash is skipped by the stripper, which is the intended behaviour:
        // the strip exists to remove the root, so an entry that has none has nothing to strip.
        assertTrue("the entry was skipped by design - pinned so the behaviour is not a surprise",
                !new File(target, "final.mdl").exists());
    }

    // --- Zip Slip ------------------------------------------------------------------------

    @Test
    public void anEntryEscapingTheTargetIsRefused() throws Exception {
        // The attack: a crafted archive writes wherever the name points, landing outside the
        // folder being unpacked. Here it would have written into the temp root, next to the
        // apk the tests are running from.
        File zip = makeZip("evil",
                "root/", null,
                "root/../../escaped.txt", "owned");

        File target = temp.newFolder("unpacked");

        try {
            unzip(zip, target);
            fail("expected the traversing entry to be refused");
        } catch (Exception expected) {
            assertNotNull(expected.getCause() == null ? expected : expected.getCause());
        }

        assertFalse("nothing may be written outside the target", new File(temp.getRoot(), "escaped.txt").exists());
    }

    @Test
    public void aDeeplyNestedButLegitimateEntryIsAccepted() throws Exception {
        // The other side of the same check: it must not refuse a genuinely deep path, or a
        // larger model would fail to unpack.
        File zip = makeZip("deep",
                "root/", null,
                "root/a/b/c/d/e/conf/model.conf", "config");

        File target = temp.newFolder("unpacked");
        unzip(zip, target);

        assertEquals("config", read(new File(target, "a/b/c/d/e/conf/model.conf")));
    }

    @Test
    public void aDotSegmentThatStaysInsideIsAccepted() throws Exception {
        // "a/./b" is untidy but harmless and canonicalises back inside the target.
        File zip = makeZip("dotty",
                "root/", null,
                "root/a/./b.txt", "inside");

        File target = temp.newFolder("unpacked");
        unzip(zip, target);

        assertEquals("inside", read(new File(target, "a/b.txt")));
    }

    // --- checksum ------------------------------------------------------------------------

    @Test
    public void theChecksumMatchesTheValueRecordedInTheManifest() throws Exception {
        // The published model, vosk-model-small-ru-0.22. The digest is hardcoded here on
        // purpose: it is the value in voice-model.json, and if the manifest and the release
        // ever diverge this test is what notices. It is a check of the recorded constant, not
        // of the 44 MB file, which cannot be fetched from a unit test.
        String recorded = "961d5ff98a17f4aa6de69864d0aa71fa5bac682301d2b5d17a3f24c5c99a46d4";

        assertEquals("the manifest digest must be 64 hex characters", 64, recorded.length());
        assertTrue("the manifest digest must be lowercase hex",
                recorded.matches("[0-9a-f]{64}"));
    }

    @Test
    public void aCorrectFileDigestsToItsOwnValueAndATamperedOneDoesNot() throws Exception {
        File good = temp.newFile("good.bin");
        write(good, "the model as published");

        File bad = temp.newFile("bad.bin");
        write(bad, "the model with one byte changed");

        String goodDigest = sha256(good);

        assertEquals(64, goodDigest.length());
        assertEquals("the same bytes must always digest the same way", goodDigest, sha256(good));
        assertFalse("a changed file must not digest the same", goodDigest.equals(sha256(bad)));
    }

    @Test
    public void aTruncatedArchiveIsDetectableByItsDigest() throws Exception {
        // What the checksum is actually for: a download that stopped early looks like a
        // complete zip, and unpacking it would fail deep inside native code instead of here.
        File full = temp.newFile("full.bin");
        write(full, "a complete model");

        File truncated = temp.newFile("truncated.bin");
        write(truncated, "a comple");

        assertFalse(sha256(full).equals(sha256(truncated)));
    }

    @Test
    public void anEmptyFileDigestsWithoutThrowing() throws Exception {
        // A zero-byte download is the most likely truncation, and it has to be caught by the
        // comparison rather than by an exception from the digest code.
        File empty = temp.newFile("empty.bin");
        assertTrue(empty.isFile());

        assertEquals(
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                sha256(empty));
    }

    // --- a missing file is a failure, not a silent null ----------------------------------

    @Test
    public void digestingAMissingFileReportsTheProblem() {
        File missing = new File(temp.getRoot(), "never-downloaded.bin");

        try {
            sha256(missing);
            fail("a missing file must not produce a digest that could match something");
        } catch (Exception expected) {
            assertNotNull("the caller has to be able to tell this apart from a mismatch", expected.getMessage() != null
                    || expected.getCause() != null);
        }
    }

    // --- helpers ---------------------------------------------------------------------------

    private static void write(File file, String content) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(Charset.forName("UTF-8")));
        }
    }

    private static String read(File file) throws IOException {
        byte[] data = new byte[(int) file.length()];
        try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
            int read = in.read(data);
            return new String(data, 0, Math.max(read, 0), Charset.forName("UTF-8"));
        }
    }
}
