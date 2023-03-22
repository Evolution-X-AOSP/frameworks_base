/*
 * Copyright (C) 2023 Microsoft Corporation
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

package android.app;

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.annotation.SystemService;
import android.content.Context;
import android.os.Bundle;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.util.Log;
import android.util.Singleton;
import android.view.InputEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

/**
 * This class given the information about, and interactive with Cross Device functions.
 * Those clients that have been authorized can communicate with {@link com.android.server.wm.CrossDeviceService}
 * through this class:
 * <p>
 * CrossDeviceManager manager = context.getSystemService("cross_device_service");
 */
@SystemService(Context.CROSS_DEVICE_SERVICE)
public class CrossDeviceManager {
    private static final String TAG = CrossDeviceManager.class.getSimpleName();

    public static final String FEATURE_BASIC = "feature_basic";
    public static final String FEATURE_REMOTE_APPS = "feature_remote_apps";

    public static final int APP_FEATURE_TYPE_UNKNOWN = 0;
    public static final int APP_FEATURE_TYPE_PHONE_SCREEN_ONLY = 1;
    public static final int APP_FEATURE_TYPE_MULTIPLE_APPS = 2;
    private static final int CROSS_DEVICE_VERSION = 1;

    @NonNull
    private static final List<String> CROSS_DEVICE_ALLOW_LIST = new ArrayList<>(Collections.singletonList("com.microsoft.deviceintegrationservice"));

    /**
     * {@hide}
     */
    public CrossDeviceManager() { }

    /** @hide */
    public static ICrossDeviceService getService() {
        return SINGLETON.get();
    }

    private static final Singleton<ICrossDeviceService> SINGLETON = new Singleton<ICrossDeviceService>() {
        @Override
        protected ICrossDeviceService create() {
            final IBinder binder = ServiceManager.getService(Context.CROSS_DEVICE_SERVICE);
            return ICrossDeviceService.Stub.asInterface(binder);
        }
    };

    /**
     * <p>
     * Check if the feature is supported.
     */
    public static boolean isFeatureSupported(@NonNull String featureName) {
        switch (featureName) {
            case FEATURE_BASIC:
            case FEATURE_REMOTE_APPS:
                // Here can be optimized to change the return value;
                return true;
            default:
                return false;
        }
    }

    @RequiresPermission(Manifest.permission.MANAGE_CROSS_DEVICE)
    public static void notifyConnectionStateChange(boolean isConnected) {
        Log.d(TAG, "Connection State Changed: " + (isConnected ? "Connected" : "Disconnected"));
    }

    /**
     * <p>
     * Get Max Fps with appsFeatureType
     */
    public static int getMaxFps(int appsFeatureType) {
        switch (appsFeatureType) {
            case APP_FEATURE_TYPE_PHONE_SCREEN_ONLY:
                // This is for devices on which isFeatureSupported(FEATURE_REMOTE_APPS) returns false
                return 30;
            case APP_FEATURE_TYPE_MULTIPLE_APPS:
                // This is for devices on which isFeatureSupported(FEATURE_REMOTE_APPS) returns true
                return 30;
            default:
                return 15;
        }
    }

    /**
     * <p>
     * Check if the caller is supported.
     */
    public static boolean isCallerAllowed(@NonNull Context context) {
        final Context appContext = context.getApplicationContext();
        final int callingUid = Binder.getCallingUid();
        final String callingPackage  = appContext.getPackageManager().getNameForUid(callingUid);

        int result = appContext.checkCallingOrSelfPermission(Manifest.permission.MANAGE_CROSS_DEVICE);
        if (result != PERMISSION_GRANTED) {
            Log.e(TAG, "permission.MANAGE_CROSS_DEVICE is not granted: " + callingPackage);
            return false;
        }

        if (!CROSS_DEVICE_ALLOW_LIST.contains(callingPackage)) {
            Log.e(TAG, "CROSS_DEVICE_ALLOW_LIST does not contain: " + callingPackage);
            return false;
        }

        return true;
    }

    /**
     * <p>
     * get cross device version
     */
    public static int getCrossDeviceVersion() {
        return CROSS_DEVICE_VERSION;
    }

