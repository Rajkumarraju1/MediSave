package com.pralayakaveri.medisave.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pralayakaveri.medisave.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkedAccountsScreen(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = viewModel()
) {
    val providers = viewModel.getProviders()
    val isGoogleLinked = providers.contains("google.com")
    val isEmailLinked = providers.contains("password")
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Linked Accounts", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBackIosNew, contentDescription = "Back", modifier = Modifier.size(20.dp))
                    }
                }
            )
        },
        containerColor = Color(0xFFF9F9F9)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
        ) {
            Text(
                "Connected login methods for your account.",
                fontSize = 14.sp,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            
            LinkedAccountItem(
                name = "Google",
                isLinked = isGoogleLinked,
                canUnlink = providers.size > 1
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            LinkedAccountItem(
                name = "Email & Password",
                isLinked = isEmailLinked,
                canUnlink = providers.size > 1
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            if (providers.size == 1) {
                Surface(
                    color = Color(0xFFFFF8E1),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Link, contentDescription = null, tint = Color(0xFFF57C00))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "You must have at least one login method linked to prevent losing access to your account.",
                            fontSize = 12.sp,
                            color = Color(0xFFF57C00).copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LinkedAccountItem(name: String, isLinked: Boolean, canUnlink: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = if (isLinked) BrandingGreen.copy(alpha = 0.1f) else Color(0xFFF5F5F5)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isLinked) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = BrandingGreen, modifier = Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Default.Link, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(if (isLinked) "Linked" else "Not Linked", fontSize = 12.sp, color = if (isLinked) BrandingGreen else TextSecondary)
            }
            
            if (isLinked && canUnlink) {
                TextButton(onClick = { /* TODO: Unlink logic */ }) {
                    Text("Unlink", color = Color.Red.copy(alpha = 0.7f))
                }
            } else if (!isLinked) {
                TextButton(onClick = { /* TODO: Link logic */ }) {
                    Text("Link", color = BrandingGreen)
                }
            }
        }
    }
}
