package com.pralayakaveri.medisave.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pralayakaveri.medisave.ui.theme.BrandingGreen
import com.pralayakaveri.medisave.ui.theme.PrimaryGreen
import com.pralayakaveri.medisave.ui.theme.TakenGreenBg

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionCodeScreen(
    onBack: () -> Unit,
    onNavigateToConnect: () -> Unit,
    viewModel: ProfileViewModel = viewModel()
) {
    val primaryUser by viewModel.primaryUser.collectAsState()
    val connections by viewModel.acceptedConnections.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Family connection", 
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBackIosNew, 
                            contentDescription = "Back", 
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Box(modifier = Modifier.padding(24.dp)) {
                Button(
                    onClick = onNavigateToConnect,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandingGreen
                    )
                ) {
                    Text(
                        "+ Connect a family member",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Illustration / Icon
                    Surface(
                        modifier = Modifier.size(80.dp),
                        shape = CircleShape,
                        color = BrandingGreen.copy(alpha = 0.1f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.GroupAdd,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = BrandingGreen
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Your connection code",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Share this with family members so they can\nconnect with you",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Code Box
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = BrandingGreen.copy(alpha = 0.05f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, 
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = primaryUser?.connectionCode?.map { it.toString() }?.joinToString(" ") ?: "— — — — — —",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black,
                                color = BrandingGreen,
                                letterSpacing = 2.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Expires in 29 days · Tap to refresh",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, "Connect with me on MediSave! My code is: ${primaryUser?.connectionCode}")
                                }
                                context.startActivity(android.content.Intent.createChooser(intent, "Share on WhatsApp"))
                            },
                            modifier = Modifier
                                .weight(1.1f)
                                .height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrandingGreen
                            )
                        ) {
                            Icon(
                                Icons.Default.GroupAdd, 
                                contentDescription = null, 
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Share on WhatsApp", 
                                fontSize = 14.sp, 
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(primaryUser?.connectionCode ?: ""))
                            },
                            modifier = Modifier
                                .weight(0.9f)
                                .height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Copy code", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            // People Connected Section
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "People connected to you",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    
                    Surface(
                        color = BrandingGreen.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "${connections.size} active",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandingGreen
                        )
                    }
                }
            }

            items(connections) { connection ->
                val otherName = if (connection.senderId == primaryUser?.userId) "Member" else "Family" // Placeholder logic
                ConnectedMemberItem(
                    name = "Family Member", // Since Connection doesn't store name directly, normally we'd fetch it
                    relation = connection.relation,
                    daysAgo = "Connected today" // Placeholder
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                )
            }
        }
    }
}

@Composable
fun ConnectedMemberItem(name: String, relation: String, daysAgo: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = BrandingGreen.copy(alpha = 0.1f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = name.firstOrNull()?.toString()?.uppercase() ?: "?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandingGreen
                )
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "$relation · $daysAgo",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
        
        Surface(
            color = BrandingGreen.copy(alpha = 0.1f),
            shape = RoundedCornerShape(50)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(BrandingGreen))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Active",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandingGreen
                )
            }
        }
    }
}
