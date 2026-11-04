package com.altrostudio.drivelog.domain.voice

import com.altrostudio.drivelog.data.trip.TripPurpose

/**
 * 말로 들은 운행 용도와 업무 내용.
 * [purpose]가 null이면 용도를 알아듣지 못했다. [cancelled]면 운전자가 "취소", "나중에"라고 했다
 */
data class SpokenTrip(
    val purpose: TripPurpose?,
    val workNote: String?,
    val cancelled: Boolean = false
)

/** 들은 내용이 맞는지 물었을 때의 대답 */
sealed interface SpokenReply {
    /** "맞아", "네", "다음" */
    data object Yes : SpokenReply

    /** "아니", "틀려", "다시" */
    data object No : SpokenReply

    /** "아니 출근", "업무 본사 회의"처럼 다른 내용을 말했다 */
    data class Correction(val spoken: SpokenTrip) : SpokenReply

    /** "취소", "나중에" */
    data object Cancel : SpokenReply

    data object Unclear : SpokenReply
}

/**
 * 받아 적은 한국어 문장에서 운행 용도와 업무 내용을 뽑는다 (Gleo AI 없이 앱이 직접 듣는 경우).
 * 예: "업무, 거래처 납품 미팅" → 일반업무 + "거래처 납품 미팅". "출근" → 출퇴근. "개인 용무" → 비업무.
 *
 * - 음성 인식은 띄어쓰기가 들쭉날쭉해서("비 업무", "일반 업무") 용도 낱말은 띄어쓰기를 무시하고 찾는다
 * - "출근 아니고 업무"처럼 고쳐 말하면 마지막 "아니고/아니라/말고" 뒤만 본다
 * - 용도 낱말이 없어도 업무 내용 낱말(거래처, 미팅, 납품…)이 있으면 일반업무로 본다. 앱이 "일반업무로 기록했습니다.
 *   업무 내용, …"이라고 되읽어 주고, 틀리면 운행기록부에서 고칠 수 있다(고친 흔적은 해시 체인에 남는다)
 * - [suggestion](같은 곳에서 지난번에 고른 용도)을 물었을 때 "네"라고 하면 그 용도로 본다
 * - 비업무는 업무 내용을 남기지 않는다 (개인정보)
 */
object SpokenTripParser {

    fun parse(text: String, suggestion: TripPurpose? = null): SpokenTrip {
        val cleaned = clean(text)
        if (cleaned.isEmpty()) return SpokenTrip(null, null)
        val scope = afterLastCorrection(cleaned)
        val compact = scope.replace(" ", "")
        if (CANCEL.matches(compact)) return SpokenTrip(null, null, cancelled = true)

        val explicit = explicitPurpose(compact)
        if (explicit != null) {
            val note = note(removeKeyword(scope, explicit.keyword), explicit.purpose)
            return SpokenTrip(explicit.purpose, note)
        }
        if (suggestion != null && suggestion != TripPurpose.UNCLASSIFIED) {
            AFFIRMATIVE.find(scope)?.let { yes ->
                return SpokenTrip(suggestion, note(scope.substring(yes.range.last + 1), suggestion))
            }
        }
        if (WORK_WORDS.any { it in compact }) {
            return SpokenTrip(TripPurpose.BUSINESS, note(scope, TripPurpose.BUSINESS))
        }
        return SpokenTrip(null, null, cancelled = "취소" in compact)
    }

    /**
     * "맞으면 맞아, 다르면 다시 말씀해 주세요"에 대한 대답. [current]는 확인 중인 용도.
     * 한 음절 대답("네")은 잘못 들릴 수 있어 "맞아", "다음", "확인"처럼 긴 말도 받는다. 모르면 [SpokenReply.Unclear]
     */
    fun reply(text: String, current: TripPurpose? = null): SpokenReply {
        val cleaned = clean(text)
        val compact = cleaned.replace(" ", "")
        if (compact.isEmpty()) return SpokenReply.Unclear
        if (NEGATIVE.containsMatchIn(compact)) {
            val corrected = parse(cleaned)
            return if (corrected.purpose != null) SpokenReply.Correction(corrected) else SpokenReply.No
        }
        val explicit = explicitPurpose(compact)
        val yes = YES_WORDS.any { it in compact } || YES_SHORT.matches(compact)
        if (yes && (explicit == null || explicit.purpose == current)) return SpokenReply.Yes
        val parsed = parse(cleaned)
        return when {
            parsed.cancelled -> SpokenReply.Cancel
            parsed.purpose != null -> SpokenReply.Correction(parsed)
            else -> SpokenReply.Unclear
        }
    }

