/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.tuner;

import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import com.android.systemui.R;

public class StatusbarItems extends PreferenceFragment
        implements OnPreferenceChangeListener {

    public static final String TAG = "StatusbarItems";

    private static final String DATA_DISABLED_ICON = "data_disabled_icon";

    private SwitchPreference mDataDisabledIcon;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.statusbar_items);

        PreferenceScreen prefSet = getPreferenceScreen();
        final ContentResolver resolver = getActivity().getContentResolver();

        mDataDisabledIcon = (SwitchPreference) findPreference(DATA_DISABLED_ICON);
        mDataDisabledIcon.setChecked((Settings.System.getInt(resolver,
                Settings.System.DATA_DISABLED_ICON, 1) == 1));
        mDataDisabledIcon.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mDataDisabledIcon) {
            boolean value = (Boolean) newValue;
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.DATA_DISABLED_ICON, value ? 1 : 0);
            return true;
        }
        return false;
    }
}
