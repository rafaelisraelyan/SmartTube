package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.chat;

import android.content.res.Resources;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.liskovsoft.smartyoutubetv2.tv.R;
import com.stfalcon.chatkit.messages.MessageHolders;

/**
 * GRTubeYou: the flat comment row, with real thread nesting.
 *
 * <p>Replaces chatkit's {@code IncomingTextMessageViewHolder} for comments. The old holder
 * assumed one bold string and a card: it set {@code messageText} to whatever
 * {@link ChatItemMessage#getText()} returned and left {@code @id/bubble} to the style's
 * rounded background. This row has an author line, a body, an action row, a reply row and a
 * nesting level, so the binding happens here.
 *
 * <p>Extends {@code BaseIncomingMessageViewHolder} rather than the text one: the base is where
 * avatar loading lives, which still applies, and it keeps {@code applyStyle} wired up. Its
 * {@code time} field stays unbound - there is no {@code messageTime} view - so the base skips
 * it, and the timestamp goes to {@code commentTime} on the author line in a muted colour.
 *
 * <p>Depth is the one thing worth reading before anything else. {@code getReplyLevel()} comes
 * straight from YouTube's payload and was being thrown away, which is why a nested thread
 * rendered as a flat list. Three things move together with it, and they have to move together:
 * the avatar's left offset, the avatar's size, and the connectors. The first two come from
 * {@link CommentThreadLinesView} so the numbers cannot drift from the ones the elbow is drawn
 * with - if the holder computed its own geometry, the line would stop in mid-air.
 *
 * <p>Two of the bound strings are already localised by YouTube when the comments are fetched:
 * the timestamp ("4 дня назад") and the reply count ("6 ответов"). Both are shown verbatim.
 * Rebuilding the reply phrase from a number would mean the app carrying its own plural forms,
 * and it would still get them wrong for languages this build does not ship.
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
    private View mRepliesRow;
    private CommentThreadLinesView mThreadLines;

    /**
     * chatkit's holder factory looks for {@code (View, Object)} first and falls back to
     * {@code (View)}. Only the single-argument form is declared, so the fallback is taken -
     * the same path the stock holders take.
     */
    public CommentItemViewHolder(View itemView) {
        super(itemView);
        init(itemView);
    }

    @Override
    public void onBind(ChatItemMessage message) {
        super.onBind(message);

        mAuthor.setText(nullSafe(message.getAuthorName()));
        mTime.setText(nullSafe(message.getPublishedDate()));
        mText.setText(nullSafe(message.getText()));

        applyDepth(message.getReplyLevel());
        applyRootEmphasis(message.getReplyLevel() == 0);
        bindVote(message);
        bindThread(message);
    }

    /**
     * Indents the row and re-draws the connectors for a nesting level.
     *
     * <p>chatkit's {@code applyStyle} also sets the avatar's size, from the list's
     * {@code incomingAvatarWidth/Height} attributes - and it runs when the holder is created,
     * before the first bind. So the depth size is applied here, on every bind, which is the
     * only point where the level is actually known.
     */
    private void applyDepth(int depth) {
        int level = depth < 0 ? 0 : depth;

        mThreadLines.setDepth(level);

        ViewGroup.LayoutParams params = userAvatar.getLayoutParams();
        float size = mThreadLines.avatarSizeFor(level);

        params.width = (int) size;
        params.height = (int) size;

        if (params instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margin = (ViewGroup.MarginLayoutParams) params;
            // Only the left edge moves. The other three stay as the layout set them, and
            // reading them back here keeps applyStyle from being undone if it ever touches them.
            margin.leftMargin = (int) mThreadLines.indentFor(level);
        }

        userAvatar.setLayoutParams(params);
    }

    /**
     * GRTubeYou: the comment a branch hangs from is its heading.
     *
     * <p>A depth-0 row gets a larger avatar, a bolder name and more air underneath. In the
     * flat comment list every row is depth 0 and so every row looks the same, which is right -
     * they are all peers there. Inside an open thread the root is the only depth-0 row, so it
     * separates from the replies below it on its own.
     *
     * <p>Kept to a size change and a margin, not a card. The earlier pass drew a hairline
     * rectangle around every row, which put back the very weight this redesign removes.
     */
    private void applyRootEmphasis(boolean isRoot) {
        // Resources come off itemView, not off the holder: RecyclerView.ViewHolder is not a
        // View and has no getResources() of its own.
        Resources res = itemView.getResources();

        mAuthor.setTextSize(TypedValue.COMPLEX_UNIT_PX, res.getDimensionPixelSize(
                isRoot ? R.dimen.comment_author_size_root : R.dimen.comment_author_size_reply));

        ViewGroup.LayoutParams params = itemView.getLayoutParams();

        if (params instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margin = (ViewGroup.MarginLayoutParams) params;
            margin.bottomMargin = res.getDimensionPixelSize(
                    isRoot ? R.dimen.comment_root_margin_bottom : R.dimen.comment_row_margin_bottom);
            itemView.setLayoutParams(params);
        }
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

        // A voted-on comment reads through its icon, but the count still helps, so it stays
        // visible in both active states - just lifted out of the muted metadata grey.
        mLikeCount.setTextColor(ContextCompat.getColor(
                itemView.getContext(),
                liked || disliked ? R.color.comment_action : R.color.comment_meta));
    }

    /**
     * The reply row.
     *
     * <p>Hidden for a comment with no replies. A reply row that opens nothing would be the
     * kind of control that looks real and does nothing.
     */
    private void bindThread(ChatItemMessage message) {
        boolean hasThread = message.hasThread();

        mRepliesRow.setVisibility(hasThread ? View.VISIBLE : View.GONE);

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
     * VectorDrawable's children by name is the kind of thing that stops working when the
     * vector is re-exported.
     *
     * <p>No colour filter is applied - each drawable carries its own tint, so an active icon
     * comes out at comment_icon_active and a resting one at comment_icon.
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
        mRepliesRow = itemView.findViewById(R.id.commentRepliesRow);
        mThreadLines = itemView.findViewById(R.id.commentThreadLines);
    }
}
