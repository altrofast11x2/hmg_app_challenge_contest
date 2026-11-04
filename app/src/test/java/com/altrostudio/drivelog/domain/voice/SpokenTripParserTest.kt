package com.altrostudio.drivelog.domain.voice

import com.altrostudio.drivelog.data.trip.TripPurpose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpokenTripParserTest {

    private fun parse(text: String, suggestion: TripPurpose? = null) = SpokenTripParser.parse(text, suggestion)

    @Test
    fun `용도와 업무 내용을 함께 말하면 둘 다 뽑는다`() {
        assertEquals(SpokenTrip(TripPurpose.BUSINESS, "거래처 납품 미팅"), parse("업무, 거래처 납품 미팅"))
        assertEquals(SpokenTrip(TripPurpose.BUSINESS, "강남 고객사 방문"), parse("일반 업무로 기록해 줘. 강남 고객사 방문"))
        assertEquals(SpokenTrip(TripPurpose.BUSINESS, "서울 본사 주간 회의"), parse("업무 내용은 서울 본사 주간 회의"))
        assertEquals(SpokenTrip(TripPurpose.BUSINESS, "견적 상담"), parse("용도는 업무 내용은 견적 상담"))
    }

    @Test
    fun `용도만 말하면 업무 내용은 비운다`() {
        assertEquals(SpokenTrip(TripPurpose.COMMUTE, null), parse("출근"))
        assertEquals(SpokenTrip(TripPurpose.COMMUTE, null), parse("퇴근이요"))
        assertEquals(SpokenTrip(TripPurpose.COMMUTE, null), parse("출퇴근으로 기록해 주세요"))
        assertEquals(SpokenTrip(TripPurpose.BUSINESS, null), parse("업무"))
    }

    @Test
    fun `음성 인식이 띄어 써도 용도를 찾는다`() {
        assertEquals(TripPurpose.PERSONAL, parse("비 업무").purpose)
        assertEquals(TripPurpose.BUSINESS, parse("일반 업 무").purpose)
    }

    @Test
    fun `비업무는 업무 내용을 남기지 않는다`() {
        assertEquals(SpokenTrip(TripPurpose.PERSONAL, null), parse("비업무"))
        assertEquals(SpokenTrip(TripPurpose.PERSONAL, null), parse("개인 용무로 병원 다녀왔어"))
        assertEquals(SpokenTrip(TripPurpose.PERSONAL, null), parse("업무 아니야"))
    }

    @Test
    fun `비업무의 업무 글자를 일반업무로 잘못 보지 않는다`() {
        assertEquals(TripPurpose.PERSONAL, parse("비업무로 해 줘").purpose)
    }

    @Test
    fun `고쳐 말하면 뒤의 말을 따른다`() {
        assertEquals(SpokenTrip(TripPurpose.BUSINESS, "본사 회의"), parse("출근 아니고 업무, 본사 회의"))
        assertEquals(SpokenTrip(TripPurpose.COMMUTE, null), parse("업무 말고 퇴근"))
    }

    @Test
    fun `용도 낱말이 없어도 업무 내용 낱말이 있으면 일반업무로 본다`() {
        assertEquals(SpokenTrip(TripPurpose.BUSINESS, "거래처 납품"), parse("거래처 납품"))
        assertEquals(SpokenTrip(TripPurpose.BUSINESS, "판교 협력사 미팅"), parse("음 판교 협력사 미팅이요"))
    }

    @Test
    fun `개인일 수도 있는 낱말만으로는 용도를 정하지 않는다`() {
        assertNull(parse("병원 방문").purpose)
        assertNull(parse("음 글쎄요").purpose)
    }

    @Test
    fun `출퇴근 길의 들른 곳은 업무 내용으로 남긴다`() {
        assertEquals(SpokenTrip(TripPurpose.COMMUTE, "거래처 들름"), parse("퇴근길에 거래처 들름"))
    }

    @Test
    fun `지난번 용도를 물었을 때 네라고 하면 그 용도로 본다`() {
        assertEquals(SpokenTrip(TripPurpose.COMMUTE, null), parse("네", TripPurpose.COMMUTE))
        assertEquals(SpokenTrip(TripPurpose.BUSINESS, "거래처 미팅"), parse("네, 거래처 미팅", TripPurpose.BUSINESS))
        assertEquals(SpokenTrip(TripPurpose.BUSINESS, null), parse("맞아요", TripPurpose.BUSINESS))
    }

    @Test
    fun `물은 용도가 없으면 네만으로는 정하지 않는다`() {
        assertNull(parse("네").purpose)
    }

    @Test
    fun `아니요는 다시 물을 수 있게 모름으로 둔다`() {
        val spoken = parse("아니요", TripPurpose.BUSINESS)
        assertNull(spoken.purpose)
        assertEquals(false, spoken.cancelled)
    }

    @Test
    fun `네 다음에 다른 용도를 말하면 말한 용도를 따른다`() {
        assertEquals(TripPurpose.PERSONAL, parse("네 비업무요", TripPurpose.BUSINESS).purpose)
    }

    @Test
    fun `취소나 나중에라고 하면 그만 듣는다`() {
        assertTrue(parse("취소").cancelled)
        assertTrue(parse("나중에 할게요").cancelled)
        assertTrue(parse("됐어").cancelled)
        assertTrue(parse("그만").cancelled)
    }

    @Test
    fun `다시 물은 업무 내용 대답을 정리한다`() {
        assertEquals("거래처 납품 미팅", SpokenTripParser.noteAnswer("거래처 납품 미팅이요"))
        assertEquals("견적서 전달", SpokenTripParser.noteAnswer("음 견적서 전달"))
        assertEquals("견적 상담", SpokenTripParser.noteAnswer("업무 내용은 견적 상담"))
        assertNull(SpokenTripParser.noteAnswer("없어요"))
        assertNull(SpokenTripParser.noteAnswer("그냥 기록해 줘"))
        assertNull(SpokenTripParser.noteAnswer(""))
    }

    @Test
    fun `맞는지 물었을 때 맞다는 대답을 여러 말로 받는다`() {
        for (yes in listOf("맞아", "맞아요", "네 맞습니다", "마자", "다음", "넘어가", "확인", "좋아요", "응", "넵", "그래")) {
            assertEquals(yes, SpokenReply.Yes, SpokenTripParser.reply(yes, TripPurpose.BUSINESS))
        }
    }

    @Test
    fun `그렇죠처럼 말해도 맞다로 받고 같은 용도 낱말이 섞여도 맞다로 본다`() {
        assertEquals(SpokenReply.Yes, SpokenTripParser.reply("그렇죠", TripPurpose.BUSINESS))
        assertEquals(SpokenReply.Yes, SpokenTripParser.reply("어 사실 어 업무 거래 업무 그렇죠", TripPurpose.BUSINESS))
        assertEquals(SpokenReply.Yes, SpokenTripParser.reply("정확해요", TripPurpose.COMMUTE))
    }

    @Test
    fun `겹쳐 받아 적은 업무 낱말은 업무 내용에서 뺀다`() {
        assertEquals(SpokenTrip(TripPurpose.BUSINESS, "거래처 미팅"), parse("업무 업무 거래처 미팅"))
        assertEquals(SpokenTrip(TripPurpose.BUSINESS, "업무협약 체결"), parse("업무 업무협약 체결"))
    }

    @Test
    fun `아니라는 대답과 고쳐 말한 내용을 구분한다`() {
        assertEquals(SpokenReply.No, SpokenTripParser.reply("아니", TripPurpose.BUSINESS))
        assertEquals(SpokenReply.No, SpokenTripParser.reply("틀렸어", TripPurpose.BUSINESS))
        assertEquals(SpokenReply.No, SpokenTripParser.reply("다시", TripPurpose.BUSINESS))
        assertEquals(
            SpokenReply.Correction(SpokenTrip(TripPurpose.COMMUTE, null)),
            SpokenTripParser.reply("아니 출근", TripPurpose.BUSINESS)
        )
        assertEquals(
            SpokenReply.Correction(SpokenTrip(TripPurpose.BUSINESS, "본사 회의")),
            SpokenTripParser.reply("업무 본사 회의", TripPurpose.BUSINESS)
        )
    }

    @Test
    fun `맞다고 하면서 같은 용도를 말하면 맞다로 본다`() {
        assertEquals(SpokenReply.Yes, SpokenTripParser.reply("네 업무 맞아요", TripPurpose.BUSINESS))
        assertEquals(
            SpokenReply.Correction(SpokenTrip(TripPurpose.PERSONAL, null)),
            SpokenTripParser.reply("네 비업무요", TripPurpose.BUSINESS)
        )
    }

    @Test
    fun `알아들을 수 없는 대답과 취소`() {
        assertEquals(SpokenReply.Unclear, SpokenTripParser.reply("음", TripPurpose.BUSINESS))
        assertEquals(SpokenReply.Unclear, SpokenTripParser.reply("", TripPurpose.BUSINESS))
        assertEquals(SpokenReply.Cancel, SpokenTripParser.reply("취소", TripPurpose.BUSINESS))
    }

    @Test
    fun `빈 말은 아무것도 정하지 않는다`() {
        assertEquals(SpokenTrip(null, null), parse(""))
        assertEquals(SpokenTrip(null, null), parse("  . "))
    }
}
