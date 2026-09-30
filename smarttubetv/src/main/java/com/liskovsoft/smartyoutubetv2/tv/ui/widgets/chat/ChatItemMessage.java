package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.chat;

import android.content.Context;
import android.text.TextUtils;

import com.liskovsoft.mediaserviceinterfaces.data.ChatItem;
import com.liskovsoft.mediaserviceinterfaces.data.CommentItem;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.stfalcon.chatkit.commons.models.IMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class ChatItemMessage implements IMessage {
    private static final int MAX_LENGTH = 700;
    private static final int LINE_LENGTH = 30;
    private String mId;
    private CharSequence mText;
    private ChatItemAuthor mAuthor;
    private Date mCreatedAt;
    private CommentItem mCommentItem;
    private String mAuthorName;
    private String mPublishedDate;
    private String mLikeCount;
    private String mReplyCount;
    private boolean mLiked;
    private boolean mDisliked;

    /**
     * GRTubeYou: live chat keeps the inline author prefix.
     *
     * <p>Only the comments list was restructured. A chat line is a single utterance by one
     * author - "@nick: text" reads correctly in one flow - and there is no vote or reply
     * structure to lay out, so the header stays part of the text here. Untouched on purpose.
     */
    public static ChatItemMessage from(ChatItem chatItem) {
        ChatItemMessage message = new ChatItemMessage();
        message.mId = chatItem.getId();
        if (chatItem.getMessage() != null && !chatItem.getMessage().trim().isEmpty()) {
            message.mText = TextUtils.concat(Utils.bold(chatItem.getAuthorName()), ": ", chatItem.getMessage());
        }
        message.mAuthor = ChatItemAuthor.from(chatItem);
        message.mCreatedAt = new Date();
        message.mAuthorName = chatItem.getAuthorName();

        return message;
    }

    /**
     * GRTubeYou: the row is no longer one string.
     *
     * <p>It used to be a single bold header line - "@name · 812 · 4 дня назад · 6 ответов" -
     * followed by the body, both rendered by one TextView inside a grey card. That is the
     * messenger look the flat list replaces, and a single span cannot express the new
     * structure anyway: the author is a separate weight from the timestamp, the vote count
     * belongs in the action row rather than next to the name, and the reply count is its own
     * tappable row with a chevron.
     *
     * <p>So each part is kept in its own field and the layout binds them to their own views.
     * {@link #getText()} still returns just the body, which is what the text view shows and
     * what {@code IMessage.checkMessage} requires.
     *
     * <p>The unused {@code context} parameter is kept on purpose: it is public API called from
     * the fragment and from {@link #fromSplit}.
     */
    public static ChatItemMessage from(Context context, CommentItem commentItem) {
        ChatItemMessage message = new ChatItemMessage();
        message.mId = commentItem.getId();
        message.mText = commentItem.getMessage();
        message.mAuthorName = commentItem.getAuthorName();
        message.mPublishedDate = commentItem.getPublishedDate();
        message.mLikeCount = commentItem.getLikeCount();
        message.mReplyCount = commentItem.getReplyCount();
        message.mLiked = commentItem.isLiked();
        message.mDisliked = commentItem.isDisliked();
        message.mAuthor = ChatItemAuthor.from(commentItem);
        message.mCreatedAt = new Date();
        message.mCommentItem = commentItem;

        return message;
    }

    public static List<ChatItemMessage> fromSplit(Context context, CommentItem commentItem) {
        if (shouldSplit(commentItem)) {
            List<String> comments = Helpers.splitStringBySize(commentItem.getMessage(), getRealMaxLen(commentItem.getMessage()));
            List<ChatItemMessage> result = new ArrayList<>();
            for (int i = 0; i < comments.size(); i++) {
                String prefix = i > 0 ? "..." : "";
                String postfix = i < (comments.size() - 1) ? "..." : "";
                String comment = prefix + comments.get(i) + postfix;
                result.add(from(context, new CommentItem() {
                    public String getId() {
                        return String.valueOf(comment.hashCode());
                    }

                    public String getMessage() {
                        return comment;
                    }

                    public String getAuthorName() {
                        return commentItem.getAuthorName();
                    }

                    public String getAuthorPhoto() {
                        return commentItem.getAuthorPhoto();
                    }

                    public String getPublishedDate() {
                        return commentItem.getPublishedDate();
                    }

                    public String getNestedCommentsKey() {
                        return commentItem.getNestedCommentsKey();
                    }

                    public boolean isLiked() {
                        return commentItem.isLiked();
                    }

                    // GRTubeYou: the vote state is shown per row now, so a split piece of a
                    // downvoted comment has to carry the flag too. Left out, it fell through
                    // to the interface default and the thumbs read as un-pressed.
                    public boolean isDisliked() {
                        return commentItem.isDisliked();
                    }

                    public String getLikeCount() {
                        return commentItem.getLikeCount();
                    }

                    public String getReplyCount() {
                        return commentItem.getReplyCount();
                    }

                    @Override
                    public boolean isEmpty() {
                        return commentItem.isEmpty();
                    }
                }));
            }
            return result;
        }

        return Collections.singletonList(from(context, commentItem));
    }

    public static boolean shouldSplit(CommentItem commentItem) {
        return commentItem != null && commentItem.getMessage() != null && commentItem.getMessage().length() > getRealMaxLen(commentItem.getMessage());
    }

    private static int getRealMaxLen(String text) {
        if (text == null) {
            return -1;
        }

        String[] split = text.split("\n");

        if (split.length == 1) {
            return MAX_LENGTH;
        }

        List<String> splitNoLongLines = new ArrayList<>();
        for (String line : split) {
            while (line.length() > LINE_LENGTH) {
                int breakPoint = line.lastIndexOf(' ', LINE_LENGTH);

                if (breakPoint == -1) {
                    breakPoint = LINE_LENGTH;
                }

                splitNoLongLines.add(line.substring(0, breakPoint));
                line = line.substring(breakPoint).trim();
            }
            splitNoLongLines.add(line);
        }
        split = splitNoLongLines.toArray(new String[0]);

        int realCount = 0;
        int fakeCount = 0;

        for (String part : split) {
            realCount += part.length();
            fakeCount += Math.max(part.length(), LINE_LENGTH);

            if (fakeCount > MAX_LENGTH) {
                return realCount;
            }
        }

        return MAX_LENGTH;
    }

    @Override
    public String getId() {
        return mId;
    }

    @Override
    public CharSequence getText() {
        return mText;
    }

    @Override
    public ChatItemAuthor getUser() {
        return mAuthor;
    }

    @Override
    public Date getCreatedAt() {
        return mCreatedAt;
    }

    public CommentItem getCommentItem() {
        return mCommentItem;
    }

    /** @return author name, or null when the payload had none. */
    public String getAuthorName() {
        return mAuthorName;
    }

    /** @return relative publish time such as "4 дня назад", already localised by YouTube. */
    public String getPublishedDate() {
        return mPublishedDate;
    }

    /** @return upvote count, or null when the comment has no votes yet. */
    public String getLikeCount() {
        return mLikeCount;
    }

    /**
     * @return the reply count phrase, already localised by YouTube ("6 ответов"), or null
     * when the comment has no replies. Shown as-is rather than rebuilt from a number, so
     * the app does not have to guess at a plural form.
     */
    public String getReplyCount() {
        return mReplyCount;
    }

    public boolean isLiked() {
        return mLiked;
    }

    public boolean isDisliked() {
        return mDisliked;
    }

    /**
     * @return true when this row can open a thread. A comment with no replies has no
     * continuation key, and asking for one would open an empty dialog.
     */
    public boolean hasThread() {
        return mReplyCount != null && !mReplyCount.trim().isEmpty() && mCommentItem != null && !mCommentItem.isEmpty();
    }
}
