package com.pralayakaveri.medisave.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Save
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
fun EditProfileScreen(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = viewModel()
) {
    val primaryUser by viewModel.primaryUser.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    var name by remember { mutableStateOf("") }
    
    LaunchedEffect(primaryUser) {
        primaryUser?.let {
            name = it.name
        }
    }

    val isDark = MaterialTheme.colorScheme.background == Color(0xFF0B0F0C)
    val TextPrimary = if (isDark) MaterialTheme.colorScheme.onBackground else com.pralayakaveri.medisave.ui.theme.TextPrimary
    val TextSecondary = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else com.pralayakaveri.medisave.ui.theme.TextSecondary
    val primaryColor = if (isDark) MaterialTheme.colorScheme.primary else BrandingGreen
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Profile", fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBackIosNew,
                            contentDescription = "Back",
                            modifier = Modifier.size(20.dp),
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isDark) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = if (isDark) MaterialTheme.colorScheme.background else Color(0xFFF9F9F9)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Avatar Section
            Surface(
                modifier = Modifier.size(100.dp),
                shape = CircleShape,
                color = primaryColor.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = name.getOrNull(0)?.toString()?.uppercase() ?: "?",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Full Name") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primaryColor,
                    unfocusedBorderColor = if (isDark) MaterialTheme.colorScheme.outline else Color.LightGray,
                    focusedLabelColor = primaryColor,
                    unfocusedLabelColor = TextSecondary,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Your email and phone are linked to your login account and cannot be changed here.",
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            Button(
                onClick = { 
                    viewModel.updateProfileName(name)
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryColor,
                    disabledContainerColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFE0E0E0)
                ),
                enabled = !isLoading && name.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = if (isDark) MaterialTheme.colorScheme.onPrimary else Color.White)
                } else {
                    Icon(Icons.Default.Save, contentDescription = null, tint = if (isDark) MaterialTheme.colorScheme.onPrimary else Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Save Changes",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (isDark) MaterialTheme.colorScheme.onPrimary else Color.White
                    )
                }
            }
        }
    }
}
