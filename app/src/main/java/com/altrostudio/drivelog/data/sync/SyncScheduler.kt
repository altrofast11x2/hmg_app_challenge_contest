package com.altrostudio.drivelog.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.altrostudio.drivelog.DriveLogApp
import com.altrostudio.drivelog.data.trip.TripRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.TimeUnit

/**
 * 올릴 기록이 생기면 인터넷이 연결됐을 때 동기화한다. 실패하면 점점 간격을 늘려 다시 시도하고,
 * 놓친 것이 없도록 15분마다 한 번씩도 확인한다. (WorkManager는 GMS 없이 동작한다)
 */
class SyncScheduler(
    private val context: Context,
    private val trips: TripRepository,
    private val pairing: StateFlow<Pairing?>,
    private val scope: CoroutineScope
) {
    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun start() {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(PERIODIC_MINUTES, TimeUnit.MINUTES).setConstraints(constraints).build()
        )
        combine(pairing, trips.observePendingCount()) { connected, pending -> if (connected != null) pending else 0 }
            .distinctUntilChanged()
            .filter { it > 0 }
            .onEach { requestSync() }
            .launchIn(scope)
    }

    fun requestSync() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_TIME_WORK,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
        )
    }

    private companion object {
        const val ONE_TIME_WORK = "trip-sync"
        const val PERIODIC_WORK = "trip-sync-periodic"
        const val PERIODIC_MINUTES = 15L
        const val BACKOFF_SECONDS = 30L
    }
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        when ((applicationContext as DriveLogApp).container.sync.sync()) {
            SyncOutcome.Offline, is SyncOutcome.Failed -> Result.retry()
            else -> Result.success()
        }
}
