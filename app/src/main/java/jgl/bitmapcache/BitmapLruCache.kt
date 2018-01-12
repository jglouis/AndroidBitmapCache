package jgl.bitmapcache

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import android.util.LruCache
import java.io.IOException
import java.io.InputStream
import java.lang.ref.SoftReference
import java.util.*
import kotlin.collections.HashSet
import android.os.Build


/**
 * Created by JGLouis on 09/01/2018.
 */

const val TAG = "BitmapLruCache"

// Amount of heap to not allocate
const val MEMORY_SAFETY_THRESHOLD = 1024 * 1024 // 1 MiB

class BitmapLruCache(private val MAX_SIZE_BYTE: Int) {

    private val listeners = HashSet<OnChangeListener>()

    // Keep evicted Bitmap with a WeakReference for potential reuse
    private val reusableBitmaps = Collections.synchronizedSet(HashSet<SoftReference<Bitmap>>())

    private val lruCache = object : LruCache<String, BitmapDrawable>(MAX_SIZE_BYTE) {
        override fun sizeOf(key: String?, value: BitmapDrawable?): Int {
            return value?.bitmap?.allocationByteCountSupport ?: 0
        }

        override fun entryRemoved(evicted: Boolean, key: String?, oldValue: BitmapDrawable?, newValue: BitmapDrawable?) {
            if (oldValue is RecyclingBitmapDrawable) {
                oldValue.setIsCached(false)
            } else {
                if (oldValue != null) {
                    reusableBitmaps.add(SoftReference(oldValue.bitmap))
                }
            }
        }
    }

    private fun put(key: String, value: Bitmap): Bitmap {
        if (value.allocationByteCountSupport > MAX_SIZE_BYTE) {
            Log.e(TAG, String.format("Bitmap (%s) does not fit in cache (%s)",
                    humanReadableByteCount(value.allocationByteCountSupport.toLong(), false),
                    humanReadableByteCount(MAX_SIZE_BYTE.toLong(), false)))
        } else {
            lruCache.put(key, BitmapDrawable(value))
            listeners.forEach { it.onBitmapLruCacheChange() }
        }
        return value
    }

    private fun get(key: String): Bitmap? {
        return lruCache.get(key)?.bitmap
    }

    fun getOrPut(key: String, defaultValue: () -> Bitmap?): Bitmap? {
        val value = get(key)
        return if (value == null) {
            val answer = defaultValue()
            if (answer != null) {
                put(key, answer)
            }
            answer
        } else {
            value
        }
    }

    fun getBitmapFromReusableSet(options: BitmapFactory.Options): Bitmap? {
        var bitmap: Bitmap? = null
        synchronized(reusableBitmaps) {
            for (item in reusableBitmaps) {
                val bmp = item.get()
                if (bmp?.isMutable == true) {
                    if (canUseForInBitmap(bmp, options)) {
                        bitmap = bmp
                        reusableBitmaps.remove(item)
                        break
                    }
                }
            }
        }
        return bitmap
    }

    fun registerListener(listener: OnChangeListener) {
        listeners += listener
    }

    fun unregisterListener(listener: OnChangeListener) {
        listeners -= listener
    }

    val bitmapAllocationByteCount: Int
        get() = lruCache.size()

    val maxBitmapAllocationCount: Int
        get() = MAX_SIZE_BYTE

    val size: Int
        get() = lruCache.putCount() - lruCache.evictionCount()

    interface OnChangeListener {
        fun onBitmapLruCacheChange()
    }
}


private var cache = BitmapLruCache(10 * 1024 * 1024) // 10Mb
fun resetCache(MAX_SIZE_BYTE: Int): BitmapLruCache {
    cache = BitmapLruCache(MAX_SIZE_BYTE)
    return cache
}

// Memoization of bitmaps
class MemoizeBitmap<in T, in U>(private val func: (T, U) -> Bitmap?) : (T, U) -> Bitmap? {
    override fun invoke(p1: T, p2: U): Bitmap? {
        val key = String.format("%s_%d_%d", func, p1?.hashCode(), p2?.hashCode())
        Log.d(TAG, String.format("Memoize Bitmap as key %s", key))
        return cache.getOrPut(key, { func(p1, p2) })
    }
}

fun Context.getAssetBitmapMem(path: String): Bitmap? {
    return MemoizeBitmap(::getAssetBitmap)(this, path)
}

fun getAssetBitmap(context: Context, path: String): Bitmap? {
    return try {
        Log.d(TAG, "Loading from assets")
        context.assets.open(path).toBitmap(Bitmap.Config.ARGB_8888)
    } catch (e: IOException) {
        Log.e("getAssetBitmap", "", e)
        null
    }
}

fun getAvailableHeapMemory() : Long {
    val rt = Runtime.getRuntime()
    return rt.maxMemory() - (rt.totalMemory() - rt.freeMemory())
}

