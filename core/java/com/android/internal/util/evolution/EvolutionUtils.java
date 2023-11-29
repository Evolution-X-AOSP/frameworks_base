/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.internal.util.evolution;

import static android.provider.Settings.Global.ZEN_MODE_OFF;
import static android.provider.Settings.Global.ZEN_MODE_IMPORTANT_INTERRUPTIONS;
import static android.view.DisplayCutout.BOUNDS_POSITION_LEFT;
import static android.view.DisplayCutout.BOUNDS_POSITION_RIGHT;

import android.Manifest;
import android.app.ActivityManager;
import android.app.ActivityThread;
import android.app.AlertDialog;
import android.app.IActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.input.InputManager;
import android.hardware.fingerprint.FingerprintManager;
import android.hardware.SensorPrivacyManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.DisplayInfo;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.Surface;
import android.widget.Toast;

import com.android.internal.notification.SystemNotificationChannels;
import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.util.ArrayUtils;
import com.android.internal.R;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EvolutionUtils {
    private static final String TAG = "EvolutionUtils";

    private static final boolean DEBUG = false;

    private static final int NO_CUTOUT = -1;

    private static OverlayManager mOverlayService;

    public static boolean isChineseLanguage() {
       return Resources.getSystem().getConfiguration().locale.getLanguage().startsWith(
               Locale.CHINESE.getLanguage());
    }

    public static boolean isAppInstalled(Context context, String appUri) {
        try {
            PackageManager pm = context.getPackageManager();
            pm.getPackageInfo(appUri, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isAvailableApp(String packageName, Context context) {
       Context mContext = context;
       final PackageManager pm = mContext.getPackageManager();
       try {
           pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
           int enabled = pm.getApplicationEnabledSetting(packageName);
           return enabled != PackageManager.COMPONENT_ENABLED_STATE_DISABLED &&
               enabled != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER;
       } catch (NameNotFoundException e) {
           return false;
       }
    }

    public static boolean isPackageInstalled(Context context, String pkg, boolean ignoreState) {
        if (pkg != null) {
            try {
                PackageInfo pi = context.getPackageManager().getPackageInfo(pkg, 0);
                if (!pi.applicationInfo.enabled && !ignoreState) {
                    return false;
                }
            } catch (NameNotFoundException e) {
                return false;
            }
        }

        return true;
    }

    public static boolean isPackageInstalled(Context context, String pkg) {
        return isPackageInstalled(context, pkg, true);
    }

    public static boolean deviceSupportsFlashLight(Context context) {
        CameraManager cameraManager = (CameraManager) context.getSystemService(
                Context.CAMERA_SERVICE);
        try {
            String[] ids = cameraManager.getCameraIdList();
            for (String id : ids) {
                CameraCharacteristics c = cameraManager.getCameraCharacteristics(id);
                Boolean flashAvailable = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer lensFacing = c.get(CameraCharacteristics.LENS_FACING);
                if (flashAvailable != null
                        && flashAvailable
                        && lensFacing != null
                        && lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
                    return true;
                }
            }
        } catch (CameraAccessException e) {
            // Ignore
        }
        return false;
    }

    public static boolean isWifiOnly(Context context) {
        return !context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
    }

    // Check to see if Wifi is connected
    public static boolean isWifiConnected(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = null;
        if (cm != null) {
            activeNetwork = cm.getActiveNetworkInfo();
        }
        NetworkInfo wifi = null;
        if (cm != null) {
            wifi = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        }
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting() && wifi.isConnected();
    }

    // Check to see if Mobile data is connected
    public static boolean isMobileConnected(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = null;
        if (cm != null) {
            activeNetwork = cm.getActiveNetworkInfo();
        }
        NetworkInfo mobile = null;
        if (cm != null) {
            mobile = cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        }
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting() && mobile.isConnected();
    }

    // Check to see if device supports the Fingerprint scanner
    public static boolean hasFingerprintSupport(Context context) {
        FingerprintManager fingerprintManager = (FingerprintManager) context.getSystemService(Context.FINGERPRINT_SERVICE);
        return context.getApplicationContext().checkSelfPermission(Manifest.permission.USE_FINGERPRINT) == PackageManager.PERMISSION_GRANTED &&
                (fingerprintManager != null && fingerprintManager.isHardwareDetected());
    }

    // Check to see if device not only supports the Fingerprint scanner but also if is enrolled
    public static boolean hasFingerprintEnrolled(Context context) {
        FingerprintManager fingerprintManager = (FingerprintManager) context.getSystemService(Context.FINGERPRINT_SERVICE);
        return context.getApplicationContext().checkSelfPermission(Manifest.permission.USE_FINGERPRINT) == PackageManager.PERMISSION_GRANTED &&
                (fingerprintManager != null && fingerprintManager.isHardwareDetected() && fingerprintManager.hasEnrolledFingerprints());
    }

    // Check to see if device has a camera
    public static boolean hasCamera(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
    }

    // Check to see if device supports NFC
    public static boolean hasNFC(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC);
    }

    // Check to see if device supports Wifi
    public static boolean hasWiFi(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI);
    }

    // Check to see if device supports Bluetooth
    public static boolean hasBluetooth(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH);
    }

    // Check to see if device supports an alterative ambient display package
    public static boolean hasAltAmbientDisplay(Context context) {
        return context.getResources().getBoolean(com.android.internal.R.bool.config_alt_ambient_display);
    }

    public static boolean deviceHasFlashlight(Context ctx) {
        return ctx.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
    }

    public static void toggleCameraFlash() {
        FireActions.toggleCameraFlash();
    }

    public static void sendKeycode(int keycode) {
        long when = SystemClock.uptimeMillis();
        final KeyEvent evDown = new KeyEvent(when, when, KeyEvent.ACTION_DOWN, keycode, 0,
                0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY,
                InputDevice.SOURCE_KEYBOARD);
        final KeyEvent evUp = KeyEvent.changeAction(evDown, KeyEvent.ACTION_UP);

        final Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                InputManager.getInstance().injectInputEvent(evDown,
                        InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
            }
        });
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                InputManager.getInstance().injectInputEvent(evUp,
                        InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
            }
        }, 20);
    }

    /**
     * Keep FireAction methods below this point.
     * Place calls to methods above this point.
     */
    private static final class FireActions {
        private static IStatusBarService mStatusBarService = null;
        private static IStatusBarService getStatusBarService() {
            synchronized (FireActions.class) {
                if (mStatusBarService == null) {
                    mStatusBarService = IStatusBarService.Stub.asInterface(
                            ServiceManager.getService("statusbar"));
                }
                return mStatusBarService;
            }
        }

        public static void toggleCameraFlash() {
            IStatusBarService service = getStatusBarService();
            if (service != null) {
                try {
                    service.toggleCameraFlash();
                } catch (RemoteException e) {
                    // do nothing.
                }
            }
        }
    }

    /* returns whether the device has a centered display cutout or not. */
    public static boolean hasCenteredCutout(Context context) {
        Display display = context.getDisplay();
        DisplayCutout cutout = display.getCutout();
        if (cutout != null) {
            Point realSize = new Point();
            display.getRealSize(realSize);

            switch (display.getRotation()) {
                case Surface.ROTATION_0: {
                    Rect rect = cutout.getBoundingRectTop();
                    return !(rect.left <= 0 || rect.right >= realSize.x);
                }
                case Surface.ROTATION_90: {
                    Rect rect = cutout.getBoundingRectLeft();
                    return !(rect.top <= 0 || rect.bottom >= realSize.y);
                }
                case Surface.ROTATION_180: {
                    Rect rect = cutout.getBoundingRectBottom();
                    return !(rect.left <= 0 || rect.right >= realSize.x);
                }
                case Surface.ROTATION_270: {
                    Rect rect = cutout.getBoundingRectRight();
                    return !(rect.top <= 0 || rect.bottom >= realSize.y);
                }
            }
        }
        return false;
    }

    /**
     * Returns whether the device is voice-capable (meaning, it is also a phone).
     */
    public static boolean isVoiceCapable(Context context) {
        TelephonyManager telephony =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        return telephony != null && telephony.isVoiceCapable();
    }

    public static int getCutoutType(Context context) {
        final DisplayInfo info = new DisplayInfo();
        context.getDisplay().getDisplayInfo(info);
        final DisplayCutout cutout = info.displayCutout;
        if (cutout == null) {
            if (DEBUG) Log.v(TAG, "noCutout");
            return NO_CUTOUT;
        }
        final Point displaySize = new Point();
        context.getDisplay().getRealSize(displaySize);
        List<Rect> cutOutBounds = cutout.getBoundingRects();
        if (cutOutBounds != null) {
            for (Rect cutOutRect : cutOutBounds) {
                if (DEBUG) Log.v(TAG, "cutout left= " + cutOutRect.left);
                if (DEBUG) Log.v(TAG, "cutout right= " + cutOutRect.right);
                if (cutOutRect.left == 0 && cutOutRect.right > 0) {  //cutout is located on top left
                    if (DEBUG) Log.v(TAG, "cutout position= " + BOUNDS_POSITION_LEFT);
                    return BOUNDS_POSITION_LEFT;
                } else if (cutOutRect.right == displaySize.x && (displaySize.x - cutOutRect.left) > 0) {  //cutout is located on top right
                    if (DEBUG) Log.v(TAG, "cutout position= " + BOUNDS_POSITION_RIGHT);
                    return BOUNDS_POSITION_RIGHT;
                }
            }
        }
        return NO_CUTOUT;
    }

    // Method to detect whether an overlay is enabled or not
    public static boolean isThemeEnabled(String packageName) {
        mOverlayService = new OverlayManager();
        try {
            List<OverlayInfo> infos = mOverlayService.getOverlayInfosForTarget("android",
                    UserHandle.myUserId());
            for (int i = 0, size = infos.size(); i < size; i++) {
                if (infos.get(i).packageName.equals(packageName)) {
                    return infos.get(i).isEnabled();
                }
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static class OverlayManager {
        private final IOverlayManager mService;

        public OverlayManager() {
            mService = IOverlayManager.Stub.asInterface(
                    ServiceManager.getService(Context.OVERLAY_SERVICE));
        }

        public void setEnabled(String pkg, boolean enabled, int userId)
                throws RemoteException {
            mService.setEnabled(pkg, enabled, userId);
        }

        public List<OverlayInfo> getOverlayInfosForTarget(String target, int userId)
                throws RemoteException {
            return mService.getOverlayInfosForTarget(target, userId);
        }
    }

    public static void restartSystemUi(Context context) {
        new RestartSystemUiTask(context).execute();
    }

    public static void showSystemUiRestartDialog(Context context) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.systemui_restart_title)
                .setMessage(R.string.systemui_restart_message)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        restartSystemUi(context);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static class RestartSystemUiTask extends AsyncTask<Void, Void, Void> {
        private Context mContext;

        public RestartSystemUiTask(Context context) {
            super();
            mContext = context;
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                ActivityManager am =
                        (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
                IActivityManager ams = ActivityManager.getService();
                for (ActivityManager.RunningAppProcessInfo app: am.getRunningAppProcesses()) {
                    if ("com.android.systemui".equals(app.processName)) {
                        ams.killApplicationProcess(app.processName, app.uid);
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    public static void showSystemRestartDialog(Context context) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.system_restart_title)
                .setMessage(R.string.system_restart_message)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        Handler handler = new Handler();
                        handler.postDelayed(() -> restartSystem(context), 2000);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    public static void restartSystem(Context context) {
        new RestartSystemTask(context).execute();
    }

    private static class RestartSystemTask extends AsyncTask<Void, Void, Void> {
        private final WeakReference<Context> mContext;

        public RestartSystemTask(Context context) {
            super();
            mContext = new WeakReference<>(context);
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                IStatusBarService mBarService = IStatusBarService.Stub.asInterface(
                        ServiceManager.getService(Context.STATUS_BAR_SERVICE));
                if (mBarService != null) {
                    try {
                        Thread.sleep(1250);
                        mBarService.reboot(false);
                    } catch (RemoteException | InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    public static void showRestartDialog(Context context, int title, int message, Runnable action) {
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.ok, (dialog, id) -> {
                    Handler handler = new Handler();
                    handler.postDelayed(action, 1250);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    public static void restartProcess(Context context, String processName) {
        new RestartTask(context, processName).execute();
    }

    private static class RestartTask extends AsyncTask<Void, Void, Void> {
        private final WeakReference<Context> mContext;
        private final String mProcessName;

        public RestartTask(Context context, String processName) {
            mContext = new WeakReference<>(context);
            mProcessName = processName;
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                ActivityManager am = (ActivityManager) mContext.get().getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    IActivityManager ams = ActivityManager.getService();
                    for (ActivityManager.RunningAppProcessInfo app : am.getRunningAppProcesses()) {
                        if (app.processName.contains(mProcessName)) {
                            ams.killApplicationProcess(app.processName, app.uid);
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    public static void showSettingsRestartDialog(Context context) {
        showRestartDialog(context, R.string.settings_restart_title, R.string.settings_restart_message, () -> restartProcess(context, "com.android.settings"));
    }

    public static void showSystemUIRestartDialog(Context context) {
        showRestartDialog(context, R.string.systemui_restart_title, R.string.systemui_restart_message, () -> restartProcess(context, "com.android.systemui"));
    }

    public static void showLauncherRestartDialog(Context context) {
        showRestartDialog(context, R.string.launcher_restart_title, R.string.launcher_restart_message, () -> restartProcess(context, "com.android.launcher3"));
    }

    public static class SleepModeController {
        private final Resources mResources;
        private final Context mUiContext;

        private Context mContext;
        private AudioManager mAudioManager;
        private NotificationManager mNotificationManager;
        private WifiManager mWifiManager;
        private SensorPrivacyManager mSensorPrivacyManager;
        private BluetoothAdapter mBluetoothAdapter;
        private int mSubscriptionId;
        private Toast mToast;

        private boolean mSleepModeEnabled;

        private static boolean mWifiState;
        private static boolean mCellularState;
        private static boolean mBluetoothState;
        private static boolean mSensorState;
        private static int mRingerState;
        private static int mZenState;

        private static final String TAG = "SleepModeController";
        private static final int SLEEP_NOTIFICATION_ID = 727;
        public static final String SLEEP_MODE_TURN_OFF = "android.intent.action.SLEEP_MODE_TURN_OFF";

        public SleepModeController(Context context) {
            mContext = context;
            mUiContext = ActivityThread.currentActivityThread().getSystemUiContext();
            mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            mNotificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
            mSensorPrivacyManager = (SensorPrivacyManager) mContext.getSystemService(Context.SENSOR_PRIVACY_SERVICE);
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            mSubscriptionId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
            mResources = mContext.getResources();

            mSleepModeEnabled = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SLEEP_MODE_ENABLED, 0, UserHandle.USER_CURRENT) == 1;

            SettingsObserver observer = new SettingsObserver(new Handler(Looper.getMainLooper()));
            observer.observe();
            observer.update();
        }

        private TelephonyManager getTelephonyManager() {
            int subscriptionId = mSubscriptionId;

            // If mSubscriptionId is invalid, get default data sub.
            if (!SubscriptionManager.isValidSubscriptionId(subscriptionId)) {
                subscriptionId = SubscriptionManager.getDefaultDataSubscriptionId();
            }

            // If data sub is also invalid, get any active sub.
            if (!SubscriptionManager.isValidSubscriptionId(subscriptionId)) {
                int[] activeSubIds = SubscriptionManager.from(mContext).getActiveSubscriptionIdList();
                if (!ArrayUtils.isEmpty(activeSubIds)) {
                    subscriptionId = activeSubIds[0];
                }
            }

            return mContext.getSystemService(
                    TelephonyManager.class).createForSubscriptionId(subscriptionId);
        }

        private boolean isWifiEnabled() {
            if (mWifiManager == null) {
                mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
            }
            try {
                return mWifiManager.isWifiEnabled();
            } catch (Exception e) {
                return false;
            }
        }

        private void setWifiEnabled(boolean enable) {
            if (mWifiManager == null) {
                mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
            }
            try {
                mWifiManager.setWifiEnabled(enable);
            } catch (Exception e) {
            }
        }

        private boolean isBluetoothEnabled() {
            if (mBluetoothAdapter == null) {
                mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            }
            try {
                return mBluetoothAdapter.isEnabled();
            } catch (Exception e) {
                return false;
            }
        }

        private void setBluetoothEnabled(boolean enable) {
            if (mBluetoothAdapter == null) {
                mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            }
            try {
                if (enable) mBluetoothAdapter.enable();
                else mBluetoothAdapter.disable();
            } catch (Exception e) {
            }
        }

        private boolean isSensorEnabled() {
            if (mSensorPrivacyManager == null) {
                mSensorPrivacyManager = (SensorPrivacyManager) mContext.getSystemService(Context.SENSOR_PRIVACY_SERVICE);
            }
            try {
                return !mSensorPrivacyManager.isAllSensorPrivacyEnabled();
            } catch (Exception e) {
                return false;
            }
        }

        private void setSensorEnabled(boolean enable) {
            if (mSensorPrivacyManager == null) {
                mSensorPrivacyManager = (SensorPrivacyManager) mContext.getSystemService(Context.SENSOR_PRIVACY_SERVICE);
            }
            try {
                mSensorPrivacyManager.setAllSensorPrivacy(!enable);
            } catch (Exception e) {
            }
        }

        private int getZenMode() {
            if (mNotificationManager == null) {
                mNotificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            }
            try {
                return mNotificationManager.getZenMode();
            } catch (Exception e) {
                return -1;
            }
        }

        private void setZenMode(int mode) {
            if (mNotificationManager == null) {
                mNotificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            }
            try {
                mNotificationManager.setZenMode(mode, null, TAG);
            } catch (Exception e) {
            }
        }

        private int getRingerModeInternal() {
            if (mAudioManager == null) {
                mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            }
            try {
                return mAudioManager.getRingerModeInternal();
            } catch (Exception e) {
                return -1;
            }
        }

        private void setRingerModeInternal(int mode) {
            if (mAudioManager == null) {
                mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            }
            try {
                mAudioManager.setRingerModeInternal(mode);
            } catch (Exception e) {
            }
        }

        private void enable() {
            if (!ActivityManager.isSystemReady()) return;

            // Disable Wi-Fi
            final boolean disableWifi = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SLEEP_MODE_WIFI_TOGGLE, 1, UserHandle.USER_CURRENT) == 1;
            if (disableWifi) {
                mWifiState = isWifiEnabled();
                setWifiEnabled(false);
            }

            // Disable Bluetooth
            final boolean disableBluetooth = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SLEEP_MODE_BLUETOOTH_TOGGLE, 1, UserHandle.USER_CURRENT) == 1;
            if (disableBluetooth) {
                mBluetoothState = isBluetoothEnabled();
                setBluetoothEnabled(false);
            }

            // Disable Mobile Data
            final boolean disableData = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SLEEP_MODE_CELLULAR_TOGGLE, 1, UserHandle.USER_CURRENT) == 1;
            if (disableData) {
                mCellularState = getTelephonyManager().isDataEnabled();
                getTelephonyManager().setDataEnabled(false);
            }

            // Disable Sensors
            final boolean disableSensors = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SLEEP_MODE_SENSORS_TOGGLE, 1, UserHandle.USER_CURRENT) == 1;
            if (disableSensors) {
                mSensorState = isSensorEnabled();
                setSensorEnabled(false);
            }

            // Set Ringer mode (0: Off, 1: Vibrate, 2:DND: 3:Silent)
            final int ringerMode = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SLEEP_MODE_RINGER_MODE, 0, UserHandle.USER_CURRENT);
            if (ringerMode != 0) {
                mRingerState = getRingerModeInternal();
                mZenState = getZenMode();
                if (ringerMode == 1) {
                    setRingerModeInternal(AudioManager.RINGER_MODE_VIBRATE);
                    setZenMode(ZEN_MODE_OFF);
                } else if (ringerMode == 2) {
                    setRingerModeInternal(AudioManager.RINGER_MODE_NORMAL);
                    setZenMode(ZEN_MODE_IMPORTANT_INTERRUPTIONS);
                } else if (ringerMode == 3) {
                    setRingerModeInternal(AudioManager.RINGER_MODE_SILENT);
                    setZenMode(ZEN_MODE_OFF);
                }
            }

            showToast(mResources.getString(R.string.sleep_mode_enabled_toast), Toast.LENGTH_LONG);
            addNotification();
        }

        private void disable() {
            if (!ActivityManager.isSystemReady()) return;

            // Enable Wi-Fi
            final boolean disableWifi = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SLEEP_MODE_WIFI_TOGGLE, 1, UserHandle.USER_CURRENT) == 1;
            if (disableWifi && mWifiState != isWifiEnabled()) {
                setWifiEnabled(mWifiState);
            }

            // Enable Bluetooth
            final boolean disableBluetooth = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SLEEP_MODE_BLUETOOTH_TOGGLE, 1, UserHandle.USER_CURRENT) == 1;
            if (disableBluetooth && mBluetoothState != isBluetoothEnabled()) {
                setBluetoothEnabled(mBluetoothState);
            }

            // Enable Mobile Data
            final boolean disableData = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SLEEP_MODE_CELLULAR_TOGGLE, 1, UserHandle.USER_CURRENT) == 1;
            if (disableData && mCellularState != getTelephonyManager().isDataEnabled()) {
                getTelephonyManager().setDataEnabled(mCellularState);
            }

            // Enable Sensors
            final boolean disableSensors = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SLEEP_MODE_SENSORS_TOGGLE, 1, UserHandle.USER_CURRENT) == 1;
            if (disableSensors && mSensorState != isSensorEnabled()) {
                setSensorEnabled(mSensorState);
            }

            // Set Ringer mode (0: Off, 1: Vibrate, 2:DND: 3:Silent)
            final int ringerMode = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                    Settings.Secure.SLEEP_MODE_RINGER_MODE, 0, UserHandle.USER_CURRENT);
            if (ringerMode != 0 && (mRingerState != getRingerModeInternal() ||
                    mZenState != getZenMode())) {
                setRingerModeInternal(mRingerState);
                setZenMode(mZenState);
            }

            showToast(mResources.getString(R.string.sleep_mode_disabled_toast), Toast.LENGTH_LONG);
            mNotificationManager.cancel(SLEEP_NOTIFICATION_ID);
        }

        private void addNotification() {
            Intent intent = new Intent(SLEEP_MODE_TURN_OFF);
            intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND | Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            // Display a notification
            Notification.Builder builder = new Notification.Builder(mContext, SystemNotificationChannels.SLEEP)
                .setTicker(mResources.getString(R.string.sleep_mode_notification_title))
                .setContentTitle(mResources.getString(R.string.sleep_mode_notification_title))
                .setContentText(mResources.getString(R.string.sleep_mode_notification_content))
                .setSmallIcon(R.drawable.ic_sleep)
                .setWhen(java.lang.System.currentTimeMillis())
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .setAutoCancel(false);

            Notification notification = builder.build();
            mNotificationManager.notify(SLEEP_NOTIFICATION_ID, notification);
        }

        private void showToast(String msg, int duration) {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (mToast != null) mToast.cancel();
                        mToast = Toast.makeText(mUiContext, msg, duration);
                        mToast.show();
                    } catch (Exception e) {
                    }
                }
            });
        }

        private void setSleepMode(boolean enabled) {
            if (mSleepModeEnabled == enabled) {
                return;
            }

            mSleepModeEnabled = enabled;

            if (mSleepModeEnabled) {
                enable();
            } else {
                disable();
            }
        }

        class SettingsObserver extends ContentObserver {
            SettingsObserver(Handler handler) {
                super(handler);
            }

            void observe() {
                ContentResolver resolver = mContext.getContentResolver();
                resolver.registerContentObserver(Settings.Secure.getUriFor(
                        Settings.Secure.SLEEP_MODE_ENABLED), false, this,
                        UserHandle.USER_ALL);
            }

            @Override
            public void onChange(boolean selfChange, Uri uri) {
                update();
            }

            void update() {
                final boolean enabled = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                        Settings.Secure.SLEEP_MODE_ENABLED, 0, UserHandle.USER_CURRENT) == 1;
                setSleepMode(enabled);
            }
        }
    }
}
