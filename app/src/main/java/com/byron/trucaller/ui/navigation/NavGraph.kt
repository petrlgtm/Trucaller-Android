package com.byron.trucaller.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.byron.trucaller.ui.admin.AdminAlarmLogsScreen
import com.byron.trucaller.ui.admin.AdminCallerIdScreen
import com.byron.trucaller.ui.admin.AdminDashboardScreen
import com.byron.trucaller.ui.admin.AdminDeviceDetailScreen
import com.byron.trucaller.ui.admin.AdminDevicesScreen
import com.byron.trucaller.ui.admin.AdminSettingsScreen
import com.byron.trucaller.ui.admin.AdminStolenReportsScreen
import com.byron.trucaller.ui.admin.AdminUserDetailScreen
import com.byron.trucaller.ui.admin.AdminUsersScreen
import com.byron.trucaller.ui.auth.AdminLoginScreen
import com.byron.trucaller.ui.auth.ForgotPasswordScreen
import com.byron.trucaller.ui.auth.LoginScreen
import com.byron.trucaller.ui.auth.OtpVerificationScreen
import com.byron.trucaller.ui.auth.RegisterScreen
import com.byron.trucaller.ui.auth.SplashScreen
import com.byron.trucaller.ui.main.CallerIdScreen
import com.byron.trucaller.ui.main.ContactsScreen
import com.byron.trucaller.ui.main.ConversationScreen
import com.byron.trucaller.ui.main.HomeScreen
import com.byron.trucaller.ui.main.MessagesScreen
import com.byron.trucaller.ui.main.ProfileScreen
import com.byron.trucaller.ui.main.SecurityScreen
import com.byron.trucaller.ui.stolen.RemoteActionsScreen
import com.byron.trucaller.ui.stolen.ReportStolenScreen
import com.byron.trucaller.ui.theme.Brand
import com.byron.trucaller.ui.theme.BrandDark
import com.byron.trucaller.ui.theme.Inactive
import com.byron.trucaller.ui.theme.Surface
import com.byron.trucaller.ui.theme.SurfaceCard
import com.byron.trucaller.viewmodel.AdminSettingsViewModel
import com.byron.trucaller.viewmodel.AlarmViewModel
import com.byron.trucaller.viewmodel.AuthViewModel
import com.byron.trucaller.viewmodel.CallerIdViewModel
import com.byron.trucaller.viewmodel.ContactsViewModel
import com.byron.trucaller.viewmodel.DeviceViewModel
import com.byron.trucaller.viewmodel.SmsViewModel
import com.byron.trucaller.viewmodel.StolenReportViewModel

sealed class TabItem(val route: String, val title: String, val icon: ImageVector) {
    data object Home : TabItem("tab_home", "Home", Icons.Default.Home)
    data object Messages : TabItem("tab_messages", "Messages", Icons.AutoMirrored.Filled.Chat)
    data object Contacts : TabItem("tab_contacts", "Contacts", Icons.Outlined.People)
    data object Profile : TabItem("tab_profile", "Profile", Icons.Default.Person)
}

val tabs = listOf(TabItem.Home, TabItem.Messages, TabItem.Contacts, TabItem.Profile)

// Shared transition specs
private val slideInDuration = 300
private val fadeSpec = tween<Float>(200)

@Composable
fun TruCallerNavGraph(authViewModel: AuthViewModel) {
    val navController = rememberNavController()
    val deviceViewModel: DeviceViewModel = viewModel(factory = DeviceViewModel.Factory)
    val contactsViewModel: ContactsViewModel = viewModel(factory = ContactsViewModel.Factory)
    val callerIdViewModel: CallerIdViewModel = viewModel(factory = CallerIdViewModel.Factory)
    val stolenReportViewModel: StolenReportViewModel = viewModel(factory = StolenReportViewModel.Factory)
    val alarmViewModel: AlarmViewModel = viewModel(factory = AlarmViewModel.Factory)
    val adminSettingsViewModel: AdminSettingsViewModel = viewModel(factory = AdminSettingsViewModel.Factory)
    val smsViewModel: SmsViewModel = viewModel(factory = SmsViewModel.Factory)

    NavHost(
        navController = navController,
        startDestination = "splash",
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                tween(slideInDuration, easing = FastOutSlowInEasing)
            ) + fadeIn(fadeSpec)
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                tween(slideInDuration, easing = FastOutSlowInEasing)
            ) + fadeOut(fadeSpec)
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                tween(slideInDuration, easing = FastOutSlowInEasing)
            ) + fadeIn(fadeSpec)
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                tween(slideInDuration, easing = FastOutSlowInEasing)
            ) + fadeOut(fadeSpec)
        }
    ) {
        // Splash — fade only
        composable(
            "splash",
            enterTransition = { fadeIn(tween(0)) },
            exitTransition = { fadeOut(tween(300)) }
        ) {
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
        composable(
            "main",
            enterTransition = { fadeIn(tween(400)) },
            exitTransition = { fadeOut(tween(200)) }
        ) {
            MainScreen(
                rootNavController = navController,
                authViewModel = authViewModel,
                deviceViewModel = deviceViewModel,
                contactsViewModel = contactsViewModel,
                callerIdViewModel = callerIdViewModel,
                stolenReportViewModel = stolenReportViewModel,
                alarmViewModel = alarmViewModel,
                smsViewModel = smsViewModel
            )
        }
        composable("sms_conversation/{address}") { backStackEntry ->
            val address = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("address") ?: "", "UTF-8"
            )
            ConversationScreen(
                navController = navController,
                address = address,
                authViewModel = authViewModel,
                smsViewModel = smsViewModel
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
    alarmViewModel: AlarmViewModel,
    smsViewModel: SmsViewModel
) {
    val tabNavController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceCard,
                tonalElevation = 0.dp,
                modifier = Modifier.shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                )
            ) {
                val navBackStackEntry by tabNavController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                tabs.forEach { tab ->
                    val selected = currentRoute == tab.route
                    NavigationBarItem(
                        icon = {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    tab.icon,
                                    contentDescription = tab.title,
                                    modifier = Modifier.size(if (selected) 26.dp else 22.dp)
                                )
                                // Yellow indicator dot under selected tab
                                if (selected) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(top = 30.dp)
                                            .width(16.dp)
                                            .height(3.dp)
                                            .background(Brand, RoundedCornerShape(2.dp))
                                    )
                                }
                            }
                        },
                        label = {
                            Text(
                                tab.title,
                                fontSize = 10.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                letterSpacing = if (selected) 0.3.sp else 0.sp
                            )
                        },
                        selected = selected,
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
                            unselectedTextColor = Inactive,
                            indicatorColor = Color.Transparent
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = tabNavController,
            startDestination = TabItem.Home.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(tween(200)) },
            exitTransition = { fadeOut(tween(150)) }
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
            composable(TabItem.Messages.route) {
                MessagesScreen(
                    rootNavController = rootNavController,
                    authViewModel = authViewModel,
                    smsViewModel = smsViewModel
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
