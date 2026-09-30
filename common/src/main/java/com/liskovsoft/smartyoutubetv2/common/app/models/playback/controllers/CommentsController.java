package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import android.content.Context;
import android.util.Pair;

import com.liskovsoft.mediaserviceinterfaces.data.CommentGroup;
import com.liskovsoft.mediaserviceinterfaces.data.CommentItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.BasePlayerController;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.CommentsReceiver;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.CommentsReceiver.Backup;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.AbstractCommentsReceiver;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter;
import io.reactivex.disposables.Disposable;

public class CommentsController extends BasePlayerController {
    private static final String TAG = CommentsController.class.getSimpleName();
    private Disposable mCommentsAction;
    private String mLiveChatKey;
    private String mCommentsKey;
    private String mTitle;
    private Pair<String, Backup> mBackup;

    public CommentsController() {
    }

    public CommentsController(Context context, MediaItemMetadata metadata) {
        setAltContext(context);
        onInit();
        onMetadata(metadata);
    }

    @Override
    public void onMetadata(MediaItemMetadata metadata) {
        mLiveChatKey = metadata != null && metadata.getLiveChatKey() != null ? metadata.getLiveChatKey() : null;
        mCommentsKey = metadata != null && metadata.getCommentsKey() != null ? metadata.getCommentsKey() : null;
        mTitle = metadata != null ? metadata.getTitle() : null;
        if (mBackup != null && !Helpers.equals(mBackup.first, mCommentsKey)) {
            mBackup = null;
        }
    }

    private void openCommentsDialog() {
        fitVideoIntoDialog();

        disposeActions();

        if (mCommentsKey == null) {
            return;
        }

        final String backupKey = mCommentsKey;

        if (getPlayer() != null) {
            getPlayer().showControls(false);
        }

        String title = getPlayer() != null && getPlayer().getVideo() != null ? getPlayer().getVideo().getTitleFull() : mTitle;

        CommentsReceiver commentsReceiver = new AbstractCommentsReceiver(getContext()) {
            @Override
            public void onLoadMore(CommentGroup commentGroup) {
                if (commentGroup == null) {
                    return;
                }

                loadComments(this, commentGroup.getNextCommentsKey());
            }

            @Override
            public void onStart() {
                if (mBackup != null && Helpers.equals(mBackup.first, mCommentsKey)) {
                    loadBackup(mBackup.second);
                    return;
                }

                loadComments(this, mCommentsKey);
            }

            @Override
            public void onCommentClicked(CommentItem commentItem) {
                if (commentItem.isEmpty()) {
                    return;
                }

                CommentsReceiver nestedReceiver = new AbstractCommentsReceiver(getContext()) {
                    @Override
                    public void onLoadMore(CommentGroup commentGroup) {
                        loadComments(this, commentGroup.getNextCommentsKey());
                    }

                    @Override
                    public void onStart() {
                        loadComments(this, commentItem.getNestedCommentsKey());
                    }

                    @Override
                    public void onCommentLongClicked(CommentItem commentItem) {
                        toggleVote(this, commentItem, true);
                    }

                    // GRTubeYou: the nested list renders the same flat row, so it needs the
                    // same action row. Without this the thumbs would be dead one level down.
                    @Override
                    public void onCommentVoteClicked(CommentItem commentItem, boolean like) {
                        toggleVote(this, commentItem, like);
                    }
                };

                showDialog(nestedReceiver, title);
            }

            @Override
            public void onCommentLongClicked(CommentItem commentItem) {
                toggleVote(this, commentItem, true);
            }

            @Override
            public void onCommentVoteClicked(CommentItem commentItem, boolean like) {
                toggleVote(this, commentItem, like);
            }

            @Override
            public void onFinish(Backup backup) {
                super.onFinish(backup);
                if (Helpers.equals(backupKey, mCommentsKey)) {
                    mBackup = new Pair<>(mCommentsKey, backup);
                }
            }
        };

        showDialog(commentsReceiver, title);
    }

    @Override
    public void onButtonClicked(int buttonId, int buttonState) {
        if (buttonId == R.id.action_chat) {
            if (mCommentsKey != null && mLiveChatKey == null) {
                openCommentsDialog();
            }

            if (mCommentsKey == null && mLiveChatKey == null) {
                MessageHelpers.showMessage(getContext(), R.string.comments_disabled);
            }
        }
    }

    @Override
    public void onEngineReleased() {
        disposeActions();
        mBackup = null;
    }

    @Override
    public void onFinish() {
        disposeActions();
    }

    private void disposeActions() {
        RxHelper.disposeActions(mCommentsAction);
    }

    private void loadComments(CommentsReceiver receiver, String commentsKey) {
        disposeActions();

        mCommentsAction = getCommentsService().getCommentsObserve(commentsKey)
                .subscribe(
                        receiver::addCommentGroup,
                        error -> {
                            Log.e(TAG, error.getMessage());
                            receiver.addCommentGroup(null); // remove loading message
                        }
                );
    }

    private void showDialog(CommentsReceiver receiver, String title) {
        AppDialogPresenter appDialogPresenter = getAppDialogPresenter();

        appDialogPresenter.appendCommentsCategory(title, UiOptionItem.from(title, receiver));
        //appDialogPresenter.enableTransparent(true);
        appDialogPresenter.showDialog();
    }

