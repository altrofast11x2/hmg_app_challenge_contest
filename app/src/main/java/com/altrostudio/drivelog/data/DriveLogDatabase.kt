package com.altrostudio.drivelog.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.altrostudio.drivelog.data.driver.DriverDao
import com.altrostudio.drivelog.data.driver.DriverEntity
import com.altrostudio.drivelog.data.trip.ChainHeadEntity
import com.altrostudio.drivelog.data.trip.TripChangeEntity
import com.altrostudio.drivelog.data.trip.TripDao
import com.altrostudio.drivelog.data.trip.TripEntity
import com.altrostudio.drivelog.data.trip.TripStopEntity

@Database(
    entities = [TripEntity::class, TripStopEntity::class, TripChangeEntity::class, ChainHeadEntity::class, DriverEntity::class],
    version = 2,
    exportSchema = true
)
abstract class DriveLogDatabase : RoomDatabase() {

    abstract fun tripDao(): TripDao

    abstract fun driverDao(): DriverDao

    companion object {
        private const val NAME = "drivelog.db"

        /**
         * 2 (0.3.0): 운행의 운전자 이름·부서(④사용자), 업무 내용, 주행 중 음성으로 정한 용도, 운전자 명단 표.
         * 기존 운행은 그대로 두고 열만 더한다 (해시 대상이 아니라 체인은 그대로다)
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `trips` ADD COLUMN `driverName` TEXT")
                db.execSQL("ALTER TABLE `trips` ADD COLUMN `driverDepartment` TEXT")
                db.execSQL("ALTER TABLE `trips` ADD COLUMN `workNote` TEXT")
                db.execSQL("ALTER TABLE `trips` ADD COLUMN `plannedPurpose` TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `drivers` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `department` TEXT, " +
                        "`comfortTempC` REAL, `comfortSeatHeat` INTEGER, `comfortAmbientColor` INTEGER, `assigned` INTEGER NOT NULL, " +
                        "`isDefault` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, `comfortDirty` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
            }
        }

        /** 운행 기록(주소·방문처 포함)은 [passphrase]로 암호화해 저장한다 ([EncryptedOpenHelperFactory]) */
        fun create(context: Context, passphrase: () -> String): DriveLogDatabase =
            Room.databaseBuilder(context, DriveLogDatabase::class.java, NAME)
                .openHelperFactory(EncryptedOpenHelperFactory(context.getDatabasePath(NAME), passphrase))
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
