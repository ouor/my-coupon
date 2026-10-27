package com.ouor.mycoupon.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.ouor.mycoupon.ui.component.AppSnackbar
import com.ouor.mycoupon.ui.component.LocalSnackbar
import com.ouor.mycoupon.ui.screen.BackupScreen
import com.ouor.mycoupon.ui.screen.BarcodeFullScreen
import com.ouor.mycoupon.ui.screen.DetailScreen
import com.ouor.mycoupon.ui.screen.EditCouponScreen
import com.ouor.mycoupon.ui.screen.EditDraftScreen
import com.ouor.mycoupon.ui.screen.GalleryScreen
import com.ouor.mycoupon.ui.screen.HomeScreen
import com.ouor.mycoupon.ui.screen.ImportScreen
import com.ouor.mycoupon.ui.screen.LicensesScreen
import com.ouor.mycoupon.ui.screen.OnboardingScreen
import com.ouor.mycoupon.ui.screen.PrivacyScreen
import com.ouor.mycoupon.ui.screen.RemindersScreen
import com.ouor.mycoupon.ui.screen.SettingsScreen
import kotlinx.serialization.Serializable

/** Type-safe routes (screen ids from docs/design/screens.md). */
object Route {
    @Serializable data object Onboarding // S0
    @Serializable data object Home // S1 / S13
    @Serializable data object Import // S3 + S4
    @Serializable data class EditDraft(val key: Int) // S5 for an import draft
    @Serializable data class EditCoupon(val id: Long) // S5 for a stored coupon
    @Serializable data class Detail(val id: Long, val fromReminder: Boolean = false) // S6
    @Serializable data class BarcodeFull(val id: Long) // S7
    @Serializable data object Gallery // S9
    @Serializable data object Settings // S10
    @Serializable data object Reminders // S11
    @Serializable data object Backup // S14
    @Serializable data object Privacy
    @Serializable data object Licenses
}

@Composable
fun AppNavHost(nav: NavHostController, start: Any) {
    val host = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val snackbar = remember { AppSnackbar(host, scope) }
    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        NavHost(nav, startDestination = start) {
            composable<Route.Onboarding> { OnboardingScreen { nav.navigate(Route.Home) { popUpTo(Route.Onboarding) { inclusive = true } } } }
            composable<Route.Home> { HomeScreen(nav) }
            composable<Route.Import> { ImportScreen(nav) }
            composable<Route.EditDraft> { EditDraftScreen(it.toRoute<Route.EditDraft>().key, nav) }
            composable<Route.EditCoupon> { EditCouponScreen(it.toRoute<Route.EditCoupon>().id, nav) }
            composable<Route.Detail> { val r = it.toRoute<Route.Detail>(); DetailScreen(r.id, r.fromReminder, nav) }
            composable<Route.BarcodeFull> { BarcodeFullScreen(it.toRoute<Route.BarcodeFull>().id, nav) }
            composable<Route.Gallery> { GalleryScreen(nav) }
            composable<Route.Settings> { SettingsScreen(nav) }
            composable<Route.Reminders> { RemindersScreen(nav) }
            composable<Route.Backup> { BackupScreen(nav) }
            composable<Route.Privacy> { PrivacyScreen(nav) }
            composable<Route.Licenses> { LicensesScreen(nav) }
        }
    }
}

/** Pops back to S1 (keeps exactly one Home at the bottom). */
fun NavHostController.toHome() {
    if (!popBackStack(Route.Home, inclusive = false)) navigate(Route.Home) { popUpTo(0) }
}
