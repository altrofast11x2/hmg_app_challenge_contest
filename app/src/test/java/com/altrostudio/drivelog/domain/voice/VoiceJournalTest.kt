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
        val audios = mutableListOf<ByteArray?>()

        override suspend fun listen(maxMs: Long, onPartial: (String) -> Unit, audio: ByteArray?): Hearing {
            calls++
            audios += audio
            gate?.await()
            val next = hearings.removeFirstOrNull() ?: Hearing.Silence
            if (next is Hearing.Heard) onPartial(next.text)
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

    private fun hear(vararg texts: String) {
        texts.forEach { listener.hearings += Hearing.Heard(it) }
    }

    @Test
    fun `들은 내용을 되읽고 맞다고 하면 기록한다`() = runTest {
        val journal = journal()
        hear("업무, 거래처 납품 미팅", "맞아")

        journal.onParkedAnnounced()
        advanceUntilIdle()

        assertEquals(listOf(VoicePhrases.confirm(TripPurpose.BUSINESS, "거래처 납품 미팅")), spoken)
        assertEquals(listOf(TripPurpose.BUSINESS to "거래처 납품 미팅"), recorded)
        assertEquals(VoiceInputState.Idle, journal.state.value)
    }

    @Test
    fun `다음이나 네라고 해도 넘어간다`() = runTest {
        val journal = journal()
        hear("출근", "다음")
        journal.onParkedAnnounced()
        advanceUntilIdle()
        assertEquals(listOf(TripPurpose.COMMUTE to null), recorded)

        tripState.value = TripState.Parked("trip-2", 1L)
        hear("비업무", "네")
        journal.listenNow()
        advanceUntilIdle()
        assertEquals(TripPurpose.PERSONAL to null, recorded.last())
    }

    @Test
    fun `확인 대답이 없으면 기록하지 않고 확인 화면을 남겨 버튼으로 기록한다`() = runTest {
        val journal = journal()
        hear("업무 거래처 미팅")

        journal.onParkedAnnounced()
        advanceUntilIdle()

        assertTrue(recorded.isEmpty())
        assertEquals(VoiceInputState.Confirming(TripPurpose.BUSINESS, "거래처 미팅"), journal.state.value)

        assertTrue(journal.confirm())
        advanceUntilIdle()
        assertEquals(listOf(TripPurpose.BUSINESS to "거래처 미팅"), recorded)
        assertEquals(VoiceInputState.Idle, journal.state.value)
    }

    @Test
    fun `확인 화면에서 다시 말하기를 누르면 대답으로 듣는다`() = runTest {
        val journal = journal()
        hear("업무 거래처 미팅")
        journal.onParkedAnnounced()
        advanceUntilIdle()
        spoken.clear()

        hear("업무 그렇죠")
        journal.listenNow()
        advanceUntilIdle()

        assertTrue(spoken.isEmpty())
        assertEquals(listOf(TripPurpose.BUSINESS to "거래처 미팅"), recorded)
    }

    @Test
    fun `확인 화면에서 버튼을 누르고 다른 내용을 말하면 그 내용으로 다시 확인한다`() = runTest {
        val journal = journal()
        hear("업무 거래처 미팅")
        journal.onParkedAnnounced()
        advanceUntilIdle()

        hear("업무 본사 회의", "맞아")
        journal.listenNow()
        advanceUntilIdle()

        assertEquals(VoicePhrases.confirm(TripPurpose.BUSINESS, "본사 회의"), spoken.last())
        assertEquals(listOf(TripPurpose.BUSINESS to "본사 회의"), recorded)
    }

    @Test
    fun `확인 중 용도 버튼을 누르면 그 용도로 바꾸고 업무 내용은 살린다`() = runTest {
        val journal = journal()
        hear("업무 거래처 미팅")
        journal.onParkedAnnounced()
        advanceUntilIdle()

        assertTrue(journal.confirm(TripPurpose.COMMUTE))
        advanceUntilIdle()

        assertEquals(listOf(TripPurpose.COMMUTE to "거래처 미팅"), recorded)
    }

    @Test
    fun `확인 중 비업무를 누르면 업무 내용은 버린다`() = runTest {
        val journal = journal()
        hear("업무 거래처 미팅")
        journal.onParkedAnnounced()
        advanceUntilIdle()

        journal.confirm(TripPurpose.PERSONAL)
        advanceUntilIdle()

        assertEquals(listOf(TripPurpose.PERSONAL to null), recorded)
    }

    @Test
    fun `확인 중이 아니면 버튼은 평소대로 분류하게 둔다`() = runTest {
        val journal = journal()
        assertFalse(journal.confirm(TripPurpose.BUSINESS))
        assertTrue(recorded.isEmpty())
    }

    @Test
    fun `아니라고 하면 다시 말해 달라고 하고 새로 듣는다`() = runTest {
        val journal = journal()
        hear("업무 거래처 미팅", "아니", "출근", "맞아")

        journal.onParkedAnnounced()
        advanceUntilIdle()

        assertEquals(
            listOf(
                VoicePhrases.confirm(TripPurpose.BUSINESS, "거래처 미팅"),
                VoicePhrases.SAY_AGAIN,
                VoicePhrases.confirm(TripPurpose.COMMUTE, null)
            ),
            spoken
        )
        assertEquals(listOf(TripPurpose.COMMUTE to null), recorded)
    }

    @Test
    fun `확인 중에 고쳐 말하면 그 내용으로 다시 확인한다`() = runTest {
        val journal = journal()
        hear("출근", "아니 업무 본사 회의", "네")

        journal.onParkedAnnounced()
        advanceUntilIdle()

        assertEquals(VoicePhrases.confirm(TripPurpose.BUSINESS, "본사 회의"), spoken.last())
        assertEquals(listOf(TripPurpose.BUSINESS to "본사 회의"), recorded)
    }

    @Test
    fun `확인 대답을 알아듣지 못하면 한 번 더 묻는다`() = runTest {
        val journal = journal()
        hear("출근", "음", "맞아요")

        journal.onParkedAnnounced()
        advanceUntilIdle()

        assertEquals(listOf(VoicePhrases.confirm(TripPurpose.COMMUTE, null), VoicePhrases.CONFIRM_AGAIN), spoken)
        assertEquals(listOf(TripPurpose.COMMUTE to null), recorded)
    }

    @Test
    fun `확인 중 취소라고 하면 기록하지 않고 확인 화면을 남긴다`() = runTest {
        val journal = journal()
        hear("출근", "취소")

        journal.onParkedAnnounced()
        advanceUntilIdle()

        assertTrue(recorded.isEmpty())
        assertEquals(VoiceInputState.Confirming(TripPurpose.COMMUTE, null), journal.state.value)
    }

    @Test
    fun `일반업무만 들리면 어떤 업무였는지 묻고 확인한다`() = runTest {
        val journal = journal()
        hear("업무", "거래처 납품 미팅이요", "맞아")

        journal.onParkedAnnounced()
        advanceUntilIdle()

        assertEquals(listOf(VoicePhrases.ASK_NOTE, VoicePhrases.confirm(TripPurpose.BUSINESS, "거래처 납품 미팅")), spoken)
        assertEquals(listOf(TripPurpose.BUSINESS to "거래처 납품 미팅"), recorded)
    }

    @Test
    fun `업무 내용을 물었는데 대답이 없으면 내용 없이 확인한다`() = runTest {
        val journal = journal()
        listener.hearings += Hearing.Heard("일반 업무")
        listener.hearings += Hearing.Silence
        listener.hearings += Hearing.Heard("맞아")

        journal.onParkedAnnounced()
        advanceUntilIdle()

        assertEquals(listOf(TripPurpose.BUSINESS to null), recorded)
    }

    @Test
    fun `출퇴근은 업무 내용을 묻지 않는다`() = runTest {
        val journal = journal()
        hear("퇴근", "맞아")

        journal.onParkedAnnounced()
        advanceUntilIdle()

        assertEquals(listOf(VoicePhrases.confirm(TripPurpose.COMMUTE, null)), spoken)
        assertEquals(2, listener.calls)
        assertEquals(listOf(TripPurpose.COMMUTE to null), recorded)
    }

    @Test
    fun `알아듣지 못하면 다시 묻는다`() = runTest {
        val journal = journal()
        hear("음 글쎄", "출근", "맞아")

        journal.onParkedAnnounced()
        advanceUntilIdle()

        assertEquals(listOf(VoicePhrases.ASK_AGAIN, VoicePhrases.confirm(TripPurpose.COMMUTE, null)), spoken)
        assertEquals(listOf(TripPurpose.COMMUTE to null), recorded)
    }

    @Test
    fun `여러 번 알아듣지 못하면 화면에서 고르게 한다`() = runTest {
        val journal = journal()
        hear("음 글쎄", "모르겠어", "몰라요")

        journal.onParkedAnnounced()
        advanceUntilIdle()

        assertEquals(listOf(VoicePhrases.ASK_AGAIN, VoicePhrases.ASK_AGAIN, VoicePhrases.PICK_ON_SCREEN), spoken)
        assertTrue(recorded.isEmpty())
        assertEquals(VoiceInputState.NotUnderstood("몰라요"), journal.state.value)
        assertEquals(3, listener.calls)
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
    fun `음성 인식을 아예 쓸 수 없으면 다음 정차부터 묻지 않고 버튼은 다시 시도한다`() = runTest {
        val journal = journal()
        listener.hearings += Hearing.Unavailable("PERMISSION_DENIED", permanent = true)

        journal.onParkedAnnounced()
        advanceUntilIdle()
        assertFalse(journal.asksAfterParking)

        hear("비업무", "맞아")
        journal.listenNow()
        advanceUntilIdle()

        assertEquals(listOf(TripPurpose.PERSONAL to null), recorded)
        assertTrue(journal.asksAfterParking)
    }

    @Test
    fun `듣는 중에 화면에서 고르면 듣기를 멈춘다`() = runTest {
        val journal = journal()
        listener.gate = CompletableDeferred()
        hear("업무")

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
    fun `지난번 용도를 물었을 때 네라고 하면 그 용도로 확인한다`() = runTest {
        suggested = TripPurpose.BUSINESS
        val journal = journal()
        hear("네 판교 협력사 미팅", "맞아")

        journal.onParkedAnnounced()
        advanceUntilIdle()

        assertEquals(listOf(TripPurpose.BUSINESS to "판교 협력사 미팅"), recorded)
    }

    @Test
    fun `처음에 취소라고 하면 기록하지 않는다`() = runTest {
        val journal = journal()
        hear("취소")

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

    @Test
    fun `시험 음성을 주면 차례로 넣고 다 쓰면 마이크를 열지 않는다`() = runTest {
        val journal = journal()
        val first = byteArrayOf(1)
        val second = byteArrayOf(2)
        hear("출근", "맞아")

        journal.listenNow(listOf(first, second))
        advanceUntilIdle()

        assertEquals(2, listener.calls)
        assertTrue(listener.audios[0] === first)
        assertTrue(listener.audios[1] === second)
        assertEquals(listOf(TripPurpose.COMMUTE to null), recorded)
    }

    private companion object {
        const val TRIP = "trip-1"
    }
}
