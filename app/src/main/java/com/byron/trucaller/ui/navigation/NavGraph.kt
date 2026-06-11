package com.byron.trucaller.ui.navigation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import com.byron.trucaller.ui.admin.AdminSmsSpamScreen
import com.byron.trucaller.ui.admin.AdminCallerIdScreen
import com.byron.trucaller.ui.admin.AdminDashboardScreen
import com.byron.trucaller.ui.admin.AdminDeviceDetailScreen
import com.byron.trucaller.ui.admin.AdminDevicesScreen
import com.byron.trucaller.ui.admin.AdminSettingsScreen
import com.byron.trucaller.ui.admin.AdminStolenReportsScreen
import com.byron.trucaller.ui.admin.AdminUserDetailScreen
import com.byron.trucaller.ui.admin.AdminUsersScreen
import com.byron.trucaller.ui.auth.AdminLoginScreen
import com.byron.trucaller.ui.auth.DeviceProtectionPromptScreen
import com.byron.trucaller.ui.auth.ForgotPasswordScreen
import com.byron.trucaller.ui.auth.LoginScreen
import com.byron.trucaller.ui.auth.OtpVerificationScreen
import com.byron.trucaller.ui.auth.RegisterScreen
import com.byron.trucaller.ui.auth.SplashScreen
import com.byron.trucaller.ui.main.AnalyticsScreen
import com.byron.trucaller.ui.main.IpLogsScreen
import com.byron.trucaller.ui.main.CallerIdScreen
import com.byron.trucaller.ui.main.CallLogScreen
import com.byron.trucaller.ui.main.DialPadScreen
import com.byron.trucaller.ui.main.ContactsScreen
import com.byron.trucaller.ui.main.ConversationScreen
import com.byron.trucaller.ui.main.HomeScreen
import com.byron.trucaller.ui.main.MessagesScreen
import com.byron.trucaller.ui.main.ProfileScreen
import com.byron.trucaller.ui.main.CallRecordingsScreen
import com.byron.trucaller.ui.main.RecordingSettingsScreen
import com.byron.trucaller.ui.main.SecurityScreen
import com.byron.trucaller.ui.main.BlockingSchedulesScreen
import com.byron.trucaller.ui.main.EditBlockingScheduleScreen
import com.byron.trucaller.ui.family.FamilyDeviceMapScreen
import com.byron.trucaller.ui.main.FamilyGroupDetailScreen
import com.byron.trucaller.ui.main.FamilyGroupsScreen
import com.byron.trucaller.ui.main.JoinGroupScreen
import com.byron.trucaller.ui.main.SmsRulesScreen
import com.byron.trucaller.ui.stolen.GeofenceManagementScreen
import com.byron.trucaller.ui.stolen.NetworkForensicsScreen
import com.byron.trucaller.ui.stolen.RemoteActionsScreen
import com.byron.trucaller.ui.stolen.ReportStolenScreen
import com.byron.trucaller.viewmodel.AnalyticsViewModel
import com.byron.trucaller.viewmodel.AdminDashboardViewModel
import com.byron.trucaller.viewmodel.AdminSettingsViewModel
import com.byron.trucaller.viewmodel.AlarmViewModel
import com.byron.trucaller.viewmodel.AuthViewModel
import com.byron.trucaller.viewmodel.CallerIdViewModel
import com.byron.trucaller.viewmodel.CallLogViewModel
import com.byron.trucaller.viewmodel.ContactsViewModel
import com.byron.trucaller.viewmodel.DialPadViewModel
import com.byron.trucaller.viewmodel.DeviceViewModel
import com.byron.trucaller.viewmodel.GeofenceViewModel
import com.byron.trucaller.viewmodel.NetworkForensicsViewModel
import com.byron.trucaller.viewmodel.CallRecordingsViewModel
import com.byron.trucaller.viewmodel.BlockingScheduleViewModel
import com.byron.trucaller.viewmodel.FamilyGroupViewModel
import com.byron.trucaller.viewmodel.SmsRulesViewModel
import com.byron.trucaller.viewmodel.SmsViewModel
import com.byron.trucaller.viewmodel.StolenReportViewModel

sealed class TabItem(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Home : TabItem("tab_home", "Home", Icons.Filled.Home, Icons.Outlined.Home)
    data object CallLog : TabItem("tab_call_log", "Calls", Icons.Filled.Phone, Icons.Outlined.Phone)
    data object Messages : TabItem(
        "tab_messages", "Messages",
        Icons.AutoMirrored.Filled.Chat, Icons.AutoMirrored.Outlined.Chat
    )
    data object Contacts : TabItem(
        "tab_contacts", "Contacts",
        Icons.Outlined.People, Icons.Outlined.People
    )
    data object Profile : TabItem("tab_profile", "Profile", Icons.Filled.Person, Icons.Outlined.Person)
}

val tabs = listOf(TabItem.Home, TabItem.CallLog, TabItem.Messages, TabItem.Contacts, TabItem.Profile)

// Shared transition specs
private val slideInDuration = 300
private val fadeSpec = tween<Float>(200)

