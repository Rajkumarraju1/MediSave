package com.pralayakaveri.medisave.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.pralayakaveri.medisave.ui.theme.PrimaryGreen
import com.pralayakaveri.medisave.viewmodel.AuthState
import com.pralayakaveri.medisave.viewmodel.AuthViewModel

@Composable
fun SplashScreen(
    navController: NavController,
    authViewModel: AuthViewModel
) {
    val authState by authViewModel.authState.collectAsState()
    var hasNavigated by remember { mutableStateOf(false) }

    LaunchedEffect(authState) {
        if (hasNavigated) return@LaunchedEffect

        when (authState) {
            is AuthState.Authenticated -> {
                hasNavigated = true
                navController.navigate(NavRoute.Home.route) {
                    popUpTo(NavRoute.Splash.route) { inclusive = true }
                }
            }
            is AuthState.Unauthenticated -> {
                hasNavigated = true
                navController.navigate(NavRoute.Login.route) {
                    popUpTo(NavRoute.Splash.route) { inclusive = true }
                }
            }
            else -> { /* Loading... stay on splash */ }
        }
    }

    val isDark = MaterialTheme.colorScheme.background == Color(0xFF0B0F0C)
    val bg = if (isDark) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.primary
    val contentColor = if (isDark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "MediSave",
                color = contentColor,
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            CircularProgressIndicator(
                color = contentColor,
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}
