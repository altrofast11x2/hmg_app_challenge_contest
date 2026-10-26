package com.altrostudio.drivelog

import android.content.Context
import com.altrostudio.drivelog.data.DriveLogDatabase
import com.altrostudio.drivelog.data.export.CsvFileSaver
import com.altrostudio.drivelog.domain.consent.ConsentManager
import com.altrostudio.drivelog.data.navi.NaviDataSource
import com.altrostudio.drivelog.data.navi.PleosNaviDataSource
import com.altrostudio.drivelog.data.settings.AppPreferences
import com.altrostudio.drivelog.data.settings.VehicleIdentity
import com.altrostudio.drivelog.data.trip.RoomTripRepository
import com.altrostudio.drivelog.data.trip.TripRepository
import com.altrostudio.drivelog.data.vehicle.PleosVehicleDataSource
import com.altrostudio.drivelog.data.vehicle.VehicleDataSource
import com.altrostudio.drivelog.domain.trip.TripRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** 앱 전체에서 하나씩만 쓰는 객체를 만든다 (수동 의존성 주입) */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val preferences = AppPreferences(appContext)

    val vehicle: VehicleDataSource by lazy { PleosVehicleDataSource(appContext) }

    val navi: NaviDataSource by lazy { PleosNaviDataSource(appContext) }

    private val database by lazy { DriveLogDatabase.create(appContext) }

    val trips: TripRepository by lazy { RoomTripRepository(database) }

    val vehicleIdentity by lazy { VehicleIdentity(preferences, vehicle) }

    val consent by lazy { ConsentManager(appContext, preferences, trips, recorder) }

    val csvSaver by lazy { CsvFileSaver(appContext) }

    val recorder by lazy {
        TripRecorder(
            vehicle = vehicle,
            navi = navi,
            trips = trips,
            vehicleId = vehicleIdentity::vehicleId,
            parkedTimeoutMs = { preferences.parkedTimeoutMs },
            scope = appScope
        )
    }
}
