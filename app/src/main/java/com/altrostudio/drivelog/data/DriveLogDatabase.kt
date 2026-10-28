package com.altrostudio.drivelog.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.altrostudio.drivelog.data.trip.ChainHeadEntity
import com.altrostudio.drivelog.data.trip.TripChangeEntity
import com.altrostudio.drivelog.data.trip.TripDao
import com.altrostudio.drivelog.data.trip.TripEntity
import com.altrostudio.drivelog.data.trip.TripStopEntity

@Database(
    entities = [TripEntity::class, TripStopEntity::class, TripChangeEntity::class, ChainHeadEntity::class],
    version = 1,
    exportSchema = true
)
abstract class DriveLogDatabase : RoomDatabase() {

    abstract fun tripDao(): TripDao

    companion object {
        private const val NAME = "drivelog.db"

        /** 운행 기록(주소·방문처 포함)은 [passphrase]로 암호화해 저장한다 ([EncryptedOpenHelperFactory]) */
        fun create(context: Context, passphrase: () -> String): DriveLogDatabase =
            Room.databaseBuilder(context, DriveLogDatabase::class.java, NAME)
                .openHelperFactory(EncryptedOpenHelperFactory(context.getDatabasePath(NAME), passphrase))
                .build()
    }
}
