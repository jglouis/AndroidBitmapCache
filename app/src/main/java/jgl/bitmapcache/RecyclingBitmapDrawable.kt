/*
 * Copyright (C) 2013 The Android Open Source Project
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

package jgl.bitmapcache

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.Log

/**
 * A BitmapDrawable that keeps track of whether it is being displayed or cached.
 * When the drawable is no longer being displayed or cached,
 * [recycle()][android.graphics.Bitmap.recycle] will be called on this drawable's bitmap.
 */
class RecyclingBitmapDrawable(res: Resources, bitmap: Bitmap) : BitmapDrawable(res, bitmap) {

    private var mCacheRefCount = 0
    private var mDisplayRefCount = 0

    private var mHasBeenDisplayed: Boolean = false

    /**
     * Notify the drawable that the displayed state has changed. Internally a
     * count is kept so that the drawable knows when it is no longer being
     * displayed.
     *
     * @param isDisplayed - Whether the drawable is being displayed or not
     */
    fun setIsDisplayed(isDisplayed: Boolean) {
        synchronized(this) {
            if (isDisplayed) {
                mDisplayRefCount++
                mHasBeenDisplayed = true
            } else {
                mDisplayRefCount--
            }
        }

        // Check to see if recycle() can be called
        checkState()
    }

    /**
     * Notify the drawable that the cache state has changed. Internally a count
     * is kept so that the drawable knows when it is no longer being cached.
     *
     * @param isCached - Whether the drawable is being cached or not
     */
    fun setIsCached(isCached: Boolean) {
        synchronized(this) {
            if (isCached) {
                mCacheRefCount++
            } else {
                mCacheRefCount--
            }
        }

        // Check to see if recycle() can be called
        checkState()
    }

    @Synchronized private fun checkState() {
        // If the drawable cache and display ref counts = 0, and this drawable
        // has been displayed, then recycle
        if (mCacheRefCount <= 0 && mDisplayRefCount <= 0 && mHasBeenDisplayed
                && hasValidBitmap()) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "No longer being used or cached so recycling. " + toString())
            }

            bitmap.recycle()
        }
    }

    @Synchronized private fun hasValidBitmap(): Boolean {
        val bitmap = bitmap
        return bitmap != null && !bitmap.isRecycled
    }

    companion object {

        internal val TAG = "CountingBitmapDrawable"
    }
}
