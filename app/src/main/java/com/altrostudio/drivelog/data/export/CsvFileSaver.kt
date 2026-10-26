package com.altrostudio.drivelog.data.export

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** CSV를 기기의 다운로드 폴더(Download/DriveLog)에 저장한다. 저장소 권한이 필요 없는 방법만 쓴다 */
class CsvFileSaver(private val context: Context) {

    /** 저장하고 사용자에게 보여 줄 경로를 돌려준다 */
    suspend fun save(fileName: String, content: String): String = withContext(Dispatchers.IO) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
            }
            val resolver = context.contentResolver
            val uri = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)) { "파일을 만들 수 없습니다" }
            checkNotNull(resolver.openOutputStream(uri)) { "파일을 열 수 없습니다" }.use { it.write(bytes) }
            "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER/$fileName"
        } else {
            // Android 9는 앱 전용 폴더에 저장한다 (권한 불필요)
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), FOLDER).apply { mkdirs() }
            File(dir, fileName).apply { writeBytes(bytes) }.absolutePath
        }
    }

    private companion object {
        const val FOLDER = "DriveLog"
    }
}
