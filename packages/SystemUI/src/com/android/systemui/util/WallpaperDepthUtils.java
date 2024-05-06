package com.android.systemui.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.Rect;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.android.systemui.Dependency;
import com.android.systemui.qs.QSImpl;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.statusbar.phone.ScrimController;
import com.android.systemui.tuner.TunerService;

public class WallpaperDepthUtils {
    
    private static final String WALLPAPER_DEPTH_KEY = "system:depth_wallpaper_subject_image_uri";
    private static final String WALLPAPER_DEPTH_ENABLED_KEY = "system:depth_wallpaper_enabled";
    private static final String WALLPAPER_DEPTH_OPACITY_KEY = "system:depth_wallpaper_opacity";
    
    private static WallpaperDepthUtils instance;
    private FrameLayout mLockScreenSubject;
    private Drawable mDimmingOverlay;

    private final Context mContext;
    private final ScrimController mScrimController;
    private final StatusBarStateController mStatusBarStateController;
    private final QSImpl mQS;

    private boolean mDWallpaperEnabled;
    private int mDWallOpacity = 255;
    private String mWallpaperSubjectPath;
    private boolean mDozing;

    private WallpaperDepthUtils(Context context) {
        mContext = context;
        mQS = Dependency.get(QSImpl.class);
        mScrimController = Dependency.get(ScrimController.class);
        mStatusBarStateController = Dependency.get(StatusBarStateController.class);
        mStatusBarStateController.addCallback(mStatusBarStateListener);
        mStatusBarStateListener.onDozingChanged(mStatusBarStateController.isDozing());
        Dependency.get(TunerService.class).addTunable(mTunable, WALLPAPER_DEPTH_KEY, WALLPAPER_DEPTH_ENABLED_KEY, WALLPAPER_DEPTH_OPACITY_KEY);
		mLockScreenSubject = new FrameLayout(mContext);
		FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -1);
		mLockScreenSubject.setLayoutParams(lp);
    }

    public static WallpaperDepthUtils getInstance(Context context) {
        if (instance == null) {
            instance = new WallpaperDepthUtils(context);
        }
        return instance;
    }

    private final StatusBarStateController.StateListener mStatusBarStateListener =
            new StatusBarStateController.StateListener() {
        @Override
        public void onStateChanged(int newState) {}

        @Override
        public void onDozingChanged(boolean dozing) {
            if (mDozing == dozing) {
                return;
            }
            mDozing = dozing;
            updateDepthWallpaperVisibility();
        }
    };

    private final TunerService.Tunable mTunable = new TunerService.Tunable() {
        @Override
        public void onTuningChanged(String key, String newValue) {
            switch (key) {
                case WALLPAPER_DEPTH_ENABLED_KEY:
                    mDWallpaperEnabled = TunerService.parseIntegerSwitch(newValue, false);
                    updateDepthWallper();
                    break;
                case WALLPAPER_DEPTH_KEY:
                    mWallpaperSubjectPath = newValue;
                    updateDepthWallper();
                    break;
                case WALLPAPER_DEPTH_OPACITY_KEY:
                    int opacity = TunerService.parseInteger(newValue, 100);
                    mDWallOpacity = Math.round(opacity * 2.55f);
                    updateDepthWallper();
                    break;
                default:
                    break;
            }
        }
    };

    public FrameLayout getDepthWallpaperView() {
        return mLockScreenSubject;
    }

    private boolean isDWallpaperEnabled() {
        return mDWallpaperEnabled && mWallpaperSubjectPath != null 
                && !mWallpaperSubjectPath.isEmpty();
    }
    
    private boolean canShowDepthWallpaper() {
        return isDWallpaperEnabled()
                && mScrimController.getState().toString().equals("KEYGUARD")
                && mQS.isFullyCollapsed() && !mDozing;
    }

    public void updateDepthWallpaperVisibility() {
        if (mLockScreenSubject == null) return;
        mLockScreenSubject.post(() -> mLockScreenSubject.setVisibility(canShowDepthWallpaper() ? View.VISIBLE : View.GONE));
    }

    public Bitmap resizeAndCrop(Bitmap wallpaperBitmap) {
        Rect displayBounds = mContext.getSystemService(WindowManager.class)
                .getCurrentWindowMetrics()
                .getBounds();
        int desiredHeight = wallpaperBitmap.getHeight() > wallpaperBitmap.getWidth()
                ? displayBounds.height()
                : Math.round(wallpaperBitmap.getHeight() * (displayBounds.width() * 1f / wallpaperBitmap.getWidth()));
        int desiredWidth = wallpaperBitmap.getWidth() > wallpaperBitmap.getHeight()
                ? displayBounds.width()
                : Math.round(wallpaperBitmap.getWidth() * (displayBounds.height() * 1f / wallpaperBitmap.getHeight()));
        Bitmap scaledWallpaperBitmap = Bitmap.createScaledBitmap(wallpaperBitmap, desiredWidth, desiredHeight, true);
        int xPixelShift = (desiredWidth - displayBounds.width()) / 2;
        int yPixelShift = (desiredHeight - displayBounds.height()) / 2;
        scaledWallpaperBitmap = Bitmap.createBitmap(scaledWallpaperBitmap, xPixelShift, yPixelShift, displayBounds.width(), displayBounds.height());
        return scaledWallpaperBitmap;
    }
    
    public void updateDepthWallper() {
        if (mLockScreenSubject == null || !isDWallpaperEnabled()) return;
        new LoadWallpaperTask().execute();
        updateDepthWallpaperVisibility();
    }

    private class LoadWallpaperTask extends AsyncTask<Void, Void, Drawable> {
        @Override
        protected Drawable doInBackground(Void... voids) {
            try {
                Bitmap bitmap = BitmapFactory.decodeFile(mWallpaperSubjectPath);
                Bitmap resizedBitmap = resizeAndCrop(bitmap);
                Drawable bitmapDrawable = new BitmapDrawable(mContext.getResources(), resizedBitmap);
                bitmapDrawable.setAlpha(255);
                mDimmingOverlay = bitmapDrawable.getConstantState().newDrawable().mutate();
                mDimmingOverlay.setTint(Color.BLACK);
                return new LayerDrawable(new Drawable[]{bitmapDrawable, mDimmingOverlay});
            } catch (Exception ignored) {
                return null;
            }
        }

        @Override
        protected void onPostExecute(Drawable drawable) {
            if (drawable != null) {
                mLockScreenSubject.setBackground(drawable);
                mLockScreenSubject.getBackground().setAlpha(mDWallOpacity);
                mDimmingOverlay.setAlpha(Math.round(mScrimController.getScrimBehindAlpha() * 240));
            } else {
                updateDepthWallpaperVisibility();
            }
        }
    }
}
