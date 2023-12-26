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

import com.android.systemui.R;
import java.util.List;

import com.android.systemui.Dependency;
import com.android.systemui.tuner.TunerService;

public class UsernameLayout extends LinearLayout implements TunerService.Tunable {
	
    private TextView mUserName;
	private String mUserText;
    private boolean mUserEnabled;
    
    private static final String CLOCK_STYLE_KEY = "clock_style";
    
    private static final String CLOCK_STYLE =
            "system:" + CLOCK_STYLE_KEY;

    public UsernameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        Dependency.get(TunerService.class).addTunable(this, CLOCK_STYLE);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
		mUserName = findViewById(R.id.username);
		mUserName.setSelected(true);
		updateProfileView();
	}

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateProfileView();
    }

	private void updateProfileView() {
	    if (mUserName != null) {
		    mUserName.setText(getUserName());
		}
	}

    private String getUserName(){
        final UserManager mUserManager = mContext.getSystemService(UserManager.class);
        String username = mUserManager.getUserName();
        return (username != null || !username.isEmpty()) ? mUserManager.getUserName() : mContext.getResources().getString(R.string.quick_settings_user_title);
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
