package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

/**
 * GRTubeYou: an icon with a number drawn to its right, as one image.
 *
 * <p>Why a drawable and not a TextView beside the ImageView:
 *
 * <ul>
 *   <li>the control row is a {@code FrameLayout} whose clickable, focusable and ripple element
 *       is a fixed 48dp circle with a 32dp icon centred on it. A sibling TextView means either
 *       the circle stops sitting under the icon, or the circle gets stretched - and a stretched
 *       circle is exactly the pill-shaped background that was asked not to be introduced;
 *   <li>the icon keeps its own size. Baked into one image the thumb is drawn at its intrinsic
 *       size and the number is added beside it. Fitted into a fixed-width box it would be scaled
 *       down to make room, and the thumb would visibly shrink;
 *   <li>composing here leaves the click target, the hover, the pressed and the focused states as
 *       the ones that already exist. No part of the control's behaviour is redefined.
 * </ul>
 *
 * <p>Styling, taken from what is already on screen rather than chosen: the number is the same
 * white as the icons, so it reads as part of the control rather than as text dropped next to
 * one; the typeface is the interface's own; there is no background, no border and no padding
 * beyond a small gap; and the size is derived from the icon instead of hardcoded, so it tracks
 * the icon at any screen density rather than being right on one density and wrong on the rest.
 *
 * <p>The number does not follow the icon into its filled state. The thumb fills in when liked;
 * the count stays white, which is how YouTube does it and which keeps the number legible in
 * both states instead of tinting it into the highlight colour.
 */
public class IconBadgeDrawable extends Drawable {
    /** Gap between the icon and the number, as a fraction of the icon height. */
    private static final float GAP_RATIO = 0.16f;

    /** Number size relative to the icon height: legible beside a 32dp icon, not competing. */
    private static final float TEXT_RATIO = 0.46f;

    /** The colour every control icon in this row already is. */
    private static final int COUNT_COLOR = 0xFFFFFFFF;

    /** Used only if an icon somehow reports no intrinsic size, so the number still has one. */
    private static final int FALLBACK_ICON_SIZE_PX = 96;

    private final Drawable mIcon;
    private final String mText;
    private final Paint mTextPaint;
    private final int mGap;
    private final int mLeftInset;
    private final Rect mIconPadding = new Rect();
    private final Rect mIconBounds = new Rect();

    /**
     * @param leftInset space kept in front of the icon so that, once the view is positioned,
     *                   the thumb lands exactly where it sits today - centred inside the
     *                   circular button behind it. The number then hangs off to the right of
     *                   that circle rather than pushing the thumb off its centre.
     */
    public IconBadgeDrawable(Drawable icon, CharSequence text, int leftInset) {
        if (icon == null) {
            throw new IllegalArgumentException("icon must not be null");
        }

        mIcon = icon;
        mText = text == null ? "" : text.toString();

        int iconHeight = icon.getIntrinsicHeight();
        if (iconHeight <= 0) {
            iconHeight = FALLBACK_ICON_SIZE_PX;
        }

        mGap = Math.round(iconHeight * GAP_RATIO);
        mLeftInset = Math.max(0, leftInset);

        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setColor(COUNT_COLOR);
        mTextPaint.setTextSize(iconHeight * TEXT_RATIO);
        mTextPaint.setTypeface(Typeface.DEFAULT);
        mTextPaint.setTextAlign(Paint.Align.LEFT);
    }

    @Override
    public int getIntrinsicWidth() {
        mIcon.getPadding(mIconPadding);

        if (mText.isEmpty()) {
            return mIconPadding.left + mIcon.getIntrinsicWidth() + mIconPadding.right;
        }

        return mLeftInset
                + mIconPadding.left
                + mIcon.getIntrinsicWidth()
                + mGap
                + Math.round(mTextPaint.measureText(mText));
    }

    @Override
    public int getIntrinsicHeight() {
        return mIcon.getIntrinsicHeight();
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        mIcon.getPadding(mIconPadding);

        int iconWidth = mIcon.getIntrinsicWidth();
        int iconHeight = mIcon.getIntrinsicHeight();

        int iconLeft = b.left + mLeftInset + mIconPadding.left;

        // The icon keeps its own size and is never scaled: the number is added beside it,
        // never traded against it.
        mIconBounds.set(iconLeft, b.top, iconLeft + iconWidth, b.top + iconHeight);
        mIcon.setBounds(mIconBounds);
        mIcon.draw(canvas);

        if (mText.isEmpty()) {
            return;
        }

        // Centred on the icon's middle rather than sitting on the same baseline: digits and a
        // thumb share a middle line, which is what makes them read as one unit.
        Paint.FontMetrics fm = mTextPaint.getFontMetrics();
        float textHeight = fm.descent - fm.ascent;
        float baseline = b.top + (iconHeight + textHeight) / 2f - fm.descent;

        canvas.drawText(mText, iconLeft + iconWidth + mGap, baseline, mTextPaint);
    }

    @Override
    public void setAlpha(int alpha) {
        mIcon.setAlpha(alpha);
        mTextPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        // Only the icon is filtered. A tint is what greys out a disabled control, and the
        // number is not the thing that should be drained by it.
        mIcon.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}