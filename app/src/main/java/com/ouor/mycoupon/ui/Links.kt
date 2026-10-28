package com.ouor.mycoupon.ui

import android.net.Uri
import com.ouor.mycoupon.domain.ImportOrigin
import java.io.File
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow

// Code review A2-05: a feature screen never reaches into another feature's ViewModel or casts the Activity.
// The Activity-wide ViewModels implement these and Nav.kt hands them over.

/** The import session (S3/S4) as S1, S2 and S9 start it. */
interface ImportStarter {
    fun start(uris: List<Uri>, origin: ImportOrigin)
    /** C2-24: file + FileProvider uri for the system camera app. */
    fun newCapture(): Pair<File, Uri>
    fun startCapture(file: File, uri: Uri)
}

/** What S6 and S4 need from S1's state: the swipe ring, the undo Snackbar after leaving, the D8 rationale. */
interface HomeLinks {
    /** available coupons in the current S1 order (C2-04 swipe ring) */
    val availableIds: StateFlow<List<Long>>
    /** S6 just finished a coupon and returned to S1 (C2-04: a late tap must not open a card) */
    fun arrivedFromFinish()
    /** Snackbar [되돌리기] after S6 has gone; [log] is the record that write made (core D-07: nothing if another came since) */
    fun undoUse(id: Long, log: Deferred<Long?>? = null): Job
    fun undoLastBalance(id: Long, log: Deferred<Long?>? = null): Job
    /** D8: S4 registered coupons */
    fun registered()
}
