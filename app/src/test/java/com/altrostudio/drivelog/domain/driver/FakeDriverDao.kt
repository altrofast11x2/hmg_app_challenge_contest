package com.altrostudio.drivelog.domain.driver

import com.altrostudio.drivelog.data.driver.DriverDao
import com.altrostudio.drivelog.data.driver.DriverEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** 메모리 운전자 명단. 명단 바꾸기([DriverDao.replaceRoster])는 실제 DAO의 기본 구현을 그대로 쓴다 */
class FakeDriverDao(initial: List<DriverEntity> = emptyList()) : DriverDao {

    val rows = MutableStateFlow(initial.associateBy { it.id })

    override fun observeDrivers(): Flow<List<DriverEntity>> = rows.map { it.values.sortedWith(compareBy({ d -> d.sortOrder }, { d -> d.name })) }

    override suspend fun drivers(): List<DriverEntity> = rows.value.values.sortedWith(compareBy({ it.sortOrder }, { it.name }))

    override suspend fun driver(id: String): DriverEntity? = rows.value[id]

    override suspend fun upsert(drivers: List<DriverEntity>) {
        rows.value = rows.value + drivers.associateBy { it.id }
    }

    override suspend fun deleteOthers(keep: List<String>) {
        rows.value = rows.value.filterKeys { it in keep }
    }

    override suspend fun deleteAll() {
        rows.value = emptyMap()
    }

    override suspend fun updateComfort(id: String, tempC: Float?, seatHeat: Int?, ambientColor: Int?, at: Long) {
        val row = rows.value[id] ?: return
        rows.value = rows.value + (
            id to row.copy(
                comfortTempC = tempC,
                comfortSeatHeat = seatHeat,
                comfortAmbientColor = ambientColor,
                comfortDirty = true,
                updatedAt = at
            )
            )
    }

    override suspend fun dirtyComforts(): List<DriverEntity> = rows.value.values.filter { it.comfortDirty }

    override suspend fun markComfortSynced(id: String, updatedAt: Long) {
        val row = rows.value[id] ?: return
        if (row.updatedAt == updatedAt) rows.value = rows.value + (id to row.copy(comfortDirty = false))
    }

    companion object {
        fun driver(
            id: String,
            name: String,
            department: String? = null,
            tempC: Float? = null,
            seatHeat: Int? = null,
            ambient: Int? = null,
            order: Int = 0,
            assigned: Boolean = false
        ) = DriverEntity(id, name, department, tempC, seatHeat, ambient, assigned, isDefault = false, sortOrder = order, updatedAt = 0)
    }
}
