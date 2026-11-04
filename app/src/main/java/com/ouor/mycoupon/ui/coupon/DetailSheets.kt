package com.ouor.mycoupon.ui.coupon

import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ouor.mycoupon.data.Coupon
import com.ouor.mycoupon.data.UsedReason
import com.ouor.mycoupon.ui.HomeLinks
import com.ouor.mycoupon.ui.component.AppSheet
import com.ouor.mycoupon.ui.component.Copy
import com.ouor.mycoupon.ui.component.CouponThumb
import com.ouor.mycoupon.ui.component.LocalSnackbar
import com.ouor.mycoupon.ui.component.PrimaryButton
import com.ouor.mycoupon.ui.component.SecondaryButton
import com.ouor.mycoupon.ui.component.title
import com.ouor.mycoupon.ui.component.won
import com.ouor.mycoupon.ui.theme.Spacing
import com.ouor.mycoupon.ui.toHome
import kotlinx.coroutines.launch

/** S8, "넘겼나요?" (F16), F10, S12 and the delete dialog of S6. [leave] goes to S1 with the undo Snackbar. */
@Composable
internal fun DetailSheets(coupon: Coupon, st: DetailState, vm: CouponViewModel, home: HomeLinks, nav: NavHostController, leave: () -> Unit) {
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val nm = coupon.title()
    val trashed = { vm.trashAnswered(); if (st.finishing != null) leave() }
    val trash = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { trashed() }

    if (st.useSheet) UseSheet(coupon, startSpent = vm.amountInputSpent, onMode = { vm.amountInputSpent = it }, onDismiss = { st.useSheet = false },
        onRemaining = { v ->
            st.useSheet = false
            // pre-checked in the sheet; a refusal here (stale balance) replaces the success Snackbar
            // code review U-21: and takes back the finishing state, or the bottom button would stay gone
            val log = vm.recordRemaining(v) { e -> st.finishing = null; snackbar.show(usageMessage(e, coupon.balance ?: coupon.faceValue ?: 0, false)) }
            if (v == 0L) st.finishing = Copy.USED
            else snackbar.show("$nm 잔액 ${won(v)}으로 기록했어요", Copy.UNDO) { home.undoLastBalance(coupon.id, log) }
        })
    if (st.askGiven) AppSheet({ st.askGiven = false }, "이 쿠폰을 넘겼나요?") {
        // C2-25: optional name; the keyboard is not raised (leaving it empty is the default)
        OutlinedTextField(st.givenTo, { st.givenTo = it.take(40) }, Modifier.fillMaxWidth(), label = { Text("누구에게(선택)") },
            placeholder = { Text("예: 김민지") }, singleLine = true)
        Spacer(Modifier.height(Spacing.l))
        PrimaryButton("넘겼어요", {
            st.askGiven = false; vm.markUsed(UsedReason.GIVEN, st.givenTo.trim().ifEmpty { null }); st.finishing = Copy.GIVEN
        })
        SecondaryButton("아니요", { st.askGiven = false })
    }
    if (st.askTrash) AppSheet({ st.askTrash = false; trashed() },
        "갤러리 원본 이미지를 휴지통으로 옮길까요?", "앱에 사본이 있어 갤러리 원본을 지워도 계속 볼 수 있어요. 휴지통에서 30일 안에 되살릴 수 있어요.") {
        CouponThumb(coupon.thumbPath, size = 96.dp)
        Spacer(Modifier.height(Spacing.l))
        PrimaryButton("휴지통으로 옮기기", {
            st.askTrash = false
            if (Build.VERSION.SDK_INT >= 30) runCatching {
                val pi = MediaStore.createTrashRequest(context.contentResolver, listOf(Uri.parse(coupon.sourceUri)), true)
                trash.launch(IntentSenderRequest.Builder(pi.intentSender).build())
            }.onFailure { trashed() }
        })
        SecondaryButton("그대로 두기", { st.askTrash = false; trashed() })
    }
    if (st.reminderSheet) ReminderSheet(coupon, vm.defaultOffsets, onDismiss = { st.reminderSheet = false }) {
        vm.setReminderOffsets(it); st.reminderSheet = false; snackbar.show("알림을 바꿨어요")
    }
    if (st.confirmDelete) AlertDialog(
        onDismissRequest = { st.confirmDelete = false },
        title = { Text("이 쿠폰을 지울까요?") },
        text = { Text("지우면 되돌릴 수 없어요. 갤러리 사진은 그대로예요.") },
        confirmButton = {
            TextButton({ st.confirmDelete = false; scope.launch { vm.delete().join(); snackbar.show("쿠폰을 지웠어요"); nav.toHome() } }) {
                Text("지우기", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton({ st.confirmDelete = false }) { Text("취소") } },
    )
}
