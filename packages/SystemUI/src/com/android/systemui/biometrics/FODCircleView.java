/**
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.biometrics;

import static com.android.internal.widget.LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_DPM_LOCK_NOW;
import static com.android.internal.widget.LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_LOCKOUT;
import static com.android.internal.widget.LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_TIMEOUT;
import static com.android.internal.widget.LockPatternUtils.StrongAuthTracker.STRONG_AUTH_REQUIRED_AFTER_USER_LOCKDOWN;

import android.app.ActivityManager;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.drawable.AnimationDrawable;
import android.hardware.biometrics.BiometricSourceType;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Handler;
import android.os.UserHandle;
import android.os.Looper;
import android.os.RemoteException;
import android.provider.Settings;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.KeyguardUpdateMonitorCallback;
import com.android.systemui.R;

import vendor.lineage.biometrics.fingerprint.inscreen.V1_0.IFingerprintInscreen;
import vendor.lineage.biometrics.fingerprint.inscreen.V1_0.IFingerprintInscreenCallback;

import java.util.NoSuchElementException;
import java.util.Timer;
import java.util.TimerTask;

import com.android.internal.util.custom.fod.FodScreenOffHandler;

public class FODCircleView extends ImageView {
    private final int mPositionX;
    private final int mPositionY;
    private final int mSize;
    private final int mDreamingMaxOffset;
    private final int mNavigationBarSize;
    private final boolean mShouldBoostBrightness;
    private final Paint mPaintFingerprint = new Paint();
    private final WindowManager.LayoutParams mParams = new WindowManager.LayoutParams();
    private final WindowManager mWindowManager;

    private IFingerprintInscreen mFingerprintInscreenDaemon;

    private int mDreamingOffsetX;
    private int mDreamingOffsetY;

    private boolean mIsBouncer;
    private boolean mIsDreaming;
    private boolean mIsKeyguard;
    private boolean mIsShowing;
    private boolean mIsCircleShowing;
    private boolean mCanUnlockWithFp;

    private Handler mHandler;

    private Timer mBurnInProtectionTimer;

    private FODAnimation mFODAnimation;
    private boolean mIsRecognizingAnimEnabled;

    private int mSelectedIcon;
    private final int[] ICON_STYLES = {
        R.drawable.fod_icon_default,
        R.drawable.fod_icon_default_1,
        R.drawable.fod_icon_default_2,
        R.drawable.fod_icon_default_3,
        R.drawable.fod_icon_default_4,
        R.drawable.fod_icon_default_5,
        R.drawable.fod_icon_arc_reactor,
        R.drawable.fod_icon_cpt_america_flat,
        R.drawable.fod_icon_cpt_america_flat_gray,
        R.drawable.fod_icon_dragon_black_flat,
        R.drawable.fod_icon_evo1,
        R.drawable.fod_icon_glow_circle,
        R.drawable.fod_icon_neon_arc,
        R.drawable.fod_icon_neon_arc_gray,
        R.drawable.fod_icon_neon_circle_pink,
        R.drawable.fod_icon_neon_triangle,
        R.drawable.fod_icon_paint_splash_circle,
        R.drawable.fod_icon_rainbow_horn,
        R.drawable.fod_icon_shooky,
        R.drawable.fod_icon_spiral_blue,
        R.drawable.fod_icon_sun_metro,
        R.drawable.fod_icon_scratch_red_blue,
        R.drawable.fod_icon_scratch_pink_blue,
        R.drawable.fod_icon_fire_ice_ouroboros,
        R.drawable.fod_icon_transparent
    };

    private int mPressedIcon;
    private final int[] PRESSED_STYLES = {
        R.drawable.fod_icon_pressed_miui_cyan_light,
        R.drawable.fod_icon_pressed_miui_white_light,
        R.drawable.fod_icon_pressed_vivo_cyan,
        R.drawable.fod_icon_pressed_vivo_cyan_shadow,
        R.drawable.fod_icon_pressed_vivo_cyan_shadow_et713,
        R.drawable.fod_icon_pressed_vivo_green,
        R.drawable.fod_icon_pressed_vivo_yellow_shadow
    };

    private FodScreenOffHandler mFodScreenOffHandler;

    private IFingerprintInscreenCallback mFingerprintInscreenCallback =
            new IFingerprintInscreenCallback.Stub() {
        @Override
        public void onFingerDown() {
            mHandler.post(() -> showCircle());
        }

        @Override
        public void onFingerUp() {
            mHandler.post(() -> hideCircle());
        }
    };

    private FingerprintManager mFingerprintManager;
    private KeyguardUpdateMonitor mUpdateMonitor;

    private KeyguardUpdateMonitorCallback mMonitorCallback = new KeyguardUpdateMonitorCallback() {
        @Override
        public void onDreamingStateChanged(boolean dreaming) {
            mIsDreaming = dreaming;
            dispatchFodDreamingStateChanged();
            updateAlpha();

            if (dreaming) {
                mBurnInProtectionTimer = new Timer();
                mBurnInProtectionTimer.schedule(new BurnInProtectionTask(), 0, 60 * 1000);
            } else if (mBurnInProtectionTimer != null) {
                mBurnInProtectionTimer.cancel();
            }
        }

        @Override
        public void onKeyguardVisibilityChanged(boolean showing) {
            mIsKeyguard = showing;
            updateStyle();
            if (mFODAnimation != null) {
                mFODAnimation.setAnimationKeyguard(mIsKeyguard);
            }
        }

        @Override
        public void onKeyguardBouncerChanged(boolean isBouncer) {
            mIsBouncer = isBouncer;

            if (isBouncer) {
                hide();
            } else if (mIsKeyguard && mUpdateMonitor.isFingerprintDetectionRunning()) {
                show();
            }
        }

        @Override
        public void onScreenTurnedOff() {
            hide();
            dispatchFodScreenStateChanged(false);
        }

        @Override
        public void onScreenTurnedOn() {
            if (mUpdateMonitor.isFingerprintDetectionRunning()) {
                show();
            }
            dispatchFodScreenStateChanged(true);
        }

        @Override
        public void onBiometricRunningStateChanged(boolean running,
            BiometricSourceType biometricSourceType) {
            dispatchFodFingerprintRunningStateChanged(running);
        }

        @Override
        public void onBiometricHelp(int msgId, String helpString,
                BiometricSourceType biometricSourceType) {
            if (msgId == -1){ // Auth error
                hideCircle();
                mHandler.post(() -> mFODAnimation.hideFODanimation());
            }
        }

        @Override
        public void onStrongAuthStateChanged(int userId) {
            mCanUnlockWithFp = canUnlockWithFp();
            if (mIsShowing && !mCanUnlockWithFp){
                hide();
            }
        }
    };

    private void dispatchFodScreenStateChanged(boolean interactive){
        dispatchFodFingerprintHasEnrolledFinger();
        if (mFodScreenOffHandler != null){
            mFodScreenOffHandler.onScreenStateChanged(interactive);
        }
    }

    private void dispatchFodFingerprintRunningStateChanged(boolean running){
        if (mFodScreenOffHandler != null){
            mFodScreenOffHandler.onFingerprintRunningStateChanged(running);
        }
    }

    private void dispatchFodDreamingStateChanged(){
        if (mFodScreenOffHandler != null){
            mFodScreenOffHandler.onDreamingStateChanged(mIsDreaming);
        }
    }

    private void dispatchFodFingerprintHasEnrolledFinger(){
        if (mFodScreenOffHandler != null &&
                mFingerprintManager != null && 
                mFingerprintManager.isHardwareDetected()) {
            boolean enrolled = mFingerprintManager.hasEnrolledFingerprints(UserHandle.myUserId());
            mFodScreenOffHandler.hasEnrolledFingerprints(enrolled);
        }
    }

    private boolean canUnlockWithFp() {
        int currentUser = ActivityManager.getCurrentUser();
        boolean biometrics = mUpdateMonitor.isUnlockingWithBiometricsPossible(currentUser);
        KeyguardUpdateMonitor.StrongAuthTracker strongAuthTracker =
                mUpdateMonitor.getStrongAuthTracker();
        int strongAuth = strongAuthTracker.getStrongAuthForUser(currentUser);
        if (biometrics && !strongAuthTracker.hasUserAuthenticatedSinceBoot()) {
            return false;
        } else if (biometrics && (strongAuth & STRONG_AUTH_REQUIRED_AFTER_TIMEOUT) != 0) {
            return false;
        } else if (biometrics && (strongAuth & STRONG_AUTH_REQUIRED_AFTER_DPM_LOCK_NOW) != 0) {
            return false;
        } else if (biometrics && (strongAuth & STRONG_AUTH_REQUIRED_AFTER_LOCKOUT) != 0) {
            return false;
        } else if (biometrics && (strongAuth & STRONG_AUTH_REQUIRED_AFTER_USER_LOCKDOWN) != 0) {
            return false;
        }
        return true;
    }

    public FODCircleView(Context context, FodScreenOffHandler fodScreenOffHandler) {
        super(context);

        mFingerprintManager = (FingerprintManager) context.getSystemService(Context.FINGERPRINT_SERVICE);
        mFodScreenOffHandler = fodScreenOffHandler;

        IFingerprintInscreen daemon = getFingerprintInScreenDaemon();
        if (daemon == null) {
            throw new RuntimeException("Unable to get IFingerprintInscreen");
        }

        try {
            mShouldBoostBrightness = daemon.shouldBoostBrightness();
            mPositionX = daemon.getPositionX();
            mPositionY = daemon.getPositionY();
            mSize = daemon.getSize();
        } catch (RemoteException e) {
            throw new RuntimeException("Failed to retrieve FOD circle position or size");
        }

        Resources res = context.getResources();

        mPaintFingerprint.setAntiAlias(true);
        mPaintFingerprint.setColor(res.getColor(R.color.config_fodColor));

        mWindowManager = context.getSystemService(WindowManager.class);

        mNavigationBarSize = res.getDimensionPixelSize(R.dimen.navigation_bar_size);

        mDreamingMaxOffset = (int) (mSize * 0.1f);

        mHandler = new Handler(Looper.getMainLooper());

        mParams.height = mSize;
        mParams.width = mSize;
        mParams.format = PixelFormat.TRANSLUCENT;

        mParams.setTitle("Fingerprint on display");
        mParams.packageName = "android";
        mParams.type = WindowManager.LayoutParams.TYPE_DISPLAY_OVERLAY;
        mParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_DIM_BEHIND |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        mParams.gravity = Gravity.TOP | Gravity.LEFT;

        mWindowManager.addView(this, mParams);

        updateStyle();
        updatePosition();
        hide();

        mUpdateMonitor = KeyguardUpdateMonitor.getInstance(context);
        mUpdateMonitor.registerCallback(mMonitorCallback);

        mCanUnlockWithFp = canUnlockWithFp();

        mFODAnimation = new FODAnimation(context, mPositionX, mPositionY);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mIsCircleShowing) {
            setImageResource(PRESSED_STYLES[mPressedIcon]);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getAxisValue(MotionEvent.AXIS_X);
        float y = event.getAxisValue(MotionEvent.AXIS_Y);

        boolean newIsInside = (x > 0 && x < mSize) && (y > 0 && y < mSize);

        if (event.getAction() == MotionEvent.ACTION_DOWN && newIsInside) {
            showCircle();
            if (mIsRecognizingAnimEnabled) {
                mHandler.post(() -> mFODAnimation.showFODanimation());
            }
            return true;
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            hideCircle();
            mHandler.post(() -> mFODAnimation.hideFODanimation());
            return true;
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            return true;
        }

        mHandler.post(() -> mFODAnimation.hideFODanimation());
        return false;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        updateStyle();
        updatePosition();
    }

    public IFingerprintInscreen getFingerprintInScreenDaemon() {
        if (mFingerprintInscreenDaemon == null) {
            try {
                mFingerprintInscreenDaemon = IFingerprintInscreen.getService();
                if (mFingerprintInscreenDaemon != null) {
                    mFingerprintInscreenDaemon.setCallback(mFingerprintInscreenCallback);
                    mFingerprintInscreenDaemon.asBinder().linkToDeath((cookie) -> {
                        mFingerprintInscreenDaemon = null;
                    }, 0);
                }
            } catch (NoSuchElementException | RemoteException e) {
                // do nothing
            }
        }
        return mFingerprintInscreenDaemon;
    }

    public void dispatchPress() {
        IFingerprintInscreen daemon = getFingerprintInScreenDaemon();
        try {
            daemon.onPress();
        } catch (RemoteException e) {
            // do nothing
        }
    }

    public void dispatchRelease() {
        IFingerprintInscreen daemon = getFingerprintInScreenDaemon();
        try {
            daemon.onRelease();
        } catch (RemoteException e) {
            // do nothing
        }
    }

    public void dispatchShow() {
        IFingerprintInscreen daemon = getFingerprintInScreenDaemon();
        try {
            daemon.onShowFODView();
        } catch (RemoteException e) {
            // do nothing
        }
    }

    public void dispatchHide() {
        IFingerprintInscreen daemon = getFingerprintInScreenDaemon();
        try {
            daemon.onHideFODView();
        } catch (RemoteException e) {
            // do nothing
        }
    }

    public void showCircle() {
        mIsCircleShowing = true;

        setKeepScreenOn(true);

        setDim(true);
        updateAlpha();
        dispatchPress();

        setImageResource(PRESSED_STYLES[mPressedIcon]);
        invalidate();
    }

    public void hideCircle() {
        mIsCircleShowing = false;

        setImageResource(ICON_STYLES[mSelectedIcon]);
        invalidate();

        dispatchRelease();

        setDim(false);
        updateAlpha();

        setKeepScreenOn(false);
    }

    public void show() {
        if (!mUpdateMonitor.isScreenOn()) {
            // Keyguard is shown just after screen turning off
            return;
        }

        if (mIsBouncer) {
            // Ignore show calls when Keyguard pin screen is being shown
            return;
        }

        if (!mCanUnlockWithFp){
            // Ignore when unlocking with fp is not possible
            return;
        }

        mIsShowing = true;

        updatePosition();

        dispatchShow();
        setVisibility(View.VISIBLE);
    }

    public void hide() {
        mIsShowing = false;

        setVisibility(View.GONE);
        hideCircle();
        dispatchHide();
    }

    private void updateAlpha() {
        if (mIsCircleShowing) {
            setAlpha(1.0f);
        } else {
            setAlpha(mIsDreaming ? 0.5f : 1.0f);
        }
    }

    private void updateStyle() {
        mIsRecognizingAnimEnabled = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.FOD_RECOGNIZING_ANIMATION, 0) != 0;
        mSelectedIcon = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.FOD_ICON, 0);
        mPressedIcon = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.FOD_PRESSED_STATE, 0);
        if (mFODAnimation != null) {
            mFODAnimation.update();
        }
    }

    private void updatePosition() {
        Display defaultDisplay = mWindowManager.getDefaultDisplay();

        Point size = new Point();
        defaultDisplay.getRealSize(size);

        int rotation = defaultDisplay.getRotation();
        switch (rotation) {
            case Surface.ROTATION_0:
                mParams.x = mPositionX;
                mParams.y = mPositionY;
                break;
            case Surface.ROTATION_90:
                mParams.x = mPositionY;
                mParams.y = mPositionX;
                break;
            case Surface.ROTATION_180:
                mParams.x = mPositionX;
                mParams.y = size.y - mPositionY - mSize;
                break;
            case Surface.ROTATION_270:
                mParams.x = size.x - mPositionY - mSize - mNavigationBarSize;
                mParams.y = mPositionX;
                break;
            default:
                throw new IllegalArgumentException("Unknown rotation: " + rotation);
        }

        if (mIsDreaming) {
            mParams.x += mDreamingOffsetX;
            mParams.y += mDreamingOffsetY;
            mFODAnimation.updateParams(mParams.y);
        }

        mWindowManager.updateViewLayout(this, mParams);
    }

    private void setDim(boolean dim) {
        if (dim) {
            int curBrightness = Settings.System.getInt(getContext().getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, 100);
            int dimAmount = 0;

            IFingerprintInscreen daemon = getFingerprintInScreenDaemon();
            try {
                dimAmount = daemon.getDimAmount(curBrightness);
            } catch (RemoteException e) {
                // do nothing
            }

            if (mShouldBoostBrightness) {
                mParams.screenBrightness = 1.0f;
            }

            mParams.dimAmount = dimAmount / 255.0f;
        } else {
            mParams.screenBrightness = 0.0f;
            mParams.dimAmount = 0.0f;
        }

        mWindowManager.updateViewLayout(this, mParams);
    }

    private class BurnInProtectionTask extends TimerTask {
        @Override
        public void run() {
            long now = System.currentTimeMillis() / 1000 / 60;

            mDreamingOffsetX = (int) (now % (mDreamingMaxOffset * 4));
            if (mDreamingOffsetX > mDreamingMaxOffset * 2) {
                mDreamingOffsetX = mDreamingMaxOffset * 4 - mDreamingOffsetX;
            }

            // Let y to be not synchronized with x, so that we get maximum movement
            mDreamingOffsetY = (int) ((now + mDreamingMaxOffset / 3) % (mDreamingMaxOffset * 2));
            if (mDreamingOffsetY > mDreamingMaxOffset * 2) {
                mDreamingOffsetY = mDreamingMaxOffset * 4 - mDreamingOffsetY;
            }

            mDreamingOffsetX -= mDreamingMaxOffset;
            mDreamingOffsetY -= mDreamingMaxOffset;

            mHandler.post(() -> updatePosition());
        }
    };
}
