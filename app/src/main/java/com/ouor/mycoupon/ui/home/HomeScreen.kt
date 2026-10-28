package com.ouor.mycoupon.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Redeem
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import com.ouor.mycoupon.AppIntents
import com.ouor.mycoupon.data.CouponType
import com.ouor.mycoupon.ui.settings.TimePickDialog
import com.ouor.mycoupon.ui.settings.timeLabel
import kotlinx.coroutines.launch
import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings as SettingsIcon
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.ouor.mycoupon.data.CouponStatus
import com.ouor.mycoupon.data.ListFilter
import com.ouor.mycoupon.data.ListSort
import com.ouor.mycoupon.domain.ImportOrigin
import com.ouor.mycoupon.ui.importing.ImportViewModel
import com.ouor.mycoupon.ui.MainActivity
import com.ouor.mycoupon.ui.Route
import com.ouor.mycoupon.ui.Sharing
import com.ouor.mycoupon.ui.component.AppSheet
import com.ouor.mycoupon.ui.component.CouponCard
import com.ouor.mycoupon.ui.component.EmptyState
import com.ouor.mycoupon.ui.component.InfoBanner
import com.ouor.mycoupon.ui.component.LocalSnackbar
import com.ouor.mycoupon.ui.component.PrimaryButton
import com.ouor.mycoupon.ui.component.SecondaryButton
import com.ouor.mycoupon.ui.component.isLargeFont
import com.ouor.mycoupon.ui.component.title
import com.ouor.mycoupon.ui.theme.Spacing
import com.ouor.mycoupon.ui.theme.statusColors

fun openNotificationSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
}

