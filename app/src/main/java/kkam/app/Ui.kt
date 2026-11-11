package kkam.app

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import kotlin.math.roundToInt

/*
 * 모든 화면이 같이 쓰는 그리기 도구. 화면 파일마다 rounded()·dp()를 따로 두지 않는다.
 * 색·글자 크기 값은 res/values의 colors.xml, dimens.xml, styles.xml(Kk.*)에 있다.
 */

/** dp → 픽셀. */
fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

/** 둥근 모서리 배경. [strokeColor]를 주면 테두리를 그린다. */
fun Context.rounded(color: Int, radiusDp: Int, strokeColor: Int? = null, strokeDp: Int = 2) = GradientDrawable().apply {
    setColor(color)
    cornerRadius = dp(radiusDp).toFloat()
    strokeColor?.let { setStroke(dp(strokeDp), it) }
}

/** 카드 배경 (surface, 기본 모서리). */
fun Context.cardBackground(color: Int = getColor(R.color.surface)) = rounded(color, radiusDp = CARD_RADIUS_DP)

/** 알약 모양 배경 (버튼·칩). */
fun Context.pillBackground(color: Int, strokeColor: Int? = null) = rounded(color, radiusDp = PILL_RADIUS_DP, strokeColor = strokeColor)

/**
 * 숫자는 크게, 단위는 작게. "60 m", "3만 원", "62 %"처럼 마지막 공백 뒤를 단위로 보고 줄인다.
 * 공백이 없으면 그대로 돌려준다.
 */
fun numberEmphasis(label: String, unitScale: Float = UNIT_SCALE): CharSequence {
    val unitStart = label.lastIndexOf(' ')
    if (unitStart < 0) return label
    return SpannableString(label).apply {
        setSpan(RelativeSizeSpan(unitScale), unitStart, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}

private const val CARD_RADIUS_DP = 28
private const val PILL_RADIUS_DP = 999
private const val UNIT_SCALE = 0.45f