    /** "어떤 업무였는지" 다시 물었을 때의 대답을 업무 내용으로 바꾼다. "없어", "됐어"면 null */
    fun noteAnswer(text: String): String? {
        val cleaned = clean(text)
        if (cleaned.isEmpty() || NO_NOTE.matches(cleaned.replace(" ", ""))) return null
        val parsed = parse(cleaned)
        if (parsed.purpose == TripPurpose.BUSINESS && parsed.workNote != null) return parsed.workNote
        return note(cleaned, TripPurpose.BUSINESS)
    }

    private data class Match(val purpose: TripPurpose, val keyword: String, val index: Int)

    /** 가장 앞에 나온 용도 낱말. 같은 자리면 긴 낱말("업무아니" > "업무") */
    private fun explicitPurpose(compact: String): Match? =
        KEYWORDS.mapNotNull { (keyword, purpose) ->
            compact.indexOf(keyword).takeIf { it >= 0 }?.let { Match(purpose, keyword, it) }
        }.minWithOrNull(compareBy<Match> { it.index }.thenByDescending { it.keyword.length })

    private fun clean(text: String): String =
        text.replace(PUNCTUATION, " ").replace(SPACES, " ").trim()

    private fun afterLastCorrection(text: String): String {
        val last = CORRECTION.findAll(text).lastOrNull() ?: return text
        return text.substring(last.range.last + 1).trim().ifEmpty { text }
    }

    /** 고른 용도 낱말(띄어 써도 됨)과 바로 뒤에 붙은 조사·어미를 한 번 지운다 */
    private fun removeKeyword(scope: String, keyword: String): String {
        val spaced = keyword.toCharArray().joinToString("\\s*") { Regex.escape(it.toString()) }
        return Regex("$spaced$KEYWORD_TAIL").replaceFirst(scope, " ")
    }

    private fun note(rest: String, purpose: TripPurpose): String? {
        if (purpose == TripPurpose.PERSONAL) return null
        var note = rest
        for (filler in FILLERS) note = filler.replace(note, " ")
        note = note.replace(SPACES, " ").trim()
        repeat(2) { note = LEADING.replace(note, "").trim() }
        // 음성 인식이 용도 낱말을 겹쳐 받아 적은 경우 ("업무 업무 거래처 미팅")
        if (purpose == TripPurpose.BUSINESS) note = LEADING_BUSINESS.replace(note, "").trim()
        note = TRAILING.replace(note, "").trim()
        return note.takeIf { it.length >= MIN_NOTE_LENGTH }
    }

    private const val MIN_NOTE_LENGTH = 2

    private val PUNCTUATION = Regex("[.,!?;:·…\"'“”‘’~()\\[\\]]")
    private val SPACES = Regex("\\s+")
    private val CORRECTION = Regex("(아니고|아니라|말고)")
    private val CANCEL = Regex("^(취소|취소해|취소해줘|취소해주세요|그만|그만해|됐어|됐어요|됐습니다|나중에|나중에할게|나중에할게요|안해|안할게|안할게요|괜찮아|괜찮아요)$")
    private val NEGATIVE = Regex("^(아니|아뇨|아녀|틀려|틀렸|틀린|다시|안맞|노노|노우)")
    private val YES_WORDS = listOf(
        "맞아", "맞어", "맞습니다", "맞다", "맞죠", "맞네", "맞음", "마자", "마저", "다음", "넘어가", "확인", "좋아", "좋습니다",
        "그래", "그렇", "정확", "오케이", "오키", "기록해", "저장해", "그걸로"
    )
    private val YES_SHORT = Regex("^(네|넵|넹|예|응|어|어어|웅|ok|okay|yes)(요)?$", RegexOption.IGNORE_CASE)
    private val NO_NOTE = Regex("^(없어|없어요|없음|없습니다|없다|딱히없어|딱히없어요|됐어|됐어요|아니요|아니|패스|그냥|그냥기록해|그냥기록해줘|괜찮아|괜찮아요|취소|몰라|나중에)$")
    private val AFFIRMATIVE = Regex("^(네|예|응|맞아요|맞아|맞습니다|그래요|그래|좋아요|좋아|오케이|지난번처럼|똑같이|같은\\s?걸로)(?=\\s|$)")

