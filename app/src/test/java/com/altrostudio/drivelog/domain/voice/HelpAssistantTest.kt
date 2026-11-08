package com.altrostudio.drivelog.domain.voice

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HelpAssistantTest {

    private class FakeListener : Listener {
        val hearings = ArrayDeque<Hearing>()
        var calls = 0
        var cancels = 0

        override suspend fun listen(maxMs: Long, onPartial: (String) -> Unit, audio: ByteArray?): Hearing {
            calls++
            return hearings.removeFirstOrNull() ?: Hearing.Silence
        }

        override fun cancel() {
            cancels++
        }
    }

    private val listener = FakeListener()
    private val spoken = mutableListOf<String>()
    private var stops = 0

    private fun TestScope.assistant() = HelpAssistant(
        listener = listener,
        say = { spoken += it },
        awaitQuiet = {},
        stopSpeaking = { stops++ },
        answer = { topic -> "답:${topic.name}" },
        scope = this
    )

    @Test
    fun `물어보면 답을 읽고 그 화면으로 갈 일을 알려 준다`() = runTest {
        val help = assistant()
        listener.hearings += Hearing.Heard("차 상태 알려 줘")

        help.start()
        advanceUntilIdle()

        assertEquals(listOf(HelpAssistant.ASK, "답:CAR_STATUS"), spoken)
        assertEquals(HelpState.Answered(HelpTopic.CAR_STATUS, "차 상태 알려 줘"), help.state.value)
    }

    @Test
    fun `알아듣지 못하면 버튼에서 고르게 한다`() = runTest {
        val help = assistant()
        listener.hearings += Hearing.Heard("오늘 날씨 어때")

        help.start()
        advanceUntilIdle()

        assertEquals(HelpAssistant.NOT_UNDERSTOOD, spoken.last())
        assertEquals(HelpState.NotUnderstood("오늘 날씨 어때"), help.state.value)
    }

    @Test
    fun `말이 없거나 음성 인식을 쓸 수 없으면 버튼만 남긴다`() = runTest {
        val help = assistant()
        help.start()
        advanceUntilIdle()
        assertEquals(HelpState.NoSpeech, help.state.value)

        listener.hearings += Hearing.Unavailable("MIC_ERROR", permanent = false)
        help.start()
        advanceUntilIdle()
        assertEquals(HelpState.Unavailable, help.state.value)
    }

    @Test
    fun `버튼으로 고르면 듣기를 멈추고 바로 답한다`() = runTest {
        val help = assistant()

        help.choose(HelpTopic.SHARE)
        advanceUntilIdle()

        assertTrue(listener.cancels > 0)
        assertEquals(1, stops)
        assertEquals(listOf("답:SHARE"), spoken)
        assertEquals(HelpState.Answered(HelpTopic.SHARE, null), help.state.value)
        assertEquals(0, listener.calls)
    }

    @Test
    fun `닫으면 처음 상태로 돌아간다`() = runTest {
        val help = assistant()
        help.choose(HelpTopic.LOGBOOK)
        advanceUntilIdle()

        help.cancel()

        assertEquals(HelpState.Idle, help.state.value)
    }

    @Test
    fun `출발해서 닫을 때는 듣기만 멈추고 안내는 끊지 않는다 - 운행 시작 안내가 지워지지 않게`() = runTest {
        val help = assistant()
        help.start()
        val stopsBefore = stops

        help.cancel(stopSpeech = false)
        advanceUntilIdle()

        assertEquals(HelpState.Idle, help.state.value)
        assertEquals(stopsBefore, stops)
        assertTrue(listener.cancels > 0)
    }

    @Test
    fun `질문을 안내할 일로 바꾼다`() {
        val cases = mapOf(
            "차 상태 알려 줘" to HelpTopic.CAR_STATUS,
            "배터리 얼마나 남았어" to HelpTopic.CAR_STATUS,
            "창문 열려 있어?" to HelpTopic.CAR_STATUS,
            "이번 달 얼마나 탔어" to HelpTopic.LOGBOOK,
            "운행기록부 보여 줘" to HelpTopic.LOGBOOK,
            "휴대폰으로 어떻게 받아" to HelpTopic.SHARE,
            "QR 코드" to HelpTopic.SHARE,
            "운전자 바꿔 줘" to HelpTopic.DRIVER,
            "말로 기록 어떻게 해" to HelpTopic.VOICE_RECORD,
            "용도는 어떻게 골라" to HelpTopic.VOICE_RECORD,
            "사용법 알려 줘" to HelpTopic.TUTORIAL,
            "처음인데 어떻게 써" to HelpTopic.TUTORIAL,
            "음성 안내 꺼 줘" to HelpTopic.SETTINGS
        )
        for ((text, topic) in cases) assertEquals(text, topic, HelpIntentParser.parse(text))
        assertNull(HelpIntentParser.parse("오늘 날씨 어때"))
        assertNull(HelpIntentParser.parse(""))
    }

    @Test
    fun `이번 달 요약 문장`() {
        assertEquals("이번 달 운행 12건, 모두 77.9킬로미터, 업무사용비율 100퍼센트예요. 운행기록부를 열게요.", HelpPhrases.logbook(12, 77.94, 100.0))
        assertEquals("이번 달 운행은 아직 없어요. 운행기록부를 열게요.", HelpPhrases.logbook(0, 0.0, null))
    }
}
