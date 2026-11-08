package com.homerentmanager.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.remember
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
    LaunchedEffect(openBackupInitially) {
        if (openBackupInitially) navController.navigate(Routes.BACKUP)
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
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
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
        ) {
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    container = container,
                    onNavigate = { routeValue -> navController.navigate(routeValue) },
                    onOpenBill = { navController.navigate(Routes.bill(it)) },
                )
            }
            composable(Routes.ROOMS) {
                RoomsScreen(
                    container = container,
                    onOpenRoom = { navController.navigate(Routes.room(it)) },
                )
            }
            composable(Routes.ROOM_DETAIL) { entry ->
                val id = UUID.fromString(entry.arguments?.getString("roomId") ?: error("Missing room id"))
                RoomDetailScreen(
                    container = container,
                    roomId = id,
                    onBack = { navController.popBackStack() },
                    onOpenBill = { navController.navigate(Routes.bill(it)) },
                    onOpenTenant = { navController.navigate(Routes.tenant(it)) },
                    onAddTenant = { navController.navigate(Routes.TENANTS) },
                )
            }
            composable(Routes.TENANTS) {
                TenantsScreen(
                    container = container,
                    onOpenTenant = { navController.navigate(Routes.tenant(it)) },
                )
            }
            composable(Routes.TENANT_DETAIL) { entry ->
                val id = UUID.fromString(entry.arguments?.getString("tenantId") ?: error("Missing tenant id"))
                TenantDetailScreen(
                    container = container,
                    tenantId = id,
                    onBack = { navController.popBackStack() },
                    onOpenBill = { navController.navigate(Routes.bill(it)) },
                    onOpenRoom = { navController.navigate(Routes.room(it)) },
                )
            }
            composable(Routes.HISTORY) {
                TenancyHistoryScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                    onOpenTenant = { navController.navigate(Routes.tenant(it)) },
                    onOpenRoom = { navController.navigate(Routes.room(it)) },
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
                    onOpenBill = { navController.navigate(Routes.bill(it)) },
                )
            }
            composable(Routes.BILL_DETAIL) { entry ->
                val id = UUID.fromString(entry.arguments?.getString("billId") ?: error("Missing bill id"))
                BillDetailScreen(
                    container = container,
                    billId = id,
                    onBack = { navController.popBackStack() },
                    onOpenTenant = { navController.navigate(Routes.tenant(it)) },
                    onOpenRoom = { navController.navigate(Routes.room(it)) },
                )
            }
            composable(Routes.PAYMENTS) {
                PaymentsScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                    onOpenBill = { navController.navigate(Routes.bill(it)) },
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
                    onNavigate = { navController.navigate(it) },
                )
            }
        }
    }
}
