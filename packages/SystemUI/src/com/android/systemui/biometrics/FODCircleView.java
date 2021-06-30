/*
 * Copyright (C) 2019-2020 The LineageOS Project
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

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.biometrics.BiometricSourceType;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.pocket.IPocketCallback;
import android.pocket.PocketManager;
import android.provider.Settings;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager.LayoutParams;
import android.view.WindowManager;
import android.widget.ImageView;

import com.android.internal.util.evolution.EvolutionUtils;
import com.android.internal.widget.LockPatternUtils;
import com.android.keyguard.KeyguardSecurityModel.SecurityMode;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.KeyguardUpdateMonitorCallback;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.tuner.TunerService;

import vendor.lineage.biometrics.fingerprint.inscreen.V1_0.IFingerprintInscreen;
import vendor.lineage.biometrics.fingerprint.inscreen.V1_0.IFingerprintInscreenCallback;

import java.util.NoSuchElementException;
import java.util.Timer;
import java.util.TimerTask;

public class FODCircleView extends ImageView implements TunerService.Tunable {
    private static final String DOZE_INTENT = "com.android.systemui.doze.pulse";
    private static final String FOD_GESTURE =
            "system:" + Settings.System.FOD_GESTURE;
    private static final String DOZE_ENABLED =
            Settings.Secure.DOZE_ENABLED;
    private static final String FOD_ANIM =
            "system:" + Settings.System.FOD_ANIM;
    private static final String FOD_RECOGNIZING_ANIMATION =
            "system:" + Settings.System.FOD_RECOGNIZING_ANIMATION;
    private static final String FOD_COLOR =
            "system:" + Settings.System.FOD_COLOR;

    private final int mPositionX;
    private final int mPositionY;
    private final int mSize;
    private final int mDreamingMaxOffset;
    private final int mNavigationBarSize;
    private final boolean mShouldBoostBrightness;
    private final boolean mShouldEnableDimlayer;
    private final Paint mPaintFingerprintBackground = new Paint();
    private final LayoutParams mParams = new LayoutParams();
    private final LayoutParams mPressedParams = new LayoutParams();
    private final WindowManager mWindowManager;

    private FODIconView mFODIcon;
    private IFingerprintInscreen mFingerprintInscreenDaemon;
    private vendor.lineage.biometrics.fingerprint.inscreen.V1_1.IFingerprintInscreen
        mFingerprintInscreenDaemonV1_1;

    private int mColorBackground;
    private int mDreamingOffsetY;

    private boolean mIsBiometricRunning;
    private boolean mIsBouncer;
    private boolean mIsCircleShowing;
    private boolean mIsDreaming;
    private boolean mIsKeyguard;

    private boolean mDozeEnabled;
    private boolean mDozeEnabledByDefault;
    private boolean mFodGestureEnable;
    private boolean mPressPending;
    private boolean mScreenTurnedOn;
    private boolean mTouchedOutside;

    private PowerManager mPowerManager;
    private PowerManager.WakeLock mWakeLock;

    private Handler mHandler;

    private final ImageView mPressedView;

    private LockPatternUtils mLockPatternUtils;

    private Timer mBurnInProtectionTimer;

    private FODAnimation mFODAnimation;
    private boolean mIsRecognizingAnimEnabled;
    private int mFodAnim = 0;

    private int mDefaultPressedColor;
    private int mPressedColor;
    private final int[] PRESSED_COLOR = {
        R.drawable.fod_icon_pressed,
        R.drawable.fod_icon_pressed_cyan,
        R.drawable.fod_icon_pressed_green,
        R.drawable.fod_icon_pressed_yellow,
        R.drawable.fod_icon_pressed_light_yellow
    };

    private IFingerprintInscreenCallback mFingerprintInscreenCallback =
            new IFingerprintInscreenCallback.Stub() {
        @Override
        public void onFingerDown() {
            if (mUpdateMonitor.userNeedsStrongAuth()) {
                // Keyguard requires strong authentication (not biometrics)
                return;
            }

            if (mFodGestureEnable && !mScreenTurnedOn) {
                if (mDozeEnabled) {
                    mHandler.post(() -> mContext.sendBroadcast(new Intent(DOZE_INTENT)));
                } else {
                    mWakeLock.acquire(3000);
                    mHandler.post(() -> mPowerManager.wakeUp(SystemClock.uptimeMillis(),
                        PowerManager.WAKE_REASON_GESTURE, FODCircleView.class.getSimpleName()));
                }
                mPressPending = true;
            } else {
                mHandler.post(() -> showCircle());
            }
        }

        @Override
        public void onFingerUp() {
            mHandler.post(() -> hideCircle());
            if (mPressPending) {
                mPressPending = false;
            }
        }
    };

    private KeyguardUpdateMonitor mUpdateMonitor;

    private KeyguardUpdateMonitorCallback mMonitorCallback = new KeyguardUpdateMonitorCallback() {
        @Override
        public void onBiometricAuthenticated(int userId, BiometricSourceType biometricSourceType,
                boolean isStrongBiometric) {
            // We assume that if biometricSourceType matches Fingerprint it will be
            // handled here, so we hide only when other biometric types authenticate
            if (biometricSourceType != BiometricSourceType.FINGERPRINT) {
                hide();
            }
        }

        @Override
        public void onBiometricRunningStateChanged(boolean running,
                BiometricSourceType biometricSourceType) {
            if (biometricSourceType == BiometricSourceType.FINGERPRINT) {
                mIsBiometricRunning = running;
            }
        }

        @Override
        public void onDreamingStateChanged(boolean dreaming) {
            mIsDreaming = dreaming;
            updateAlpha();

            if (mIsKeyguard && mUpdateMonitor.isFingerprintDetectionRunning()) {
                show();
                updateAlpha();
            } else {
                hide();
            }

            if (dreaming) {
                if (shouldShowOnDoze()) {
                    mBurnInProtectionTimer = new Timer();
                    mBurnInProtectionTimer.schedule(new BurnInProtectionTask(), 0, 60 * 1000);
                } else {
                    setImageDrawable(null);
                    invalidate();
                }
            } else {
                if (mBurnInProtectionTimer != null) {
                    mBurnInProtectionTimer.cancel();
                    updatePosition();
                }
                if (!shouldShowOnDoze()) {
                    invalidate();
                }
            }
        }

        @Override
        public void onKeyguardVisibilityChanged(boolean showing) {
            mIsKeyguard = showing;
            if (!showing) {
                hide();
            } else {
                updateAlpha();
            }
            if (mFODAnimation != null && mIsRecognizingAnimEnabled) {
                mFODAnimation.setAnimationKeyguard(mIsKeyguard);
            }
            if (mFODIcon != null) {
                mFODIcon.setIsKeyguard(mIsKeyguard);
            }
            handlePocketManagerCallback(showing);
        }

        @Override
        public void onKeyguardBouncerChanged(boolean isBouncer) {
            mIsBouncer = isBouncer;
            if (mUpdateMonitor.isFingerprintDetectionRunning() && !mUpdateMonitor.userNeedsStrongAuth()) {
                if (isPinOrPattern(mUpdateMonitor.getCurrentUser()) || !isBouncer) {
                    show();
                } else {
                    hide();
                }
            } else {
                hide();
            }
        }

        @Override
        public void onScreenTurnedOff() {
            mScreenTurnedOn = false;
            if (!mFodGestureEnable) {
                hide();
            } else {
                hideCircle();
            }
        }

        @Override
        public void onStartedWakingUp() {
            if (mUpdateMonitor.isFingerprintDetectionRunning()) {
                show();
            }
        }

        @Override
        public void onScreenTurnedOn() {
            if (mUpdateMonitor.isFingerprintDetectionRunning() && !mFodGestureEnable) {
                show();
            } else if (mFodGestureEnable && mPressPending) {
                mHandler.post(() -> showCircle());
                mPressPending = false;
            }
            mScreenTurnedOn = true;
        }

        @Override
        public void onBiometricHelp(int msgId, String helpString,
                BiometricSourceType biometricSourceType) {
            if (msgId == -1 && mFODAnimation != null && mIsRecognizingAnimEnabled) { // Auth error
                mHandler.post(() -> mFODAnimation.hideFODanimation());
            }
        }
    };

    private void handlePocketManagerCallback(boolean keyguardShowing){
        if (!keyguardShowing){
            if (mPocketCallbackAdded){
                mPocketCallbackAdded = false;
                mPocketManager.removeCallback(mPocketCallback);
            }
        } else {
            if (!mPocketCallbackAdded){
                mPocketCallbackAdded = true;
                mPocketManager.addCallback(mPocketCallback);
            }
        }
    }

    private PocketManager mPocketManager;
    private boolean mIsDeviceInPocket;
    private boolean mPocketCallbackAdded = false;
    private final IPocketCallback mPocketCallback = new IPocketCallback.Stub() {

        @Override
        public void onStateChanged(boolean isDeviceInPocket, int reason) {
            boolean wasDeviceInPocket = mIsDeviceInPocket;
            if (reason == PocketManager.REASON_SENSOR) {
                mIsDeviceInPocket = isDeviceInPocket;
            } else {
                mIsDeviceInPocket = false;
            }
        }
    };

    public FODCircleView(Context context) {
        super(context);

        IFingerprintInscreen daemon = getFingerprintInScreenDaemon();
        if (daemon == null) {
            throw new RuntimeException("Unable to get IFingerprintInscreen");
        }

        try {
            mShouldBoostBrightness = daemon.shouldBoostBrightness();
            mPositionX = daemon.getPositionX();
            mPositionY = daemon.getPositionY();
            mSize = daemon.getSize();
            mShouldEnableDimlayer = mFingerprintInscreenDaemonV1_1 == null ||
                    mFingerprintInscreenDaemonV1_1.shouldEnableDimlayer();
        } catch (RemoteException e) {
            throw new RuntimeException("Failed to retrieve FOD circle position or size");
        }

        Resources res = mContext.getResources();

        mColorBackground = res.getColor(R.color.config_fodColorBackground);
        mDefaultPressedColor = res.getInteger(com.android.internal.R.
             integer.config_fod_pressed_color);
        mPaintFingerprintBackground.setColor(mColorBackground);
        mPaintFingerprintBackground.setAntiAlias(true);

        mPowerManager = context.getSystemService(PowerManager.class);
        mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                 FODCircleView.class.getSimpleName());

        mWindowManager = mContext.getSystemService(WindowManager.class);
        boolean isFodAnimationAvailable = EvolutionUtils.isPackageInstalled(context,
                                    context.getResources().getString(
                                    com.android.internal.R.string.config_fodAnimationPackage));
        if (isFodAnimationAvailable) {
            mFODAnimation = new FODAnimation(mContext, mWindowManager, mPositionX, mPositionY);
        }

        mFODIcon = new FODIconView(mContext, mSize, mPositionX, mPositionY);
        mNavigationBarSize = res.getDimensionPixelSize(R.dimen.navigation_bar_size);

        mDreamingMaxOffset = (int) (mSize * 0.1f);

        mHandler = new Handler(Looper.getMainLooper());

        mParams.height = mSize;
        mParams.width = mSize;
        mParams.format = PixelFormat.TRANSLUCENT;

        mParams.packageName = "android";
        mParams.type = WindowManager.LayoutParams.TYPE_DISPLAY_OVERLAY;
        mParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        mParams.gravity = Gravity.TOP | Gravity.LEFT;

        mPressedParams.copyFrom(mParams);
        mPressedParams.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;

        mParams.setTitle("Fingerprint on display");
        mPressedParams.setTitle("Fingerprint on display.touched");

        if (!mShouldEnableDimlayer) {
            mParams.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            mParams.dimAmount = 0.0f;
        }

        mPressedView = new ImageView(context)  {
            @Override
            protected void onDraw(Canvas canvas) {
                if (mIsCircleShowing) {
                    setImageResource(PRESSED_COLOR[mPressedColor]);
                }
                super.onDraw(canvas);
            }
        };

        mWindowManager.addView(this, mParams);

        updatePosition();
        hide();

        mLockPatternUtils = new LockPatternUtils(mContext);

        mUpdateMonitor = Dependency.get(KeyguardUpdateMonitor.class);
        mUpdateMonitor.registerCallback(mMonitorCallback);

        mDozeEnabledByDefault = mContext.getResources().getBoolean(
                    com.android.internal.R.bool.config_dozeDefaultEnabled);

        Dependency.get(TunerService.class).addTunable(this,
                FOD_GESTURE,
                DOZE_ENABLED,
                FOD_ANIM,
                FOD_RECOGNIZING_ANIMATION,
                FOD_COLOR);

        // Pocket
        mPocketManager = (PocketManager) context.getSystemService(Context.POCKET_SERVICE);
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        switch (key) {
            case FOD_GESTURE:
                mFodGestureEnable =
                        TunerService.parseIntegerSwitch(newValue, false);
                break;
            case DOZE_ENABLED:
                mDozeEnabled =
                        TunerService.parseIntegerSwitch(newValue, mDozeEnabledByDefault);
                break;
            case FOD_ANIM:
                mFodAnim =
                        TunerService.parseInteger(newValue, 0);
                if (mFODAnimation != null) {
                    mFODAnimation.update(mIsRecognizingAnimEnabled, mFodAnim);
                }
                break;
            case FOD_RECOGNIZING_ANIMATION:
                mIsRecognizingAnimEnabled =
                        TunerService.parseIntegerSwitch(newValue, false);
                if (mFODAnimation != null) {
                    mFODAnimation.update(mIsRecognizingAnimEnabled, mFodAnim);
                }
                break;
            case FOD_COLOR:
                mPressedColor =
                        TunerService.parseInteger(newValue, mDefaultPressedColor);
                break;
            default:
                break;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!mIsCircleShowing) {
            canvas.drawCircle(mSize / 2, mSize / 2, mSize / 2.0f, mPaintFingerprintBackground);
        }
        super.onDraw(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getAxisValue(MotionEvent.AXIS_X);
        float y = event.getAxisValue(MotionEvent.AXIS_Y);

        boolean newIsInside = (x > 0 && x < mSize) && (y > 0 && y < mSize);
        mTouchedOutside = false;

        if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
            mTouchedOutside = true;
            return true;
        }

        if (event.getAction() == MotionEvent.ACTION_DOWN && newIsInside) {
            showCircle();
            return true;
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            hideCircle();
            return true;
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            return true;
        }

        return false;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
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
                    mFingerprintInscreenDaemonV1_1 =
                        vendor.lineage.biometrics.fingerprint.inscreen.V1_1.IFingerprintInscreen
                                .castFrom(mFingerprintInscreenDaemon);
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
        if (mTouchedOutside) return;
        if (mIsKeyguard && mIsDeviceInPocket) {
            return;
        }
        mIsCircleShowing = true;

        setKeepScreenOn(true);

        setDim(true);
        dispatchPress();

        setImageDrawable(null);
        updatePosition();
        invalidate();
        if (mFODAnimation != null && mIsRecognizingAnimEnabled) {
            mFODAnimation.showFODanimation();
        }
    }

    public void hideCircle() {
        mIsCircleShowing = false;

        invalidate();

        dispatchRelease();
        setDim(false);

        setKeepScreenOn(false);
        if (mFODAnimation != null && mIsRecognizingAnimEnabled) {
            mFODAnimation.hideFODanimation();
        }
    }

    public void show() {
        if (mUpdateMonitor.userNeedsStrongAuth()) {
            // Keyguard requires strong authentication (not biometrics)
            return;
        }

        if (!mUpdateMonitor.isScreenOn() && !mFodGestureEnable) {
            // Keyguard is shown just after screen turning off
            return;
        }

        if (mIsBouncer && !isPinOrPattern(mUpdateMonitor.getCurrentUser())) {
            // Ignore show calls when Keyguard password screen is being shown
            return;
        }

        if (mIsKeyguard && mUpdateMonitor.getUserCanSkipBouncer(mUpdateMonitor.getCurrentUser()) && !mFodGestureEnable) {
            // Ignore show calls if user can skip bouncer
            return;
        }

        if (mIsKeyguard && !mIsBiometricRunning && !mFodGestureEnable) {
            return;
        }

        if (mIsDreaming && !shouldShowOnDoze()) {
            setImageDrawable(null);
        } else {
            mFODIcon.show();
        }
        updatePosition();

        setVisibility(View.VISIBLE);
        dispatchShow();
    }

    public void hide() {
        mFODIcon.hide();
        setVisibility(View.GONE);
        hideCircle();
        dispatchHide();
    }

    private void updateAlpha() {
        setAlpha(mIsDreaming ? 0.5f : 1.0f);
    }

    private void updatePosition() {
        Display defaultDisplay = mWindowManager.getDefaultDisplay();

        Point size = new Point();
        defaultDisplay.getRealSize(size);

        int rotation = defaultDisplay.getRotation();
        int x, y;
        switch (rotation) {
            case Surface.ROTATION_0:
                x = mPositionX;
                y = mPositionY;
                break;
            case Surface.ROTATION_90:
                x = mPositionY;
                y = mPositionX;
                break;
            case Surface.ROTATION_180:
                x = mPositionX;
                y = size.y - mPositionY - mSize;
                break;
            case Surface.ROTATION_270:
                x = size.x - mPositionY - mSize - mNavigationBarSize;
                y = mPositionX;
                break;
            default:
                throw new IllegalArgumentException("Unknown rotation: " + rotation);
        }

        mPressedParams.x = mParams.x = x;
        mPressedParams.y = mParams.y = y;

        if (mIsDreaming && !mIsCircleShowing) {
            mParams.y += mDreamingOffsetY;
            if (mFODAnimation != null && mIsRecognizingAnimEnabled) {
                mFODAnimation.updateParams(mParams.y);
            }
        }

        mWindowManager.updateViewLayout(this, mParams);
        FODIconView fODIconView = this.mFODIcon;

        if (mPressedView.getParent() != null) {
            mWindowManager.updateViewLayout(mPressedView, mPressedParams);
        }

        WindowManager.LayoutParams layoutParams3 = this.mParams;
        fODIconView.updatePosition(layoutParams3.x, layoutParams3.y);
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
                mPressedParams.screenBrightness = 1.0f;
            }

            mPressedParams.dimAmount = dimAmount / 255.0f;
            if (mPressedView.getParent() == null) {
                mWindowManager.addView(mPressedView, mPressedParams);
            } else {
                mWindowManager.updateViewLayout(mPressedView, mPressedParams);
            }
        } else {
            if (mShouldBoostBrightness) {
                mPressedParams.screenBrightness = 0.0f;
            }
            mPressedParams.dimAmount = 0.0f;
            if (mPressedView.getParent() != null) {
                mWindowManager.removeView(mPressedView);
            }
        }
    }

    private boolean isPinOrPattern(int userId) {
        int passwordQuality = mLockPatternUtils.getActivePasswordQuality(userId);
        switch (passwordQuality) {
            // PIN
            case DevicePolicyManager.PASSWORD_QUALITY_NUMERIC:
            case DevicePolicyManager.PASSWORD_QUALITY_NUMERIC_COMPLEX:
            // Pattern
            case DevicePolicyManager.PASSWORD_QUALITY_SOMETHING:
                return true;
        }

        return false;
    }

    private boolean shouldShowOnDoze() {
        return Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.FOD_ON_DOZE, 1) == 1;
    }

    private class BurnInProtectionTask extends TimerTask {
        @Override
        public void run() {
            long now = System.currentTimeMillis() / 1000 / 60;

            // Let y to be not synchronized with x, so that we get maximum movement
            mDreamingOffsetY = (int) ((now + mDreamingMaxOffset / 3) % (mDreamingMaxOffset * 2));
            mDreamingOffsetY -= mDreamingMaxOffset;

            mHandler.post(() -> updatePosition());
        }
    };
}
