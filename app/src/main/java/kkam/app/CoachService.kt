package kkam.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import kkam.engine.model.Gear

/**
 * [CoachSession]을 갖는 포그라운드 서비스.
 * 내비가 앞에 와서 우리 화면이 숨겨져도 재생, 신호 수집, 엔진, 음성이 계속 돈다.
 * 화면(MainActivity)은 bind해서 세션을 받아 그리기만 한다.
 * 화면이 떨어져 있고 주행도 멈추면 스스로 끝난다.
 */
class CoachService : Service() {
    inner class LocalBinder : Binder() {
        val session: CoachSession get() = this@CoachService.session
    }

    private lateinit var session: CoachSession
    private lateinit var popup: CoachPopup
    private lateinit var headsUp: CoachHeadsUp
    private val binder = LocalBinder()
    private var bound = false
    private var lastGear: Gear? = null
    private val onSessionChanged = CoachSession.Listener { frame ->
        // 앱 화면이 보이면 코치 카드가 같은 내용을 보여주므로 팝업은 숨긴다.
        val shown = if (session.appVisible) null else session.popup
        // 권한이 있으면 겹침 창 카드, 없으면 상단 알림. 권한이 바뀌면 다른 쪽은 숨긴다.
        val (use, other) = if (Settings.canDrawOverlays(this)) popup to headsUp else headsUp to popup
        other.show(null, null)
        use.show(shown, session.appArea)
        frame?.let { openAppWhenParked(it.gear) }
        stopIfIdle()
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotice()
        session = CoachSession(this)
        popup = CoachPopup(this)
        headsUp = CoachHeadsUp(this)
        session.addListener(onSessionChanged)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder {
        bound = true
        return binder
    }

    override fun onRebind(intent: Intent?) {
        bound = true
    }

    override fun onUnbind(intent: Intent?): Boolean {
        bound = false
        stopIfIdle()
        return true
    }

    override fun onDestroy() {
        popup.release()
        headsUp.release()
        session.release()
        super.onDestroy()
    }

    /** 주행 중 내비를 보다가 기어를 P로 바꾸면 우리 앱을 앞으로 띄워 리포트를 보여준다 (기획서 4.2의 5번). */
    private fun openAppWhenParked(gear: Gear) {
        val parkedNow = gear == Gear.P && lastGear != null && lastGear != Gear.P
        lastGear = gear
        if (parkedNow && !session.appVisible) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        }
    }

    private fun stopIfIdle() {
        if (!bound && session.running == null) stopSelf()
    }

    /** 포그라운드 서비스는 알림이 꼭 있어야 한다. 조용한 채널을 쓴다. */
    private fun startForegroundWithNotice() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.service_channel), NotificationManager.IMPORTANCE_MIN))
        val notice = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_turn_left)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.service_running))
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTICE_ID, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTICE_ID, notice)
        }
    }

    companion object {
        private const val CHANNEL = "coach_service"
        private const val NOTICE_ID = 100

        fun start(context: Context) {
            context.startForegroundService(Intent(context, CoachService::class.java))
        }
    }
}