@Composable
fun TruCallerNavGraph(authViewModel: AuthViewModel) {
    val navController = rememberNavController()
    val context = LocalContext.current

    // Redirect to login when token refresh fails (session expired on server)
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                authViewModel.logout()
                navController.navigate("login") {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
        val filter = IntentFilter("com.byron.trucaller.SESSION_EXPIRED")
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    val deviceViewModel: DeviceViewModel = viewModel(factory = DeviceViewModel.Factory)
    val contactsViewModel: ContactsViewModel = viewModel(factory = ContactsViewModel.Factory)
    val callerIdViewModel: CallerIdViewModel = viewModel(factory = CallerIdViewModel.Factory)
    val stolenReportViewModel: StolenReportViewModel = viewModel(factory = StolenReportViewModel.Factory)
    val alarmViewModel: AlarmViewModel = viewModel(factory = AlarmViewModel.Factory)
    val adminSettingsViewModel: AdminSettingsViewModel = viewModel(factory = AdminSettingsViewModel.Factory)
    val adminDashboardViewModel: AdminDashboardViewModel = viewModel(factory = AdminDashboardViewModel.Factory)
    val smsViewModel: SmsViewModel = viewModel(factory = SmsViewModel.Factory)
    val callLogViewModel: CallLogViewModel = viewModel(factory = CallLogViewModel.Factory)
    val geofenceViewModel: GeofenceViewModel = viewModel(factory = GeofenceViewModel.Factory)
    val networkForensicsViewModel: NetworkForensicsViewModel = viewModel(factory = NetworkForensicsViewModel.Factory)
    val callRecordingsViewModel: CallRecordingsViewModel = viewModel(factory = CallRecordingsViewModel.Factory)
    val smsRulesViewModel: SmsRulesViewModel = viewModel(factory = SmsRulesViewModel.Factory)
    val blockingScheduleViewModel: BlockingScheduleViewModel = viewModel(factory = BlockingScheduleViewModel.Factory)
    val familyGroupViewModel: FamilyGroupViewModel = viewModel(factory = FamilyGroupViewModel.Factory)
    val analyticsViewModel: AnalyticsViewModel = viewModel(factory = AnalyticsViewModel.Factory)
    val dialPadViewModel: DialPadViewModel = viewModel(factory = DialPadViewModel.Factory)

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
        composable("otp_email/{email}") { backStackEntry ->
            val email = backStackEntry.arguments?.getString("email") ?: ""
            OtpVerificationScreen(
                navController = navController,
                authViewModel = authViewModel,
                email = email
            )
        }
        composable("forgot_password") {
            ForgotPasswordScreen(navController = navController, authViewModel = authViewModel)
        }
        composable("device_protection_prompt") {
            DeviceProtectionPromptScreen(
                navController = navController,
                authViewModel = authViewModel
            )
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
                smsViewModel = smsViewModel,
                callLogViewModel = callLogViewModel
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
        composable("caller_id_lookup/{number}") { backStackEntry ->
            val number = java.net.URLDecoder.decode(
                backStackEntry.arguments?.getString("number") ?: "", "UTF-8"
            )
            androidx.compose.runtime.LaunchedEffect(number) {
                if (number.isNotBlank()) {
                    callerIdViewModel.lookup(number)
                }
            }
            CallerIdScreen(
                callerIdViewModel = callerIdViewModel,
                authViewModel = authViewModel
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
        composable("geofence_management/{deviceId}") { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: ""
            GeofenceManagementScreen(
                navController = navController,
                deviceId = deviceId,
                geofenceViewModel = geofenceViewModel
            )
        }
        composable("network_forensics/{deviceId}") { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: ""
            NetworkForensicsScreen(
                navController = navController,
                deviceId = deviceId,
                networkForensicsViewModel = networkForensicsViewModel
            )
        }
        composable("ip_logs") {
            IpLogsScreen(
                navController = navController,
                authViewModel = authViewModel,
                deviceViewModel = deviceViewModel
            )
        }
        composable("security") {
            SecurityScreen(navController = navController, authViewModel = authViewModel)
        }
        composable("recording_settings") {
            RecordingSettingsScreen(navController = navController)
        }
        composable("sms_rules") {
            SmsRulesScreen(
                navController = navController,
                authViewModel = authViewModel,
                smsRulesViewModel = smsRulesViewModel
            )
        }
        composable("dial_pad") {
            DialPadScreen(
                rootNavController = navController,
                viewModel = dialPadViewModel
            )
        }
        composable("call_recordings") {
            CallRecordingsScreen(
                navController = navController,
                callRecordingsViewModel = callRecordingsViewModel
            )
        }
        composable("blocking_schedules") {
            BlockingSchedulesScreen(
                navController = navController,
                authViewModel = authViewModel,
                blockingScheduleViewModel = blockingScheduleViewModel
            )
        }
        composable("edit_blocking_schedule/{scheduleId}") { backStackEntry ->
            val scheduleId = backStackEntry.arguments?.getString("scheduleId") ?: "new"
            EditBlockingScheduleScreen(
                navController = navController,
                scheduleId = scheduleId,
                authViewModel = authViewModel,
                blockingScheduleViewModel = blockingScheduleViewModel
            )
        }
        // Family Group routes
        composable("family_groups") {
            FamilyGroupsScreen(
                navController = navController,
                authViewModel = authViewModel,
                familyGroupViewModel = familyGroupViewModel
            )
        }
        composable("family_group_detail/{groupId}") { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            FamilyGroupDetailScreen(
                navController = navController,
                groupId = groupId,
                authViewModel = authViewModel,
                familyGroupViewModel = familyGroupViewModel
            )
        }
        composable("join_family_group") {
            JoinGroupScreen(
                navController = navController,
                authViewModel = authViewModel,
                familyGroupViewModel = familyGroupViewModel
            )
        }
        composable("family_device_map/{groupId}") { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            FamilyDeviceMapScreen(
                navController = navController,
                groupId = groupId,
                authViewModel = authViewModel,
                familyGroupViewModel = familyGroupViewModel
            )
        }
        // Analytics route
        composable("analytics") {
            AnalyticsScreen(
                navController = navController,
                analyticsViewModel = analyticsViewModel
            )
        }
        // Admin routes
        composable("admin_login") {
            AdminLoginScreen(navController = navController, authViewModel = authViewModel)
        }
        composable("admin_dashboard") {
            AdminDashboardScreen(
                navController = navController,
                authViewModel = authViewModel,
                dashboardViewModel = adminDashboardViewModel
            )
        }
        composable("admin_devices") {
            AdminDevicesScreen(navController = navController)
        }
        composable("admin_device_detail/{deviceId}") { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: ""
            AdminDeviceDetailScreen(
                navController = navController,
                deviceId = deviceId,
                deviceViewModel = deviceViewModel,
                alarmViewModel = alarmViewModel,
                authViewModel = authViewModel,
                stolenReportViewModel = stolenReportViewModel
            )
        }
        composable("admin_users") {
            AdminUsersScreen(navController = navController)
        }
        composable("admin_user_detail/{userId}") { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            AdminUserDetailScreen(
                navController = navController,
                userId = userId
            )
        }
        composable("admin_stolen_reports") {
            AdminStolenReportsScreen(navController = navController)
        }
        composable("admin_caller_id") {
            AdminCallerIdScreen(
                navController = navController,
                callerIdViewModel = callerIdViewModel
            )
        }
        composable("admin_alarm_logs") {
            AdminAlarmLogsScreen(navController = navController)
        }
        composable("admin_settings") {
            AdminSettingsScreen(
                navController = navController,
                authViewModel = authViewModel,
                adminSettingsViewModel = adminSettingsViewModel
            )
        }
        composable("admin_sms_spam") {
            AdminSmsSpamScreen(navController = navController)
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
    smsViewModel: SmsViewModel,
    callLogViewModel: CallLogViewModel
) {
    val tabNavController = rememberNavController()
    val hapticFeedback = LocalHapticFeedback.current

    // Badge counts
    val callLogEntries by callLogViewModel.callLogEntries.collectAsState()
    val conversations by smsViewModel.conversations.collectAsState()
    val missedCallCount = callLogEntries.count {
        it.callType == com.byron.trucaller.data.model.CallType.MISSED
    }
    val unreadMessageCount = conversations.sumOf { it.unreadCount }

    Scaffold(
        bottomBar = {
            val navBackStackEntry by tabNavController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            val selectedIndex = tabs.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                        )
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .shadow(10.dp)
                        .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                // Glass top border
                androidx.compose.material3.HorizontalDivider(
                    thickness = 0.5.dp,
                    color = com.byron.trucaller.ui.theme.GlassBorder
                )
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp
                ) {
                    tabs.forEach { tab ->
                        val selected = currentRoute == tab.route

                        val badgeCount = when (tab) {
                            TabItem.CallLog -> missedCallCount
                            TabItem.Messages -> unreadMessageCount
                            else -> 0
                        }

                        NavigationBarItem(
                            modifier = Modifier.testTag("nav_tab_${tab.title.lowercase()}"),
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (badgeCount > 0) {
                                            Badge(
                                                containerColor = MaterialTheme.colorScheme.tertiary,
                                                contentColor = MaterialTheme.colorScheme.onTertiary
                                            ) {
                                                Text(
                                                    text = if (badgeCount > 99) "99+" else "$badgeCount",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = "${tab.title} tab",
                                        modifier = Modifier
                                            .size(24.dp)
                                    )
                                }
                            },
                            label = {
                                Text(
                                    tab.title,
                                    fontFamily = com.byron.trucaller.ui.theme.MontserratFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    letterSpacing = if (selected) 0.3.sp else 0.sp
                                )
                            },
                            selected = selected,
                            onClick = {
                                if (currentRoute != tab.route) {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    tabNavController.navigate(tab.route) {
                                        popUpTo(tabNavController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = Color.Transparent
                            )
                        )
                    }
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
            composable(TabItem.CallLog.route) {
                CallLogScreen(
                    rootNavController = rootNavController,
                    callLogViewModel = callLogViewModel
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
