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
            // 같은 이름의 파일이 있으면 안드로이드가 "DriveLog_2026-10 (1).csv"처럼 바꿔 저장한다 → 실제 이름으로 안내
            val savedName = resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: fileName
            "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER/$savedName"
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