/** S1 home + S13 (the [지난 쿠폰] filter state), S2 add sheet, D8 notification rationale sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavHostController) {
    val activity = LocalActivity.current as MainActivity
    val vm: HomeViewModel = hiltViewModel(activity)
    val importVm: ImportViewModel = hiltViewModel(activity)
    val st by vm.state.collectAsStateWithLifecycle()
    val askNotif by vm.notificationRationale.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    var addSheet by rememberSaveable { mutableStateOf(false) }
    var kakaoGuide by rememberSaveable { mutableStateOf(false) }
    var shareGuide by rememberSaveable { mutableStateOf(false) }
    // C2-24: the capture file outlives a process death while the camera app is in front
    var capture by rememberSaveable { mutableStateOf<List<String>?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val (path, uri) = capture ?: return@rememberLauncherForActivityResult
        capture = null
        if (ok) { importVm.startCapture(java.io.File(path), android.net.Uri.parse(uri)); nav.navigate(Route.Import) }
    }
    val canCapture = remember { AppIntents.canCapture(activity) }
    val takePhoto = {
        val (f, u) = importVm.newCapture()
        capture = listOf(f.path, u.toString())
        runCatching { camera.launch(u) }.onFailure { capture = null; snackbar.show("카메라 앱을 열지 못했어요") }
        Unit
    }
    var searching by rememberSaveable { mutableStateOf(st.query.isNotEmpty()) }
    val listState = rememberLazyListState()
    val atTop by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    // QA R1-01: a banner inserted above the first visible item stays off screen; scroll it in when the user was at the top
    LaunchedEffect(st.banner, searching) {
        if (st.banner != null && !searching && listState.firstVisibleItemIndex <= 1) listState.animateScrollToItem(0)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { uris ->
        if (uris.isNotEmpty()) {
            importVm.start(uris, ImportOrigin.PICKER)
            nav.navigate(Route.Import)
        }
    }
    val pickPhotos = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refresh() }

    BackHandler(searching || st.query.isNotEmpty()) { searching = false; vm.query.value = "" }
    val past = st.filter == ListFilter.PAST

    Scaffold(
        topBar = {
            if (searching) SearchTopBar(st.query, { vm.query.value = it }) { searching = false; vm.query.value = "" }
            else TopAppBar(
                title = { Text("내 쿠폰", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() }) },
                actions = {
                    if (st.hasCoupons) IconButton({ searching = true }) { Icon(Icons.Rounded.Search, "쿠폰 검색") }
                    IconButton({ nav.navigate(Route.Settings) }) { Icon(Icons.Rounded.SettingsIcon, "설정") }
                },
            )
        },
        floatingActionButton = {
            // review U6: filled primary keeps contrast in dark mode
            if (st.hasCoupons && !past && !searching) ExtendedFloatingActionButton(
                containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                onClick = { addSheet = true }, expanded = atTop,
                icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("추가", style = MaterialTheme.typography.labelLarge) },
                modifier = Modifier.semantics { contentDescription = "쿠폰 추가" },
            )
        },
        snackbarHost = { SnackbarHost(snackbar.host) },
    ) { pad ->
        if (!st.loaded) return@Scaffold // no empty-state flash before the first DB emission
        if (!st.hasCoupons) {
            Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = Spacing.l)) {
                // QA R1-17: new-images / notification banners matter most when the list is empty
                st.banner?.let { b -> HomeBannerView(b, st, vm, nav, notifPermission::launch) }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EmptyState(Icons.Rounded.ConfirmationNumber, "아직 쿠폰이 없어요", "쿠폰 사진을 고르면 알아서 읽어 등록해요",
                        primary = "사진에서 추가" to pickPhotos, secondary = "갤러리에서 쿠폰 찾기" to { nav.navigate(Route.Gallery) })
                }
                TextButton({ kakaoGuide = true }, Modifier.fillMaxWidth().heightIn(min = Spacing.touch).padding(bottom = Spacing.l)) {
                    Text("카카오톡 선물함 쿠폰은 이렇게 넣어요", style = MaterialTheme.typography.labelLarge)
                }
            }
            return@Scaffold
        }
        LazyColumn(Modifier.padding(pad).fillMaxSize(), listState,
            contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, bottom = 112.dp), // review U6: last card clears the FAB
            verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            if (!searching) st.banner?.let { b -> item(key = "banner") { HomeBannerView(b, st, vm, nav, notifPermission::launch) } }
            item(key = "filters") {
                // QA R1-05: at 200 % the chips leave no room, so the sort button gets its own line (FlowRow wraps it)
                FlowRow(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalArrangement = Arrangement.spacedBy(Spacing.s), itemVerticalAlignment = Alignment.CenterVertically) {
                    FilterChip(!past, { vm.filter.value = ListFilter.AVAILABLE }, { Text("사용 가능 ${st.availableCount}", style = MaterialTheme.typography.labelLarge) },
                        Modifier.heightIn(min = Spacing.touch))
                    FilterChip(past, { vm.filter.value = ListFilter.PAST }, { Text("지난 쿠폰", style = MaterialTheme.typography.labelLarge) },
                        Modifier.heightIn(min = Spacing.touch))
                    if (!isLargeFont()) Spacer(Modifier.weight(1f))
                    if (!past) SortMenu(st.sort) { vm.sort.value = it }
                }
            }
            // review U9: find a coupon at the counter in one tap
            // C2-10: the banner tap shows only today's and tomorrow's; this chip undoes it
            if (st.endingOnly && !past && !searching) item(key = "ending") {
                FilterChip(true, { vm.endingOnly.value = false }, { Text("오늘·내일 끝나는 쿠폰 · 해제", style = MaterialTheme.typography.labelLarge) },
                    Modifier.heightIn(min = Spacing.touch), trailingIcon = { Icon(Icons.Rounded.Close, null, Modifier.size(18.dp)) })
            }
            else if (!past && !searching && st.brands.size >= 2) item(key = "brands") {
                BrandChipRow(st.brands, st.query) { b -> vm.query.value = if (st.query == b) "" else b }
            }
            if (st.coupons.isEmpty()) item(key = "empty") {
                when {
                    st.query.isNotBlank() -> Text("‘${st.query}’과 맞는 쿠폰이 없어요", Modifier.fillMaxWidth().padding(vertical = Spacing.xxl),
                        style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    past -> EmptyState(Icons.Rounded.TaskAlt, "지난 쿠폰이 없어요", "다 쓴 쿠폰과 기간이 지난 쿠폰이 여기에 모여요")
                    else -> EmptyState(Icons.Rounded.ConfirmationNumber, "쓸 수 있는 쿠폰이 없어요", "새 쿠폰을 추가해 보세요", primary = "사진에서 추가" to pickPhotos)
                }
            }
            // review U3: identical names get the last four digits so they can be told apart
            val twins = st.coupons.groupingBy { it.brand to it.title() }.eachCount().filterValues { it > 1 }.keys
            items(st.coupons, key = { it.id }) { c ->
                CouponCard(c, st.today, onClick = { nav.navigate(Route.Detail(c.id)) }, modifier = Modifier.animateItem(),
                    codeTail = (c.brand to c.title()) in twins,
                    onUndo = if (c.status == CouponStatus.USED) ({
                        vm.undoUse(c.id); snackbar.show("${c.title()} 다시 쓸 수 있게 되돌렸어요")
                    }) else null)
            }
        }
    }

    if (addSheet) AddSheet({ addSheet = false }, onShare = { addSheet = false; shareGuide = true }, onPick = { addSheet = false; pickPhotos() },
        onCamera = if (canCapture) ({ addSheet = false; takePhoto() }) else null,
        onGallery = { addSheet = false; nav.navigate(Route.Gallery) }, onKakao = { addSheet = false; kakaoGuide = true })
    if (shareGuide) ShareGuideSheet({ shareGuide = false })
    // review U12: the path first-time users could not find on their own
    if (kakaoGuide) AppSheet({ kakaoGuide = false }, "카카오톡 선물함 쿠폰 넣기") {
        listOf(
            "카카오톡에서 [더보기] → [선물하기] → [선물함]을 열어요",
            "받은 쿠폰을 열고 오른쪽 위 ⋮ → [이미지 저장]을 눌러요. 없으면 화면을 캡처해도 돼요",
            "여기서 [사진 고르기]로 저장한 이미지를 골라요",
        ).forEachIndexed { i, step ->
            Row(Modifier.padding(vertical = Spacing.s)) {
                Text("${i + 1}", Modifier.width(28.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(step, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Spacer(Modifier.height(Spacing.m))
        PrimaryButton("사진 고르기", { kakaoGuide = false; pickPhotos() })
    }
    if (askNotif) ReminderTimeSheet(vm.reminderMinute, onDismiss = { vm.notificationsAsked() }) { minute ->
        // C2-08: the time is saved first, so it holds even if the permission is refused (AC2)
        vm.setReminderMinute(minute)
        vm.notificationsAsked()
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else openNotificationSettings(activity)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TopAppBar(
        navigationIcon = { IconButton(onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "검색 닫기") } },
        title = {
            TextField(query, onQuery, Modifier.fillMaxWidth().focusRequester(focus), singleLine = true,
                placeholder = { Text("브랜드·상품명·메모 검색") }, textStyle = MaterialTheme.typography.bodyLarge,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
        },
        actions = { if (query.isNotEmpty()) IconButton({ onQuery("") }) { Icon(Icons.Rounded.Close, "검색어 지우기") } },
    )
}

@Composable
private fun SortMenu(sort: ListSort, onSort: (ListSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = { s: ListSort -> if (s == ListSort.EXPIRY) "만료 임박순" else "최근 등록순" }
    Box {
        TextButton({ open = true }, Modifier.heightIn(min = Spacing.touch)) {
            Text(label(sort), style = MaterialTheme.typography.labelLarge)
            Icon(Icons.Rounded.ExpandMore, null, Modifier.size(20.dp))
        }
        DropdownMenu(open, { open = false }) {
            ListSort.entries.forEach { s ->
                DropdownMenuItem({ Text(label(s), style = MaterialTheme.typography.bodyLarge) }, { open = false; onSort(s) })
            }
        }
    }
}

@Composable
private fun HomeBannerView(b: HomeBanner, st: HomeState, vm: HomeViewModel, nav: NavHostController, request: (String) -> Unit) {
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val review = st.reviewCount
    val newImages = st.newImages
    val s = MaterialTheme.statusColors
    val cs = MaterialTheme.colorScheme
    when (b) {
        HomeBanner.CRASH -> InfoBanner(Icons.Rounded.BugReport, "지난번에 앱이 멈췄어요.", cs.surfaceContainerHighest, cs.onSurface) {
            BannerButton("닫기") { vm.crashBannerShown() }
            BannerButton(onClick = {
                vm.crashFile()?.let { context.startActivity(Sharing.intent(context, it, it.name, "text/plain")) }
                vm.crashBannerShown()
            }, "오류 기록 보내기")
        }
        HomeBanner.NOTIFICATIONS_OFF -> InfoBanner(Icons.Rounded.NotificationsOff, "알림이 꺼져 있어 만료를 알려 드릴 수 없어요", s.reviewContainer, cs.onSurface) {
            BannerButton(onClick = {
                if (Build.VERSION.SDK_INT >= 33 && !(context as MainActivity).settings.notificationAsked) {
                    context.settings.notificationAsked = true
                    request(Manifest.permission.POST_NOTIFICATIONS)
                } else openNotificationSettings(context)
            }, "켜기")
        }
        HomeBanner.USE_ASK -> st.useAsk?.let { c ->
            val nm = c.title()
            InfoBanner(Icons.Rounded.TaskAlt, "아까 $nm 썼어요?", cs.surfaceContainerHighest, cs.onSurface) {
                BannerButton("아니요") { vm.answerUseAsk(c.id, used = false) }
                if (c.type == CouponType.AMOUNT) BannerButton("쓴 금액 입력") {
                    vm.answerUseAsk(c.id, used = false); nav.navigate(Route.Detail(c.id, openUse = true))
                } else BannerButton("썼어요") {
                    vm.answerUseAsk(c.id, used = true); snackbar.show("$nm 사용 완료했어요", "되돌리기") { vm.undoUse(c.id) }
                }
            }
        }
        HomeBanner.ENDING_SOON -> InfoBanner(Icons.Rounded.Schedule, "오늘·내일 끝나는 쿠폰 ${st.endingSoon.size}개", s.soonContainer, cs.onSurface,
            onClick = { vm.filter.value = ListFilter.AVAILABLE; vm.query.value = ""; vm.endingOnly.value = true }) {
            BannerButton("닫기") { vm.hideEndingBanner() }
        }
        HomeBanner.NEEDS_REVIEW -> InfoBanner(Icons.Rounded.ErrorOutline, "확인할 쿠폰이 ${review}개 있어요", s.reviewContainer, cs.onSurface,
            onClick = { vm.state.value.firstReviewId?.let { nav.navigate(Route.EditCoupon(it)) } })
        HomeBanner.NEW_IMAGES -> InfoBanner(Icons.Rounded.ImageSearch, "새 쿠폰 이미지 ${newImages}장이 있어요", cs.primaryContainer, cs.onPrimaryContainer) {
            BannerButton("확인하기") { vm.reviewNewImages(); nav.navigate(Route.Import) }
        }
    }
}

/** S2 (C2-13: [공유] first, C2-24: camera when a camera app exists). */
@Composable
private fun AddSheet(onDismiss: () -> Unit, onShare: () -> Unit, onPick: () -> Unit, onCamera: (() -> Unit)?, onGallery: () -> Unit, onKakao: () -> Unit) {
    AppSheet(onDismiss, "쿠폰 추가") {
        AddOption(Icons.Rounded.Share, "카톡·문자에서 [공유]로 넣기", "사진을 길게 눌러 공유 → 내 쿠폰", onShare)
        AddOption(Icons.Rounded.PhotoLibrary, "사진 고르기", "한 번에 30장까지 골라요", onPick)
        if (onCamera != null) AddOption(Icons.Rounded.PhotoCamera, "카메라로 찍기", "종이 쿠폰을 찍어 넣어요", onCamera)
        AddOption(Icons.Rounded.ImageSearch, "갤러리에서 쿠폰 찾기", "쿠폰 같은 사진을 대신 찾아 드려요", onGallery)
        AddOption(Icons.Rounded.Redeem, "카카오톡 선물함 쿠폰", "선물함 → 이미지 저장 → 사진 고르기", onKakao)
    }
}

