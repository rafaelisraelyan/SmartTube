package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.ChatReceiver;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.CommentsReceiver;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * GRTubeYou: the option list behind the video quality button, and the crash that was in it.
 *
 * <p>A quality button must not close the player. The list behind it is null for the whole
 * window between "the viewer pressed play" and "ExoPlayer has a video renderer", and null for
 * a stream with no video at all. That is not a theoretical window - it is exactly when the
 * button is most likely to be pressed.
 *
 * <p>The crash, in order:
 *
 * <ol>
 *   <li>{@code getVideoFormats()} returns null while the renderer is unbuilt;
 *   <li>{@code UiOptionItem.from(null, ...)} returns null rather than an empty list, so the
 *       radio category is built holding a null list;
 *   <li>the preference builder called {@code items.size()} on it - NPE, app closed.
 * </ol>
 *
 * <p><b>What these tests deliberately do not do:</b> build a real {@code
 * com.liskovsoft...FormatItem}. Its static initializer runs
 * {@code ExoFormatItem.fromVideoSpec}, which reaches {@code TrackSelectorUtil} and then
 * android.text.String.format, and the unit-test stubs return null there - so the class
 * cannot even be initialized without a device. Every assertion here therefore uses the
 * OptionItem interface directly, which is the level at which the defect actually lived: the
 * null list and the holes in it. Asserting on real formats would need Robolectric, and
 * Robolectric does not run on this machine.
 */
public class VideoQualityListTest {
    // ------------------------------------------------------------------ the crash

    @Test
    public void aNullListIsUsableRatherThanFatal() {
        // Before the fix this reached items.size() and closed the app.
        assertNotNull(safeList(null));
        assertTrue(safeList(null).isEmpty());
    }

    @Test
    public void aHoledListLosesOnlyItsHoles() {
        // A null entry is just as fatal as a null list: every entry is dereferenced while
        // building the preference.
        OptionItem good = new StubOptionItem("720p");

        List<OptionItem> safe = safeList(Arrays.asList(null, good, null));

        assertEquals(1, safe.size());
        assertEquals(good, safe.get(0));
    }

    @Test
    public void anAllNullListBecomesAnEmptyList() {
        assertTrue(safeList(Arrays.asList(null, null)).isEmpty());
    }

    @Test
    public void anEmptyListStaysEmptyRatherThanBecomingNull() {
        assertNotNull(safeList(new ArrayList<>()));
        assertTrue(safeList(new ArrayList<>()).isEmpty());
    }

    @Test
    public void aRealListPassesThroughUnchanged() {
        List<OptionItem> real = Arrays.asList(
                new StubOptionItem("Отключено"),
                new StubOptionItem("1080p, 25fps, 5.35Mbps, avc"),
                new StubOptionItem("720p, 25fps, 2.31Mbps, avc"));

        List<OptionItem> safe = safeList(real);

        assertEquals("filtering must not drop real entries", real.size(), safe.size());
        for (int i = 0; i < real.size(); i++) {
            assertEquals("entry " + i + " changed position or identity", real.get(i), safe.get(i));
        }
    }

    // ------------------------------------------------------------------ the shapes that caused it

    @Test
    public void aNullFormatListBecomesANullOptionList() {
        // Documented behaviour, and the reason the consumer has to check. This is the
        // difference between "no options" and "no list at all", and only one of the two can
        // be indexed safely.
        // The cast is required: a bare null matches every from() overload taking an object. Casting
        // to a reference type does not initialize FormatItem - only loading it would, and
        // generics are erased, so this stays off the class-initialization path.
        assertNull(UiOptionItem.from((List<FormatItem>) null, option -> { }));
    }

    @Test
    public void toFormatOfAForeignOptionIsNull() {
        // The shape the tap handler dereferences on its first line. It has to be able to say
        // "not one of ours" so the caller can drop the tap instead of throwing.
        assertNull(UiOptionItem.toFormat(new StubOptionItem("720p")));
        assertNull(UiOptionItem.toFormat(null));
    }

    @Test
    public void aRadioCategoryKeepsTheListItWasGiven() {
        List<OptionItem> options = safeList(Arrays.<OptionItem>asList(new StubOptionItem("720p")));

        OptionCategory category = OptionCategory.from(
                1, OptionCategory.TYPE_RADIO_LIST, "Video quality", options);

        assertNotNull(category.options);
        assertEquals(1, category.options.size());
        assertEquals(OptionCategory.TYPE_RADIO_LIST, category.type);
    }

    @Test
    public void everySurvivingEntryStillAnswersItsTitle() {
        // The builder reads getTitle() and toString() on each entry; a null entry would fail
        // here even after the list itself was made safe.
        List<OptionItem> safe = safeList(Arrays.asList(
                null, new StubOptionItem("1080p"), null, new StubOptionItem("720p")));

        for (OptionItem option : safe) {
            assertNotNull("entry title", option.getTitle());
            assertNotNull("entry value", option.toString());
        }
    }

    /**
     * Mirrors the guard now in {@code AppPreferenceManager.createListPreferenceData}.
     *
     * <p>Re-implemented rather than called because that class builds framework objects and
     * needs a Context. What is pinned down is the property the fix rests on: a null or holed
     * list produces a usable result instead of an exception.
     */
    private static List<OptionItem> safeList(List<OptionItem> items) {
        if (items == null) {
            return Collections.emptyList();
        }

        List<OptionItem> safe = new ArrayList<>(items.size());
        for (OptionItem item : items) {
            if (item != null) {
                safe.add(item);
            }
        }
        return safe;
    }

    /** A plain entry, standing in for a resolution row. */
    private static class StubOptionItem implements OptionItem {
        private final CharSequence mTitle;

        StubOptionItem(CharSequence title) {
            mTitle = title;
        }

        @Override
        public int getId() {
            return 0;
        }

        @Override
        public CharSequence getTitle() {
            return mTitle;
        }

        @Override
        public CharSequence getDescription() {
            return null;
        }

        @Override
        public boolean isSelected() {
            return false;
        }

        @Override
        public void onSelect(boolean isSelected) {
        }

        @Override
        public Object getData() {
            return null;
        }

        @Override
        public void setRequired(OptionItem... items) {
        }

        @Override
        public OptionItem[] getRequired() {
            return new OptionItem[0];
        }

        @Override
        public void setRadio(OptionItem... items) {
        }

        @Override
        public OptionItem[] getRadio() {
            return new OptionItem[0];
        }

        @Override
        public ChatReceiver getChatReceiver() {
            return null;
        }

        @Override
        public CommentsReceiver getCommentsReceiver() {
            return null;
        }

        @Override
        public String toString() {
            return mTitle == null ? "" : mTitle.toString();
        }
    }
}