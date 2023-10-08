/*
 * Copyright (C) 2023 The RisingOS Android Project
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

package org.rising.server;

import static android.os.Process.THREAD_PRIORITY_DEFAULT;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ParceledListSlice;
import android.content.pm.UserInfo;
import android.os.Handler;
import android.os.IUserManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;

import com.android.server.ServiceThread;
import com.android.server.SystemService;

import java.io.IOException;
import java.lang.Boolean;
import java.lang.IllegalArgumentException;
import java.lang.Integer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public final class QuickSwitchService extends SystemService {

    private static final List<String> LAUNCHER_PACKAGES = List.of(
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher"
    );

    private static final String TAG = "QuickSwitchService";
    private static final int THREAD_PRIORITY_DEFAULT = android.os.Process.THREAD_PRIORITY_DEFAULT;

    private final Context mContext;
    private final IPackageManager mPM;
    private final IUserManager mUM;
    private final ContentResolver mResolver;
    private final String mOpPackageName;

    private ServiceThread mWorker;
    private Handler mHandler;

    public static boolean shouldHide(int userId, String packageName) {
        return packageName != null && getDisabledDefaultLaunchers().contains(packageName);
    }

    public static ParceledListSlice<PackageInfo> recreatePackageList(
            int userId, ParceledListSlice<PackageInfo> list) {
        List<PackageInfo> appList = list.getList();
        List<String> disabledLaunchers = getDisabledDefaultLaunchers();
        appList.removeIf(info -> disabledLaunchers.contains(info.packageName));
        return new ParceledListSlice<>(appList);
    }

    public static List<ApplicationInfo> recreateApplicationList(
            int userId, List<ApplicationInfo> list) {
        List<ApplicationInfo> appList = new ArrayList<>(list);
        List<String> disabledLaunchers = getDisabledDefaultLaunchers();
        appList.removeIf(info -> disabledLaunchers.contains(info.packageName));
        return appList;
    }

    private void updateStateForUser(int userId) {
        int defaultLauncher = SystemProperties.getInt("persist.sys.default_launcher", 0);
        try {
            for (String packageName : LAUNCHER_PACKAGES) {
                try {
                    if (packageName.equals(LAUNCHER_PACKAGES.get(defaultLauncher))) {
                        mPM.setApplicationEnabledSetting(packageName,
                                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                                0, userId, mOpPackageName);
                    } else {
                        mPM.setApplicationEnabledSetting(packageName,
                                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                0, userId, mOpPackageName);
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (RemoteException e) {
            e.rethrowAsRuntimeException();
        }
    }

    public static List<String> getDisabledDefaultLaunchers() {
        int defaultLauncher = SystemProperties.getInt("persist.sys.default_launcher", 0);
        List<String> disabledDefaultLaunchers = new ArrayList<>();
        for (int i = 0; i < LAUNCHER_PACKAGES.size(); i++) {
            if (i != defaultLauncher) {
                disabledDefaultLaunchers.add(LAUNCHER_PACKAGES.get(i));
            }
        }
        return disabledDefaultLaunchers;
    }


    private void initForUser(int userId) {
        if (userId < 0)
            return;
        updateStateForUser(userId);
    }

    private void init() {
        try {
            for (UserInfo user : mUM.getUsers(false, false, false)) {
                initForUser(user.id);
            }
        } catch (RemoteException e) {
            e.rethrowAsRuntimeException();
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_ADDED);
        filter.addAction(Intent.ACTION_USER_REMOVED);
        mContext.registerReceiver(new UserReceiver(), filter,
                android.Manifest.permission.MANAGE_USERS, mHandler);
    }

    @Override
    public void onStart() {
        mWorker = new ServiceThread(TAG, THREAD_PRIORITY_DEFAULT, false);
        mWorker.start();
        mHandler = new Handler(mWorker.getLooper());
        init();
    }

    public QuickSwitchService(Context context) {
        super(context);
        mContext = context;
        mResolver = context.getContentResolver();
        mPM = IPackageManager.Stub.asInterface(ServiceManager.getService("package"));
        mUM = IUserManager.Stub.asInterface(ServiceManager.getService(Context.USER_SERVICE));
        mOpPackageName = context.getOpPackageName();
    }

    private final class UserReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int userId = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, -1);

            if (Intent.ACTION_USER_ADDED.equals(intent.getAction())) {
                initForUser(userId);
            }
        }
    }
}
