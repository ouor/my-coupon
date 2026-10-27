package com.ouor.mycoupon

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Every intent the app sends to itself (reminder N1, widget W1, tile T1). The only place outside ui/ that names the
 * activity (code review A-03), and it names it through the non-exported alias `.ui.InternalEntry` (manifest), so these
 * extras are honoured only when the intent came from the app itself (security review S-08).
 * Each source has its own PendingIntent request code: equal intents with code 0 shared one token and overwrote each
 * other's extras (android review A2).
 */
object AppIntents {
    const val EXTRA_COUPON_ID = "coupon_id"
    const val EXTRA_FROM_REMINDER = "from_reminder"
    const val EXTRA_FROM_TILE = "from_tile"
    /** N1 for several coupons: the notified ids (F18) */
    const val EXTRA_REMINDER_IDS = "reminder_ids"

    const val REQUEST_REMINDER = 1
    const val REQUEST_TILE = 2

    private const val INTERNAL = "com.ouor.mycoupon.ui.InternalEntry"

    fun isInternal(intent: Intent) = intent.component?.className == INTERNAL

    fun home(context: Context): Intent = Intent().setClassName(context, INTERNAL)

    /** S6 of one coupon; the action keeps one PendingIntent per coupon (widget rows). */
    fun coupon(context: Context, id: Long, fromReminder: Boolean = false): Intent =
        home(context).setAction("open-$id").putExtra(EXTRA_COUPON_ID, id).putExtra(EXTRA_FROM_REMINDER, fromReminder)

    fun reminder(context: Context, ids: LongArray): Intent =
        if (ids.size == 1) coupon(context, ids[0], fromReminder = true)
        else home(context).putExtra(EXTRA_REMINDER_IDS, ids).putExtra(EXTRA_FROM_REMINDER, true)

    fun tile(context: Context): Intent = home(context).putExtra(EXTRA_FROM_TILE, true)

    fun pending(context: Context, requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}
