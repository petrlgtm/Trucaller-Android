package com.example.trucaller.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.trucaller.ui.admin.AdminAlarmLogsScreen
import com.example.trucaller.ui.admin.AdminCallerIdScreen
import com.example.trucaller.ui.admin.AdminDashboardScreen
import com.example.trucaller.ui.admin.AdminDeviceDetailScreen
import com.example.trucaller.ui.admin.AdminDevicesScreen
import com.example.trucaller.ui.admin.AdminSettingsScreen
import com.example.trucaller.ui.admin.AdminStolenReportsScreen
import com.example.trucaller.ui.admin.AdminUserDetailScreen
import com.example.trucaller.ui.admin.AdminUsersScreen
import com.example.trucaller.ui.auth.AdminLoginScreen
import com.example.trucaller.ui.auth.ForgotPasswordScreen
import com.example.trucaller.ui.auth.LoginScreen
import com.example.trucaller.ui.auth.OtpVerificationScreen
import com.example.trucaller.ui.auth.RegisterScreen
import com.example.trucaller.ui.auth.SplashScreen
import com.example.trucaller.ui.main.CallerIdScreen
import com.example.trucaller.ui.main.ContactsScreen
import com.example.trucaller.ui.main.HomeScreen
import com.example.trucaller.ui.main.ProfileScreen
import com.example.trucaller.ui.main.SecurityScreen
import com.example.trucaller.ui.stolen.RemoteActionsScreen
import com.example.trucaller.ui.stolen.ReportStolenScreen
import com.example.trucaller.ui.theme.Brand
import com.example.trucaller.ui.theme.Inactive
import com.example.trucaller.ui.theme.Surface
import com.example.trucaller.viewmodel.AdminSettingsViewModel
import com.example.trucaller.viewmodel.AlarmViewModel
import com.example.trucaller.viewmodel.AuthViewModel
import com.example.trucaller.viewmodel.CallerIdViewModel
import com.example.trucaller.viewmodel.ContactsViewModel
import com.example.trucaller.viewmodel.DeviceViewModel
import com.example.trucaller.viewmodel.StolenReportViewModel

sealed class TabItem(val route: String, val title: String, val icon: ImageVector) {
    data object Home : TabItem("tab_home", "Home", Icons.Default.Home)
    data object Search : TabItem("tab_search", "Search", Icons.Default.Search)
    data object Contacts : TabItem("tab_contacts", "Contacts", Icons.Outlined.People)
    data object Profile : TabItem("tab_profile", "Profile", Icons.Default.Person)
}

val tabs = listOf(TabItem.Home, TabItem.Search, TabItem.Contacts, TabItem.Profile)

