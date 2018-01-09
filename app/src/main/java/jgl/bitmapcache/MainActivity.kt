package jgl.bitmapcache

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(), ComponentCallbacks2 {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        memoryClass.text = activityManager?.memoryClass.toString()
    }

    @Suppress("UNUSED_PARAMETER")
    fun loadImage(view: View) {
        val inputStream = assets.open("aps_icon_512.png")
        val drawable = Drawable.createFromStream(inputStream, null)
        imageView.setImageDrawable(drawable)
    }

    @Suppress("UNUSED_PARAMETER")
    fun rotate90(view: View) {
        val inputStream = assets.open("aps_icon_512.png")
        val bitmapOriginal = BitmapFactory.decodeStream(inputStream)
        val bitmapResult = Bitmap.createBitmap(bitmapOriginal.height, bitmapOriginal.width, Bitmap.Config.ARGB_8888)
        val tempCanvas = Canvas(bitmapResult)
        val pivotX = bitmapOriginal.width / 2f
        val pivotY = bitmapOriginal.height / 2f
        tempCanvas.rotate(90f, pivotX, pivotY)
        tempCanvas.drawBitmap(bitmapOriginal, 0f, 0f, null)
        imageView.setImageBitmap(bitmapResult)
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
    }
}


