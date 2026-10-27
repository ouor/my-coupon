package com.ouor.mycoupon.ui.home

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
                st.banner?.let { b -> HomeBannerView(b, st.reviewCount, st.newImages, vm, nav, notifPermission::launch) }
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
            if (!searching) st.banner?.let { b -> item(key = "banner") { HomeBannerView(b, st.reviewCount, st.newImages, vm, nav, notifPermission::launch) } }
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
            if (!past && !searching && st.brands.size >= 2) item(key = "brands") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    items(st.brands, key = { it }) { b ->
                        FilterChip(st.query == b, { vm.query.value = if (st.query == b) "" else b }, { Text(b, style = MaterialTheme.typography.labelLarge) },
                            Modifier.heightIn(min = Spacing.touch))
                    }
                }
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
                        vm.undoUse(c.id); snackbar.show("다시 쓸 수 있게 되돌렸어요")
                    }) else null)
            }
        }
    }

    if (addSheet) AddSheet({ addSheet = false }, { addSheet = false; pickPhotos() }, { addSheet = false; nav.navigate(Route.Gallery) },
        { addSheet = false; kakaoGuide = true })
    // review U12: the path first-time users could not find on their own
    if (kakaoGuide) AppSheet({ kakaoGuide = false }, "카카오톡 선물함 쿠폰 넣기") {
        listOf(
            "카카오톡에서 [더보기] → [선물하기] → [선물함]을 열어요",
            "받은 쿠폰을 누르고 [이미지 저장]을 눌러요. 없으면 화면을 캡처해도 돼요",
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
    if (askNotif) AppSheet({ vm.notificationsAsked() }, "만료되기 전에 알려 드릴게요", "하루에 한 번까지만 보내요. 광고 알림은 없어요.") {
        PrimaryButton("알림 받기", {
            vm.notificationsAsked()
            if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else openNotificationSettings(activity)
        })
        SecondaryButton("나중에", { vm.notificationsAsked() })
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
                placeholder = { Text("브랜드나 상품 이름") }, textStyle = MaterialTheme.typography.bodyLarge,
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
private fun HomeBannerView(b: HomeBanner, review: Int, newImages: Int, vm: HomeViewModel, nav: NavHostController, request: (String) -> Unit) {
    val context = LocalContext.current
    val s = MaterialTheme.statusColors
    val cs = MaterialTheme.colorScheme
    when (b) {
        HomeBanner.CRASH -> InfoBanner(Icons.Rounded.BugReport, "지난번에 앱이 멈췄어요.", cs.surfaceContainerHighest, cs.onSurface) {
            TextButton({ vm.crashBannerShown() }) { Text("닫기") }
            TextButton({
                vm.crashFile()?.let { context.startActivity(Sharing.intent(context, it, it.name, "text/plain")) }
                vm.crashBannerShown()
            }) { Text("오류 기록 보내기") }
        }
        HomeBanner.NOTIFICATIONS_OFF -> InfoBanner(Icons.Rounded.NotificationsOff, "알림이 꺼져 있어 만료를 알려 드릴 수 없어요", s.reviewContainer, cs.onSurface) {
            TextButton({
                if (Build.VERSION.SDK_INT >= 33 && !(context as MainActivity).settings.notificationAsked) {
                    context.settings.notificationAsked = true
                    request(Manifest.permission.POST_NOTIFICATIONS)
                } else openNotificationSettings(context)
            }) { Text("켜기") }
        }
        HomeBanner.NEEDS_REVIEW -> InfoBanner(Icons.Rounded.ErrorOutline, "확인할 쿠폰이 ${review}개 있어요", s.reviewContainer, cs.onSurface,
            onClick = { vm.state.value.firstReviewId?.let { nav.navigate(Route.EditCoupon(it)) } })
        HomeBanner.NEW_IMAGES -> InfoBanner(Icons.Rounded.ImageSearch, "새 쿠폰 이미지 ${newImages}장이 있어요", cs.primaryContainer, cs.onPrimaryContainer) {
            TextButton({ vm.reviewNewImages(); nav.navigate(Route.Import) }) { Text("확인하기") }
        }
    }
}

/** S2 */
@Composable
private fun AddSheet(onDismiss: () -> Unit, onPick: () -> Unit, onGallery: () -> Unit, onKakao: () -> Unit) {
    AppSheet(onDismiss, "쿠폰 추가") {
        AddOption(Icons.Rounded.PhotoLibrary, "사진 고르기", "한 번에 30장까지 골라요", onPick)
        AddOption(Icons.Rounded.ImageSearch, "갤러리에서 쿠폰 찾기", "쿠폰 같은 사진을 대신 찾아 드려요", onGallery)
        AddOption(Icons.Rounded.CardGiftcard, "카카오톡 선물함 쿠폰", "이미지로 저장해서 넣는 방법", onKakao)
        Spacer(Modifier.height(Spacing.s))
        Text("다른 앱에서 이미지를 [공유] → 내 쿠폰을 골라도 돼요", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
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
