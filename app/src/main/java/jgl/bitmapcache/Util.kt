package jgl.bitmapcache

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast

/**
 * Created by JGLouis on 09/01/2018.
 */

fun humanReadableByteCount(bytes: Long, si: Boolean): String {
    val unit = if (si) 1000 else 1024
    if (bytes < unit) return bytes.toString() + " B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(unit.toDouble())).toInt()
    val pre = (if (si) "kMGTPE" else "KMGTPE")[exp - 1] + if (si) "" else "i"
    return String.format("%.1f %sB", bytes / Math.pow(unit.toDouble(), exp.toDouble()), pre)
}

fun Context.toast(msg: String, isShort: Boolean = true) {
    Toast.makeText(this, msg, if (isShort) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
}

// Bitmap#allocationBytecount does not exist before 4.2
val Bitmap.allocationByteCountSupport : Int
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        this.allocationByteCount
    } else {
        this.byteCount
    }

