package com.ouor.mycoupon.ui.coupon

import android.app.Activity
import android.os.Build
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.ouor.mycoupon.data.Coupon
import com.ouor.mycoupon.ui.component.codeSpoken

/**
 * S6 state in one place (code review A2-07). What must outlive a font-size change, a trip to S7/S5 and a process death is saved
 * (code review U-04/U-05): the open S8 sheet, "넘겼나요?", [그래도 보기], and a running undo (its deadline in wall-clock time).
 * Menus, dialogs and the F10 sheet come back closed.
 */
@Stable
internal class DetailState(useSheet: Boolean, askGiven: Boolean, givenTo: String, revealed: Boolean, finishing: String?, undoUntil: Long) {
    /** S8 open (also from S1 "아까 썼어요?" [쓴 금액 입력], applied once as the first value) */
    var useSheet by mutableStateOf(useSheet)
    /** C2-26: the chooser reported a picked app: "이 쿠폰을 넘겼나요?" */
    var askGiven by mutableStateOf(askGiven)
    var givenTo by mutableStateOf(givenTo)
    /** C2-02: a past coupon's barcode after [그래도 보기] */
    var revealed by mutableStateOf(revealed)
    /** set when this screen just ended the coupon ("사용 완료했어요"): then F10, then S1 with an undo Snackbar */
    var finishing by mutableStateOf(finishing)
    /** C2-04 undo bar deadline (System.currentTimeMillis), 0 = no bar */
    var undoUntil by mutableLongStateOf(undoUntil)

    var askTrash by mutableStateOf(false)
    var showOriginal by mutableStateOf(false)
    var reminderSheet by mutableStateOf(false)
    var confirmDelete by mutableStateOf(false)
    var menu by mutableStateOf(false)
    var history by mutableStateOf(false)
    var notesOpen by mutableStateOf(false)
    /**
     * C2-04: the CTA ignores presses until this (elapsedRealtime): a second after a swipe to this coupon (a palm swipe must not
     * mark it used) and after an undo (a second tap on the same spot must not mark it used again).
     */
    var ctaFrom by mutableLongStateOf(0L)
    /** code review U-07: [기록 지우기] is locked for a second after a delete, so a double tap cannot take the next record too */
    var deleteFrom by mutableLongStateOf(0L)

    fun ctaOpen() = SystemClock.elapsedRealtime() >= ctaFrom

    companion object {
        val Saver = listSaver<DetailState, Any?>(
            save = { listOf(it.useSheet, it.askGiven, it.givenTo, it.revealed, it.finishing, it.undoUntil) },
            restore = { DetailState(it[0] as Boolean, it[1] as Boolean, it[2] as String, it[3] as Boolean, it[4] as String?, it[5] as Long) },
        )
    }
}

@Composable
internal fun rememberDetailState(openUse: Boolean, swiped: Boolean): DetailState {
    return rememberSaveable(saver = DetailState.Saver) {
        DetailState(openUse, false, "", false, null, 0L).also { if (swiped) it.ctaFrom = SystemClock.elapsedRealtime() + 1_000 }
    }
}

/**
 * F6: max brightness + keep-on for this window while [on]; restored on leave. Bars follow the system theme (S6 is no longer forced light, U11).
 * Returns whether the window really is at full brightness (C2-01: the chip shows only then).
 */
@Composable
internal fun CheckoutWindow(on: Boolean): Boolean {
    val activity = LocalActivity.current as Activity
    var applied by remember { mutableStateOf(false) }
    // counted: S6 and S7 overlap during the navigation transition, the last one out restores
    DisposableEffect(on) {
        val w = activity.window
        if (on) {
            Checkout.bright++
            w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
            w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        applied = on && w.attributes.screenBrightness == WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL &&
            w.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        onDispose {
            if (on && --Checkout.bright == 0) {
                w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
                w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
    // security review S-12: the recents thumbnail (saved to disk) never shows the barcode; screenshots stay allowed
    if (Build.VERSION.SDK_INT >= 33) DisposableEffect(Unit) {
        if (Checkout.hidden++ == 0) activity.setRecentsScreenshotEnabled(false)
        onDispose { if (--Checkout.hidden == 0) activity.setRecentsScreenshotEnabled(true) }
    }
    return applied
}

private object Checkout {
    var bright = 0
    var hidden = 0
}

/** C2-21: number (and 인증번호) read aloud by the device TTS, the same words TalkBack uses (C2-05). */
internal fun speakCode(tts: TextToSpeech, c: Coupon) {
    val text = listOfNotNull(c.code?.let { "번호, " + codeSpoken(it) }, c.authCode?.let { "인증번호, " + codeSpoken(it) }).joinToString(". ")
    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "code")
}
