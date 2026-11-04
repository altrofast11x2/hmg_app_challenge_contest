package com.altrostudio.drivelog.domain.voice

import com.altrostudio.drivelog.data.trip.TripPurpose
import com.altrostudio.drivelog.domain.trip.TripState
import com.altrostudio.drivelog.domain.trip.VoiceRecord
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceJournalTest {

    private class FakeListener : Listener {
        val hearings = ArrayDeque<Hearing>()
        var calls = 0
        var cancels = 0
        var gate: CompletableDeferred<Unit>? = null
        val partials = mutableListOf<String>()
        val audios = mutableListOf<ByteArray?>()

        override suspend fun listen(maxMs: Long, onPartial: (String) -> Unit, audio: ByteArray?): Hearing {
            calls++
            audios += audio
            gate?.await()
            val next = hearings.removeFirstOrNull() ?: Hearing.Silence
            if (next is Hearing.Heard) {
                onPartial(next.text)
                partials += next.text
            }
            return next
        }

        override fun cancel() {
            cancels++
        }
    }

    private class MemorySettings : VoiceJournalSettings {
        override var voiceJournal = true
    }

    private val listener = FakeListener()
    private val settings = MemorySettings()
    private val tripState = MutableStateFlow<TripState>(TripState.Parked(TRIP, 0L))
    private val spoken = mutableListOf<String>()
    private val recorded = mutableListOf<Pair<TripPurpose, String?>>()
    private var guidance = true
    private var suggested: TripPurpose? = null
    private var stops = 0

    private fun TestScope.journal() = VoiceJournal(
        listener = listener,
        tripState = tripState,
        settings = settings,
        guidanceOn = { guidance },
        say = { spoken += it },
        awaitQuiet = {},
        stopSpeaking = { stops++ },
        suggestion = { suggested },
        record = { purpose, note ->
            recorded += purpose to note
            tripState.value = TripState.Idle
            VoiceRecord.Completed(TRIP)
        },
        scope = this
    )

    @Test
    fun `정차 안내 뒤에 듣고 용도와 업무 내용을 기록한다`() = runTest {
        val journal = journal()
        listener.hearings += Hearing.Heard("업무, 거래처 납품 미팅")

        journal.onParkedAnnounced()
        advanceUntilIdle()

        assertEquals(listOf(TripPurpose.BUSINESS to "거래처 납품 미팅"), recorded)
        assertEquals(VoiceInputState.Idle, journal.state.value)
        assertTrue(spoken.isEmpty())
    }

    @Test
    fun `일반업무만 들리면 어떤 업무였는지 한 번 더 묻는다`() = runTest {
        val journal = journal()
        listener.hearings += Hearing.Heard("업무")
        listener.hearings += Hearing.Heard("거래처 납품 미팅이요")

        journal.onParkedAnnounced()
        advanceUntilIdle()

        assertEquals(listOf(VoicePhrases.ASK_NOTE), spoken)
        assertEquals(listOf(TripPurpose.BUSINESS to "거래처 납품 미팅"), recorded)
    }

    @Test
    fun `업무 내용을 물었는데 대답이 없거나 없다고 하면 내용 없이 기록한다`() = runTest {
        val journal = journal()
        listener.hearings += Hearing.Heard("일반 업무")
        listener.hearings += Hearing.Silence

        journal.onParkedAnnounced()
        advanceUntilIdle()
        assertEquals(listOf(TripPurpose.BUSINESS to null), recorded)

        tripState.value = TripState.Parked("trip-2", 1L)
        listener.hearings += Hearing.Heard("업무")
        listener.hearings += Hearing.Heard("없어")
        journal.listenNow()
        advanceUntilIdle()
        assertEquals(TripPurpose.BUSINESS to null, recorded.last())
    }

    @Test
    fun `출퇴근과 비업무는 업무 내용을 묻지 않는다`() = runTest {
        val journal = journal()
        listener.hearings += Hearing.Heard("퇴근")

        journal.onParkedAnnounced()
        advanceUntilIdle()

        assertTrue(spoken.isEmpty())
        assertEquals(1, listener.calls)
        assertEquals(listOf(TripPurpose.COMMUTE to null), recorded)
    }

    @Test
    fun `시험 음성을 주면 차례로 넣고 다 쓰면 마이크를 열지 않는다`() = runTest {
        val journal = journal()
        val first = byteArrayOf(1)
        listener.hearings += Hearing.Heard("업무")

        journal.listenNow(listOf(first))
        advanceUntilIdle()

        assertEquals(1, listener.calls)
        assertTrue(listener.audios.single() === first)
        assertEquals(listOf(TripPurpose.BUSINESS to null), recorded)
    }

    @Test
    fun `음성 안내를 끄면 자동으로 묻지 않는다`() = runTest {
        guidance = false
        val journal = journal()

        assertFalse(journal.asksAfterParking)
        journal.onParkedAnnounced()
        advanceUntilIdle()

        assertEquals(0, listener.calls)
    }

    @Test
    fun `설정에서 끄면 자동으로 묻지 않는다`() = runTest {
        val journal = journal()
        journal.setEnabled(false)

        journal.onParkedAnnounced()
        advanceUntilIdle()

        assertEquals(0, listener.calls)
        assertFalse(settings.voiceJournal)
    }

    @Test
    fun `알아듣지 못하면 한 번 다시 묻는다`() = runTest {
        val journal = journal()
        listener.hearings += Hearing.Heard("음 글쎄")
        listener.hearings += Hearing.Heard("출근")

        journal.onParkedAnnounced()
        advanceUntilIdle()

        assertEquals(listOf(VoicePhrases.ASK_AGAIN), spoken)
        assertEquals(listOf(TripPurpose.COMMUTE to null), recorded)
    }

    @Test
    fun `두 번 알아듣지 못하면 화면에서 고르게 한다`() = runTest {
        val journal = journal()
        listener.hearings += Hearing.Heard("음 글쎄")
        listener.hearings += Hearing.Heard("모르겠어")

        journal.onParkedAnnounced()
        advanceUntilIdle()

        assertEquals(listOf(VoicePhrases.ASK_AGAIN, VoicePhrases.PICK_ON_SCREEN), spoken)
        assertTrue(recorded.isEmpty())
        assertEquals(VoiceInputState.NotUnderstood("모르겠어"), journal.state.value)
        assertEquals(2, listener.calls)
    }

    @Test
    fun `자동으로 물었는데 말이 없으면 조용히 끝낸다`() = runTest {
        val journal = journal()

        journal.onParkedAnnounced()
        advanceUntilIdle()

        assertEquals(VoiceInputState.Idle, journal.state.value)
        assertTrue(spoken.isEmpty())
    }

    @Test
    fun `버튼을 눌렀는데 말이 없으면 못 들었다고 보여 준다`() = runTest {
        val journal = journal()

        journal.listenNow()
        advanceUntilIdle()

        assertEquals(VoiceInputState.NoSpeech, journal.state.value)
        assertEquals(1, stops)
    }

    @Test
    fun `음성 인식을 아예 쓸 수 없으면 다음 정차부터 묻지 않고 버튼은 다시 시도한다`() = runTest {
        val journal = journal()
        listener.hearings += Hearing.Unavailable("PERMISSION_DENIED", permanent = true)

        journal.onParkedAnnounced()
        advanceUntilIdle()
        assertFalse(journal.asksAfterParking)

        listener.hearings += Hearing.Heard("비업무")
        journal.listenNow()
        advanceUntilIdle()

        assertEquals(listOf(TripPurpose.PERSONAL to null), recorded)
        assertTrue(journal.asksAfterParking)
    }

    @Test
    fun `듣는 중에 화면에서 고르면 듣기를 멈춘다`() = runTest {
        val journal = journal()
        listener.gate = CompletableDeferred()
        listener.hearings += Hearing.Heard("업무")

        journal.onParkedAnnounced()
        advanceUntilIdle()
        assertEquals(VoiceInputState.Listening(""), journal.state.value)

        tripState.value = TripState.Idle
        advanceUntilIdle()

        assertTrue(listener.cancels > 0)
        assertTrue(recorded.isEmpty())
        assertEquals(VoiceInputState.Idle, journal.state.value)
    }

    @Test
    fun `지난번 용도를 물었을 때 네라고 하면 그 용도로 기록한다`() = runTest {
        suggested = TripPurpose.BUSINESS
        val journal = journal()
        listener.hearings += Hearing.Heard("네 판교 협력사 미팅")

        journal.onParkedAnnounced()
        advanceUntilIdle()

        assertEquals(listOf(TripPurpose.BUSINESS to "판교 협력사 미팅"), recorded)
    }

    @Test
    fun `취소라고 하면 기록하지 않는다`() = runTest {
        val journal = journal()
        listener.hearings += Hearing.Heard("취소")

        journal.onParkedAnnounced()
        advanceUntilIdle()

        assertTrue(recorded.isEmpty())
        assertTrue(spoken.isEmpty())
        assertEquals(VoiceInputState.Idle, journal.state.value)
    }

    @Test
    fun `정차 중이 아니면 듣지 않는다`() = runTest {
        tripState.value = TripState.Driving(TRIP)
        val journal = journal()

        journal.listenNow()
        advanceUntilIdle()

        assertEquals(0, listener.calls)
    }

    private companion object {
        const val TRIP = "trip-1"
    }
}
