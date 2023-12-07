package nl.tudelft.birthdayattack.utils

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScreenManager(private val activity: Activity) {

    private val powerManager: PowerManager by lazy {
        activity.getSystemService(Context.POWER_SERVICE) as PowerManager
    }

    private val windowManager: WindowManager by lazy {
        activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    suspend fun keepScreenOn() {
        withContext(Dispatchers.Default) {
            // Acquire a wake lock to keep the screen on
            val wakeLockTag = "BirthdayAttack:ScreenOn"
            val wakeLock: PowerManager.WakeLock? = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                wakeLockTag
            )

            wakeLock?.let {
                if (!it.isHeld) {
                    it.acquire()
                }
            }

            // Prevent the device from sleeping by setting appropriate flags
            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                android.graphics.PixelFormat.TRANSLUCENT
            )

            activity.window.attributes = layoutParams
        }
    }

    suspend fun releaseScreen() {
        withContext(Dispatchers.Default) {
            // Release the wake lock
            val wakeLockTag = "BirthdayAttack:ScreenOn"
            val wakeLock: PowerManager.WakeLock? = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                wakeLockTag
            )

            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
        }
    }
}


