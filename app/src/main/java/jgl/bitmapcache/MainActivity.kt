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
import kotlinx.android.synthetic.main.memory_info.*

class MainActivity : AppCompatActivity(), ComponentCallbacks2 {

    private val activityManager: ActivityManager? by lazy {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        activityManager
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        memoryClass.text = activityManager?.memoryClass.toString()
    }

    @Suppress("UNUSED_PARAMETER")
    fun loadImage(view: View) {
        val bitmap = getAssetBitmapMem(fileName)
        imageView.setImageBitmap(bitmap)
    }

    @Suppress("UNUSED_PARAMETER")
    fun fillMemory(view: View) {
        val bitmap = getAssetBitmap(this, fileName) // No memoization
        if (bitmap != null) {
            bitmapBucket.add(bitmap)
        } else {
            Log.e(TAG, "Bitmap is null!")
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun fillMemoryUsingCache(view: View) {
        val bitmap = getAssetBitmapMem(fileName)
        if (bitmap != null) {
            bitmapBucket.add(bitmap)
        } else {
            Log.e(TAG, "Bitmap is null!")
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun rotate90(view: View) {
        val bitmap = getAssetBitmapMem(fileName)?.rotateMem(90f)
        imageView.setImageBitmap(bitmap)
    }

    @Suppress("UNUSED_PARAMETER")
    fun scaleTimes2(view: View) {
        val bitmap = getAssetBitmapMem(fileName)?.scaleMem(2f)
        imageView.setImageBitmap(bitmap)
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
        var msg = "Unrecognized level"
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
        const val fileName = "aps_icon_512.png"
    }
}


