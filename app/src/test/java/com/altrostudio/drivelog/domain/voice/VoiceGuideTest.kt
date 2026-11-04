package com.altrostudio.drivelog.domain.voice

import com.altrostudio.drivelog.data.trip.TripPurpose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VoiceGuideTest {

    private class RecordingSpeaker : Speaker {
        val spoken = mutableListOf<String>()
        var stops = 0
        override fun speak(text: String) {
            spoken += text
        }
        override fun stop() {
            stops++
        }
    }

    private class MemoryVoiceSettings : VoiceSettings {
        override var voiceGuidance = true
    }

    private val speaker = RecordingSpeaker()
    private val settings = MemoryVoiceSettings()
    private val guide = VoiceGuide(speaker, settings)

    @Test
    fun `정차하면 거리와 시간을 소리 나는 대로 읽고 용도를 골라 달라고 한다`() {
        assertEquals(
            "운행을 마쳤습니다. 12.3킬로미터, 25분 운행했습니다. 화면에서 용도를 골라 주세요.",
            VoicePhrases.of(TripAnnouncement.Parked(12.299999999999997, 25 * 60_000L, suggestion = null))
        )
        assertEquals(
            "운행을 마쳤습니다. 12킬로미터, 2시간 운행했습니다. 화면에서 용도를 골라 주세요.",
            VoicePhrases.of(TripAnnouncement.Parked(12.0, 120 * 60_000L, suggestion = null))
        )
    }

    @Test
    fun `같은 곳에서 지난번에 고른 용도가 있으면 받침에 맞춰 알려 준다`() {
        assertEquals(
            "운행을 마쳤습니다. 7.7킬로미터, 1시간 5분 운행했습니다. 이곳은 지난번에 출퇴근으로 기록했습니다. 화면에서 용도를 골라 주세요.",
            VoicePhrases.of(TripAnnouncement.Parked(7.7, 65 * 60_000L, TripPurpose.COMMUTE))
        )
        assertEquals("일반업무로 기록했습니다.", VoicePhrases.of(TripAnnouncement.Classified(TripPurpose.BUSINESS)))
        assertEquals("비업무로 기록했습니다.", VoicePhrases.of(TripAnnouncement.Classified(TripPurpose.PERSONAL)))
    }

    @Test
    fun `계기판 값을 모르거나 움직이지 않았으면 거리를 말하지 않는다`() {
        assertEquals(
            "운행을 마쳤습니다. 2분 운행했습니다. 화면에서 용도를 골라 주세요.",
            VoicePhrases.of(TripAnnouncement.Parked(null, 2 * 60_000L, suggestion = null))
        )
        assertEquals(
            "운행을 마쳤습니다. 1분 운행했습니다. 화면에서 용도를 골라 주세요.",
            VoicePhrases.of(TripAnnouncement.Parked(0.0, 70_000L, suggestion = null))
        )
    }

    @Test
    fun `말로 기록이 켜져 있으면 정차 안내 끝에 용도를 말로 묻는다`() {
        assertEquals(
            "운행을 마쳤습니다. 2분 운행했습니다. 용도와 업무 내용을 말씀해 주세요.",
            VoicePhrases.of(TripAnnouncement.Parked(null, 2 * 60_000L, suggestion = null), asking = true)
        )
        assertEquals(
            "운행을 마쳤습니다. 2분 운행했습니다. 이곳은 지난번에 일반업무로 기록했습니다. 맞으면 네, 아니면 용도를 말씀해 주세요.",
            VoicePhrases.of(TripAnnouncement.Parked(null, 2 * 60_000L, suggestion = TripPurpose.BUSINESS), asking = true)
        )
    }

    @Test
    fun `말로 남긴 업무 내용은 분류 안내 때 되읽어 준다`() {
        assertEquals(
            "일반업무로 기록했습니다. 업무 내용, 거래처 납품 미팅.",
            VoicePhrases.of(TripAnnouncement.Classified(TripPurpose.BUSINESS, "거래처 납품 미팅"))
        )
        assertEquals("비업무로 기록했습니다.", VoicePhrases.of(TripAnnouncement.Classified(TripPurpose.PERSONAL, "병원")))
        assertEquals("출퇴근으로 기록했습니다.", VoicePhrases.of(TripAnnouncement.Classified(TripPurpose.COMMUTE)))
    }

    @Test
    fun `안내 장치가 묻는지 여부를 정차 안내에 반영한다`() {
        val asking = VoiceGuide(speaker, settings, asksAfterParking = { true })
        asking.announce(TripAnnouncement.Parked(null, 2 * 60_000L, suggestion = null))
        assertEquals(listOf("운행을 마쳤습니다. 2분 운행했습니다. 용도와 업무 내용을 말씀해 주세요."), speaker.spoken)
    }

    @Test
    fun `설정에서 끄면 읽던 안내를 멈추고 다음 안내부터 읽지 않는다`() {
        guide.announce(TripAnnouncement.Started)
        guide.setEnabled(false)
        guide.announce(TripAnnouncement.Classified(TripPurpose.BUSINESS))

        assertEquals(listOf("운행 기록을 시작합니다."), speaker.spoken)
        assertEquals(1, speaker.stops)
        assertFalse(settings.voiceGuidance)
    }

    @Test
    fun `다시 켜면 켜졌다고 들려준다`() {
        settings.voiceGuidance = false

        guide.setEnabled(true)
        guide.announce(TripAnnouncement.Started)

        assertEquals(listOf(VoicePhrases.ENABLED, "운행 기록을 시작합니다."), speaker.spoken)
    }
}
