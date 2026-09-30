package com.liskovsoft.smartyoutubetv2.tv.ui.widgets.chat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * GRTubeYou: the thread lines that connect a reply to its parent.
 *
 * <p>This is the whole nesting mechanism, and it is one view that paints rather than a stack
 * of positioned children. The reason is depth: a row at level 4 needs a vertical line for
 * every one of its four ancestors, and the natural way to express that in a layout file is
 * four nested views per row, which is four times the view count for every comment in the
 * list. Painting them instead means one view per row at any depth.
 *
 * <p>What it draws, for a row at level N:
 *
 * <ul>
 *   <li>a full-height vertical line at the centre of the avatar of every level above it -
 *       so the outermost line runs unbroken down the whole thread, exactly as in the
 *       reference, and each deeper reply adds a shorter line inside it;
 *   <li>an elbow from the parent's line to this row's avatar, with a rounded corner where the
 *       two meet.
 * </ul>
 *
 * <p>The avatar's own centre is the line's x position, so the line passes through the avatar
 * rather than beside it - that is what makes a level read as a child at a glance.
 *
 * <p>Geometry lives in resources so the numbers are in one place and can be compared against
 * the reference. It is computed rather than declared because the avatar shrinks with depth,
 * so the x positions are not constants.
 */
public class CommentThreadLinesView extends View {
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mPath = new Path();

    private int mDepth;
    private float mBaseIndent;
    private float mDepthStep;
    private float mAvatarRoot;
    private float mAvatarShrink;
    private float mAvatarMin;
    private float mLineWidth;
    private float mElbowRadius;

    public CommentThreadLinesView(Context context) {
        this(context, null);
    }

    public CommentThreadLinesView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setColor(ContextCompat.getColor(getContext(), R.color.comment_thread_line));

        mBaseIndent = getResources().getDimension(R.dimen.comment_row_indent);
        mDepthStep = getResources().getDimension(R.dimen.comment_depth_step);
        mAvatarRoot = getResources().getDimension(R.dimen.comment_avatar_size);
        mAvatarShrink = getResources().getDimension(R.dimen.comment_avatar_shrink);
        mAvatarMin = getResources().getDimension(R.dimen.comment_avatar_min);
        mLineWidth = getResources().getDimension(R.dimen.comment_thread_width);
        mElbowRadius = getResources().getDimension(R.dimen.comment_thread_elbow);

        mPaint.setStrokeWidth(mLineWidth);
    }

    /**
     * @param depth 0 for a top-level comment, 1 for a reply to it, and so on. A row at depth 0
     *               draws nothing, which is the point: the anchor has no line above it.
     */
    public void setDepth(int depth) {
        int clamped = depth < 0 ? 0 : depth;

        if (clamped != mDepth) {
            mDepth = clamped;
            invalidate();
        }
    }

    public int getDepth() {
        return mDepth;
    }

    /**
     * The avatar size the row should use at the current depth.
     *
     * <p>Exposed rather than recomputed by the holder, so the two can never disagree about
     * where the avatar edge is - the elbow is drawn up to that edge, and if the holder used a
     * different size the line would stop in mid-air.
     */
    public float avatarSizeFor(int depth) {
        if (depth <= 0) {
            return mAvatarRoot;
        }

        float size = mAvatarRoot - mAvatarShrink * depth;

        return Math.max(size, mAvatarMin);
    }

    /** The left offset of the avatar at a depth, relative to the row's own content start. */
    public float indentFor(int depth) {
        return depth <= 0 ? 0 : mDepthStep * depth;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mDepth <= 0) {
            return;
        }

        int height = getHeight();

        if (height <= 0) {
            return;
        }

        // One full-height line per ancestor, so the outermost spine is continuous through the
        // whole thread and a deeper reply adds its own line inside it.
        for (int level = 0; level < mDepth; level++) {
            float x = lineX(level);
            canvas.drawLine(x, 0, x, height, mPaint);
        }

        // The elbow: down the parent's line, a rounded turn, then across to this avatar.
        float parentX = lineX(mDepth - 1);
        float ownIndent = indentFor(mDepth);
        float elbowY = avatarSizeFor(mDepth) / 2f;
        float radius = Math.min(mElbowRadius, Math.max(0, ownIndent));

        if (radius <= 0) {
            // No room to turn - draw a plain corner rather than an arc that doubles back.
            mPath.reset();
            mPath.moveTo(parentX, 0);
            mPath.lineTo(parentX, height);
            mPath.moveTo(parentX, elbowY);
            mPath.lineTo(ownIndent, elbowY);
            canvas.drawPath(mPath, mPaint);
            return;
        }

        mPath.reset();
        mPath.moveTo(parentX, 0);
        mPath.lineTo(parentX, elbowY - radius);
        mPath.quadTo(parentX, elbowY, parentX + radius, elbowY);
        mPath.lineTo(ownIndent, elbowY);

        canvas.drawPath(mPath, mPaint);
    }

    /**
     * The x of a level's vertical line: the centre of the avatar at that level.
     *
     * <p>The view is laid out across the row's full width starting at the row's left padding,
     * so the row's own base indent has to be added here - the avatar sits at
     * {@code comment_row_indent + indent(level)} from the row's left edge.
     */
    private float lineX(int level) {
        return mBaseIndent + indentFor(level) + avatarSizeFor(level) / 2f;
    }
}
