package jgl.bitmapcache

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.Log
import android.util.LruCache
import java.io.IOException
import java.io.InputStream
import kotlin.collections.HashSet

/**
 * Created by JGLouis on 09/01/2018.
 */

const val TAG = "BitmapLruCache"

// Amount of heap to not allocate
const val MEMORY_SAFETY_THRESHOLD = 1024 * 1024 // 1 MiB

class BitmapLruCache(private val MAX_SIZE_BYTE: Int) {

    private val lruCache = object : LruCache<String, Bitmap>(MAX_SIZE_BYTE) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.allocationByteCount
        }
    }
    private val listeners = HashSet<OnChangeListener>()

    private fun put(key: String, value: Bitmap): Bitmap {
        if (value.allocationByteCount > MAX_SIZE_BYTE) {
            Log.e(TAG, String.format("Bitmap (%s) does not fit in cache (%s)",
                    humanReadableByteCount(value.allocationByteCount.toLong(), false),
                    humanReadableByteCount(MAX_SIZE_BYTE.toLong(), false)))
        } else {
            lruCache.put(key, value)
            listeners.forEach { it.onBitmapLruCacheChange() }
        }
        return value
    }

    private fun get(key: String): Bitmap? {
        return lruCache.get(key)
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

fun InputStream.toBitmap(encoding: Bitmap.Config): Bitmap? {
    val options = BitmapFactory.Options()
    options.inJustDecodeBounds = true
    options.inPreferredConfig = encoding
    BitmapFactory.decodeStream(this, null, options)
    val estimatedSize = getBytesPerPixel(encoding) * options.outWidth * options.outHeight

    val rt = Runtime.getRuntime()
    val availableHeapMemory = rt.maxMemory() - (rt.totalMemory() - rt.freeMemory())
    Log.d(TAG, "Estimated size " + humanReadableByteCount(estimatedSize.toLong(), false))
    return if (estimatedSize > availableHeapMemory - MEMORY_SAFETY_THRESHOLD) {
        if (estimatedSize > availableHeapMemory) {
            Log.e(TAG, String.format("Bitmap (%s) does not fit in heap (%s)",
                    humanReadableByteCount(estimatedSize.toLong(), false),
                    humanReadableByteCount(availableHeapMemory, false)))
        } else {
            Log.e(TAG, String.format("Bitmap (%s) does fit in heap (%s) but we keep some safety marge (%s)",
                    humanReadableByteCount(estimatedSize.toLong(), false),
                    humanReadableByteCount(availableHeapMemory, false),
                    humanReadableByteCount(MEMORY_SAFETY_THRESHOLD.toLong(), false)))
        }
        null
    } else {
        options.inJustDecodeBounds = false
        this.reset()
        BitmapFactory.decodeStream(this, null, options)
    }
}

fun Bitmap.rotateMem(degrees: Float): Bitmap {
    return MemoizeBitmap(::rotate)(this, degrees)!!
}

fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
    Log.d(TAG, "rotate")
    val bitmapResult = Bitmap.createBitmap(bitmap.height, bitmap.width, Bitmap.Config.ARGB_8888)
    val tempCanvas = Canvas(bitmapResult)
    val pivotX = bitmap.width / 2f
    val pivotY = bitmap.height / 2f
    tempCanvas.rotate(degrees, pivotX, pivotY)
    tempCanvas.drawBitmap(bitmap, 0f, 0f, null)
    return bitmapResult
}

fun Bitmap.scaleMem(scalingFactor: Float): Bitmap {
    return MemoizeBitmap(::scale)(this, scalingFactor)!!
}

fun scale(bitmap: Bitmap, scalingFactor: Float): Bitmap {
    Log.d(TAG, "scale")
    val bitmapResult = Bitmap.createBitmap(bitmap.height, bitmap.width, Bitmap.Config.ARGB_8888)
    val tempCanvas = Canvas(bitmapResult)
    tempCanvas.scale(scalingFactor, scalingFactor)
    tempCanvas.drawBitmap(bitmap, 0f, 0f, null)
    return bitmapResult
}

fun getBytesPerPixel(config: Bitmap.Config): Int {
    if (config === Bitmap.Config.ARGB_8888) {
        return 4
    } else if (config === Bitmap.Config.RGB_565) {
        return 2
    } else if (config === Bitmap.Config.ARGB_4444) {
        return 2
    } else if (config === Bitmap.Config.ALPHA_8) {
        return 1
    }
    return 1
}
