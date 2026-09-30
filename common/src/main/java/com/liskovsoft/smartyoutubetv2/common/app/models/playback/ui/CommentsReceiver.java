package com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui;

import com.liskovsoft.mediaserviceinterfaces.data.CommentGroup;
import com.liskovsoft.mediaserviceinterfaces.data.CommentItem;

public interface CommentsReceiver {
    interface Callback {
        void onCommentGroup(CommentGroup commentGroup);
        void onBackup(Backup backup);
        void onSync(CommentItem commentItem);
    }
    interface Backup {}
    void addCommentGroup(CommentGroup commentGroup);
    void loadBackup(Backup backup);
    void sync(CommentItem commentItem);
    void setCallback(Callback callback);
    void onLoadMore(CommentGroup commentGroup);
    void onStart();

    /**
     * GRTubeYou: opens the reply thread. The row body, the reply count and the Reply
     * label all land here - same behaviour, one path, so they can never drift apart.
     */
    void onCommentClicked(CommentItem commentItem);

    void onCommentLongClicked(CommentItem commentItem);

    /**
     * GRTubeYou: the thumbs in the action row.
     *
     * <p>Separate from the long press rather than reusing it, because a long press is not
     * reachable from a TV remote - the one input device this app is built for. The long
     * press is kept working for touch.
     *
     * @param like true for the thumbs up, false for the thumbs down.
     */
    void onCommentVoteClicked(CommentItem commentItem, boolean like);

    void onFinish(Backup backup);
    String getLoadingMessage();
    String getErrorMessage();
}
