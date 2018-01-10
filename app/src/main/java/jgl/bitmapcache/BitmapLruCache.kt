package jgl.bitmapcache

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.util.*

/**
 * Created by JGLouis on 09/01/2018.
 */

class BitmapLruCache(private val MAX_SIZE_BYTE: Long) {

    private val indexedBitmaps = HashMap<String, Bitmap?>()
    private val lru = ArrayDeque<String>()
    private var totalByteSize = 0L


    companion object {
        val TAG = BitmapLruCache::class.java.simpleName
    }

    private fun put(key: String, value: Bitmap): Bitmap {
        // Check size
        if (value.allocationByteCount > MAX_SIZE_BYTE) {
            Log.e(TAG, String.format("Bitmap (%s) does not fit in cache (%s)",
                    humanReadableByteCount(value.allocationByteCount.toLong(), true),
                    humanReadableByteCount(MAX_SIZE_BYTE, true)))
        } else {
            // Let's see if we can fit this value...
            while (value.allocationByteCount + totalByteSize > MAX_SIZE_BYTE) {
                // ... Ok, we remove one value and try again
                val keyToRemove = lru.pop()
                val bitmapToRemove = indexedBitmaps.remove(keyToRemove)
                if (bitmapToRemove != null) {
                    totalByteSize -= bitmapToRemove.allocationByteCount
                }
            }

            // Add to the cache and update memory count
            indexedBitmaps[key] = value
            lru.push(key)
            totalByteSize += value.allocationByteCount
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
}

val cache = BitmapLruCache(10 * 1024 * 1024L) // 10Mb

// Memoization of bitmaps
class MemoizeBitmap<in T, in U>(private val func: (T, U) -> Bitmap?) : (T, U) -> Bitmap? {
    override fun invoke(p1: T, p2: U) =
            cache.getOrPut(String.format("%s_%d_%d", func, p1?.hashCode(), p2?.hashCode()), { func(p1, p2) })
}

fun Context.getAssetBitmapMem(path: String): Bitmap? {
    return MemoizeBitmap(::getAssetBitmap)(this, path)
}

fun getAssetBitmap(context: Context, path: String): Bitmap? {
    return try {
        Log.d("DEBUG", "Loading from assets")
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
    Log.d("DEBUG", "rotate")
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
    Log.d("DEBUG", "scale")
    val bitmapResult = Bitmap.createBitmap(bitmap.height, bitmap.width, Bitmap.Config.ARGB_8888)
    val tempCanvas = Canvas(bitmapResult)
    tempCanvas.scale(scalingFactor, scalingFactor)
    tempCanvas.drawBitmap(bitmap, 0f, 0f, null)
    return bitmapResult
}
