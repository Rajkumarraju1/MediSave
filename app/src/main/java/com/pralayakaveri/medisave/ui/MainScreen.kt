package com.pralayakaveri.medisave.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pralayakaveri.medisave.ui.theme.PrimaryGreen
import com.pralayakaveri.medisave.ui.theme.TakenGreenBg
import com.pralayakaveri.medisave.ui.theme.TextSecondary
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pralayakaveri.medisave.ui.theme.CardWhite
import com.pralayakaveri.medisave.viewmodel.AuthState
import com.pralayakaveri.medisave.viewmodel.DashboardViewModel
import kotlinx.coroutines.launch

sealed class NavRoute(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Home : NavRoute("home", "Home", Icons.Default.Home)
    object Reminders : NavRoute("reminders", "Reminders", Icons.Default.DateRange)
    object Dashboard : NavRoute("dashboard", "Insights", Icons.Default.TrendingUp)
    object Map : NavRoute("map?mode={mode}", "Map", Icons.Default.LocationOn)
    object Profile : NavRoute("profile", "Profile", Icons.Default.Person)
    object Settings : NavRoute("settings", "Settings", Icons.Default.Settings)
    object Login : NavRoute("login", "Login", Icons.Default.Login)
    object Register : NavRoute("register", "Register", Icons.Default.AppRegistration)
    object Splash : NavRoute("splash", "Splash", Icons.Default.CloudQueue)
    object MemberDetail : NavRoute("member_detail/{memberId}/{memberName}?connectionId={connectionId}&medicineId={medicineId}", "Member Detail", Icons.Default.Person)
    object ConnectionCode : NavRoute("connection_code", "My Code", Icons.Default.QrCode)
    object Account : NavRoute("account", "Account", Icons.Default.AccountCircle)
    object FamilyConnection : NavRoute("family_connection", "Connection", Icons.Default.Share)
    object ConnectionRequest : NavRoute("connection_request/{requestId}/{senderId}", "Consent", Icons.Default.VerifiedUser)
    object ConnectionSuccess : NavRoute("connection_success/{requestId}/{receiverId}/{relation}", "Success", Icons.Default.CheckCircle)
    object EditProfile : NavRoute("edit_profile", "Edit Profile", Icons.Default.Edit)
    object ChangePassword : NavRoute("change_password", "Change Password", Icons.Default.Lock)
    object LinkedAccounts : NavRoute("linked_accounts", "Linked Accounts", Icons.Default.Link)
}


val BottomNavItems = listOf(
    NavRoute.Home,
    NavRoute.Reminders,
    NavRoute.Dashboard,
    NavRoute.Map,
    NavRoute.Profile
)

