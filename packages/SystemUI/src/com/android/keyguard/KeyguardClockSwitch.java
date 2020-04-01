package com.android.keyguard;

import static com.android.systemui.util.InjectionInflationController.VIEW_CONTEXT;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.app.WallpaperManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Typeface;
import android.os.Build;
import android.transition.Fade;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.transition.Transition;
import android.transition.TransitionListenerAdapter;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.transition.TransitionValues;
import android.util.AttributeSet;
import android.util.Log;
import android.util.MathUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextClock;

import androidx.annotation.VisibleForTesting;

import com.android.internal.colorextraction.ColorExtractor;
import com.android.internal.colorextraction.ColorExtractor.OnColorsChangedListener;
import com.android.keyguard.clock.ClockManager;
import com.android.systemui.Interpolators;
import com.android.systemui.R;
import com.android.systemui.colorextraction.SysuiColorExtractor;
import com.android.systemui.plugins.ClockPlugin;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.statusbar.StatusBarState;
import com.android.systemui.util.wakelock.KeepAwakeAnimationListener;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.TimeZone;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Switch to show plugin clock when plugin is connected, otherwise it will show default clock.
 */
public class KeyguardClockSwitch extends RelativeLayout {

    private static final String TAG = "KeyguardClockSwitch";

    private static final boolean CUSTOM_CLOCKS_ENABLED = true;

    /**
     * Animation fraction when text is transitioned to/from bold.
     */
    private static final float TO_BOLD_TRANSITION_FRACTION = 0.7f;

    /**
     * Controller used to track StatusBar state to know when to show the big_clock_container.
     */
    private final StatusBarStateController mStatusBarStateController;

    /**
     * Color extractor used to apply colors from wallpaper to custom clock faces.
     */
    private final SysuiColorExtractor mSysuiColorExtractor;

    /**
     * Manager used to know when to show a custom clock face.
     */
    private final ClockManager mClockManager;

    /**
     * Layout transition that scales the default clock face.
     */
    private final Transition mTransition;

    private final ClockVisibilityTransition mClockTransition;
    private final ClockVisibilityTransition mBoldClockTransition;

    /**
     * Optional/alternative clock injected via plugin.
     */
    private ClockPlugin mClockPlugin;

    /**
     * Default clock.
     */
    private TextClock mClockView;

    /**
     * Default clock, bold version.
     * Used to transition to bold when shrinking the default clock.
     */
    private TextClock mClockViewBold;

    /**
     * Frame for default and custom clock.
     */
    private FrameLayout mSmallClockFrame;

    /**
     * Container for big custom clock.
     */
    private ViewGroup mBigClockContainer;

    /**
     * Status area (date and other stuff) shown below the clock. Plugin can decide whether or not to
     * show it below the alternate clock.
     */
    private View mKeyguardStatusArea;

    /**
     * Maintain state so that a newly connected plugin can be initialized.
     */
    private float mDarkAmount;

    /**
     * Boolean value indicating if notifications are visible on lock screen.
     */
    private boolean mHasVisibleNotifications;

    /**
     * If the Keyguard Slice has a header (big center-aligned text.)
     */
    private boolean mShowingHeader;
    private boolean mSupportsDarkText;
    private int[] mColorPalette;
    private boolean mShowCurrentUserTime;

    /**
     * Track the state of the status bar to know when to hide the big_clock_container.
     */
    private int mStatusBarState;

    private final StatusBarStateController.StateListener mStateListener =
            new StatusBarStateController.StateListener() {
                @Override
                public void onStateChanged(int newState) {
                    mStatusBarState = newState;
                    updateBigClockVisibility();
                }
            };

    private ClockManager.ClockChangedListener mClockChangedListener = this::setClockPlugin;

    /**
     * Listener for changes to the color palette.
     *
     * The color palette changes when the wallpaper is changed.
     */
    private final OnColorsChangedListener mColorsListener = (extractor, which) -> {
        if ((which & WallpaperManager.FLAG_LOCK) != 0) {
            updateColors();
        }
    };

