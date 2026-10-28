package com.ouor.mycoupon.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ouor.mycoupon.data.AppDatabase
import com.ouor.mycoupon.data.CouponChangeListener
import com.ouor.mycoupon.reminder.ReminderScheduler
import com.ouor.mycoupon.widget.WidgetUpdater
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** Process-wide scope for work that must outlive a screen (import batch, widget refresh). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "mycoupon.db")
            .addMigrations(AppDatabase.MIGRATION_2_3)
            // S-03: deleted coupons are overwritten with zeros, not left in free pages
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    db.query("PRAGMA secure_delete=ON").use { it.moveToFirst() }
                }
            })
            .build()

    @Provides @Singleton @AppScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e ->
        // D-11: a failed background job is logged (class name only, O-12), it does not end the app
        android.util.Log.w("AppScope", "job failed: ${e.javaClass.name}")
    })
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ListenerModule {
    @Binds @IntoSet abstract fun reminders(r: ReminderScheduler): CouponChangeListener
    @Binds @IntoSet abstract fun widget(w: WidgetUpdater): CouponChangeListener
}