@Composable
fun TruCallerNavGraph(authViewModel: AuthViewModel) {
    val navController = rememberNavController()
    val deviceViewModel: DeviceViewModel = viewModel(factory = DeviceViewModel.Factory)
    val contactsViewModel: ContactsViewModel = viewModel(factory = ContactsViewModel.Factory)
    val callerIdViewModel: CallerIdViewModel = viewModel(factory = CallerIdViewModel.Factory)
    val stolenReportViewModel: StolenReportViewModel = viewModel(factory = StolenReportViewModel.Factory)
    val alarmViewModel: AlarmViewModel = viewModel(factory = AlarmViewModel.Factory)
    val adminSettingsViewModel: AdminSettingsViewModel = viewModel(factory = AdminSettingsViewModel.Factory)

    NavHost(navController = navController, startDestination = "splash") {
        composable("splash") {
            SplashScreen(navController = navController, authViewModel = authViewModel)
        }
        composable("login") {
            LoginScreen(navController = navController, authViewModel = authViewModel)
        }
        composable("register") {
            RegisterScreen(navController = navController, authViewModel = authViewModel)
        }
        composable("otp/{phone}") { backStackEntry ->
            val phone = backStackEntry.arguments?.getString("phone") ?: ""
            OtpVerificationScreen(
                navController = navController,
                authViewModel = authViewModel,
                phone = phone
            )
        }
        composable("forgot_password") {
            ForgotPasswordScreen(navController = navController, authViewModel = authViewModel)
        }
        composable("main") {
            MainScreen(
                rootNavController = navController,
                authViewModel = authViewModel,
                deviceViewModel = deviceViewModel,
                contactsViewModel = contactsViewModel,
                callerIdViewModel = callerIdViewModel,
                stolenReportViewModel = stolenReportViewModel,
                alarmViewModel = alarmViewModel
            )
        }
        composable("report_stolen") {
            ReportStolenScreen(
                navController = navController,
                authViewModel = authViewModel,
                deviceViewModel = deviceViewModel,
                stolenReportViewModel = stolenReportViewModel
            )
        }
        composable("remote_actions") {
            RemoteActionsScreen(
                navController = navController,
                authViewModel = authViewModel,
                deviceViewModel = deviceViewModel,
                stolenReportViewModel = stolenReportViewModel,
                alarmViewModel = alarmViewModel
            )
        }
        composable("security") {
            SecurityScreen(navController = navController, authViewModel = authViewModel)
        }
        // Admin routes
        composable("admin_login") {
            AdminLoginScreen(navController = navController, authViewModel = authViewModel)
        }
        composable("admin_dashboard") {
            AdminDashboardScreen(
                navController = navController,
                authViewModel = authViewModel,
                deviceViewModel = deviceViewModel,
                stolenReportViewModel = stolenReportViewModel,
                alarmViewModel = alarmViewModel,
                contactsViewModel = contactsViewModel
            )
        }
        composable("admin_devices") {
            AdminDevicesScreen(navController = navController, deviceViewModel = deviceViewModel)
        }
        composable("admin_device_detail/{deviceId}") { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: ""
            AdminDeviceDetailScreen(
                navController = navController,
                deviceId = deviceId,
                deviceViewModel = deviceViewModel,
                alarmViewModel = alarmViewModel,
                authViewModel = authViewModel
            )
        }
        composable("admin_users") {
            AdminUsersScreen(navController = navController)
        }
        composable("admin_user_detail/{userId}") { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            AdminUserDetailScreen(
                navController = navController,
                userId = userId,
                deviceViewModel = deviceViewModel,
                stolenReportViewModel = stolenReportViewModel
            )
        }
        composable("admin_stolen_reports") {
            AdminStolenReportsScreen(
                navController = navController,
                stolenReportViewModel = stolenReportViewModel
            )
        }
        composable("admin_caller_id") {
            AdminCallerIdScreen(
                navController = navController,
                callerIdViewModel = callerIdViewModel
            )
        }
        composable("admin_alarm_logs") {
            AdminAlarmLogsScreen(navController = navController, alarmViewModel = alarmViewModel)
        }
        composable("admin_settings") {
            AdminSettingsScreen(
                navController = navController,
                authViewModel = authViewModel,
                adminSettingsViewModel = adminSettingsViewModel
            )
        }
    }
}

@Composable
fun MainScreen(
    rootNavController: NavController,
    authViewModel: AuthViewModel,
    deviceViewModel: DeviceViewModel,
    contactsViewModel: ContactsViewModel,
    callerIdViewModel: CallerIdViewModel,
    stolenReportViewModel: StolenReportViewModel,
    alarmViewModel: AlarmViewModel
) {
    val tabNavController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Surface) {
                val navBackStackEntry by tabNavController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                tabs.forEach { tab ->
                    NavigationBarItem(
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) },
                        selected = currentRoute == tab.route,
                        onClick = {
                            tabNavController.navigate(tab.route) {
                                popUpTo(tabNavController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Brand,
                            selectedTextColor = Brand,
                            unselectedIconColor = Inactive,
                            unselectedTextColor = Inactive
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = tabNavController,
            startDestination = TabItem.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(TabItem.Home.route) {
                HomeScreen(
                    rootNavController = rootNavController,
                    authViewModel = authViewModel,
                    deviceViewModel = deviceViewModel,
                    contactsViewModel = contactsViewModel,
                    alarmViewModel = alarmViewModel,
                    stolenReportViewModel = stolenReportViewModel
                )
            }
            composable(TabItem.Search.route) {
                CallerIdScreen(
                    callerIdViewModel = callerIdViewModel,
                    authViewModel = authViewModel
                )
            }
            composable(TabItem.Contacts.route) {
                ContactsScreen(
                    authViewModel = authViewModel,
                    contactsViewModel = contactsViewModel
                )
            }
            composable(TabItem.Profile.route) {
                ProfileScreen(
                    rootNavController = rootNavController,
                    authViewModel = authViewModel,
                    deviceViewModel = deviceViewModel
                )
            }
        }
    }
}
