/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tooltips;

import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.leanback.R;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.PlaybackControlsRow;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.PresenterSelector;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.CountBadgeAction;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.IconBadgeDrawable;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions.PaddingAction;

/**
 * Displays primary and secondary controls for a {@link PlaybackControlsRow}.
 *
 * Binds to items of type {@link Action}.
 */
public class ControlButtonPresenterSelector extends PresenterSelector {
    private final ControlButtonPresenter mPrimaryPresenter =
            new ControlButtonPresenter(R.layout.lb_control_button_primary);
    private final ControlButtonPresenter mSecondaryPresenter =
            new ControlButtonPresenter(R.layout.lb_control_button_secondary);
    private final Presenter[] mPresenters = new Presenter[]{mPrimaryPresenter};

    public ControlButtonPresenterSelector(boolean tooltipsEnabled) {
        mPrimaryPresenter.tooltipsEnabled = mSecondaryPresenter.tooltipsEnabled = tooltipsEnabled;
    }

    /**
     * Returns the presenter for primary controls.
     */
    public Presenter getPrimaryPresenter() {
        return mPrimaryPresenter;
    }

    /**
     * Returns the presenter for secondary controls.
     */
    public Presenter getSecondaryPresenter() {
        return mSecondaryPresenter;
    }

    /**
     * Always returns the presenter for primary controls.
     */
    @Override
    public Presenter getPresenter(Object item) {
        return mPrimaryPresenter;
    }

    @Override
    public Presenter[] getPresenters() {
        return mPresenters;
    }

    static class ActionViewHolder extends Presenter.ViewHolder {
        ImageView mIcon;
        TextView mLabel;
        View mFocusableView;
        /**
         * GRTubeYou: the icon box geometry from the inflated layout, remembered so it can be
         * put back. Width zero means "use whatever the layout says".
         */
        int mDefaultIconWidth;
        int mDefaultIconGravity;
        int mDefaultIconMarginStart;

        public ActionViewHolder(View view) {
            super(view);
            mIcon = (ImageView) view.findViewById(R.id.icon);
            mLabel = (TextView) view.findViewById(R.id.label);
            mFocusableView = view.findViewById(R.id.button);

            ViewGroup.LayoutParams lp = mIcon.getLayoutParams();
            mDefaultIconWidth = lp.width;
            if (lp instanceof FrameLayout.LayoutParams) {
                mDefaultIconGravity = ((FrameLayout.LayoutParams) lp).gravity;
                mDefaultIconMarginStart = ((FrameLayout.LayoutParams) lp).leftMargin;
            }
        }
    }

    public static class ControlButtonPresenter extends Presenter {
        private int mLayoutResourceId;
        private boolean tooltipsEnabled;

        ControlButtonPresenter(int layoutResourceId) {
            mLayoutResourceId = layoutResourceId;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(mLayoutResourceId, parent, false);
            return new ActionViewHolder(v);
        }

        // Used inside: com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tweaks.ControlBarPresenter.ViewHolder.bindControlToAction()
        // Restore focus: com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tweaks.ControlBar.onRequestFocusInDescendants()
        @Override
        public void onBindViewHolder(ViewHolder viewHolder, Object item) {
            Action action = (Action) item;
            ActionViewHolder vh = (ActionViewHolder) viewHolder;

            // GRTubeYou: the like and dislike buttons carry their count beside the icon.
            //
            // The icon box is a fixed 32dp, so a wider image is scaled down to fit and the
            // thumb would shrink to make room. The box is therefore widened for these two
            // actions only, and put back for every other one - which matters because the
            // holders are recycled: a holder that once showed a count is reused for the gear
            // button a moment later, and without the reset every control after it would stay
            // wider than it should be.
            CharSequence badge = badgeOf(action);

            if (badge != null && action.getIcon() != null) {
                // The circle behind the button is a fixed 48dp and the icon 32dp, so the icon
                // carries 8dp of slack on each side inside it. The number goes into that slack
                // and then past the circle, which keeps the thumb exactly where it was instead
                // of letting the wider image shift it off the centre.
                int circle = vh.mFocusableView.getResources()
                        .getDimensionPixelSize(R.dimen.lb_control_button_secondary_diameter);
                int inset = Math.max(0, (circle - action.getIcon().getIntrinsicWidth()) / 2);

                IconBadgeDrawable composed = new IconBadgeDrawable(action.getIcon(), badge, inset);
                int width = composed.getIntrinsicWidth();

                vh.mIcon.setImageDrawable(composed);

                // Left-aligned with a margin of half the overflow: the frame grows to the right,
                // the circle stays centred in it, and the thumb - which now begins after the
                // inset - lands back on the circle's centre.
                int overflow = Math.max(0, width - circle);
                setIconGeometry(vh, width, Gravity.TOP | Gravity.START, overflow / 2);
            } else {
                vh.mIcon.setImageDrawable(action.getIcon());
                setIconGeometry(vh, 0, vh.mDefaultIconGravity, 0);
            }

            if (action instanceof PaddingAction) {
                int padding = ((PaddingAction) action).getPadding();
                if (padding > 0) {
                    vh.mIcon.setPadding(padding, padding, padding, padding);
                }
            }
            if (vh.mLabel != null) {
                if (action.getIcon() == null) {
                    vh.mLabel.setText(action.getLabel1());
                } else {
                    vh.mLabel.setText(null);
                }
            }
            CharSequence contentDescription = TextUtils.isEmpty(action.getLabel2())
                    ? action.getLabel1() : action.getLabel2();
            if (!TextUtils.equals(vh.mFocusableView.getContentDescription(), contentDescription)) {
                vh.mFocusableView.setContentDescription(contentDescription);
                vh.mFocusableView.sendAccessibilityEvent(
                        AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);

                // MOD: enable control tooltips
                if (tooltipsEnabled) {
                    TooltipCompatHandler.setTooltipText(vh.mFocusableView, action.getLabel1());
                }
            }
        }

