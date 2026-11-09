package kkam.app

import android.app.Activity
import android.view.View

/**
 * 법규 Q&A 화면 (기획서 UI-5). 기어 P일 때만 보인다.
 * 어느 화면을 보일지는 MainActivity가 [Screen]으로 정하고, 이 클래스는 자기 화면만 그린다.
 */
class QnaScreen(activity: Activity) {
    private val root: View = activity.findViewById(R.id.qnaScreen)

    fun show(screen: Screen) {
        root.visibility = if (screen == Screen.QNA) View.VISIBLE else View.GONE
    }
}
