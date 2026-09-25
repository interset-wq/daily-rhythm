package com.intersetwq.dailyrhythm

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** 调起系统相机拍摄药盒照片，存到私有 cache/photos/ 目录。 */
object PhotoTaker {

    const val REQ_CODE = 1001

    fun photosDir(ctx: Context): File =
        File(ctx.cacheDir, "photos").apply { mkdirs() }

    /**
     * 发起拍照。[idSeed] 用于生成唯一文件名（编辑中的提醒 id 或临时 id）。
     * 结果通过 EditReminderActivity.onActivityResult 返回文件名。
     */
    fun take(activity: EditReminderActivity, idSeed: Long) {
        val name = "pill_${idSeed}_${System.currentTimeMillis()}.jpg"
        val file = File(photosDir(activity), name)
        val uri = FileProvider.getUriForFile(
            activity, "${activity.packageName}.fileprovider", file
        )
        val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(android.provider.MediaStore.EXTRA_OUTPUT, uri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.pendingPhotoName = name
        if (intent.resolveActivity(activity.packageManager) != null) {
            activity.startActivityForResult(intent, REQ_CODE)
        } else {
            android.widget.Toast.makeText(activity, "未找到相机应用", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