@Composable
fun MainScreen(
    targetMedicineId: String? = null,
    deepLinkRequestId: String? = null,
    deepLinkSenderId: String? = null,
    deepLinkPatientId: String? = null,
    deepLinkPatientName: String? = null,
    deepLinkMedicineId: String? = null,
    deepLinkReceiverId: String? = null,
    deepLinkRelation: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
    authViewModel: com.pralayakaveri.medisave.viewmodel.AuthViewModel = viewModel()
) {
    val navController = rememberNavController()
    val authState by authViewModel.authState.collectAsState()
    val lastHandledRequestId by authViewModel.lastHandledRequestId.collectAsState()
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    // FCM INTENT MERGE: Mark deep-link request as handled to avoid duplicate trigger
    LaunchedEffect(deepLinkRequestId, authState, currentRoute) {
        if (deepLinkRequestId != null && 
            authState is AuthState.Authenticated && 
            currentRoute != NavRoute.Splash.route &&
            currentRoute != NavRoute.Login.route &&
            currentRoute != NavRoute.Register.route
        ) {
            authViewModel.markRequestHandled(deepLinkRequestId)
            onDeepLinkConsumed()
            navController.navigate("connection_request/$deepLinkRequestId/$deepLinkSenderId") {
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(deepLinkPatientId, authState, currentRoute) {
        if (deepLinkPatientId != null && 
            authState is AuthState.Authenticated && 
            currentRoute != NavRoute.Splash.route &&
            currentRoute != NavRoute.Login.route &&
            currentRoute != NavRoute.Register.route
        ) {
            val encodedName = android.net.Uri.encode(deepLinkPatientName ?: "")
            val queryParams = mutableListOf<String>()
            if (deepLinkMedicineId != null) queryParams.add("medicineId=$deepLinkMedicineId")
            val queryStr = if (queryParams.isNotEmpty()) "?" + queryParams.joinToString("&") else ""
            onDeepLinkConsumed()
            navController.navigate("member_detail/$deepLinkPatientId/$encodedName$queryStr") {
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(deepLinkReceiverId, authState, currentRoute) {
        if (deepLinkReceiverId != null && 
            authState is AuthState.Authenticated && 
            currentRoute != NavRoute.Splash.route &&
            currentRoute != NavRoute.Login.route &&
            currentRoute != NavRoute.Register.route
        ) {
            val reqId = deepLinkRequestId ?: "fcm_accepted"
            onDeepLinkConsumed()
            navController.navigate("connection_success/$reqId/$deepLinkReceiverId/$deepLinkRelation") {
                launchSingleTop = true
            }
        }
    }

    // CONECTION SUCCESS TRIGGER: Listener for Device A (Sender)
    val acceptedSentRequest by authViewModel.acceptedSentRequest.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val prefManager = remember { com.pralayakaveri.medisave.data.PreferenceManager(authViewModel.getApplication()) }

    LaunchedEffect(acceptedSentRequest) {
        val request = acceptedSentRequest
        if (request != null) {
            // ✅ STEP 1: Navigate first
            navController.navigate("connection_success/${request.id}/${request.receiverId}/${request.relation}")
            
            // ✅ STEP 2: Consume/Clear local state immediately after navigation
            authViewModel.consumeAcceptedSentRequest()
            
            // ✅ STEP 3: Update Firestore last (to prevent listener from killing state too soon)
            // Safety check: only write if not already marked handled
            if (request.handledBySender != true) {
                authViewModel.markRequestAsHandledInFirestore(request.id)
            }
        }
    }
    
    val isAppReady = authState !is AuthState.Loading
    val isAuthScreen = currentRoute == NavRoute.Login.route || currentRoute == NavRoute.Register.route
    val isSplashScreen = currentRoute == NavRoute.Splash.route

    Scaffold(
        bottomBar = { 
            if (!isAuthScreen && !isSplashScreen && authState is AuthState.Authenticated && currentRoute != NavRoute.ConnectionRequest.route) {
                AppBottomNavigation(navController) 
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = NavRoute.Splash.route,
            modifier = Modifier.padding(bottom = if (isAuthScreen || isSplashScreen || currentRoute == NavRoute.ConnectionRequest.route) 0.dp else paddingValues.calculateBottomPadding())
        ) {
            composable(NavRoute.Splash.route) {
                SplashScreen(navController = navController, authViewModel = authViewModel)
            }
            composable(NavRoute.Login.route) {
                LoginScreen(
                    onNavigateToRegister = { navController.navigate(NavRoute.Register.route) },
                    onLoginSuccess = { 
                        navController.navigate(NavRoute.Home.route) {
                            popUpTo(NavRoute.Login.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(NavRoute.Register.route) {
                RegisterScreen(
                    onRegisterSuccess = {
                        navController.navigate(NavRoute.Home.route) {
                            popUpTo(NavRoute.Login.route) { inclusive = true }
                        }
                    },
                    onBackToLogin = { navController.popBackStack() }
                )
            }
            composable(NavRoute.Home.route) {
                HomeScreen(
                    onNavigateToGenerics = {
                        navController.navigate("map?mode=generics") {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onAddMedicine = {
                        navController.navigate(NavRoute.Reminders.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToProfile = {
                        if (currentRoute?.split("?")?.first() != NavRoute.Profile.route) {
                            navController.navigate(NavRoute.Profile.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
            composable(NavRoute.Reminders.route) {
                ReminderScreen(navController = navController)
            }
            composable(
                route = NavRoute.Map.route,
                arguments = listOf(navArgument("mode") { defaultValue = "pharmacies" })
            ) { backStackEntry ->
                val modeStr = backStackEntry.arguments?.getString("mode") ?: "pharmacies"
                val initialMode = if (modeStr == "generics") MapMode.GENERICS else MapMode.PHARMACIES
                MapScreen(initialMode = initialMode)
            }
            composable(NavRoute.Dashboard.route) {
                val activity = androidx.compose.ui.platform.LocalContext.current as? androidx.activity.ComponentActivity
                val dashboardViewModel: DashboardViewModel = if (activity != null) {
                    viewModel(activity)
                } else {
                    viewModel()
                }
                DashboardScreen(
                    viewModel = dashboardViewModel,
                    onViewDetails = {
                        navController.navigate("performance_details")
                    },
                    onNavigateToHistory = { filter ->
                        navController.navigate("activity_history?filter=$filter")
                    },
                    onNavigateToAchievements = {
                        navController.navigate("achievements")
                    },
                    onNavigateToRiskDetails = {
                        navController.navigate("adherence_risk_details")
                    },
                    onNavigateToRefillStatus = {
                        navController.navigate("refill_status")
                    }
                )
            }
            composable("performance_details") {
                val activity = androidx.compose.ui.platform.LocalContext.current as? androidx.activity.ComponentActivity
                val dashboardViewModel: DashboardViewModel = if (activity != null) {
                    viewModel(activity)
                } else {
                    viewModel()
                }
                PerformanceDetailsScreen(
                    viewModel = dashboardViewModel,
                    onBack = { navController.popBackStack() },
                    onViewAllActivity = {
                        navController.navigate("activity_history?filter=all")
                    }
                )
            }
            composable("achievements") {
                val activity = androidx.compose.ui.platform.LocalContext.current as? androidx.activity.ComponentActivity
                val dashboardViewModel: DashboardViewModel = if (activity != null) {
                    viewModel(activity)
                } else {
                    viewModel()
                }
                AchievementsScreen(
                    viewModel = dashboardViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("adherence_risk_details") {
                val activity = androidx.compose.ui.platform.LocalContext.current as? androidx.activity.ComponentActivity
                val dashboardViewModel: DashboardViewModel = if (activity != null) {
                    viewModel(activity)
                } else {
                    viewModel()
                }
                AdherenceRiskDetailsScreen(
                    viewModel = dashboardViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("refill_status") {
                val activity = androidx.compose.ui.platform.LocalContext.current as? androidx.activity.ComponentActivity
                val dashboardViewModel: DashboardViewModel = if (activity != null) {
                    viewModel(activity)
                } else {
                    viewModel()
                }
                RefillStatusScreen(
                    viewModel = dashboardViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "activity_history?filter={filter}",
                arguments = listOf(navArgument("filter") { defaultValue = "all" })
            ) { backStackEntry ->
                val filter = backStackEntry.arguments?.getString("filter") ?: "all"
                val activity = androidx.compose.ui.platform.LocalContext.current as? androidx.activity.ComponentActivity
                val dashboardViewModel: DashboardViewModel = if (activity != null) {
                    viewModel(activity)
                } else {
                    viewModel()
                }
                ActivityHistoryScreen(
                    viewModel = dashboardViewModel,
                    initialFilter = filter,
                    onBack = { navController.popBackStack() },
                    onNavigateToDetails = { activityId ->
                        navController.navigate("activity_details/$activityId")
                    }
                )
            }
            composable(
                route = "activity_details/{activityId}",
                arguments = listOf(navArgument("activityId") { type = androidx.navigation.NavType.StringType })
            ) { backStackEntry ->
                val activityId = backStackEntry.arguments?.getString("activityId") ?: ""
                val activity = androidx.compose.ui.platform.LocalContext.current as? androidx.activity.ComponentActivity
                val dashboardViewModel: DashboardViewModel = if (activity != null) {
                    viewModel(activity)
                } else {
                    viewModel()
                }
                ActivityDetailsScreen(
                    viewModel = dashboardViewModel,
                    activityId = activityId,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(NavRoute.Profile.route) {
                ProfileScreen(
                    onNavigateToHome = {
                        navController.navigate(NavRoute.Home.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToSettings = {
                        navController.navigate(NavRoute.Settings.route)
                    },
                    onNavigateToDetail = { id, name, connectionId ->
                        navController.navigate("member_detail/$id/$name?connectionId=$connectionId")
                    },
                    onNavigateToConnection = {
                        navController.navigate(NavRoute.FamilyConnection.route)
                    },
                    onNavigateToCode = {
                        navController.navigate(NavRoute.ConnectionCode.route)
                    },
                    onNavigateToAccount = {
                        navController.navigate(NavRoute.Account.route)
                    },
                    onNavigateToConsent = { reqId, sId ->
                        navController.navigate("connection_request/$reqId/$sId")
                    }
                )
            }
            composable(NavRoute.Settings.route) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onLogout = { 
                        navController.navigate(NavRoute.Login.route) {
                            popUpTo(0) // Clear backstack
                        }
                    }
                )
            }
            composable(
                route = NavRoute.MemberDetail.route,
                arguments = listOf(
                    navArgument("memberId") { type = androidx.navigation.NavType.StringType },
                    navArgument("memberName") { type = androidx.navigation.NavType.StringType },
                    navArgument("connectionId") { 
                        type = androidx.navigation.NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("medicineId") {
                        type = androidx.navigation.NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val memberId = backStackEntry.arguments?.getString("memberId") ?: ""
                val memberName = backStackEntry.arguments?.getString("memberName") ?: ""
                val connectionId = backStackEntry.arguments?.getString("connectionId") ?: ""
                val medicineId = backStackEntry.arguments?.getString("medicineId")
                MemberDetailScreen(
                    memberId = memberId,
                    memberName = memberName,
                    connectionId = connectionId,
                    highlightedMedicineId = medicineId,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(NavRoute.ConnectionCode.route) {
                ConnectionCodeScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToConnect = { navController.navigate(NavRoute.FamilyConnection.route) }
                )
            }
            composable(NavRoute.Account.route) {
                AccountScreen(
                    onBack = { navController.popBackStack() },
                    onLogout = { 
                        navController.navigate(NavRoute.Login.route) {
                            popUpTo(0)
                        }
                    },
                    onNavigateToEditProfile = { navController.navigate(NavRoute.EditProfile.route) },
                    onNavigateToChangePassword = { navController.navigate(NavRoute.ChangePassword.route) },
                    onNavigateToLinkedAccounts = { navController.navigate(NavRoute.LinkedAccounts.route) }

                )
            }
            composable(NavRoute.FamilyConnection.route) {
                FamilyConnectionScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = NavRoute.ConnectionRequest.route,
                arguments = listOf(
                    navArgument("requestId") { type = androidx.navigation.NavType.StringType },
                    navArgument("senderId") { type = androidx.navigation.NavType.StringType }
                )
            ) { backStackEntry ->
                val requestId = backStackEntry.arguments?.getString("requestId") ?: ""
                val senderId = backStackEntry.arguments?.getString("senderId") ?: ""
                ConnectionRequestScreen(
                    requestId = requestId,
                    senderId = senderId,
                    onHandled = { 
                        navController.popBackStack()
                    }
                )
            }
            composable(
                route = NavRoute.ConnectionSuccess.route,
                arguments = listOf(
                    navArgument("requestId") { type = androidx.navigation.NavType.StringType },
                    navArgument("receiverId") { type = androidx.navigation.NavType.StringType },
                    navArgument("relation") { type = androidx.navigation.NavType.StringType }
                )
            ) { backStackEntry ->
                val requestId = backStackEntry.arguments?.getString("requestId") ?: ""
                val receiverId = backStackEntry.arguments?.getString("receiverId") ?: ""
                val relation = backStackEntry.arguments?.getString("relation") ?: ""
                ConnectionSuccessScreen(
                    requestId = requestId,
                    receiverId = receiverId,
                    relation = relation,
                    onViewDashboard = { rId ->
                        scope.launch {
                            prefManager.saveActiveProfileId(rId)
                            navController.navigate(NavRoute.Dashboard.route) {
                                popUpTo(NavRoute.Home.route) { inclusive = false }
                            }
                        }
                    },
                    onBack = { 
                        navController.popBackStack()
                    }
                )
            }
            composable(NavRoute.EditProfile.route) {
                EditProfileScreen(onBack = { navController.popBackStack() })
            }
            composable(NavRoute.ChangePassword.route) {
                ChangePasswordScreen(onBack = { navController.popBackStack() })
            }
            composable(NavRoute.LinkedAccounts.route) {
                LinkedAccountsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}


@Composable
fun AppBottomNavigation(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Surface(
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            windowInsets = NavigationBarDefaults.windowInsets
        ) {
            BottomNavItems.forEach { item ->
                NavigationBarItem(
                    selected = currentRoute?.split("?")?.first() == item.route.split("?")?.first(),
                    onClick = {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(item.icon, contentDescription = item.title) },
                    label = { Text(item.title, fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                )
            }
        }
    }
}

@Composable
fun PlaceholderScreen(text: String) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Text(text)
    }
}
