package com.intersetwq.dailyrhythm

import android.graphics.BitmapFactory
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import java.io.File

/** 查看已拍的药盒照片（弹窗大图）。 */
object PhotoViewer {

    fun show(activity: androidx.appcompat.app.AppCompatActivity, photoName: String) {
        if (photoName.isBlank()) return
        val file = File(PhotoTaker.photosDir(activity), photoName)
        if (!file.exists()) {
            android.widget.Toast.makeText(activity, "照片不存在", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val iv = ImageView(activity)
        // 按需缩放解码，避免大图 OOM
        val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
        val bmp = BitmapFactory.decodeFile(file.absolutePath, opts)
        iv.setImageBitmap(bmp)
        AlertDialog.Builder(activity)
            .setTitle("药盒照片")
            .setView(iv)
            .setPositiveButton("关闭", null)
            .show()
    }
}
