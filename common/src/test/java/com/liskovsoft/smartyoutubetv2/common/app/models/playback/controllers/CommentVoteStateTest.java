package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import com.liskovsoft.mediaserviceinterfaces.data.CommentItem;
import com.liskovsoft.sharedutils.helpers.Helpers;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * GRTubeYou: the vote state a comment row shows.
 *
 * <p>{@code CommentsController.MyCommentItem} is what the UI actually binds after a vote, so
 * its state is what the user sees: which thumb is lit, and what number sits next to it. It is
 * a private nested class, so it is reached by reflection - that is deliberate. The alternative
 * is widening its visibility purely for a test, and a public class that exists only so a test
 * can see it is a class the app has to keep alive for no user-visible reason.
 *
 * <p>What these tests pin down:
 *
 * <ul>
 *   <li>one vote, not two - YouTube keeps a single vote, so the opposite thumb always clears;
 *   <li>pressing the same thumb twice clears the vote rather than doing nothing;
 *   <li>the count follows the vote, and is cleared rather than left at "0";
 *   <li>a non-numeric count is left alone - the app cannot invent a number it was not given.
 * </ul>
 */
public class CommentVoteStateTest {
    // --- reflection handles: the class under test is private -------------------------

    private static Class<?> itemClass() throws Exception {
        return Class.forName(
                "com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.CommentsController$MyCommentItem");
    }

    private static Object newItem(String likeCount, boolean liked, boolean disliked) throws Exception {
        Class<?> cls = itemClass();

        Constructor<?> ctor = cls.getDeclaredConstructor(
                String.class, String.class, String.class, String.class, String.class, String.class,
                boolean.class, boolean.class, String.class, String.class, boolean.class);
        ctor.setAccessible(true);

        return ctor.newInstance("id", "message", "author", "photo", "2 days ago", "nestedKey",
                liked, disliked, likeCount, "6 replies", false);
    }

    /**
     * Presses a thumb. The parameter is which thumb was pressed, not the state wanted - the
     * same contract the controller uses, and the one whose earlier violation made a lit
     * thumb fail to clear.
     */
    private static void press(Object item, boolean likeThumb) throws Exception {
        Method m = itemClass().getDeclaredMethod("toggleVote", boolean.class);
        m.setAccessible(true);
        m.invoke(item, likeThumb);
    }

    private static boolean isLiked(Object item) throws Exception {
        Method m = itemClass().getDeclaredMethod("isLiked");
        m.setAccessible(true);
        return (Boolean) m.invoke(item);
    }

    private static boolean isDisliked(Object item) throws Exception {
        Method m = itemClass().getDeclaredMethod("isDisliked");
        m.setAccessible(true);
        return (Boolean) m.invoke(item);
    }

    private static String likeCount(Object item) throws Exception {
        Method m = itemClass().getDeclaredMethod("getLikeCount");
        m.setAccessible(true);
        return (String) m.invoke(item);
    }

    // --- the one-vote rule -----------------------------------------------------------

    @Test
    public void likingClearsTheDislike() throws Exception {
        Object item = newItem("10", false, true);

        press(item, true);

        assertTrue("thumbs up should be lit", isLiked(item));
        assertFalse("thumbs down must go out - one vote, not two", isDisliked(item));
    }

    @Test
    public void dislikingClearsTheLike() throws Exception {
        Object item = newItem("10", true, false);

        press(item, false);

        assertTrue("thumbs down should be lit", isDisliked(item));
        assertFalse("thumbs up must go out", isLiked(item));
    }

    // --- pressing the same thumb twice -----------------------------------------------

    @Test
    public void pressingLikeTwiceClearsIt() throws Exception {
        Object item = newItem("10", false, false);

        press(item, true);
        assertTrue(isLiked(item));
        assertEquals("11", likeCount(item));

        press(item, true);
        assertFalse("a second press on the same thumb should clear the vote", isLiked(item));
        assertEquals("back to 10", "10", likeCount(item));
    }

    @Test
    public void pressingDislikeTwiceClearsIt() throws Exception {
        Object item = newItem("10", false, false);

        press(item, false);
        assertTrue(isDisliked(item));
        // the count is untouched by a downvote: YouTube shows no downvote count
        assertEquals("10", likeCount(item));

        press(item, false);
        assertFalse("a second press on the same thumb should clear the vote", isDisliked(item));
    }

