package jgl.bitmapcache

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.util.*
import kotlin.collections.HashSet

/**
 * Created by JGLouis on 09/01/2018.
 */

const val TAG = "BitmapLruCache"

class BitmapLruCache(private val MAX_SIZE_BYTE: Long) {

    private val indexedBitmaps = HashMap<String, Bitmap>()
    private val lru = ArrayDeque<String>()
    private var totalByteSize = 0L
    private val listeners = HashSet<OnChangeListener>()

    private fun put(key: String, value: Bitmap): Bitmap {
        // Check size
        if (value.allocationByteCount > MAX_SIZE_BYTE) {
            Log.e(TAG, String.format("Bitmap (%s) does not fit in cache (%s)",
                    humanReadableByteCount(value.allocationByteCount.toLong(), true),
                    humanReadableByteCount(MAX_SIZE_BYTE, true)))
        } else {
            // Let's see if we can fit this value...
            Log.d(TAG, String.format("Storing Bitmap (%s)", humanReadableByteCount(value.allocationByteCount.toLong(), true)))
            while (value.allocationByteCount + totalByteSize > MAX_SIZE_BYTE) {
                // ... Ok, we remove one value and try again
                val keyToRemove = lru.removeLast()
                val bitmapToRemove = indexedBitmaps.remove(keyToRemove)
                if (bitmapToRemove != null) {
                    totalByteSize -= bitmapToRemove.allocationByteCount
                    Log.d(TAG, "Had to remove one bitmap to fit the new one...")
                }
            }

            // Add to the cache and update memory count
            indexedBitmaps[key] = value
            lru.push(key)
            totalByteSize += value.allocationByteCount
            listeners.forEach { it.onBitmapLruCacheChange(indexedBitmaps.size, totalByteSize) }
        }
        return value
    }

    private fun get(key: String): Bitmap? {
        val bitmap = indexedBitmaps[key]
        if (bitmap != null) {
            lru.remove(key)
            lru.push(key)
            Log.d(TAG, String.format("Loaded from cache"))
        }
        return bitmap
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

    fun removeListener(listener: OnChangeListener) {
        listeners -= listener
    }

    val bitmapAllocationByteCount: Long
        get() = totalByteSize

    val size: Int
        get() = indexedBitmaps.size

    interface OnChangeListener {
        fun onBitmapLruCacheChange(numBitmap: Int, totalSize: Long)
    }
}


private var cache = BitmapLruCache(10 * 1024 * 1024L) // 10Mb
fun resetCache(MAX_SIZE_BYTE: Long) : BitmapLruCache  {
    cache = BitmapLruCache(MAX_SIZE_BYTE)
    return cache
}

// Memoization of bitmaps
class MemoizeBitmap<in T, in U>(private val func: (T, U) -> Bitmap?) : (T, U) -> Bitmap? {
    override fun invoke(p1: T, p2: U) : Bitmap? {
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
        context.assets.open(path).toBitmap()
    } catch (e: IOException) {
        Log.e("getAssetBitmap", "", e)
        null
    }
}

fun InputStream.toBitmap(): Bitmap? {
    return BitmapFactory.decodeStream(this)
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