        @Override
        public void onUnbindViewHolder(ViewHolder viewHolder) {
            ActionViewHolder vh = (ActionViewHolder) viewHolder;
            vh.mIcon.setImageDrawable(null);
            if (vh.mLabel != null) {
                vh.mLabel.setText(null);
            }
            vh.mFocusableView.setContentDescription(null);
        }

        @Override
        public void setOnClickListener(ViewHolder viewHolder,
                                       View.OnClickListener listener) {
            ActionViewHolder vh = (ActionViewHolder) viewHolder;
            vh.mFocusableView.setOnClickListener(listener);

            // GRTubeYou: the icon sits on top of the clickable circle, so a press on the thumb
            // - or on the number beside it - has to reach the same listener. Without this only
            // the ring around the icon reacts and pressing the number itself does nothing.
            // Clickable but NOT focusable on purpose: the D-pad focus stays on the circle, so
            // the focus ring and the focus behaviour are exactly what they were.
            vh.mIcon.setClickable(true);
            vh.mIcon.setFocusable(false);
            vh.mIcon.setOnClickListener(listener);
        }

        public void setOnLongClickListener(ViewHolder viewHolder,
                                       View.OnLongClickListener listener) {
            ActionViewHolder vh = (ActionViewHolder) viewHolder;
            vh.mFocusableView.setOnLongClickListener(listener);
            vh.mIcon.setOnLongClickListener(listener);
        }

        /** GRTubeYou: the number this action wants beside its icon, or null if it wants none. */
        private CharSequence badgeOf(Action action) {
            if (!(action instanceof CountBadgeAction)) {
                return null;
            }

            CharSequence badge = ((CountBadgeAction) action).getBadgeText();
            if (badge == null || badge.toString().trim().isEmpty()) {
                return null;
            }

            return badge;
        }

        /**
         * GRTubeYou: resize and reposition the icon box, or restore it.
         *
         * <p>A width of zero means "use the layout's width", which is how the original
         * geometry comes back without a second copy of it lying around. The restore matters
         * because these holders are recycled: one that once showed a count is reused for the
         * gear button moments later, and without it every following control would keep the
         * wider box and the left margin.
         */
        private void setIconGeometry(ActionViewHolder vh, int widthPx, int gravity, int marginStart) {
            ViewGroup.LayoutParams lp = vh.mIcon.getLayoutParams();
            int width = widthPx > 0 ? widthPx : vh.mDefaultIconWidth;
            boolean changed = lp.width != width;

            if (lp instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams fp = (FrameLayout.LayoutParams) lp;
                int g = widthPx > 0 ? gravity : vh.mDefaultIconGravity;
                int ms = widthPx > 0 ? marginStart : vh.mDefaultIconMarginStart;

                if (fp.gravity != g) {
                    fp.gravity = g;
                    changed = true;
                }
                if (fp.leftMargin != ms) {
                    fp.leftMargin = ms;
                    changed = true;
                }
            }

            if (changed) {
                lp.width = width;
                vh.mIcon.setLayoutParams(lp);
            }
        }
    }
}