    /**
     * {@hide}
     * <p>
     * Inject input event
     */
    @RequiresPermission(Manifest.permission.MANAGE_CROSS_DEVICE)
    public void injectInputEvent(InputEvent event, int injectInputEventModeAsync) {
        try {
            getService().injectInputEvent(event, injectInputEventModeAsync);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * {@hide}
     * <p>
     * Get top running package name which will be used by input injection
     */
    @RequiresPermission(Manifest.permission.MANAGE_CROSS_DEVICE)
    public String getTopRunningPackageName() {
        try {
            return getService().getTopRunningPackageName();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * {@hide}
     * <p>
     * Wake up phone when injecting input from main display
     */
    @RequiresPermission(Manifest.permission.MANAGE_CROSS_DEVICE)
    public void wakeUp(long time) {
        try {
            getService().wakeUp(time);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * {@hide}
     * <p>
     * Update white list.
     */
    @RequiresPermission(Manifest.permission.MANAGE_CROSS_DEVICE)
    public void updateBackgroundActivityList(List<String> activityList) {
        try {
            getService().updateBackgroundActivityList(activityList);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * {@hide}
     * <p>
     * check white list.
     */
    @RequiresPermission(Manifest.permission.MANAGE_CROSS_DEVICE)
    public void isFromBackgroundWhiteList() {
        try {
            getService().isFromBackgroundWhiteList();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * {@hide}
     * <p>
     * check white list.
     */
    @RequiresPermission(Manifest.permission.MANAGE_CROSS_DEVICE)
    public void isFromBackgroundWhiteListByUid(int uid) {
        try {
            getService().isFromBackgroundWhiteListByUid(uid);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

     /**
     * {@hide}
     * <p>
     * get device temperature
     */
    @RequiresPermission(Manifest.permission.MANAGE_CROSS_DEVICE)
    public Bundle getDeviceTemperature() {
        try {
            return getService().getDeviceTemperature();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * {@hide}
     * <p>
     * get the threshold of overheat
     */
    @RequiresPermission(Manifest.permission.MANAGE_CROSS_DEVICE)
    public Bundle getOverheatThreshold() {
        try {
            return getService().getOverheatThreshold();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     *
     * {@hide}<p>
     * get the threshold of resume overheat
     */
    @RequiresPermission(Manifest.permission.MANAGE_CROSS_DEVICE)
    public Bundle getResumeOverheatThreshold() {
        try {
            return getService().getResumeOverheatThreshold();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * {@hide}
     * <p>
     * get the maximum app count of Suspend
     */
    @RequiresPermission(Manifest.permission.MANAGE_CROSS_DEVICE)
    public Bundle getMaximumAppCount() {
        try {
            return getService().getMaximumAppCount();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * {@hide}
     * <p>
     * Is app touch required
     */
    @RequiresPermission(Manifest.permission.MANAGE_CROSS_DEVICE)
    public boolean isTouchRequired(String packageName) {
        try {
            return getService().isTouchRequired(packageName);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * {@hide}
     * <p>
     * Get {@link #android.app.IRemoteTaskInstanceBroker}
     * @return IRemoteTaskInstanceBroker stub
     */
    @RequiresPermission(Manifest.permission.MANAGE_CROSS_DEVICE)
    public IRemoteTaskInstanceBroker getRemoteTaskInstanceBroker() {
        try {
            return getService().getRemoteTaskInstanceBroker();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * {@hide}
     * <p>
     * Client who want to handle remote task lifecycle event should register a
     * {@link IRemoteTaskHandler} into Service at the beginning.
     */
    @RequiresPermission(Manifest.permission.MANAGE_CROSS_DEVICE)
    public void registerRemoteTaskHandler(IRemoteTaskHandler handler) {
        try {
            getService().registerRemoteTaskHandler(handler);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * {@hide}
     * <p>
     * Unregister {@link IRemoteTaskHandler} from {@link com.android.server.wm.CrossDeviceService}
     */
    @RequiresPermission(Manifest.permission.MANAGE_CROSS_DEVICE)
    public void unRegisterRemoteTaskHandler(IRemoteTaskHandler handler) {
        try {
            getService().unRegisterRemoteTaskHandler(handler);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * {@hide}
     * <p>
     * Set media projection permission granted
     */
    @RequiresPermission(Manifest.permission.MANAGE_CROSS_DEVICE)
    public void setPermissionGranted(int uid) {
        try {
            getService().setPermissionGranted(uid);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * {@hide}
     * <p>
     * Get media projection permission granted status
     */
    @RequiresPermission(Manifest.permission.MANAGE_CROSS_DEVICE)
    public boolean getPermissionGranted(int uid) {
        try {
            return getService().getPermissionGranted(uid);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }
}
