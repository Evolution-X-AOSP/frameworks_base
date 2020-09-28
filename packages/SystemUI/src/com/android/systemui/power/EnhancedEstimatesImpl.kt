/*
 * Copyright (C) 2021 Benzo Rom
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/
package com.android.systemui.power

import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.Settings
import android.util.KeyValueListParser
import android.util.Log
import com.android.settingslib.fuelgauge.Estimate
import com.android.settingslib.utils.PowerUtil
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.power.EnhancedEstimates
import java.lang.Exception
import java.lang.IllegalArgumentException
import java.time.Duration
import javax.inject.Inject

@SysUISingleton
class EnhancedEstimatesImpl @Inject constructor(
    private val mContext: Context
) : EnhancedEstimates {
    val mParser: KeyValueListParser = KeyValueListParser(',')

    override fun isHybridNotificationEnabled(): Boolean {
        val isHybridEnabled: Boolean = try {
            if (!mContext.packageManager.getPackageInfo(
                    "com.google.android.apps.turbo",
                    PackageManager.MATCH_DISABLED_COMPONENTS
                ).applicationInfo.enabled
            ) return false
            updateFlags()
            mParser.getBoolean("hybrid_enabled", true)
        } catch (ex: PackageManager.NameNotFoundException) {
            false
        }
        return isHybridEnabled
    }

    override fun getEstimate(): Estimate {
            var query: Cursor? = null
            try {
                query = mContext.contentResolver.query(
                    Uri.Builder()
                        .scheme("content")
                        .authority("com.google.android.apps.turbo.estimated_time_remaining")
                        .appendPath("time_remaining")
                        .build(),
                    null, null, null, null
                )
            } catch (ex: Exception) {
                Log.d(TAG, "Something went wrong when getting an estimate from Turbo", ex)
            }
            if (query != null && query.moveToFirst()) {
                var averageDischargeTime: Long = -1
                val isBasedOnUsage = (query.getColumnIndex("is_based_on_usage") == -1
                        || query.getInt(query.getColumnIndex("is_based_on_usage")) != 0)
                val columnIndex = query.getColumnIndex("average_battery_life")
                if (columnIndex != -1) {
                    val averageBatteryLife = query.getLong(columnIndex)
                    if (averageBatteryLife != -1L) {
                        var threshold = Duration.ofMinutes(15L).toMillis()
                        if (Duration.ofMillis(averageBatteryLife) >= Duration.ofDays(1L)) {
                            threshold = Duration.ofHours(1L).toMillis()
                        }
                        averageDischargeTime =
                            PowerUtil.roundTimeToNearestThreshold(averageBatteryLife, threshold)
                        val estimate = Estimate(
                            query.getLong(query.getColumnIndex("battery_estimate")),
                            isBasedOnUsage,
                            averageDischargeTime
                        )
                        query.close()
                        return estimate
                    }
                }
                val estimate = Estimate(
                    query.getLong(query.getColumnIndex("battery_estimate")),
                    isBasedOnUsage,
                    averageDischargeTime
                )
                query.close()
                return estimate
            }
        query?.close()
        return Estimate(-1L, false, -1L)
    }

    private fun updateFlags() {
        try {
            mParser.setString(
                Settings.Global.getString(
                    mContext.contentResolver,
                    "hybrid_sysui_battery_warning_flags"
                )
            )
        } catch (ex: IllegalArgumentException) {
            Log.e(TAG, "Bad hybrid sysui warning flags")
        }
    }

    override fun getLowWarningEnabled(): Boolean {
        updateFlags()
        return mParser.getBoolean("low_warning_enabled", false)
    }

    override fun getLowWarningThreshold(): Long {
        updateFlags()
        return mParser.getLong("low_threshold", Duration.ofHours(3L).toMillis())
    }

    override fun getSevereWarningThreshold(): Long {
        updateFlags()
        return mParser.getLong("severe_threshold", Duration.ofHours(1L).toMillis())
    }

    companion object {
        const val TAG = "EnhancedEstimates"
    }
}