package com.altrostudio.drivelog.data.driver

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.altrostudio.drivelog.data.trip.DriverRef
import com.altrostudio.drivelog.data.vehicle.Comfort
import kotlinx.coroutines.flow.Flow

/**
 * 이 차에서 고를 수 있는 운전자 (조직 서버 명단의 사본). 이름·부서가 있어 암호화된 운행 DB에 둔다.
 * 맞춤 설정(온도·시트 열선·무드등 색)은 운전자가 마지막으로 쓴 값이고, 차에서 바뀌면 [comfortDirty]로 표시해 서버에 올린다
 */
@Entity(tableName = "drivers")
data class DriverEntity(
    @PrimaryKey val id: String,
    val name: String,
    val department: String?,
    val comfortTempC: Float?,
    val comfortSeatHeat: Int?,
    /** 0xRRGGBB */
    val comfortAmbientColor: Int?,
    /** 관리자가 이 차를 쓰도록 배정한 직원 (명단 위쪽에 둔다) */
    val assigned: Boolean,
    /** 연결할 때 만든 차량의 기본 운전자 */
    val isDefault: Boolean,
    /** 서버 명단 순서 (배정, 최근 운전 순) */
    val sortOrder: Int,
    val comfortDirty: Boolean = false,
    val updatedAt: Long
) {
    val comfort: Comfort get() = Comfort(comfortTempC, comfortSeatHeat, comfortAmbientColor)

    val ref: DriverRef get() = DriverRef(id, name, department)
}

@Dao
interface DriverDao {

    @Query("SELECT * FROM drivers ORDER BY sortOrder, name")
    fun observeDrivers(): Flow<List<DriverEntity>>

    @Query("SELECT * FROM drivers ORDER BY sortOrder, name")
    suspend fun drivers(): List<DriverEntity>

    @Query("SELECT * FROM drivers WHERE id = :id")
    suspend fun driver(id: String): DriverEntity?

    @Upsert
    suspend fun upsert(drivers: List<DriverEntity>)

    @Query("DELETE FROM drivers WHERE id NOT IN (:keep)")
    suspend fun deleteOthers(keep: List<String>)

    @Query("DELETE FROM drivers")
    suspend fun deleteAll()

    @Query(
        "UPDATE drivers SET comfortTempC = :tempC, comfortSeatHeat = :seatHeat, comfortAmbientColor = :ambientColor, " +
            "comfortDirty = 1, updatedAt = :at WHERE id = :id"
    )
    suspend fun updateComfort(id: String, tempC: Float?, seatHeat: Int?, ambientColor: Int?, at: Long)

    @Query("SELECT * FROM drivers WHERE comfortDirty = 1")
    suspend fun dirtyComforts(): List<DriverEntity>

    /** 올리는 동안 다시 바뀌지 않았을 때만(updatedAt이 같을 때만) 올린 것으로 표시한다 */
    @Query("UPDATE drivers SET comfortDirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markComfortSynced(id: String, updatedAt: Long)

    /**
     * 서버 명단으로 바꾼다. 차에서 바뀌어 아직 올리지 못한 맞춤 설정은 서버 값으로 덮지 않는다.
     * 명단에서 빠진 운전자(내보낸 직원, 숨긴 운전자)는 지운다
     */
    @Transaction
    suspend fun replaceRoster(roster: List<DriverEntity>) {
        val local = drivers().associateBy { it.id }
        upsert(
            roster.map { remote ->
                val mine = local[remote.id]
                if (mine != null && mine.comfortDirty) {
                    remote.copy(
                        comfortTempC = mine.comfortTempC,
                        comfortSeatHeat = mine.comfortSeatHeat,
                        comfortAmbientColor = mine.comfortAmbientColor,
                        comfortDirty = true,
                        updatedAt = mine.updatedAt
                    )
                } else {
                    remote
                }
            }
        )
        if (roster.isEmpty()) deleteAll() else deleteOthers(roster.map { it.id })
    }
}