fun checkHeapSpace(estimatedSize: Int): Boolean {
    val rt = Runtime.getRuntime()
    Log.d(TAG, "Estimated size " + humanReadableByteCount(estimatedSize.toLong(), false))
    return when {
        estimatedSize > rt.maxMemory() - MEMORY_SAFETY_THRESHOLD -> {
            Log.e(TAG, String.format("Bitmap (%s) does not fit in heap (%s) even if we would trim the cache",
                    humanReadableByteCount(estimatedSize.toLong(), false),
                    humanReadableByteCount(rt.maxMemory(), false)))
            false
        }
        estimatedSize > getAvailableHeapMemory() - MEMORY_SAFETY_THRESHOLD -> {
            val availableHeapMemory = getAvailableHeapMemory()
            when {
                estimatedSize > availableHeapMemory -> {
                    Log.e(TAG, String.format("Bitmap (%s) does not fit in heap (%s available)",
                            humanReadableByteCount(estimatedSize.toLong(), false),
                            humanReadableByteCount(availableHeapMemory, false)))
                    false
                }
                else -> {
                    Log.e(TAG, String.format("Bitmap (%s) does fit in heap (%s available) but we keep some safety marge (%s)",
                            humanReadableByteCount(estimatedSize.toLong(), false),
                            humanReadableByteCount(availableHeapMemory, false),
                            humanReadableByteCount(MEMORY_SAFETY_THRESHOLD.toLong(), false)))
                    false
                }
            }
        }
        else -> true
    }
}

fun InputStream.toBitmap(encoding: Bitmap.Config): Bitmap? {
    val options = BitmapFactory.Options()
    options.inJustDecodeBounds = true
    options.inPreferredConfig = encoding
    BitmapFactory.decodeStream(this, null, options)
    val estimatedSize = getBytesPerPixel(encoding) * options.outWidth * options.outHeight
    return if (checkHeapSpace(estimatedSize)) {
        options.inJustDecodeBounds = false
        options.inMutable = true
        this.reset()
        cache.getBitmapFromReusableSet(options).let {
            options.inBitmap = it
            Log.d(TAG, "Reusing an evicted bitmap")
        }
        BitmapFactory.decodeStream(this, null, options)
    } else null
}

fun Bitmap.rotateMem(degrees: Float): Bitmap? {
    return MemoizeBitmap(::rotate)(this, degrees)
}

fun rotate(bitmap: Bitmap, degrees: Float): Bitmap? {
    Log.d(TAG, "rotate")
    return if (checkHeapSpace(bitmap.allocationByteCountSupport)) {
        val bitmapResult = Bitmap.createBitmap(bitmap.height, bitmap.width, Bitmap.Config.ARGB_8888)
        val tempCanvas = Canvas(bitmapResult)
        val pivotX = bitmap.width / 2f
        val pivotY = bitmap.height / 2f
        tempCanvas.rotate(degrees, pivotX, pivotY)
        tempCanvas.drawBitmap(bitmap, 0f, 0f, null)
        bitmapResult
    } else null
}

fun Bitmap.scaleMem(scalingFactor: Float): Bitmap? {
    return MemoizeBitmap(::scale)(this, scalingFactor)
}

fun scale(bitmap: Bitmap, scalingFactor: Float): Bitmap? {
    Log.d(TAG, "scale")
    return if (checkHeapSpace(bitmap.allocationByteCountSupport)) {
        val bitmapResult = Bitmap.createBitmap(bitmap.height, bitmap.width, Bitmap.Config.ARGB_8888)
        val tempCanvas = Canvas(bitmapResult)
        tempCanvas.scale(scalingFactor, scalingFactor)
        tempCanvas.drawBitmap(bitmap, 0f, 0f, null)
        bitmapResult
    } else null
}

fun getBytesPerPixel(config: Bitmap.Config): Int {
    return when (config) {
        Bitmap.Config.ARGB_8888 -> 4
        Bitmap.Config.RGB_565 -> 2
        Bitmap.Config.ARGB_4444 -> 2
        Bitmap.Config.ALPHA_8 -> 1
        else -> 1
    }
}

fun canUseForInBitmap(
        candidate: Bitmap, targetOptions: BitmapFactory.Options): Boolean {

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        // From Android 4.4 (KitKat) onward we can re-use if the byte size of
        // the new bitmap is smaller than the reusable bitmap candidate
        // allocation byte count.
        if (targetOptions.inSampleSize == 0) return false // Prevent divide by zero
        val width = targetOptions.outWidth / targetOptions.inSampleSize
        val height = targetOptions.outHeight / targetOptions.inSampleSize
        val byteCount = width * height * getBytesPerPixel(candidate.config)
        return byteCount <= candidate.allocationByteCount
    }

    // On earlier versions, the dimensions must match exactly and the inSampleSize must be 1
    return (candidate.width == targetOptions.outWidth
            && candidate.height == targetOptions.outHeight
            && targetOptions.inSampleSize == 1)
}