/** C2-13: three drawn steps (no real coupon, no real number, AC2) for the share path. */
@Composable
private fun ShareGuideSheet(onDismiss: () -> Unit) {
    AppSheet(onDismiss, "[공유] 한 번으로 넣어요") {
        listOf(
            Triple(Icons.Rounded.TouchApp, "쿠폰 사진을 길게 눌러요", "카카오톡 대화방·문자·갤러리 어디서든"),
            Triple(Icons.Rounded.Share, "[공유]를 눌러요", "사진 아래나 오른쪽 위 메뉴에 있어요"),
            Triple(Icons.Rounded.ConfirmationNumber, "'내 쿠폰'을 골라요", "알아서 읽어 확인 화면을 보여 드려요"),
        ).forEachIndexed { i, (icon, title, sub) ->
            Row(Modifier.fillMaxWidth().padding(vertical = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Surface(Modifier.size(72.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                }
                Spacer(Modifier.width(Spacing.l))
                Column(Modifier.weight(1f)) {
                    Text("${i + 1}. $title", style = MaterialTheme.typography.titleMedium)
                    Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text("카카오톡 선물함 쿠폰은 쿠폰을 열고 오른쪽 위 ⋮ → [이미지 저장] 뒤 [사진 고르기]로 넣어요", Modifier.padding(vertical = Spacing.m),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PrimaryButton("알겠어요", onDismiss)
    }
}

/** design-system §6.6 (C2-07): banner buttons have a border so they read as buttons. */
@Composable
private fun BannerButton(text: String, onClick: () -> Unit) = BannerButton(onClick, text)

@Composable
private fun BannerButton(onClick: () -> Unit, text: String) {
    OutlinedButton(onClick, Modifier.padding(start = Spacing.s).heightIn(min = Spacing.touch)) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** C2-08 / design-system §6.19: asks the time together with the permission (first registration). */
@Composable
private fun ReminderTimeSheet(initialMinute: Int, onDismiss: () -> Unit, onOk: (Int) -> Unit) {
    var minute by rememberSaveable { mutableStateOf(initialMinute) }
    var picker by remember { mutableStateOf(false) }
    AppSheet(onDismiss, "만료되기 전에 알려 드릴게요", "하루에 한 번, 그날 챙길 쿠폰을 모아 알려 드려요. 광고 알림은 없어요.") {
        Text("몇 시에 알려 드릴까요?", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth().padding(vertical = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            Text(timeLabel(minute), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            OutlinedButton({ picker = true }, Modifier.heightIn(min = Spacing.touch)) { Text("바꾸기") }
        }
        Spacer(Modifier.height(Spacing.m))
        PrimaryButton("좋아요", { onOk(minute) })
        SecondaryButton("나중에", onDismiss)
    }
    if (picker) TimePickDialog(minute, { picker = false }) { minute = it; picker = false }
}

/**
 * design-system §6.15 (C2-15): brand chips; when the row runs off screen a fade + arrow says there is more
 * (at 200 % only two or three chips fit).
 */
@Composable
private fun BrandChipRow(brands: List<String>, selected: String, onPick: (String) -> Unit) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val surface = MaterialTheme.colorScheme.surface
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val step = with(LocalDensity.current) { maxWidth.toPx() * 0.8f }
        LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.semantics { contentDescription = "브랜드로 거르기" }) {
            items(brands, key = { it }) { b ->
                FilterChip(selected == b, { onPick(b) }, { Text(b, style = MaterialTheme.typography.labelLarge) }, Modifier.heightIn(min = Spacing.touch))
            }
        }
        if (state.canScrollForward) Box(Modifier.align(Alignment.CenterEnd).width(64.dp).height(Spacing.touch)
            .background(Brush.horizontalGradient(listOf(Color.Transparent, surface, surface)))) {
            IconButton({ scope.launch { state.animateScrollBy(step) } }, Modifier.align(Alignment.CenterEnd)) {
                Icon(Icons.Rounded.ChevronRight, "브랜드 더 보기")
            }
        }
        if (state.canScrollBackward) Box(Modifier.align(Alignment.CenterStart).width(64.dp).height(Spacing.touch)
            .background(Brush.horizontalGradient(listOf(surface, surface, Color.Transparent)))) {
            IconButton({ scope.launch { state.animateScrollBy(-step) } }, Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.Rounded.ChevronLeft, "앞 브랜드 보기")
            }
        }
    }
}

@Composable
private fun AddOption(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, onClick: () -> Unit) {
    androidx.compose.material3.Surface(onClick, Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(vertical = Spacing.xs),
        shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        ListItem(
            headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
            supportingContent = { Text(sub, style = MaterialTheme.typography.bodyMedium) },
            leadingContent = { Icon(icon, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}
