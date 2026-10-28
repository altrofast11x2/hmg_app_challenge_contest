package com.altrostudio.drivelog.data

import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteNotADatabaseException
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File

/**
 * 운행 기록 DB를 SQLCipher(AES-256)로 암호화해 연다 (Pleos 앱 보안 가이드: SQLite에 저장하는 민감정보 암호화).
 * 키는 Keystore로 암호화해 설정에 둔다 (AppPreferences.databasePassphrase).
 * 키를 꺼내고 파일을 옮기는 느린 일은 Room이 처음 DB를 여는 백그라운드 스레드에서 한다 (화면을 만들 때가 아니라)
 */
internal class EncryptedOpenHelperFactory(
    private val databaseFile: File,
    private val passphrase: () -> String
) : SupportSQLiteOpenHelper.Factory {

    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper =
        LazyHelper(configuration)

    private inner class LazyHelper(private val configuration: SupportSQLiteOpenHelper.Configuration) : SupportSQLiteOpenHelper {

        private var delegate: SupportSQLiteOpenHelper? = null
        private var writeAheadLogging: Boolean? = null

        override val databaseName: String? get() = configuration.name

        @Synchronized
        override fun setWriteAheadLoggingEnabled(enabled: Boolean) {
            writeAheadLogging = enabled
            delegate?.setWriteAheadLoggingEnabled(enabled)
        }

        override val writableDatabase: SupportSQLiteDatabase get() = open { it.writableDatabase }

        override val readableDatabase: SupportSQLiteDatabase get() = open { it.readableDatabase }

        @Synchronized
        override fun close() {
            delegate?.close()
        }

        @Synchronized
        private fun open(get: (SupportSQLiteOpenHelper) -> SupportSQLiteDatabase): SupportSQLiteDatabase {
            delegate?.let { return get(it) }
            System.loadLibrary("sqlcipher")
            val key = passphrase()
            PlainDatabaseMigration.encryptIfPlain(databaseFile, key)
            return try {
                get(newDelegate(key))
            } catch (e: SQLiteNotADatabaseException) {
                // 키로 열 수 없는 파일(키를 잃었거나 망가짐): 차가 매번 멈추지 않도록 옆으로 치우고 새로 만든다.
                // 조직에 연결한 차량의 올린 기록은 서버에 남는다
                Log.e(TAG, "운행 기록 DB를 열 수 없어 새로 만듭니다", e)
                delegate?.close()
                PlainDatabaseMigration.quarantine(databaseFile)
                get(newDelegate(key))
            }
        }

        private fun newDelegate(key: String): SupportSQLiteOpenHelper =
            SupportOpenHelperFactory(key.toByteArray(Charsets.UTF_8)).create(configuration).also { helper ->
                writeAheadLogging?.let(helper::setWriteAheadLoggingEnabled)
                delegate = helper
            }
    }

    private companion object {
        const val TAG = "DriveLog/Database"
    }
}

/** 암호화 전 버전(0.1.0)이 만든 평문 DB를 암호화본으로 옮긴다. 중간에 앱이 죽어도 다음 실행 때 이어서 한다 */
internal object PlainDatabaseMigration {

    private const val TAG = "DriveLog/Database"
    private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    private val SIDECARS = listOf("-wal", "-shm", "-journal")

    fun encryptIfPlain(file: File, passphrase: String) {
        val plainBackup = File(file.path + "-plain")
        // 평문을 치운 뒤 암호화본을 놓기 전에 멈췄으면 평문부터 되돌린다
        if (!file.exists() && plainBackup.exists()) plainBackup.renameTo(file)
        if (!file.exists() || !isPlain(file)) {
            deleteWithSidecars(plainBackup)
            return
        }
        val encrypted = File(file.path + "-encrypting")
        deleteWithSidecars(encrypted)
        // ATTACH는 원본을 연 플래그로 새 파일을 만들므로 CREATE_IF_NECESSARY가 있어야 한다
        val db = SQLiteDatabase.openDatabase(
            file.path, "", null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY, null
        )
        try {
            // ATTACH는 연결마다 따로라서, Room이 켜 둔 WAL을 끄고 연결 하나로만 옮긴다
            // (WAL이면 SELECT가 다른 읽기 연결에서 돌아 "invalid target database"가 났다, 2026-10-28)
            db.disableWriteAheadLogging()
            // 키는 SQL 문장에 넣지 않고 바인딩한다 (실패하면 SQLite가 문장을 로그에 남긴다)
            db.execSQL("ATTACH DATABASE ? AS encrypted KEY ?", arrayOf<Any>(encrypted.path, passphrase))
            db.rawExecSQL("SELECT sqlcipher_export('encrypted')")
            db.execSQL("PRAGMA encrypted.user_version = ${db.version}")
            db.execSQL("DETACH DATABASE encrypted")
        } finally {
            db.close()
        }
        SIDECARS.forEach { File(file.path + it).delete() }
        check(file.renameTo(plainBackup) && encrypted.renameTo(file)) { "암호화한 DB로 바꾸지 못함" }
        deleteWithSidecars(plainBackup)
        Log.i(TAG, "운행 기록 DB를 암호화했습니다")
    }

    /** 열 수 없는 DB를 지우지 않고 이름을 바꿔 둔다 */
    fun quarantine(file: File) {
        val aside = File(file.path + "-unreadable-" + System.currentTimeMillis())
        file.renameTo(aside)
        SIDECARS.forEach { File(file.path + it).delete() }
    }

    private fun isPlain(file: File): Boolean {
        val header = ByteArray(SQLITE_HEADER.size)
        val read = file.inputStream().use { it.read(header) }
        return read == header.size && header.contentEquals(SQLITE_HEADER)
    }

    private fun deleteWithSidecars(file: File) {
        file.delete()
        SIDECARS.forEach { File(file.path + it).delete() }
    }
}
