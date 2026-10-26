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
        fun create(context: Context): DriveLogDatabase =
            Room.databaseBuilder(context, DriveLogDatabase::class.java, "drivelog.db").build()
    }
}