    /** 용도 낱말 (띄어쓰기 없이 비교). "비업무", "업무아니"는 "업무"보다 먼저 맞는다 */
    private val KEYWORDS = listOf(
        "비업무" to TripPurpose.PERSONAL,
        "업무아니" to TripPurpose.PERSONAL,
        "업무가아니" to TripPurpose.PERSONAL,
        "업무외" to TripPurpose.PERSONAL,
        "개인" to TripPurpose.PERSONAL,
        "사적" to TripPurpose.PERSONAL,
        "출퇴근" to TripPurpose.COMMUTE,
        "출근" to TripPurpose.COMMUTE,
        "퇴근" to TripPurpose.COMMUTE,
        "통근" to TripPurpose.COMMUTE,
        "일반업무" to TripPurpose.BUSINESS,
        "업무용" to TripPurpose.BUSINESS,
        "업무" to TripPurpose.BUSINESS,
        "회사일" to TripPurpose.BUSINESS,
        "회삿일" to TripPurpose.BUSINESS
    )

    /** 용도 낱말 없이도 일반업무로 볼 만한 업무 내용 낱말 ("병원 방문"처럼 개인일 수도 있는 낱말은 넣지 않는다) */
    private val WORK_WORDS = listOf(
        "거래처", "미팅", "회의", "납품", "출장", "외근", "고객", "현장", "영업", "배송",
        "세미나", "견적", "계약", "상담", "협력사", "본사", "지사", "지점", "공장", "업체"
    )

    /** 용도 낱말 바로 뒤의 조사·어미 ("업무로", "출근길에", "퇴근하면서", "업무이고") */
    private const val KEYWORD_TAIL =
        "(으로|로|이고요|이고|이구요|이에요|예요|입니다|이요|요|이야|야|이었어|였어|인데|하는\\s*길에|하는\\s*길|길에|길|하면서|하고|해서|용무|용|적인|적으로)?"

    /** 업무 내용에서 지울 명령·안내 말 */
    private val FILLERS = listOf(
        Regex("(용도|목적)\\s*(는|은|가|이)?(?=\\s|$)"),
        Regex("(업무\\s*)?내용\\s*(은|는|이|으로|:)?(?=\\s|$)"),
        Regex("(으로|로)?\\s*(기록|저장|입력|등록)\\s*(해\\s*(줘|주세요|줄래|줘요)?|할게요?|해요|합니다|하기|하자)?(?=\\s|$)"),
        Regex("(남겨|적어)\\s*(줘|주세요|줘요)(?=\\s|$)")
    )

    /** 업무 내용 앞의 군말과 이어 주는 말 */
    private val LEADING = Regex("^(음+|어+|그+|저기|그리고|그냥|네|예|응|아니요|아니|은|는|이|가|으로|로|에|에서|하고|해서)(\\s+|$)")

    /** 업무 내용 앞에 겹친 "업무" ("업무협약"처럼 붙은 낱말은 남긴다) */
    private val LEADING_BUSINESS = Regex("^((일반\\s*)?업무(로|요|이고)?(\\s+|$))+")

    /** 업무 내용 끝의 말투 ("거래처 미팅이요" → "거래처 미팅") */
    private val TRAILING = Regex("(이었어요|였어요|이에요|예요|입니다|이요|요|이야|했어요|했어|했습니다)$")
}
