package kkam.app

import android.content.Context

/**
 * 주행 모드 (기획서 4.3 모드 표, F3). 표의 한 행이 enum 값 하나다.
 * 판정 기록은 네 모드 모두 "전부"라서 따로 두지 않는다.
 */
enum class DriveMode(
    /** 화면에 크게 보이는 이름. */
    val label: String,
    /** 표의 "대상" 칸. */
    val target: String,
    /** 표의 "주행 중 안내" 칸. */
    val guideLabel: String,
) {
    DIAGNOSE("진단", "기존 운전자, 첫 2주", "없음"),
    CORRECT("교정", "기존 운전자", "고른 습관만"),
    TRAINING("연수", "초보 운전자", "모든 룰"),
    QUIET("조용", "누구나", "없음"),
    ;

    companion object {
        /**
         * 처음 켰을 때 모드.
         * TODO(기본 모드 질문): 기획서 3.1은 기존 운전자=진단, 초보=연수로 시작한다.
         *  누구를 기본으로 할지 정해지기 전까지 지금 데모 흐름(습관 고르고 안내 듣기)과 맞는 교정을 쓴다.
         */
        val DEFAULT = CORRECT

        /** 저장된 이름을 모드로. 모르는 값이면 [DEFAULT]. */
        fun fromName(name: String?): DriveMode = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** 주행 중에 음성 안내를 하는 모드인가. 진단·조용은 기록만 한다 (4.3 표). */
fun guidesOn(mode: DriveMode): Boolean = when (mode) {
    DriveMode.DIAGNOSE, DriveMode.QUIET -> false
    DriveMode.CORRECT, DriveMode.TRAINING -> true
}

/**
 * 고른 모드를 SharedPreferences에 저장한다. 앱을 다시 켜도 남는다.
 * TODO(엔진 모드 입력, spec v0.3 · AI 담당 P6): 나오면 CoachSession이 주행을 시작할 때 [load] 값을 엔진에 넘긴다.
 *  지금은 저장과 화면 표시만 하고, 안내는 모드와 관계없이 엔진이 내는 대로 읽는다.
 */
class ModeStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): DriveMode = DriveMode.fromName(prefs.getString(KEY_MODE, null))

    fun save(mode: DriveMode) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
    }

    private companion object {
        const val PREFS = "kkam_mode"
        const val KEY_MODE = "mode"
    }
}