    @Inject
    public KeyguardClockSwitch(@Named(VIEW_CONTEXT) Context context, AttributeSet attrs,
            StatusBarStateController statusBarStateController, SysuiColorExtractor colorExtractor,
            ClockManager clockManager) {
        super(context, attrs);
        mStatusBarStateController = statusBarStateController;
        mStatusBarState = mStatusBarStateController.getState();
        mSysuiColorExtractor = colorExtractor;
        mClockManager = clockManager;

        mClockTransition = new ClockVisibilityTransition().setCutoff(
                1 - TO_BOLD_TRANSITION_FRACTION);
        mClockTransition.addTarget(R.id.default_clock_view);
        mBoldClockTransition = new ClockVisibilityTransition().setCutoff(
                TO_BOLD_TRANSITION_FRACTION);
        mBoldClockTransition.addTarget(R.id.default_clock_view_bold);
        mTransition = new TransitionSet()
                .setOrdering(TransitionSet.ORDERING_TOGETHER)
                .addTransition(mClockTransition)
                .addTransition(mBoldClockTransition)
                .setDuration(KeyguardSliceView.DEFAULT_ANIM_DURATION / 2)
                .setInterpolator(Interpolators.LINEAR_OUT_SLOW_IN);
    }

    /**
     * Returns if this view is presenting a custom clock, or the default implementation.
     */
    public boolean hasCustomClock() {
        return mClockPlugin != null;
    }

    public boolean hasCustomClockInBigContainer() {
        return hasCustomClock() && mClockPlugin.shouldShowInBigContainer();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mClockView = findViewById(R.id.default_clock_view);
        mClockViewBold = findViewById(R.id.default_clock_view_bold);
        mSmallClockFrame = findViewById(R.id.clock_view);
        mKeyguardStatusArea = findViewById(R.id.keyguard_status_area);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mClockManager.addOnClockChangedListener(mClockChangedListener);
        mStatusBarStateController.addCallback(mStateListener);
        mSysuiColorExtractor.addOnColorsChangedListener(mColorsListener);
        updateColors();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mClockManager.removeOnClockChangedListener(mClockChangedListener);
        mStatusBarStateController.removeCallback(mStateListener);
        mSysuiColorExtractor.removeOnColorsChangedListener(mColorsListener);
        setClockPlugin(null);
    }

