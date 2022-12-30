/*
 * Copyright (C) 2019 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.statusbar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.util.Log
import android.util.MathUtils
import com.android.internal.graphics.ColorUtils
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.statusbar.notification.MediaNotificationProcessor
import javax.inject.Inject

private const val TAG = "MediaArtworkProcessor"
private const val COLOR_ALPHA = 255
private const val BLUR_RADIUS = 1f
private const val DOWNSAMPLE = 2

@SysUISingleton
class MediaArtworkProcessor @Inject constructor() {

    private val mTmpSize = Point()
    private var mArtworkCache: Bitmap? = null

    fun processArtwork(context: Context, artwork: Bitmap): Bitmap? {
        if (mArtworkCache != null) {
            return mArtworkCache
        }
        var inBitmap: Bitmap? = null
        try {
            @Suppress("DEPRECATION")
            context.display?.getSize(mTmpSize)
            val rect = Rect(0, 0, artwork.width, artwork.height)
            MathUtils.fitRect(rect, Math.max(mTmpSize.x / DOWNSAMPLE, mTmpSize.y / DOWNSAMPLE))
            inBitmap = Bitmap.createScaledBitmap(artwork, rect.width(), rect.height(),
                    true /* filter */)
            val outBitmap = inBitmap.copy(inBitmap.getConfig(), true)

            return outBitmap
        } catch (ex: IllegalArgumentException) {
            Log.e(TAG, "error while processing artwork", ex)
            return null
        } finally {
            inBitmap?.recycle()
        }
    }

    fun clearCache() {
        mArtworkCache?.recycle()
        mArtworkCache = null
    }
}