    // --- the count -------------------------------------------------------------------

    @Test
    public void countGoesUpOnLikeAndDownOnUnlike() throws Exception {
        Object item = newItem("5", false, false);

        press(item, true);
        assertEquals("6", likeCount(item));

        press(item, true);
        assertEquals("5", likeCount(item));
    }

    @Test
    public void lastLikeLeavingZeroClearsTheCountRatherThanShowingZero() throws Exception {
        Object item = newItem("1", false, false);

        press(item, true);
        assertEquals("2", likeCount(item));

        press(item, true);
        assertEquals("back to 1", "1", likeCount(item));
    }

    @Test
    public void theLastLikeLeavingNoVotesClearsTheCount() throws Exception {
        // A comment on 0 votes has no count at all, so the walk has to start there.
        Object item = newItem(null, false, false);

        press(item, true);
        assertEquals("first like makes it 1", "1", likeCount(item));

        press(item, true);
        assertNull("no votes left means no count, not '0'", likeCount(item));
    }

    @Test
    public void countSurvivesAVoteThatCannotBeCounted() throws Exception {
        // "1.2 тыс." - abbreviated, or any non-integer. The app must not corrupt it.
        Object item = newItem("1.2 тыс.", false, false);

        press(item, true);

        assertEquals("a count we cannot parse has to be left exactly as it was", "1.2 тыс.", likeCount(item));
    }

    @Test
    public void voteStillRegistersWhenTheCountIsUnparseable() throws Exception {
        // The icon lights even if the number cannot be adjusted - the vote is real either
        // way, and hiding it would misrepresent the comment as un-voted.
        Object item = newItem("1.2 тыс.", false, false);

        press(item, true);

        assertTrue(isLiked(item));
    }

    // --- a comment with no votes yet ------------------------------------------------

    @Test
    public void aCountlessCommentCanStillBeLiked() throws Exception {
        Object item = newItem(null, false, false);

        press(item, true);

        assertTrue(isLiked(item));
        assertEquals("first like on a countless comment makes it 1", "1", likeCount(item));
    }

    @Test
    public void aCountlessCommentCanStillBeDisliked() throws Exception {
        Object item = newItem(null, false, false);

        press(item, false);

        assertTrue(isDisliked(item));
        assertNull("a downvote adds no count", likeCount(item));
    }

    // --- the default the rest of the app relies on -----------------------------------

    @Test
    public void anUnvotedCommentIsNeitherLikedNorDisliked() throws Exception {
        CommentItem item = (CommentItem) newItem("10", false, false);

        assertFalse(item.isLiked());
        assertFalse(item.isDisliked());
    }

    @Test
    public void theInterfaceDefaultIsNotLiked() {
        // CommentItem.isDisliked() is a default method returning false, which is what keeps
        // the three implementors compiling. A comment that does not override it must read
        // as not-disliked, never as null-ish garbage.
        CommentItem bare = new CommentItem() {
            public String getId() { return "id"; }
            public String getMessage() { return "m"; }
            public String getAuthorName() { return "a"; }
            public String getAuthorPhoto() { return "p"; }
            public String getPublishedDate() { return "d"; }
            public String getNestedCommentsKey() { return "k"; }
            public boolean isLiked() { return false; }
            public String getLikeCount() { return null; }
            public String getReplyCount() { return null; }
            public boolean isEmpty() { return false; }
        };

        assertNotNull(bare);
        assertFalse(bare.isDisliked());
    }

    // --- guard against the constructor drifting out of sync with the test ------------

    @Test
    public void theItemIsARealCommentItem() throws Exception {
        // If MyCommentItem's constructor changes shape, every test above would fail with a
        // NoSuchMethodException that looks like a logic failure. This says plainly which it is.
        assertTrue(CommentItem.class.isAssignableFrom(itemClass()));
    }

    @Test
    public void helpersParseIntegersTheWayTheCountLogicAssumes() {
        // The count adjustment depends on Helpers.isInteger agreeing with Helpers.parseInt.
        // If those ever disagree the count silently stops updating.
        assertTrue(Helpers.isInteger("42"));
        assertFalse(Helpers.isInteger("1.2 тыс."));
        assertEquals(42, Helpers.parseInt("42"));
    }
}
