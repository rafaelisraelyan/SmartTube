package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.chat;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.stfalcon.chatkit.messages.MessageHolders;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * GRTubeYou: the flat comment row.
 *
 * <p>Replaces chatkit's {@code IncomingTextMessageViewHolder} for comments. The old holder
 * assumed one bold string and a card: it set {@code messageText} to whatever
 * {@link ChatItemMessage#getText()} returned and left {@code @id/bubble} to the style's
 * rounded background. The new row has an author line, a body, an action row and a reply row,
 * so the binding happens here instead.
 *
 * <p>Extends {@code BaseIncomingMessageViewHolder} rather than the text one on purpose: the
 * base is where avatar loading and avatar sizing live, both of which still apply, and it
 * keeps {@code applyStyle} wired up. Its {@code time} field is left unbound - the row has no
 * {@code messageTime} view - so the base simply skips it, and the timestamp is bound to
 * {@code commentTime} instead, in a muted colour on the author line.
 *
 * <p>Two of the bound strings are already localised by YouTube when the comments are
 * fetched: the timestamp ("4 дня назад") and the reply count ("6 ответов"). Both are shown
 * verbatim. Rebuilding the reply phrase from a number would mean the app carrying its own
 * plural forms, and it would still get them wrong for languages this build does not ship.
 */
public class CommentItemViewHolder extends MessageHolders.BaseIncomingMessageViewHolder<ChatItemMessage> {
    private TextView mAuthor;
    private TextView mTime;
    private TextView mText;
    private TextView mLikeCount;
    private TextView mReplyAction;
    private TextView mRepliesCount;
    private ImageView mLikeIcon;
    private ImageView mDislikeIcon;
    private View mThreadLine;
    private View mRepliesRow;

    /**
     * chatkit's holder factory looks for {@code (View, Object)} first and falls back to
     * {@code (View)}. Only the single-argument form is declared, so the fallback is taken -
     * the same path the stock holders take.
     */
    public CommentItemViewHolder(View itemView) {
        super(itemView);
        init(itemView);
        applyRowBackground();
    }

    @Override
    public void onBind(ChatItemMessage message) {
        super.onBind(message);

        mAuthor.setText(nullSafe(message.getAuthorName()));
        mTime.setText(nullSafe(message.getPublishedDate()));
        mText.setText(nullSafe(message.getText()));

        bindVote(message);
        bindThread(message);
    }

    /**
     * GRTubeYou: the focus ring, replacing the old filled card.
     *
     * <p>chatkit's incoming text holder used to do the same job with a tinted bubble
     * ({@code shape_incoming_message_focused}). A fill is the wrong shape for a flat list, so
     * the ring is a hairline outline and nothing else - a resting row keeps no background at
     * all, which is what makes the list read as a list.
     *
     * <p>Not set in the layout, and not in {@code applyStyle}: this holder cannot override
     * that method, because {@code MessagesListStyle} is package-private to chatkit and the
     * signature would not be overridable from another package. It is set once in the
     * constructor instead - a ring is a fixed property of the row, unlike the old bubble
     * colours, which were swapped per focus change and per selection.
     */
    private void applyRowBackground() {
        itemView.setBackgroundResource(R.drawable.comment_focus_background);
    }

    /**
     * The vote: one icon filled, the other hollow, the count beside the up thumb.
     *
     * <p>Like and dislike are a single choice, so exactly one is ever active. The count is
     * hidden when the comment has no votes rather than shown as a zero - an empty comment has
     * no votes, and a "0" would be a claim the payload never made.
     */
    private void bindVote(ChatItemMessage message) {
        boolean liked = message.isLiked();
        boolean disliked = message.isDisliked();

        setIconActive(mLikeIcon, liked, R.drawable.comment_thumb_up, R.drawable.comment_thumb_up_active);
        setIconActive(mDislikeIcon, disliked, R.drawable.comment_thumb_down, R.drawable.comment_thumb_down_active);

        String likeCount = message.getLikeCount();
        mLikeCount.setText(nullSafe(likeCount));
        mLikeCount.setVisibility(likeCount == null || likeCount.trim().isEmpty() ? View.GONE : View.VISIBLE);

        // A liked or downvoted comment reads through its icon, but the count still helps,
        // so it stays visible in both active states - just lifted out of the muted metadata
        // grey so the voted-on state is visible even in the count alone.
        mLikeCount.setTextColor(ContextCompat.getColor(
                itemView.getContext(),
                liked || disliked ? R.color.comment_action : R.color.comment_meta));
    }

    /**
     * The reply row and the thread line.
     *
     * <p>Both are hidden for a comment with no replies. A reply row that opens nothing, and a
     * thread line with no thread under it, would be the kind of control that looks real and
     * does nothing.
     */
    private void bindThread(ChatItemMessage message) {
        boolean hasThread = message.hasThread();

        mRepliesRow.setVisibility(hasThread ? View.VISIBLE : View.GONE);
        mThreadLine.setVisibility(hasThread ? View.VISIBLE : View.GONE);

        if (hasThread) {
            mRepliesCount.setText(message.getReplyCount());
        } else {
            mRepliesCount.setText("");
        }

        // 'Reply' opens the same thread, so it is only offered when there is one.
        mReplyAction.setVisibility(hasThread ? View.VISIBLE : View.GONE);
    }

    /**
     * Swaps a thumb between its resting and active drawable.
     *
     * <p>Two whole drawables per thumb rather than two named paths inside one vector: swapping
     * with {@code setImageResource} keeps the state change in one place, and reaching into a
     * VectorDrawable's children by name to find a path is the kind of thing that silently
     * stops working when the vector is re-exported.
     *
     * <p>No colour filter is applied - each drawable already carries its own tint, so an
     * active icon comes out at comment_icon_active and a resting one at comment_icon.
     */
    private void setIconActive(ImageView icon, boolean active, int restingIcon, int activeIcon) {
        icon.setImageResource(active ? activeIcon : restingIcon);
    }

    private static String nullSafe(CharSequence value) {
        return value == null ? "" : value.toString();
    }

    private void init(View itemView) {
        mAuthor = itemView.findViewById(R.id.commentAuthor);
        mTime = itemView.findViewById(R.id.commentTime);
        mText = itemView.findViewById(R.id.commentText);
        mLikeCount = itemView.findViewById(R.id.commentLikeCount);
        mReplyAction = itemView.findViewById(R.id.commentReplyAction);
        mRepliesCount = itemView.findViewById(R.id.commentRepliesCount);
        mLikeIcon = itemView.findViewById(R.id.commentLikeIcon);
        mDislikeIcon = itemView.findViewById(R.id.commentDislikeIcon);
        mThreadLine = itemView.findViewById(R.id.commentThreadLine);
        mRepliesRow = itemView.findViewById(R.id.commentRepliesRow);
    }
}
