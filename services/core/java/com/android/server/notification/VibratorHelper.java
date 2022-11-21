/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.notification;

import static android.os.VibrationEffect.VibrationParameter.targetAmplitude;
import static android.os.VibrationEffect.VibrationParameter.targetFrequency;

import android.annotation.Nullable;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.media.AudioAttributes;
import android.os.Process;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Slog;

import com.android.internal.R;
import com.android.server.pm.PackageManagerService;

import java.time.Duration;
import java.util.Arrays;

/**
 * NotificationManagerService helper for functionality related to the vibrator.
 */
public final class VibratorHelper {
    private static final String TAG = "NotificationVibratorHelper";

    private static final long[] DEFAULT_VIBRATE_PATTERN = {0, 250, 250, 250};
    private static final int VIBRATE_PATTERN_MAXLEN = 8 * 2 + 1; // up to eight bumps

    private static final long[] DZZZ_DA_VIBRATION_PATTERN = {0, 500, 200, 70, 720};
    private static final long[] MM_MM_MM_VIBRATION_PATTERN = {0, 300, 400, 300, 400, 300, 1400};
    private static final long[] DA_DA_DZZZ_VIBRATION_PATTERN = {0, 70, 80, 70, 180, 600, 1050};
    private static final long[] DA_DZZZ_DA_VIBRATION_PATTERN = {0, 80, 200, 600, 150, 60, 1050};

    private final Vibrator mVibrator;
    private final long[] mDefaultPattern;
    private final long[] mFallbackPattern;
    @Nullable private final long[] mCustomPattern;
    @Nullable private final float[] mDefaultPwlePattern;
    @Nullable private final float[] mFallbackPwlePattern;

    public VibratorHelper(Context context) {
        mVibrator = context.getSystemService(Vibrator.class);
        mDefaultPattern = getLongArray(context.getResources(),
                com.android.internal.R.array.config_defaultNotificationVibePattern,
                VIBRATE_PATTERN_MAXLEN,
                DEFAULT_VIBRATE_PATTERN);
        mFallbackPattern = getLongArray(context.getResources(),
                R.array.config_notificationFallbackVibePattern,
                VIBRATE_PATTERN_MAXLEN,
                DEFAULT_VIBRATE_PATTERN);
        mDefaultPwlePattern = getFloatArray(context.getResources(),
                com.android.internal.R.array.config_defaultNotificationVibeWaveform);
        mFallbackPwlePattern = getFloatArray(context.getResources(),
                com.android.internal.R.array.config_notificationFallbackVibeWaveform);

        final int value = Settings.System.getInt(context.getContentResolver(),
                Settings.System.NOTIFICATION_VIBRATION_PATTERN, 0);
        switch (value) {
            default:
            case 0:
                mCustomPattern = null;
                break;
            case 1:
                mCustomPattern = DZZZ_DA_VIBRATION_PATTERN;
                break;
            case 2:
                mCustomPattern = MM_MM_MM_VIBRATION_PATTERN;
                break;
            case 3:
                mCustomPattern = DA_DA_DZZZ_VIBRATION_PATTERN;
                break;
            case 4:
                mCustomPattern = DA_DZZZ_DA_VIBRATION_PATTERN;
                break;
            case 5:
                String customVibValue = Settings.System.getString(
                        context.getContentResolver(),
                        Settings.System.CUSTOM_NOTIFICATION_VIBRATION_PATTERN);
                String[] customVib = new String[3];
                if (customVibValue != null && !customVibValue.equals("")) {
                    customVib = customVibValue.split(",", 3);
                } else { // If no value - use default
                    customVib[0] = "80";
                    customVib[1] = "40";
                    customVib[2] = "0";
                }
                long[] vibPattern = {
                    0, // No delay before starting
                    Long.parseLong(customVib[0]), // How long to vibrate
                    120, // Delay
                    Long.parseLong(customVib[1]), // How long to vibrate
                    120, // Delay
                    Long.parseLong(customVib[2]), // How long to vibrate
                    120, // How long to wait before vibrating again
                };
                mCustomPattern = vibPattern;
                break;
        }
    }

    /**
     * Safely create a {@link VibrationEffect} from given vibration {@code pattern}.
     *
     * <p>This method returns {@code null} if the pattern is also {@code null} or invalid.
     *
     * @param pattern The off/on vibration pattern, where each item is a duration in milliseconds.
     * @param insistent {@code true} if the vibration should loop until it is cancelled.
     */
    @Nullable
    public static VibrationEffect createWaveformVibration(@Nullable long[] pattern,
            boolean insistent) {
        try {
            if (pattern != null) {
                return VibrationEffect.createWaveform(pattern, /* repeat= */ insistent ? 0 : -1);
            }
        } catch (IllegalArgumentException e) {
            Slog.e(TAG, "Error creating vibration waveform with pattern: "
                    + Arrays.toString(pattern));
        }
        return null;
    }

