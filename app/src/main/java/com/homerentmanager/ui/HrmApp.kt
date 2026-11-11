package com.homerentmanager.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.homerentmanager.AppContainer
import com.homerentmanager.HrmApplication
import com.homerentmanager.ui.screens.backup.BackupScreen
import com.homerentmanager.ui.screens.billing.BillDetailScreen
import com.homerentmanager.ui.screens.billing.BillingScreen
import com.homerentmanager.ui.screens.dashboard.DashboardScreen
import com.homerentmanager.ui.screens.electricity.ElectricityScreen
import com.homerentmanager.ui.screens.history.TenancyHistoryScreen
import com.homerentmanager.ui.screens.more.MoreScreen
import com.homerentmanager.ui.screens.payments.PaymentsScreen
import com.homerentmanager.ui.screens.reports.ReportsScreen
import com.homerentmanager.ui.screens.rooms.RoomDetailScreen
import com.homerentmanager.ui.screens.rooms.RoomsScreen
import com.homerentmanager.ui.screens.settings.SettingsScreen
import com.homerentmanager.ui.screens.tenants.TenantDetailScreen
import com.homerentmanager.ui.screens.tenants.TenantsScreen
import com.homerentmanager.ui.viewmodel.HrmViewModelFactory
import java.util.UUID

object Routes {
    const val DASHBOARD = "dashboard"
    const val ROOMS = "rooms"
    const val ROOM_DETAIL = "room/{roomId}"
    const val TENANTS = "tenants"
    const val TENANT_DETAIL = "tenant/{tenantId}"
    const val HISTORY = "history"
    const val ELECTRICITY = "electricity"
    const val BILLING = "billing"
    const val BILL_DETAIL = "bill/{billId}"
    const val PAYMENTS = "payments"
    const val REPORTS = "reports"
    const val BACKUP = "backup"
    const val SETTINGS = "settings"
    const val MORE = "more"

    fun room(id: UUID) = "room/$id"
    fun tenant(id: UUID) = "tenant/$id"
    fun bill(id: UUID) = "bill/$id"
}

/**
 * Ignores duplicate taps that would push the same screen twice. Rapid repeated taps used to
 * stack duplicate destinations, which made the back stack appear stuck on a frozen screen.
 */
private fun NavHostController.navigateSafely(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) { launchSingleTop = true }
}

/**
 * Bottom navigation uses state saving so each tab keeps its scroll position. Tapping the tab
 * that is already selected is a no-op, which prevents repeated taps from re-adding entries
 * and making the back stack look frozen.
 */
private fun NavHostController.navigateTopLevel(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private data class BottomDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val bottomDestinations = listOf(
    BottomDestination(Routes.DASHBOARD, "Home", Icons.Outlined.Dashboard),
    BottomDestination(Routes.ROOMS, "Rooms", Icons.Outlined.MeetingRoom),
    BottomDestination(Routes.TENANTS, "Tenants", Icons.Outlined.People),
    BottomDestination(Routes.BILLING, "Billing", Icons.Outlined.ReceiptLong),
    BottomDestination(Routes.MORE, "More", Icons.Outlined.MoreHoriz),
)

@Composable
inline fun <reified VM : ViewModel> hrmViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val app = LocalContext.current.applicationContext as HrmApplication
    val factory = remember(app, key) {
        HrmViewModelFactory(app.container) { container -> create(container) }
    }
    return viewModel(key = key, factory = factory)
}

@Composable
fun HrmApp(container: AppContainer, openBackupInitially: Boolean) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val isTopLevel = bottomDestinations.any { it.route == route }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    var openBackupInitiallyConsumed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(openBackupInitially) {
        if (openBackupInitially && !openBackupInitiallyConsumed) {
            openBackupInitiallyConsumed = true
            navController.navigateSafely(Routes.BACKUP)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (isTopLevel) {
                NavigationBar {
                    bottomDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = route == destination.route,
                            onClick = {
                                navController.navigateTopLevel(destination.route)
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier.padding(padding),
            enterTransition = {
                fadeIn(tween(180)) + slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    tween(220),
                    initialOffset = { it / 12 },
                )
            },
            exitTransition = {
                fadeOut(tween(150)) + slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    tween(220),
                    targetOffset = { it / 12 },
                )
            },
            popEnterTransition = {
                fadeIn(tween(180)) + slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    tween(220),
                    initialOffset = { it / 12 },
                )
            },
            popExitTransition = {
                fadeOut(tween(150)) + slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    tween(220),
                    targetOffset = { it / 12 },
                )
            },
        ) {
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    container = container,
                    onNavigate = { routeValue -> navController.navigateSafely(routeValue) },
                    onOpenBill = { navController.navigateSafely(Routes.bill(it)) },
                )
            }
            composable(Routes.ROOMS) {
                RoomsScreen(
                    container = container,
                    onOpenRoom = { navController.navigateSafely(Routes.room(it)) },
                )
            }
            composable(Routes.ROOM_DETAIL) { entry ->
                val id = UUID.fromString(entry.arguments?.getString("roomId") ?: error("Missing room id"))
                RoomDetailScreen(
                    container = container,
                    roomId = id,
                    onBack = { navController.popBackStack() },
                    onOpenBill = { navController.navigateSafely(Routes.bill(it)) },
                    onOpenTenant = { navController.navigateSafely(Routes.tenant(it)) },
                    onAddTenant = { navController.navigateSafely(Routes.TENANTS) },
                )
            }
            composable(Routes.TENANTS) {
                TenantsScreen(
                    container = container,
                    onOpenTenant = { navController.navigateSafely(Routes.tenant(it)) },
                )
            }
            composable(Routes.TENANT_DETAIL) { entry ->
                val id = UUID.fromString(entry.arguments?.getString("tenantId") ?: error("Missing tenant id"))
                TenantDetailScreen(
                    container = container,
                    tenantId = id,
                    onBack = { navController.popBackStack() },
                    onOpenBill = { navController.navigateSafely(Routes.bill(it)) },
                    onOpenRoom = { navController.navigateSafely(Routes.room(it)) },
                )
            }
            composable(Routes.HISTORY) {
                TenancyHistoryScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                    onOpenTenant = { navController.navigateSafely(Routes.tenant(it)) },
                    onOpenRoom = { navController.navigateSafely(Routes.room(it)) },
                )
            }
            composable(Routes.ELECTRICITY) {
                ElectricityScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.BILLING) {
                BillingScreen(
                    container = container,
                    onOpenBill = { navController.navigateSafely(Routes.bill(it)) },
                )
            }
            composable(Routes.BILL_DETAIL) { entry ->
                val id = UUID.fromString(entry.arguments?.getString("billId") ?: error("Missing bill id"))
                BillDetailScreen(
                    container = container,
                    billId = id,
                    onBack = { navController.popBackStack() },
                    onOpenTenant = { navController.navigateSafely(Routes.tenant(it)) },
                    onOpenRoom = { navController.navigateSafely(Routes.room(it)) },
                )
            }
            composable(Routes.PAYMENTS) {
                PaymentsScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                    onOpenBill = { navController.navigateSafely(Routes.bill(it)) },
                )
            }
            composable(Routes.REPORTS) {
                ReportsScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.BACKUP) {
                BackupScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.MORE) {
                MoreScreen(
                    onNavigate = { navController.navigateSafely(it) },
                )
            }
        }
    }
}
