package com.altrostudio.drivelog.data.trip

sealed interface ChainVerification {
    data class Valid(val count: Int) : ChainVerification
    data class Broken(val atSeq: Long, val reason: String) : ChainVerification
}

/**
 * 완료된 운행(계기판 원본)과 분류·수정 이력을 차량별 해시 체인 하나로 잇고 검증한다.
 * 주소는 체인에 넣지 않는다. 비업무로 분류되면 지워야 하기 때문이다.
 */
object TripLedger {

    fun tripFields(trip: TripEntity, seq: Long): List<Pair<String, String?>> = listOf(
        "type" to "TRIP",
        "vehicleId" to trip.vehicleId,
        "seq" to seq.toString(),
        "tripId" to trip.id,
        "startedAt" to trip.startedAt.toString(),
        "endedAt" to trip.endedAt?.toString(),
        "startOdometerKm" to HashChain.km(trip.startOdometerKm),
        "endOdometerKm" to HashChain.km(trip.endOdometerKm),
        "odometerSource" to trip.odometerSource.name,
        "startSocPercent" to trip.startSocPercent?.toString(),
        "endSocPercent" to trip.endSocPercent?.toString()
    )

    fun changeFields(change: TripChangeEntity): List<Pair<String, String?>> = listOf(
        "type" to "CHANGE",
        "vehicleId" to change.vehicleId,
        "seq" to change.chainSeq.toString(),
        "changeId" to change.id,
        "tripId" to change.tripId,
        "field" to change.field.name,
        "oldValue" to change.chainValue(change.oldValue),
        "newValue" to change.chainValue(change.newValue),
        "actor" to change.actor,
        "reason" to change.reason,
        "createdAt" to change.createdAt.toString()
    )

    /** 완료된 운행을 체인 끝에 붙인다 */
    fun sealTrip(trip: TripEntity, head: ChainHeadEntity?): TripEntity {
        val seq = (head?.seq ?: 0) + 1
        val prevHash = head?.hash ?: HashChain.GENESIS
        return trip.copy(chainSeq = seq, prevHash = prevHash, hash = HashChain.hash(prevHash, tripFields(trip, seq)))
    }

    /** 분류·수정 이력 한 건을 체인 끝에 붙인다 */
    fun newChange(
        id: String,
        trip: TripEntity,
        field: ChangeField,
        oldValue: String?,
        newValue: String?,
        actor: String,
        at: Long,
        head: ChainHeadEntity?,
        reason: String? = null
    ): TripChangeEntity {
        val unsealed = TripChangeEntity(
            id = id,
            tripId = trip.id,
            vehicleId = trip.vehicleId,
            field = field,
            oldValue = oldValue,
            newValue = newValue,
            actor = actor,
            reason = reason,
            createdAt = at,
            chainSeq = (head?.seq ?: 0) + 1,
            prevHash = head?.hash ?: HashChain.GENESIS,
            hash = ""
        )
        return unsealed.copy(hash = HashChain.hash(unsealed.prevHash, changeFields(unsealed)))
    }

    /**
     * 운행을 마칠 때 운행 기록 바로 뒤에 함께 남기는 이력: 운전자(④사용자), 주행 중 음성으로 남긴 업무 내용.
     * 운행 기록(TRIP)의 해시 규칙은 바꾸지 않고 수정 이력으로 체인에 넣는다 (docs/integrity.md)
     */
    fun completionChanges(sealed: TripEntity, at: Long, newId: () -> String): List<TripChangeEntity> {
        var head = headOf(sealed)
        val actor = driverActor(sealed.driverId)
        val changes = mutableListOf<TripChangeEntity>()
        sealed.driverLabel()?.let { label ->
            newChange(newId(), sealed, ChangeField.DRIVER, null, label, actor, at, head).also {
                changes += it
                head = headOf(it)
            }
        }
        sealed.workNote?.let { note ->
            changes += newChange(newId(), sealed, ChangeField.WORK_NOTE, null, note, actor, at, head)
        }
        return changes
    }

    /** 수정 이력의 "바꾼 사람". 운전자를 모르면 차량의 기본 운전자 */
    fun driverActor(driverId: String?): String = "driver:" + (driverId ?: "default")

    /** 음성(Gleo AI)으로 바꾼 이력의 "바꾼 사람" */
    fun voiceActor(driverId: String?): String = "voice:" + (driverId ?: "default")

    fun headOf(trip: TripEntity) =
        ChainHeadEntity(trip.vehicleId, checkNotNull(trip.chainSeq), checkNotNull(trip.hash))

    fun headOf(change: TripChangeEntity) = ChainHeadEntity(change.vehicleId, change.chainSeq, change.hash)

    /** 위치 값의 원문을 지우고 해시값만 남긴다. 체인 검증 결과는 그대로이고, 서버 사본도 가리도록 다시 올린다 */
    fun TripChangeEntity.redacted(): TripChangeEntity =
        if (field.isLocation) {
            copy(
                oldValue = HashChain.commitment(oldValue),
                newValue = HashChain.commitment(newValue),
                syncState = SyncState.PENDING
            )
        } else {
            this
        }

    /**
     * 체인을 처음부터 다시 계산한다. 값이 바뀌었거나, 중간·끝 기록이 빠졌거나, 순서가 바뀌었거나,
     * 운행의 현재 분류가 마지막 분류 이력과 다르면 처음 끊긴 위치를 알려 준다.
     */
    fun verify(trips: List<TripEntity>, changes: List<TripChangeEntity>, head: ChainHeadEntity?): ChainVerification {
        val sealedTrips = trips.filter { it.chainSeq != null }
        val entries = sealedTrips.map { Entry(checkNotNull(it.chainSeq), it.prevHash.orEmpty(), it.hash.orEmpty(), tripFields(it, checkNotNull(it.chainSeq))) } +
            changes.map { Entry(it.chainSeq, it.prevHash, it.hash, changeFields(it)) }

        var expectedPrevHash = HashChain.GENESIS
        var expectedSeq = 1L
        for (entry in entries.sortedBy { it.seq }) {
            when {
                entry.seq != expectedSeq -> return ChainVerification.Broken(expectedSeq, "기록이 빠졌습니다")
                entry.prevHash != expectedPrevHash -> return ChainVerification.Broken(entry.seq, "이전 기록과 이어지지 않습니다")
                HashChain.hash(entry.prevHash, entry.fields) != entry.hash -> return ChainVerification.Broken(entry.seq, "값이 바뀌었습니다")
            }
            expectedPrevHash = entry.hash
            expectedSeq++
        }
        if (entries.isNotEmpty() && (head == null || head.seq != expectedSeq - 1 || head.hash != expectedPrevHash)) {
            return ChainVerification.Broken(expectedSeq, "마지막 기록이 빠졌습니다")
        }

        val lastPurposeByTrip = changes.filter { it.field == ChangeField.PURPOSE }
            .sortedBy { it.chainSeq }
            .associate { it.tripId to it.newValue }
        sealedTrips.firstOrNull { trip ->
            trip.purpose.name != (lastPurposeByTrip[trip.id] ?: TripPurpose.UNCLASSIFIED.name)
        }?.let { return ChainVerification.Broken(checkNotNull(it.chainSeq), "분류가 이력과 다릅니다") }

        return ChainVerification.Valid(entries.size)
    }

    private fun TripChangeEntity.chainValue(value: String?) = if (field.isLocation) HashChain.commitment(value) else value

    private class Entry(val seq: Long, val prevHash: String, val hash: String, val fields: List<Pair<String, String?>>)
}
