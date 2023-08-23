/*
* Copyright (C) 2014 The Android Open Source Project
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 2 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*
*/
package com.android.systemui.custom;

import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.app.WallpaperInfo;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import androidx.palette.graphics.Palette;

import com.android.settingslib.Utils;
import com.android.systemui.R;

public class AmbientText extends FrameLayout {
   private static final boolean DEBUG = false;
   private static final String TAG = "AmbientText";
   private TextView mAmbientText;
   private ValueAnimator mTextAnimator;
   private ValueAnimator mTextEndAnimator;
   private boolean mEnable;
   private WallpaperManager mWallManager;

   public AmbientText(Context context) {
       this(context, null);
   }

   public AmbientText(Context context, AttributeSet attrs) {
       this(context, attrs, 0);
   }

   public AmbientText(Context context, AttributeSet attrs, int defStyleAttr) {
       this(context, attrs, defStyleAttr, 0);
   }

   public AmbientText(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
       super(context, attrs, defStyleAttr, defStyleRes);
       if (DEBUG) Log.d(TAG, "new");
   }

   private Runnable mTextUpdate = new Runnable() {
       @Override
       public void run() {
           if (DEBUG) Log.d(TAG, "run");
           animateText(mEnable);
       }
   };

   @Override
   public void draw(Canvas canvas) {
       super.draw(canvas);
       if (DEBUG) Log.d(TAG, "draw");
   }

   public void update() {

      ContentResolver resolver = getContext().getContentResolver();
      TextView textView = (TextView) findViewById(R.id.ambient_text);

      String text = Settings.System.getStringForUser(resolver ,
                      Settings.System.AMBIENT_TEXT_STRING,
                      UserHandle.USER_CURRENT);

      FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) textView.getLayoutParams();

      int align = Settings.System.getIntForUser(resolver,
              Settings.System.AMBIENT_TEXT_ALIGNMENT, 3, UserHandle.USER_CURRENT);

      switch (align) {
          case 0:
            textView.setGravity(Gravity.START|Gravity.TOP);
            lp.gravity = Gravity.START | Gravity.TOP;
            break;
          case 1:
            textView.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
            lp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
            break;
          case 2:
            textView.setGravity(Gravity.START|Gravity.BOTTOM);
            lp.gravity = Gravity.START | Gravity.BOTTOM;
            break;
          case 3:
          default:
            textView.setGravity(Gravity.CENTER);
            lp.gravity = Gravity.CENTER;
            break;
          case 4:
            textView.setGravity(Gravity.END|Gravity.TOP);
            lp.gravity = Gravity.END | Gravity.TOP;
            break;
          case 5:
            textView.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
            lp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
            break;
          case 6:
            textView.setGravity(Gravity.END|Gravity.BOTTOM);
            lp.gravity = Gravity.END | Gravity.BOTTOM;
            break;
      }
      textView.setLayoutParams(lp);
      textView.setText(text);
      textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, updateTextSize());

   }

   private int updateTextSize() {
        final ContentResolver resolver = mContext.getContentResolver();
        int mAmbientTextSize = Settings.System.getIntForUser(resolver,
                Settings.System.AMBIENT_TEXT_SIZE, 30, UserHandle.USER_CURRENT);

        return (int) mContext.getResources().getDimension(R.dimen.amb_font_base_multiplier_dp) * mAmbientTextSize;
    }

   public void animateText(boolean mEnable) {
       TextView text = (TextView) findViewById(R.id.ambient_text);
       mTextAnimator = ValueAnimator.ofFloat(new float[]{0.0f, 2.0f});
       mTextAnimator.setDuration(5000);
       int textColorType = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.AMBIENT_TEXT_TYPE_COLOR, 0, UserHandle.USER_CURRENT);
       int color = Utils.getColorAccentDefaultColor(getContext());
       switch (textColorType) {
           case 1:
               try {
                   WallpaperManager wallpaperManager = WallpaperManager.getInstance(mContext);
                   WallpaperInfo wallpaperInfo = wallpaperManager.getWallpaperInfo();
                   if (wallpaperInfo == null) { // if not a live wallpaper
                       Drawable wallpaperDrawable = wallpaperManager.getDrawable();
                       Bitmap bitmap = ((BitmapDrawable)wallpaperDrawable).getBitmap();
                       if (bitmap != null) { // if wallpaper is not blank
                           Palette p = Palette.from(bitmap).generate();
                           int wallColor = p.getDominantColor(color);
                           if (color != wallColor)
                               color = wallColor;
                       }
                   }
               } catch (Exception e) {
                   // Nothing to do
               }
               break;
           case 2:
               color = Settings.System.getIntForUser(mContext.getContentResolver(),
                       Settings.System.AMBIENT_TEXT_COLOR, 0xFF3980FF,
                       UserHandle.USER_CURRENT);
               break;
       }

       if (mEnable) {
           mTextAnimator.setRepeatCount(ValueAnimator.INFINITE);
       }
       mTextAnimator.setRepeatMode(ValueAnimator.REVERSE);
       text.setTextColor(color);
       mTextAnimator.addUpdateListener(new AnimatorUpdateListener() {
           public void onAnimationUpdate(ValueAnimator animation) {
               if (DEBUG) Log.d(TAG, "onAnimationUpdate");
               float progress = ((Float) animation.getAnimatedValue()).floatValue();
               float alpha = 1.0f;
               if (mEnable) {
                 if (progress <= 0.3f) {
                     alpha = progress / 0.3f;
                 } else if (progress >= 1.0f) {
                     alpha = 2.0f - progress;
                 }
               }
               text.setAlpha(alpha);
           }
       });
       if (DEBUG) Log.d(TAG, "start");
       mTextAnimator.start();
   }

   public void animateEndText() {
       TextView text = (TextView) findViewById(R.id.ambient_text);
       mTextAnimator = ValueAnimator.ofFloat(new float[]{0.0f, 1.0f});
       mTextAnimator.setDuration(5000);

       mTextAnimator.addUpdateListener(new AnimatorUpdateListener() {
           public void onAnimationUpdate(ValueAnimator animation) {
               if (DEBUG) Log.d(TAG, "onAnimationUpdate");
               float progress = ((Float) animation.getAnimatedValue()).floatValue();
               text.setAlpha(progress);
           }
       });
       if (DEBUG) Log.d(TAG, "start");
       mTextAnimator.reverse();
   }

}