    /**
     * GRTubeYou: one path for both thumbs.
     *
     * <p>The row is updated locally first and the request follows, so the icon reacts on
     * the press instead of after a round trip. The service is asked to toggle whichever
     * thumb was pressed, and it reads the current state itself to decide set vs clear -
     * see {@code CommentsServiceInt.toggleDislike}.
     */
    private void toggleVote(CommentsReceiver receiver, CommentItem commentItem, boolean like) {
        MyCommentItem myCommentItem = MyCommentItem.from(commentItem);
        myCommentItem.toggleVote(like);

        receiver.sync(myCommentItem);

        String key = commentItem.getNestedCommentsKey();

        if (key == null) {
            // The row has already been updated, but there is no key to send the vote on, so
            // nothing reaches YouTube. Say so rather than let the icon sit lit over a comment
            // that was never actually voted on.
            Log.e(TAG, "cannot vote: comment has no nested comments key");
            return;
        }

        RxHelper.execute(
                like ? getCommentsService().toggleLikeObserve(key) : getCommentsService().toggleDislikeObserve(key),
                e -> MessageHelpers.showMessage(getContext(), e.getMessage()));
    }

    private static final class MyCommentItem implements CommentItem {
        private final String mId;
        private final String mMessage;
        private final String mAuthorName;
        private final String mAuthorPhoto;
        private final String mPublishedDate;
        private final String mNestedCommentsKey;
        private boolean mIsLiked;
        private boolean mIsDisliked;
        private String mLikeCount;
        private final String mReplyCount;
        private final boolean mIsEmpty;

        private MyCommentItem(
                String id, String message, String authorName, String authorPhoto, String publishedDate,
                String nestedCommentsKey, boolean isLiked, boolean isDisliked, String likeCount, String replyCount, boolean isEmpty) {
            mId = id;
            mMessage = message;
            mAuthorName = authorName;
            mAuthorPhoto = authorPhoto;
            mPublishedDate = publishedDate;
            mNestedCommentsKey = nestedCommentsKey;
            mIsLiked = isLiked;
            mIsDisliked = isDisliked;
            mLikeCount = likeCount;
            mReplyCount = replyCount;
            mIsEmpty = isEmpty;
        }

        @Override
        public String getId() {
            return mId;
        }

        @Override
        public String getMessage() {
            return mMessage;
        }

        @Override
        public String getAuthorName() {
            return mAuthorName;
        }

        @Override
        public String getAuthorPhoto() {
            return mAuthorPhoto;
        }

        @Override
        public String getPublishedDate() {
            return mPublishedDate;
        }

        @Override
        public String getNestedCommentsKey() {
            return mNestedCommentsKey;
        }

        @Override
        public boolean isLiked() {
            return mIsLiked;
        }

        @Override
        public boolean isDisliked() {
            return mIsDisliked;
        }

        /**
         * GRTubeYou: applies the press locally.
         *
         * <p>The parameter is the thumb that was <b>pressed</b>, not the state to be in, so
         * this has to toggle that thumb. Passing the desired state instead was a real bug:
         * pressing an already-lit thumbs-up left the local vote on while the service - which
         * genuinely toggles - cleared it server-side. The row and YouTube then disagreed until
         * the list was rebuilt, which is the exact failure this whole path is meant to avoid.
         *
         * <p>The other thumb always clears, because YouTube keeps a single vote: the two
         * buttons are alternatives, not two independent switches.
         */
        public void toggleVote(boolean likePressed) {
            if (likePressed) {
                boolean wasLiked = mIsLiked;
                mIsLiked = !mIsLiked;
                mIsDisliked = false;
                if (mIsLiked != wasLiked) {
                    adjustLikeCount(mIsLiked);
                }
                return;
            }

            boolean wasLiked = mIsLiked;
            mIsLiked = false;
            mIsDisliked = !mIsDisliked;
            if (wasLiked) {
                // Downvoting a comment you had liked gives the like back, so the count
                // drops. Downvoting without a prior like leaves the count alone.
                adjustLikeCount(false);
            }
        }

        private void adjustLikeCount(boolean increment) {
            if (mLikeCount == null) {
                mLikeCount = String.valueOf(0);
            }

            if (Helpers.isInteger(mLikeCount)) {
                int likeCount = Helpers.parseInt(mLikeCount);
                int count = increment ? ++likeCount : --likeCount;
                mLikeCount = count > 0 ? String.valueOf(count) : null;
            }
        }

        @Override
        public String getLikeCount() {
            return mLikeCount;
        }

        @Override
        public String getReplyCount() {
            return mReplyCount;
        }

        @Override
        public boolean isEmpty() {
            return mIsEmpty;
        }

        public static MyCommentItem from(CommentItem commentItem) {
            return new MyCommentItem(commentItem.getId(), commentItem.getMessage(), commentItem.getAuthorName(),
                    commentItem.getAuthorPhoto(), commentItem.getPublishedDate(), commentItem.getNestedCommentsKey(),
                    commentItem.isLiked(), commentItem.isDisliked(), commentItem.getLikeCount(), commentItem.getReplyCount(),
                    commentItem.isEmpty());
        }
    }
}