    private boolean showLockClockInfo() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.LOCKSCREEN_INFO, 1) == 1;
    }

    private int getLockClockFont() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.LOCK_CLOCK_FONTS, 28);
    }

    private int getLockClockSize() {
        return Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.LOCKCLOCK_FONT_SIZE, 54);
    }

    private void setClockPlugin(ClockPlugin plugin) {
        // Disconnect from existing plugin.
        if (mClockPlugin != null) {
            View smallClockView = mClockPlugin.getView();
            if (smallClockView != null && smallClockView.getParent() == mSmallClockFrame) {
                mSmallClockFrame.removeView(smallClockView);
            }
            View bigClockView = mClockPlugin.getBigClockView();
            if (bigClockView != null && bigClockView.getParent() == mSmallClockFrame) {
                mSmallClockFrame.removeView(bigClockView);
            }
            if (mBigClockContainer != null) {
                mBigClockContainer.removeAllViews();
                updateBigClockVisibility();
            }
            mClockPlugin.onDestroyView();
            mClockPlugin = null;
        }
        if (plugin == null) {
            if (mShowingHeader) {
                mClockView.setVisibility(View.GONE);
                mClockViewBold.setVisibility(View.VISIBLE);
            } else {
                mClockView.setVisibility(View.VISIBLE);
                mClockViewBold.setVisibility(View.INVISIBLE);
            }
            mKeyguardStatusArea.setVisibility(showLockClockInfo() ? View.VISIBLE : View.GONE);
            return;
        }
        // Attach small and big clock views to hierarchy.
        View smallClockView = plugin.getView();
        View bigClockView = plugin.getBigClockView();

        if (plugin.shouldShowInBigContainer()) {
            if (smallClockView != null) {
                mSmallClockFrame.addView(smallClockView, -1,
                        new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT));
                mClockView.setVisibility(View.GONE);
                mClockViewBold.setVisibility(View.GONE);
            }
            if (bigClockView != null && mBigClockContainer != null) {
                mBigClockContainer.addView(bigClockView);
                updateBigClockVisibility();
            }
        } else {
            mClockView.setVisibility(View.GONE);
            mClockViewBold.setVisibility(View.GONE);

            if (bigClockView != null ) {
                mSmallClockFrame.addView(bigClockView, -1,
                        new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT));
            }
        }
        // Hide default clock.
        mKeyguardStatusArea.setVisibility(showLockClockInfo() ? View.VISIBLE : View.GONE);
        // Initialize plugin parameters.
        mClockPlugin = plugin;
        mClockPlugin.setStyle(getPaint().getStyle());
        mClockPlugin.setTextColor(getCurrentTextColor());
        mClockPlugin.setDarkAmount(mDarkAmount);
        if (mColorPalette != null) {
            mClockPlugin.setColorPalette(mSupportsDarkText, mColorPalette);
        }
    }

    /**
     * Set container for big clock face appearing behind NSSL and KeyguardStatusView.
     */
    public void setBigClockContainer(ViewGroup container) {
        if (mClockPlugin != null && container != null) {
            if (mClockPlugin.shouldShowInBigContainer()) {
                View bigClockView = mClockPlugin.getBigClockView();
                if (bigClockView != null) {
                    container.addView(bigClockView);
                }
                mBigClockContainer = container;
            } else {
                mBigClockContainer = null;
            }
        }
        updateBigClockVisibility();
    }

    /**
     * It will also update plugin setStyle if plugin is connected.
     */
    public void setStyle(Style style) {
        mClockView.getPaint().setStyle(style);
        mClockViewBold.getPaint().setStyle(style);
        if (mClockPlugin != null) {
            mClockPlugin.setStyle(style);
        }
    }

    /**
     * It will also update plugin setTextColor if plugin is connected.
     */
    public void setTextColor(int color) {
        mClockView.setTextColor(color);
        mClockViewBold.setTextColor(color);
        if (mClockPlugin != null) {
            mClockPlugin.setTextColor(color);
        }
    }

    public void setShowCurrentUserTime(boolean showCurrentUserTime) {
        mClockView.setShowCurrentUserTime(showCurrentUserTime);
        mClockViewBold.setShowCurrentUserTime(showCurrentUserTime);
        mShowCurrentUserTime = showCurrentUserTime;
    }

    public void setTextSize(int unit, float size) {
        mClockView.setTextSize(unit, size);
    }

    public void setFormat12Hour(CharSequence format) {
        mClockView.setFormat12Hour(format);
        mClockViewBold.setFormat12Hour(format);
    }

    public void setFormat24Hour(CharSequence format) {
        mClockView.setFormat24Hour(format);
        mClockViewBold.setFormat24Hour(format);
    }

    /**
     * Set the amount (ratio) that the device has transitioned to doze.
     *
     * @param darkAmount Amount of transition to doze: 1f for doze and 0f for awake.
     */
    public void setDarkAmount(float darkAmount) {
        mDarkAmount = darkAmount;
        if (mClockPlugin != null) {
            mClockPlugin.setDarkAmount(darkAmount);
        }
        updateBigClockAlpha();
    }

    /**
     * Set whether or not the lock screen is showing notifications.
     */
    void setHasVisibleNotifications(boolean hasVisibleNotifications) {
        if (hasVisibleNotifications == mHasVisibleNotifications) {
            return;
        }
        mHasVisibleNotifications = hasVisibleNotifications;
        if (mDarkAmount == 0f && mBigClockContainer != null) {
            // Starting a fade transition since the visibility of the big clock will change.
            TransitionManager.beginDelayedTransition(mBigClockContainer,
                    new Fade().setDuration(KeyguardSliceView.DEFAULT_ANIM_DURATION / 2).addTarget(
                            mBigClockContainer));
        }
        updateBigClockAlpha();
    }

    public Paint getPaint() {
        return mClockView.getPaint();
    }

    public int getCurrentTextColor() {
        return mClockView.getCurrentTextColor();
    }

    public float getTextSize() {
        return mClockView.getTextSize();
    }

    /**
     * Returns the preferred Y position of the clock.
     *
     * @param totalHeight Height of the parent container.
     * @return preferred Y position.
     */
    int getPreferredY(int totalHeight) {
        if (mClockPlugin != null) {
            return mClockPlugin.getPreferredY(totalHeight);
        } else {
            return totalHeight / 2;
        }
    }

    /**
     * Refresh the time of the clock, due to either time tick broadcast or doze time tick alarm.
     */
    public void refresh() {
        mClockView.refresh();
        mClockViewBold.refresh();
        if (mClockPlugin != null) {
            mClockPlugin.onTimeTick();
        }
        if (Build.IS_DEBUGGABLE) {
            // Log for debugging b/130888082 (sysui waking up, but clock not updating)
            Log.d(TAG, "Updating clock: " + mClockView.getText());
        }
    }

    /**
     * Notifies that the time zone has changed.
     */
    public void onTimeZoneChanged(TimeZone timeZone) {
        if (mClockPlugin != null) {
            mClockPlugin.onTimeZoneChanged(timeZone);
        }
    }

    private void updateColors() {
        ColorExtractor.GradientColors colors = mSysuiColorExtractor.getColors(
                WallpaperManager.FLAG_LOCK);
        mSupportsDarkText = colors.supportsDarkText();
        mColorPalette = colors.getColorPalette();
        if (mClockPlugin != null) {
            mClockPlugin.setColorPalette(mSupportsDarkText, mColorPalette);
        }
    }

    private void updateBigClockVisibility() {
        if (mBigClockContainer == null) {
            return;
        }
        final boolean inDisplayState = mStatusBarState == StatusBarState.KEYGUARD
                || mStatusBarState == StatusBarState.SHADE_LOCKED;
        final int visibility =
                inDisplayState && mBigClockContainer.getChildCount() != 0 ? View.VISIBLE
                        : View.GONE;
        if (mBigClockContainer.getVisibility() != visibility) {
            mBigClockContainer.setVisibility(visibility);
        }
    }

    private void updateBigClockAlpha() {
        if (mBigClockContainer != null) {
            final float alpha = mHasVisibleNotifications ? mDarkAmount : 1f;
            mBigClockContainer.setAlpha(alpha);
            if (alpha == 0f) {
                mBigClockContainer.setVisibility(INVISIBLE);
            } else if (mBigClockContainer.getVisibility() == INVISIBLE) {
                mBigClockContainer.setVisibility(VISIBLE);
            }
        }
    }

    public void refreshLockFont() {
        final Resources res = getContext().getResources();
        boolean isPrimary = UserHandle.getCallingUserId() == UserHandle.USER_OWNER;
        int lockClockFont = isPrimary ? getLockClockFont() : 28;

        switch (lockClockFont) {
            case 0:
                mClockView.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                mClockViewBold.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                break;
            case 1:
                mClockView.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                mClockViewBold.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                break;
            case 2:
                mClockView.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
                mClockViewBold.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
                break;
            case 3:
                mClockView.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
                mClockViewBold.setTypeface(Typeface.create("sans-serif", Typeface.BOLD_ITALIC));
                break;
            case 4:
                mClockView.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
                mClockViewBold.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
                break;
            case 5:
                mClockView.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
                mClockViewBold.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
                break;
            case 6:
                mClockView.setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
                mClockViewBold.setTypeface(Typeface.create("sans-serif-thin", Typeface.ITALIC));
                break;
            case 7:
                mClockView.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
                mClockViewBold.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
                break;
            case 8:
                mClockView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
                mClockViewBold.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
                break;
            case 9:
                mClockView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
                mClockViewBold.setTypeface(Typeface.create("sans-serif-condensed", Typeface.ITALIC));
                break;
            case 10:
                mClockView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
                mClockViewBold.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
                break;
            case 11:
                mClockView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
                mClockViewBold.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC));
                break;
            case 12:
                mClockView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                mClockViewBold.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                break;
            case 13:
                mClockView.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
                mClockViewBold.setTypeface(Typeface.create("sans-serif-medium", Typeface.ITALIC));
                break;
            case 14:
                mClockView.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
                mClockViewBold.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.NORMAL));
                break;
            case 15:
                mClockView.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
                mClockViewBold.setTypeface(Typeface.create("sans-serif-condensed-light", Typeface.ITALIC));
                break;
            case 16:
                mClockView.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
                mClockViewBold.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
                break;
            case 17:
                mClockView.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
                mClockViewBold.setTypeface(Typeface.create("sans-serif-black", Typeface.ITALIC));
                break;
            case 18:
                mClockView.setTypeface(Typeface.create("cursive", Typeface.NORMAL));
                mClockViewBold.setTypeface(Typeface.create("cursive", Typeface.NORMAL));
                break;
            case 19:
                mClockView.setTypeface(Typeface.create("cursive", Typeface.BOLD));
                mClockViewBold.setTypeface(Typeface.create("cursive", Typeface.BOLD));
                break;
            case 20:
                mClockView.setTypeface(Typeface.create("casual", Typeface.NORMAL));
                mClockViewBold.setTypeface(Typeface.create("casual", Typeface.NORMAL));
                break;
            case 21:
                mClockView.setTypeface(Typeface.create("serif", Typeface.NORMAL));
                mClockViewBold.setTypeface(Typeface.create("serif", Typeface.NORMAL));
                break;
            case 22:
                mClockView.setTypeface(Typeface.create("serif", Typeface.ITALIC));
                mClockViewBold.setTypeface(Typeface.create("serif", Typeface.ITALIC));
                break;
            case 23:
                mClockView.setTypeface(Typeface.create("serif", Typeface.BOLD));
                mClockViewBold.setTypeface(Typeface.create("serif", Typeface.BOLD));
                break;
            case 24:
                mClockView.setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
                mClockViewBold.setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
                break;
            case 25:
                mClockView.setTypeface(Typeface.create("gobold-light-sys", Typeface.NORMAL));
                mClockViewBold.setTypeface(Typeface.create("gobold-light-sys", Typeface.NORMAL));
                break;
            case 26:
                mClockView.setTypeface(Typeface.create("roadrage-sys", Typeface.NORMAL));
                mClockViewBold.setTypeface(Typeface.create("roadrage-sys", Typeface.NORMAL));
                break;
            case 27:
                mClockView.setTypeface(Typeface.create("snowstorm-sys", Typeface.NORMAL));
                mClockViewBold.setTypeface(Typeface.create("snowstorm-sys", Typeface.NORMAL));
                break;
            case 28:
                mClockView.setTypeface(Typeface.create("googlesans-sys", Typeface.NORMAL));
                mClockViewBold.setTypeface(Typeface.create("googlesans-sys", Typeface.NORMAL));
                break;
            case 29:
                mClockView.setTypeface(Typeface.create("neoneon-sys", Typeface.NORMAL));
                mClockViewBold.setTypeface(Typeface.create("neoneon-sys", Typeface.NORMAL));
                break;
            case 30:
                mClockView.setTypeface(Typeface.create("themeable-sys", Typeface.NORMAL));
                mClockViewBold.setTypeface(Typeface.create("themeable-sys", Typeface.NORMAL));
                break;
            case 31:
                mClockView.setTypeface(Typeface.create("samsung-sys", Typeface.NORMAL));
                mClockViewBold.setTypeface(Typeface.create("samsung-sys", Typeface.NORMAL));
                break;
            case 32:
                mClockView.setTypeface(Typeface.create("mexcellent-sys", Typeface.NORMAL));
                mClockViewBold.setTypeface(Typeface.create("mexcellent-sys", Typeface.NORMAL));
                break;
            case 33:
                mClockView.setTypeface(Typeface.create("burnstown-sys", Typeface.NORMAL));
                mClockViewBold.setTypeface(Typeface.create("burnstown-sys", Typeface.NORMAL));
                break;
            case 34:
                mClockView.setTypeface(Typeface.create("dumbledor-sys", Typeface.NORMAL));
                mClockViewBold.setTypeface(Typeface.create("dumbledor-sys", Typeface.NORMAL));
                break;
            case 35:
                mClockView.setTypeface(Typeface.create("phantombold-sys", Typeface.NORMAL));
                mClockViewBold.setTypeface(Typeface.create("phantombold-sys", Typeface.NORMAL));
                break;
            case 36:
                mClockView.setTypeface(Typeface.create("sourcesanspro-sys", Typeface.NORMAL));
                mClockViewBold.setTypeface(Typeface.create("sourcesanspro-sys", Typeface.NORMAL));
                break;
            case 37:
                mClockView.setTypeface(Typeface.create("circularstd-sys", Typeface.NORMAL));
                mClockViewBold.setTypeface(Typeface.create("circularstd-sys", Typeface.NORMAL));
                break;
            case 38:
                mClockView.setTypeface(Typeface.create("oneplusslate-sys", Typeface.NORMAL));
                mClockViewBold.setTypeface(Typeface.create("oneplusslate-sys", Typeface.NORMAL));
                break;
            case 39:
                mClockView.setTypeface(Typeface.create("aclonica-sys", Typeface.NORMAL));
                mClockViewBold.setTypeface(Typeface.create("aclonica-sys", Typeface.NORMAL));
                break;
            case 40:
                mClockView.setTypeface(Typeface.create("amarante-sys", Typeface.NORMAL));
                mClockViewBold.setTypeface(Typeface.create("amarante-sys", Typeface.NORMAL));
                break;
            case 41:
                mClockView.setTypeface(Typeface.create("bariol-sys", Typeface.NORMAL));
                mClockViewBold.setTypeface(Typeface.create("bariol-sys", Typeface.NORMAL));
                break;
            case 42:
                mClockView.setTypeface(Typeface.create("cagliostro-sys", Typeface.NORMAL));
                mClockViewBold.setTypeface(Typeface.create("cagliostro-sys", Typeface.NORMAL));
                break;
            case 43:
                mClockView.setTypeface(Typeface.create("coolstory-sys", Typeface.NORMAL));
                mClockViewBold.setTypeface(Typeface.create("coolstory-sys", Typeface.NORMAL));
                break;
            case 44:
                mClockView.setTypeface(Typeface.create("lgsmartgothic-sys", Typeface.NORMAL));
                mClockViewBold.setTypeface(Typeface.create("lgsmartgothic-sys", Typeface.NORMAL));
                break;
            case 45:
                mClockView.setTypeface(Typeface.create("rosemary-sys", Typeface.NORMAL));
                mClockViewBold.setTypeface(Typeface.create("rosemary-sys", Typeface.NORMAL));
                break;
            case 46:
                mClockView.setTypeface(Typeface.create("sonysketch-sys", Typeface.NORMAL));
                mClockViewBold.setTypeface(Typeface.create("sonysketch-sys", Typeface.NORMAL));
                break;
            case 47:
                mClockView.setTypeface(Typeface.create("surfer-sys", Typeface.NORMAL));
                mClockViewBold.setTypeface(Typeface.create("surfer-sys", Typeface.NORMAL));
                break;
        }
    }

    public void refreshclocksize() {
        final Resources res = getContext().getResources();
        boolean isPrimary = UserHandle.getCallingUserId() == UserHandle.USER_OWNER;
        int lockClockSize = isPrimary ? getLockClockSize() : 54;

        switch (lockClockSize) {
            case 10:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_10));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_10));
                break;
            case 11:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_11));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_11));
                break;
            case 12:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_12));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_12));
                break;
            case 13:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_13));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_13));
                break;
            case 14:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_14));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_14));
                break;
            case 15:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_15));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_15));
                break;
            case 16:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_16));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_16));
                break;
            case 17:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_17));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_17));
                break;
            case 18:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_18));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_18));
                break;
            case 19:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_19));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_19));
                break;
            case 20:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_20));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_20));
                break;
            case 21:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_21));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_21));
                break;
            case 22:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_22));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_22));
                break;
            case 23:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_23));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_23));
                break;
            case 24:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_24));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_24));
                break;
            case 25:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_25));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_25));
                break;
            case 26:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_26));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_26));
                break;
            case 27:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_27));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_27));
                break;
            case 28:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_28));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_28));
                break;
            case 29:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_29));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_29));
                break;
            case 30:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_30));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_30));
                break;
            case 31:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_31));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_31));
                break;
            case 32:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_32));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_32));
                break;
            case 33:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_33));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_33));
                break;
            case 34:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_34));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_34));
                break;
            case 35:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_35));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_35));
                break;
            case 36:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_36));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_36));
                break;
            case 37:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_37));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_37));
                break;
            case 38:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_38));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_38));
                break;
            case 39:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_39));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_39));
                break;
            case 40:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_40));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_40));
                break;
            case 41:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_41));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_41));
                break;
            case 42:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_42));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_42));
                break;
            case 43:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_43));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_43));
                break;
            case 44:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_44));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_44));
                break;
            case 45:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_45));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_45));
                break;
            case 46:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_46));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_46));
                break;
            case 47:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_47));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_47));
                break;
            case 48:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_48));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_48));
                break;
            case 49:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_49));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_49));
                break;
            case 50:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_50));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_date_font_size_50));
                break;
            case 51:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_51));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_51));
                break;
            case 52:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_52));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_52));
                break;
            case 53:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_53));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_53));
                break;
            case 54:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_54));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_54));
                break;
            case 55:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_55));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_55));
                break;
            case 56:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_56));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_56));
                break;
            case 57:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_57));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_57));
                break;
            case 58:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_58));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_58));
                break;
            case 59:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_59));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_59));
                break;
            case 60:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_60));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_60));
                break;
            case 61:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_61));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_61));
                break;
            case 62:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_62));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_62));
                break;
            case 63:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_63));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_63));
                break;
            case 64:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_64));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_64));
                break;
            case 65:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_65));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_65));
                break;
            case 66:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_66));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_66));
                break;
            case 67:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_67));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_67));
                break;
            case 68:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_68));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_68));
                break;
            case 69:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_69));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_69));
                break;
            case 70:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_70));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_70));
                break;
            case 71:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_71));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_71));
                break;
            case 72:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_72));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_72));
                break;
            case 73:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_73));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_73));
                break;
            case 74:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_74));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_74));
                break;
            case 75:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_75));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_75));
                break;
            case 76:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_76));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_76));
                break;
            case 77:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_77));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_77));
                break;
            case 78:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_78));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_78));
                break;
            case 79:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_79));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_79));
                break;
            case 80:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_80));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_80));
                break;
            case 81:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_81));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_81));
                break;
            case 82:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_82));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_82));
                break;
            case 83:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_83));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_83));
                break;
            case 84:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_84));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_84));
                break;
            case 85:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_85));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_85));
                break;
            case 86:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_86));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_86));
                break;
            case 87:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_87));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_87));
                break;
            case 88:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_88));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_88));
                break;
            case 89:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_89));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_89));
                break;
            case 90:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_90));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_90));
                break;
            case 91:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_91));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_91));
                break;
            case 92:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_92));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_92));
                break;
            case 93:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_93));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_93));
                break;
            case 94:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_94));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_94));
                break;
            case 95:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_95));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_95));
                break;
            case 96:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_96));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_96));
                break;
            case 97:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_97));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_97));
                break;
            case 98:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_98));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_98));
                break;
            case 99:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_99));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_99));
                break;
            case 100:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_100));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_100));
                break;
            case 101:
                mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_101));
                mClockViewBold.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimensionPixelSize(R.dimen.lock_clock_font_size_101));
                break;
        }
    }

    /**
     * Sets if the keyguard slice is showing a center-aligned header. We need a smaller clock in
     * these cases.
     */
    void setKeyguardShowingHeader(boolean hasHeader) {
        if (mShowingHeader == hasHeader) {
            return;
        }
        mShowingHeader = hasHeader;
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    ClockManager.ClockChangedListener getClockChangedListener() {
        return mClockChangedListener;
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    StatusBarStateController.StateListener getStateListener() {
        return mStateListener;
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("KeyguardClockSwitch:");
        pw.println("  mClockPlugin: " + mClockPlugin);
        pw.println("  mClockView: " + mClockView);
        pw.println("  mClockViewBold: " + mClockViewBold);
        pw.println("  mSmallClockFrame: " + mSmallClockFrame);
        pw.println("  mBigClockContainer: " + mBigClockContainer);
        pw.println("  mKeyguardStatusArea: " + mKeyguardStatusArea);
        pw.println("  mDarkAmount: " + mDarkAmount);
        pw.println("  mShowingHeader: " + mShowingHeader);
        pw.println("  mSupportsDarkText: " + mSupportsDarkText);
        pw.println("  mColorPalette: " + Arrays.toString(mColorPalette));
    }

    /**
     * {@link Visibility} transformation that scales the view while it is disappearing/appearing and
     * transitions suddenly at a cutoff fraction during the animation.
     */
    private class ClockVisibilityTransition extends android.transition.Visibility {

        private static final String PROPNAME_VISIBILITY = "systemui:keyguard:visibility";

        private float mCutoff;
        private float mScale;

        /**
         * Constructs a transition that switches between visible/invisible at a cutoff and scales in
         * size while appearing/disappearing.
         */
        ClockVisibilityTransition() {
            setCutoff(1f);
            setScale(1f);
        }

        /**
         * Sets the transition point between visible/invisible.
         *
         * @param cutoff The fraction in [0, 1] when the view switches between visible/invisible.
         * @return This transition object
         */
        public ClockVisibilityTransition setCutoff(float cutoff) {
            mCutoff = cutoff;
            return this;
        }

        /**
         * Sets the scale factor applied while appearing/disappearing.
         *
         * @param scale Scale factor applied while appearing/disappearing. When factor is less than
         *              one, the view will shrink while disappearing. When it is greater than one,
         *              the view will expand while disappearing.
         * @return This transition object
         */
        public ClockVisibilityTransition setScale(float scale) {
            mScale = scale;
            return this;
        }

        @Override
        public void captureStartValues(TransitionValues transitionValues) {
            super.captureStartValues(transitionValues);
            captureVisibility(transitionValues);
        }

        @Override
        public void captureEndValues(TransitionValues transitionValues) {
            super.captureStartValues(transitionValues);
            captureVisibility(transitionValues);
        }

        private void captureVisibility(TransitionValues transitionValues) {
            transitionValues.values.put(PROPNAME_VISIBILITY,
                    transitionValues.view.getVisibility());
        }

        @Override
        public Animator onAppear(ViewGroup sceneRoot, View view, TransitionValues startValues,
                TransitionValues endValues) {
            if (!sceneRoot.isShown()) {
                return null;
            }
            final float cutoff = mCutoff;
            final int startVisibility = View.INVISIBLE;
            final int endVisibility = (int) endValues.values.get(PROPNAME_VISIBILITY);
            final float startScale = mScale;
            final float endScale = 1f;
            return createAnimator(view, cutoff, startVisibility, endVisibility, startScale,
                    endScale);
        }

        @Override
        public Animator onDisappear(ViewGroup sceneRoot, View view, TransitionValues startValues,
                TransitionValues endValues) {
            if (!sceneRoot.isShown()) {
                return null;
            }
            final float cutoff = 1f - mCutoff;
            final int startVisibility = View.VISIBLE;
            final int endVisibility = (int) endValues.values.get(PROPNAME_VISIBILITY);
            final float startScale = 1f;
            final float endScale = mScale;
            return createAnimator(view, cutoff, startVisibility, endVisibility, startScale,
                    endScale);
        }

        private Animator createAnimator(View view, float cutoff, int startVisibility,
                int endVisibility, float startScale, float endScale) {
            view.setPivotY(view.getHeight() - view.getPaddingBottom());
            ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            animator.addUpdateListener(animation -> {
                final float fraction = animation.getAnimatedFraction();
                if (fraction > cutoff) {
                    view.setVisibility(endVisibility);
                }
                final float scale = MathUtils.lerp(startScale, endScale, fraction);
                view.setScaleX(scale);
                view.setScaleY(scale);
            });
            animator.addListener(new KeepAwakeAnimationListener(getContext()) {
                @Override
                public void onAnimationStart(Animator animation) {
                    super.onAnimationStart(animation);
                    view.setVisibility(startVisibility);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    animation.removeListener(this);
                }
            });
            addListener(new TransitionListenerAdapter() {
                @Override
                public void onTransitionEnd(Transition transition) {
                    view.setVisibility(endVisibility);
                    view.setScaleX(1f);
                    view.setScaleY(1f);
                    transition.removeListener(this);
                }
            });
            return animator;
        }
    }
}
