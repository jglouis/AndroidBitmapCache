package jgl.bitmapcache

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.cache_info.*
import kotlinx.android.synthetic.main.memory_info.*

class MainActivity : AppCompatActivity(), ComponentCallbacks2, BitmapLruCache.OnChangeListener {
    override fun onBitmapLruCacheChange(numBitmap: Int, totalSize: Int) {
        cacheInfoNumElements.text = String.format("%d Bitmap in cache", numBitmap)
        cacheInfoTotalAllocatedMemory.text =
                String.format("%s / %s",
                        humanReadableByteCount(totalSize.toLong(), true),
                        humanReadableByteCount(cache.maxBitmapAllocationCount.toLong(), true))
    }

    private val activityManager: ActivityManager? by lazy {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        activityManager
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)



        cacheInfoNumElements.text = String.format("%d Bitmap in library", cache.size)
        cacheInfoTotalAllocatedMemory.text =
                String.format("%s / %s",
                        humanReadableByteCount(cache.bitmapAllocationByteCount.toLong(), true),
                        humanReadableByteCount(cache.maxBitmapAllocationCount.toLong(), true))

        memoryClass.text = activityManager?.memoryClass.toString()
    }

    private val cache :BitmapLruCache by lazy {
        resetCache((activityManager?.memoryClass?.times(1024 * 1024 / 2) ?: 1024 * 1024))
    }
    override fun onResume() {
        super.onResume()
        cache.registerListener(this)
    }

    override fun onPause() {
        super.onPause()
        cache.unregisterListener(this)
    }

    @Suppress("UNUSED_PARAMETER")
    fun loadImage(view: View) {
        val bitmap = getAssetBitmapMem(fileName512)
        imageView.setImageBitmap(bitmap)
    }

    @Suppress("UNUSED_PARAMETER")
    fun fillMemory(view: View) {
        for (i in 1..150) {
            val bitmap = getAssetBitmap(this, fileName512) // No memoization
            if (bitmap != null) {
                bitmapBucket.add(bitmap)
            } else {
                Log.e(TAG, "Bitmap is null!")
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun fillMemoryUsingCache(view: View) {
        for (i in 1..150) {
            val bitmap = getAssetBitmapMem(fileName512)
            if (bitmap != null) {
                bitmapBucket.add(bitmap)
            } else {
                Log.e(TAG, "Bitmap is null!")
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun rotate90(view: View) {
        val bitmap = getAssetBitmapMem(fileName2048)?.rotateMem(90f)
        imageView.setImageBitmap(bitmap)
    }

    private var i = 1
    @Suppress("UNUSED_PARAMETER")
    fun scaleTimes2(view: View) {
        val bitmap = getAssetBitmapMem(fileName512)?.scaleMem(2f * i)
        imageView.setImageBitmap(bitmap)
        i += 1
    }

    @Suppress("UNUSED_PARAMETER")
    fun getMemoryInfo(view: View) {
        val outInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(outInfo)
        available_memory.text = String.format(getString(R.string.available_memory), humanReadableByteCount(outInfo.availMem, true))
        low_memory.text = if (outInfo.lowMemory) getString(R.string.memory_low) else getString(R.string.memory_ok)
        threshold.text = String.format(getString(R.string.memory_info_threshold), humanReadableByteCount(outInfo.threshold, true))
        total_memory.text = String.format(getString(R.string.total_memory_kernel), humanReadableByteCount(outInfo.totalMem, true))

    }

    override fun onTrimMemory(level: Int) {
        var msg = "Unrecognized onTrimMemory level"
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> msg = "TRIM_MEMORY_BACKGROUND"
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> msg = "TRIM_MEMORY_COMPLETE"
            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> msg = "TRIM_MEMORY_MODERATE"
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> msg = "TRIM_MEMORY_RUNNING_CRITICAL"
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> msg = "TRIM_MEMORY_RUNNING_LOW"
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> msg = "TRIM_MEMORY_RUNNING_MODERATE"
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> msg = "TRIM_MEMORY_UI_HIDDEN"
        }
        Log.d(TAG, msg)
        messageTrimMemory.text = msg
    }


    companion object {
        val TAG = MainActivity::class.java.simpleName!!
        val bitmapBucket = ArrayList<Bitmap>()
        const val fileName512 = "aps_icon_512.png"
        const val fileName2048 = "aps_icon_2048.png"
    }
}


