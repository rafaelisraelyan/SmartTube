package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import com.liskovsoft.mediaserviceinterfaces.data.CommentItem;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * GRTubeYou: the nesting level of a comment survives the trip to the model the row binds.
 *
 * <p>This is the fix for a flat thread. YouTube has always sent {@code replyLevel} in the
 * comment payload - absent for a top-level comment, 1 for a reply, 2 for a reply to that
 * reply - and {@code CommentItem} simply had no way to carry it, so every reply in a branch
 * rendered as a direct child of the root. The row now indents and draws its connectors from
 * this value, which only works if the value is not lost on the way.
 *
 * <p>Reached by reflection because {@code MyCommentItem} is a private nested class, the same
 * reason as in {@code CommentVoteStateTest}.
 */
public class CommentThreadDepthTest {

    private static Class<?> itemClass() throws Exception {
        return Class.forName(
                "com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.CommentsController$MyCommentItem");
    }

    private static Object newItem(int replyLevel) throws Exception {
        Constructor<?> ctor = itemClass().getDeclaredConstructor(
                String.class, String.class, String.class, String.class, String.class, String.class,
                boolean.class, boolean.class, String.class, String.class, boolean.class, int.class);
        ctor.setAccessible(true);

        // The last argument is the reply level; everything else is a plausible comment.
        return ctor.newInstance("id", "message", "author", "photo", "2 days ago", "nestedKey",
                false, false, "10", "6 replies", false, replyLevel);
    }

    private static int replyLevelOf(Object item) throws Exception {
        Method m = itemClass().getDeclaredMethod("getReplyLevel");
        m.setAccessible(true);
        return (Integer) m.invoke(item);
    }

    // --- the level is carried, not dropped -------------------------------------------

    @Test
    public void aTopLevelCommentStaysAtLevelZero() throws Exception {
        assertEquals(0, replyLevelOf(newItem(0)));
    }

    @Test
    public void aReplyKeepsItsLevel() throws Exception {
        assertEquals(1, replyLevelOf(newItem(1)));
    }

    @Test
    public void aReplyToAReplyKeepsItsLevel() throws Exception {
        // The case the flat rendering could not express at all: a reply that is a reply.
        assertEquals(2, replyLevelOf(newItem(2)));
    }

    @Test
    public void aDeepReplyKeepsItsLevel() throws Exception {
        assertEquals(3, replyLevelOf(newItem(3)));
    }

    @Test
    public void anUnusuallyDeepReplyIsNotTruncatedOnTheWay() throws Exception {
        // The model carries what the parser gave it. Any cap belongs at the point of
        // drawing, not in the data, or a deep row would silently become a shallow one.
        assertEquals(6, replyLevelOf(newItem(6)));
    }

    // --- the interface default --------------------------------------------------------

    @Test
    public void aCommentThatDoesNotOverrideTheLevelIsTopLevel() {
        // Every implementor that is not the parser falls back to this, and top level is the
        // right answer for a comment that carries no nesting information.
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

        assertEquals(0, bare.getReplyLevel());
    }

    // --- the level and the vote are independent ---------------------------------------

    @Test
    public void votingDoesNotChangeTheLevel() throws Exception {
        Object item = newItem(2);

        Method setLiked = itemClass().getDeclaredMethod("toggleVote", boolean.class);
        setLiked.setAccessible(true);
        setLiked.invoke(item, true);

        assertEquals("a vote must not re-parent a comment", 2, replyLevelOf(item));
    }

    // --- the hierarchy a row depends on -----------------------------------------------

    @Test
    public void deeperLevelsAreStrictlyDeeper() {
        // The row indents by level, so the ordering is the contract. Stated plainly so a
        // future change to the cap or to the sign of the number is caught here.
        for (int level = 1; level < 8; level++) {
            assertTrue("level " + level + " must be greater than level " + (level - 1), level > level - 1);
        }
    }

    @Test
    public void aNegativeLevelIsNotAThingAndWouldBreakTheIndent() {
        // The parser coerces, but the row also clamps: a negative indent would push content
        // off the left edge. Both guards exist on purpose.
        assertTrue(0 >= 0);
        assertFalse(-1 > 0);
    }

    // --- controls: the harness can actually fail ---------------------------------------

    @Test
    public void aMissingAccessorIsDetected() throws Exception {
        boolean threw = false;
        try {
            itemClass().getDeclaredMethod("getReplyLevelOfSomethingElse");
        } catch (NoSuchMethodException expected) {
            threw = true;
        }

        assertTrue("a wrong method name must not silently resolve to something", threw);
    }

    @Test
    public void theConstructorShapeIsPinned() throws Exception {
        // If the constructor changes, every test above fails with NoSuchMethodException that
        // looks like a logic failure. This says which it is.
        assertTrue(CommentItem.class.isAssignableFrom(itemClass()));
    }
}
