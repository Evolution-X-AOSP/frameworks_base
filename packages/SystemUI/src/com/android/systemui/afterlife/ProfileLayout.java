/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.afterlife;

import android.content.*;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.*;
import android.net.wifi.*;
import android.os.*;
import android.provider.Settings;
import android.telephony.*;
import android.text.*;
import android.text.format.Formatter;
import android.text.format.Formatter.BytesResult;
import android.util.*;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.systemui.res.R;
import java.util.List;

import com.android.internal.util.UserIcons;
import com.android.settingslib.drawable.CircleFramedDrawable;
import com.android.systemui.Dependency;
import com.android.systemui.tuner.TunerService;

public class ProfileLayout extends LinearLayout implements TunerService.Tunable {

	private ImageView mUserAvatar;

    private static final String CLOCK_STYLE_KEY = "clock_style";
    private static final String CLOCK_STYLE =
            "system:" + CLOCK_STYLE_KEY;

    public ProfileLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        Dependency.get(TunerService.class).addTunable(this, CLOCK_STYLE);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
		mUserAvatar = findViewById(R.id.user_picture);
		mUserAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
		updateProfileView();
	}

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateProfileView();
    }

	private void updateProfileView() {
	    if (mUserAvatar != null) {
	        final Drawable avatarDrawable = getCircularUserIcon(mContext);
		    mUserAvatar.setImageDrawable(avatarDrawable);
		}
	}

    private Drawable getCircularUserIcon(Context context) {
        final UserManager mUserManager = mContext.getSystemService(UserManager.class);
        Bitmap bitmapUserIcon = mUserManager.getUserIcon(UserHandle.myUserId());
        if (bitmapUserIcon == null) {
            final Drawable defaultUserIcon = UserIcons.getDefaultUserIcon(
                    mContext.getResources(), UserHandle.myUserId(), false);
            bitmapUserIcon = UserIcons.convertToBitmap(defaultUserIcon);
        }
        Drawable drawableUserIcon = new CircleFramedDrawable(bitmapUserIcon,
                (int) mContext.getResources().getDimension(R.dimen.custom_clock_2_avatar_width));
        return drawableUserIcon;
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        switch (key) {
            case CLOCK_STYLE:
                updateProfileView();
                break;
            default:
                break;
        }
    }
}
