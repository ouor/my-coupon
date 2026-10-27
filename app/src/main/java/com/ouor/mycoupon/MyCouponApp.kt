package com.ouor.mycoupon

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.ouor.mycoupon.crash.CrashLog
import com.ouor.mycoupon.di.AppScope
import com.ouor.mycoupon.reminder.ReminderNotifier
import com.ouor.mycoupon.reminder.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MyCouponApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var crashLog: CrashLog
    @Inject lateinit var notifier: ReminderNotifier
    @Inject lateinit var reminders: ReminderScheduler
    @Inject lateinit var data: AppData
    @Inject @AppScope lateinit var scope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun attachBaseContext(base: android.content.Context) {
        // ONNX Runtime telemetry off before the native library can load (code review S-01; the initializer provider is removed too)
        runCatching { android.system.Os.setenv("ORT_DISABLE_TELEMETRY", "1", true) }
        super.attachBaseContext(base)
    }

    override fun onCreate() {
        super.onCreate()
        crashLog.install()
        runCatching { crashLog.recordNativeCrashes() }
        notifier.createChannel()
        reminders.ensure()
        val started = System.currentTimeMillis()
        scope.launch(Dispatchers.IO) { runCatching { data.cleanUp(started) } }
    }
}
