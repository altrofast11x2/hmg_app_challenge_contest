package com.altrostudio.drivelog.fake

import com.altrostudio.drivelog.data.vehicle.EnergyEfficiency
import com.altrostudio.drivelog.data.vehicle.GearPosition
import com.altrostudio.drivelog.data.vehicle.VehicleDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** debug 빌드·단위 테스트 전용: 값을 직접 바꿔 가며 차량을 흉내 낸다 */
class FakeVehicleDataSource : VehicleDataSource {

    val odometer = MutableStateFlow<Float?>(0f)
    val gearPosition = MutableStateFlow(GearPosition.PARK)
    val parkingBrakeOn = MutableStateFlow<Boolean?>(false)
    val restricted = MutableStateFlow(false)
    var vin: String? = null
    var socPercent: Int? = 80
    var efficiencyValue: EnergyEfficiency? = null

    override suspend fun readOdometerKm(): Float? = odometer.value

    override suspend fun readVin(): String? = vin

    override suspend fun readEfficiency(): EnergyEfficiency? = efficiencyValue

    override suspend fun readBatterySocPercent(): Int? = socPercent

    override val odometerKm: Flow<Float?> = odometer

    override val gear: Flow<GearPosition> = gearPosition

    override val parkingBrake: Flow<Boolean?> = parkingBrakeOn

    override val drivingRestricted: Flow<Boolean> = restricted
}
