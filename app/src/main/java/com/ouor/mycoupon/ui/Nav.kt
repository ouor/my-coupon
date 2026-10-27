package com.ouor.mycoupon.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import com.ouor.mycoupon.ui.settings.BackupScreen
import com.ouor.mycoupon.ui.coupon.BarcodeFullScreen
import com.ouor.mycoupon.ui.coupon.DetailScreen
import com.ouor.mycoupon.ui.coupon.EditCouponScreen
import com.ouor.mycoupon.ui.coupon.EditDraftScreen
import com.ouor.mycoupon.ui.gallery.GalleryScreen
import com.ouor.mycoupon.ui.home.HomeScreen
import com.ouor.mycoupon.ui.importing.ImportScreen
import com.ouor.mycoupon.ui.settings.LicensesScreen
import com.ouor.mycoupon.ui.onboarding.OnboardingScreen
import com.ouor.mycoupon.ui.settings.PrivacyScreen
import com.ouor.mycoupon.ui.settings.RemindersScreen
import com.ouor.mycoupon.ui.settings.SettingsScreen
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
        // design-system §5: S1 → S6 within 200 ms; system "remove animations" scales these to 0
        NavHost(nav, startDestination = start,
            enterTransition = { fadeIn(tween(150)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(200), initialOffset = { it / 12 }) },
            exitTransition = { fadeOut(tween(100)) },
            popEnterTransition = { fadeIn(tween(150)) },
            popExitTransition = { fadeOut(tween(100)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(200), targetOffset = { it / 12 }) },
        ) {
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
