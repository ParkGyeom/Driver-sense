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
    private val binder = LocalBinder()
    private var bound = false
    private val stopWhenIdle = CoachSession.Listener { stopIfIdle() }

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotice()
        session = CoachSession(this)
        session.addListener(stopWhenIdle)
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
        session.release()
        super.onDestroy()
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