    /**
     * Safely create a {@link VibrationEffect} from given waveform description.
     *
     * <p>The waveform is described by a sequence of values for target amplitude, frequency and
     * duration, that are forwarded to {@link VibrationEffect.WaveformBuilder#addTransition}.
     *
     * <p>This method returns {@code null} if the pattern is also {@code null} or invalid.
     *
     * @param values The list of values describing the waveform as a sequence of target amplitude,
     *               frequency and duration.
     * @param insistent {@code true} if the vibration should loop until it is cancelled.
     */
    @Nullable
    public static VibrationEffect createPwleWaveformVibration(@Nullable float[] values,
            boolean insistent) {
        try {
            if (values == null) {
                return null;
            }

            int length = values.length;
            // The waveform is described by triples (amplitude, frequency, duration)
            if ((length == 0) || (length % 3 != 0)) {
                return null;
            }

            VibrationEffect.WaveformBuilder waveformBuilder = VibrationEffect.startWaveform();
            for (int i = 0; i < length; i += 3) {
                waveformBuilder.addTransition(Duration.ofMillis((int) values[i + 2]),
                        targetAmplitude(values[i]), targetFrequency(values[i + 1]));
            }

            VibrationEffect effect = waveformBuilder.build();
            if (insistent) {
                return VibrationEffect.startComposition()
                        .repeatEffectIndefinitely(effect)
                        .compose();
            }
            return effect;
        } catch (IllegalArgumentException e) {
            Slog.e(TAG, "Error creating vibration PWLE waveform with pattern: "
                    + Arrays.toString(values));
        }
        return null;
    }

    /**
     * Vibrate the device with given {@code effect}.
     *
     * <p>We need to vibrate as "android" so we can breakthrough DND.
     */
    public void vibrate(VibrationEffect effect, AudioAttributes attrs, String reason) {
        mVibrator.vibrate(Process.SYSTEM_UID, PackageManagerService.PLATFORM_PACKAGE_NAME,
                effect, reason, new VibrationAttributes.Builder(attrs).build());
    }

    /** Stop all notification vibrations (ringtone, alarm, notification usages). */
    public void cancelVibration() {
        int usageFilter =
                VibrationAttributes.USAGE_CLASS_ALARM | ~VibrationAttributes.USAGE_CLASS_MASK;
        mVibrator.cancel(usageFilter);
    }

    /**
     * Creates a vibration to be used as fallback when the device is in vibrate mode.
     *
     * @param insistent {@code true} if the vibration should loop until it is cancelled.
     */
    public VibrationEffect createFallbackVibration(boolean insistent) {
        if (mVibrator.hasFrequencyControl()) {
            VibrationEffect effect = createPwleWaveformVibration(mFallbackPwlePattern, insistent);
            if (effect != null) {
                return effect;
            }
        }
        return createWaveformVibration(mFallbackPattern, insistent);
    }

    /**
     * Creates a vibration to be used by notifications without a custom pattern.
     *
     * @param insistent {@code true} if the vibration should loop until it is cancelled.
     */
    public VibrationEffect createDefaultVibration(boolean insistent) {
        final boolean hasCustom = mCustomPattern != null;
        if (mVibrator.hasFrequencyControl() && !hasCustom) {
            VibrationEffect effect = createPwleWaveformVibration(mDefaultPwlePattern, insistent);
            if (effect != null) {
                return effect;
            }
        }
        return createWaveformVibration(hasCustom ? mCustomPattern : mDefaultPattern, insistent);
    }

    @Nullable
    private static float[] getFloatArray(Resources resources, int resId) {
        TypedArray array = resources.obtainTypedArray(resId);
        try {
            float[] values = new float[array.length()];
            for (int i = 0; i < values.length; i++) {
                values[i] = array.getFloat(i, Float.NaN);
                if (Float.isNaN(values[i])) {
                    return null;
                }
            }
            return values;
        } finally {
            array.recycle();
        }
    }

    private static long[] getLongArray(Resources resources, int resId, int maxLength, long[] def) {
        int[] ar = resources.getIntArray(resId);
        if (ar == null) {
            return def;
        }
        final int len = ar.length > maxLength ? maxLength : ar.length;
        long[] out = new long[len];
        for (int i = 0; i < len; i++) {
            out[i] = ar[i];
        }
        return out;
    }
}
