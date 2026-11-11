package kkam.app

import android.animation.ArgbEvaluator
import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan

/**
 * 코치 카드(주행 화면)와 내비 위 팝업이 같이 쓰는 색 규칙.
 * 바탕은 늘 어두운 surface이고, 상태 색은 포인트(방향 그림 원, 행동 알약, 테두리, 왼쪽 기운)에만 쓴다.
 *
 * | 모습 | 상태 색 | 방향 그림 원 | 행동 알약 아이콘 | 언제 |
 * | --- | --- | --- | --- | --- |
 * | IDLE | 없음 (회색 테두리) | surface_2 + muted 그림 | 숨김 | 안내 대상 없음 |
 * | WARN | warn 주황 | 주황 원 + 어두운 그림 | ! | 지금 켜야 함 |
 * | OK | ok 파랑 | 파랑 원 + 어두운 그림 | ✓ | 켰음 |
 * | MISS | miss 빨강 | 빨강 원 + 어두운 그림 | X | 판정 지점을 꺼진 채 지남 ("다음엔 미리 켜요") |
 *
 * 색만으로 구분하지 않게 알약 아이콘이 상태마다 다르다.
 */
enum class CardLook { IDLE, WARN, OK, MISS }

/** 주행 화면 상태 → 모습. 판정 지점을 주황인 채로 지나 고정되면 놓침(MISS)이다. */
fun CoachState.look(): CardLook = when {
    tone == Tone.IDLE -> CardLook.IDLE
    tone == Tone.OK -> CardLook.OK
    checkedTone == Tone.WARN -> CardLook.MISS
    else -> CardLook.WARN
}

/** 팝업은 놓침 때 숨으므로(PopupPolicy) 주황·파랑 둘뿐이다. */
fun PopupTone.look(): CardLook = if (this == PopupTone.OK) CardLook.OK else CardLook.WARN

/** 행동 알약 아이콘. 상태마다 모양이 다르다. */
fun CardLook.badgeIconRes(): Int = when (this) {
    CardLook.OK -> R.drawable.ic_check
    CardLook.MISS -> R.drawable.ic_miss
    CardLook.IDLE, CardLook.WARN -> R.drawable.ic_alert
}

/**
 * 카드 한 장에 칠할 색.
 * accent = 상태 색 (그림 원, 알약, 켜진 깜빡이 칩, 레버 그림), edge = 카드 테두리,
 * tint = 카드 왼쪽 기운 (오른쪽은 surface), iconBg·iconInk = 방향 그림 원과 그림.
 */
data class CardPalette(
    val accent: Int,
    val edge: Int,
    val tint: Int,
    val iconBg: Int,
    val iconInk: Int,
) {
    /** 두 색 사이 중간값. fraction 0이면 this, 1이면 to. 상태가 바뀔 때 한 번 페이드하는 데 쓴다. */
    fun mix(to: CardPalette, fraction: Float) = CardPalette(
        accent = blend(accent, to.accent, fraction),
        edge = blend(edge, to.edge, fraction),
        tint = blend(tint, to.tint, fraction),
        iconBg = blend(iconBg, to.iconBg, fraction),
        iconInk = blend(iconInk, to.iconInk, fraction),
    )

    private fun blend(a: Int, b: Int, fraction: Float) = ARGB.evaluate(fraction, a, b) as Int

    private companion object {
        val ARGB = ArgbEvaluator()
    }
}

fun Context.palette(look: CardLook): CardPalette = when (look) {
    CardLook.IDLE -> CardPalette(
        accent = getColor(R.color.muted),
        edge = getColor(R.color.line),
        tint = getColor(R.color.surface),
        iconBg = getColor(R.color.surface_2),
        iconInk = getColor(R.color.muted),
    )
    CardLook.WARN -> accentPalette(R.color.warn, R.color.warn_tint)
    CardLook.OK -> accentPalette(R.color.ok, R.color.ok_tint)
    CardLook.MISS -> accentPalette(R.color.miss, R.color.miss_tint)
}

private fun Context.accentPalette(accent: Int, tint: Int) = CardPalette(
    accent = getColor(accent),
    edge = getColor(accent),
    tint = getColor(tint),
    iconBg = getColor(accent),
    iconInk = getColor(R.color.on_accent),
)

/** "60 m" → 숫자는 크게(numberEmphasis), 단위는 작고 흐리게(unitColor). */
fun distanceText(label: String, unitColor: Int): CharSequence {
    val text = SpannableString(numberEmphasis(label))
    val unitStart = label.lastIndexOf(' ')
    if (unitStart >= 0) {
        text.setSpan(ForegroundColorSpan(unitColor), unitStart, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    return text
}

/** color의 RGB에 alphaFrom의 투명도를 씌운다. 반투명 팝업 바탕 위 그라데이션에 쓴다. */
fun withAlphaOf(color: Int, alphaFrom: Int): Int = (color and 0x00FFFFFF) or (alphaFrom and 0xFF000000.toInt())
