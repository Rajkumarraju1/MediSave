package com.pralayakaveri.medisave.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pralayakaveri.medisave.data.FamilyConnectionRepository
import com.pralayakaveri.medisave.viewmodel.AuthViewModel
import com.pralayakaveri.medisave.ui.theme.*
import kotlinx.coroutines.launch

sealed class ConsentScreenState {
    object Loading : ConsentScreenState()
    data class Success(val name: String, val joinedAt: Long) : ConsentScreenState()
    object Error : ConsentScreenState()
}

@Composable
fun ConnectionRequestScreen(
    requestId: String,
    senderId: String,
    onHandled: () -> Unit,
    authViewModel: AuthViewModel = viewModel()
) {
    val repository = remember { FamilyConnectionRepository() }
    val scope = rememberCoroutineScope()
    var screenState by remember { mutableStateOf<ConsentScreenState>(ConsentScreenState.Loading) }
    val activeRequest by authViewModel.activeIncomingRequest.collectAsState()
    val allRequests by authViewModel.allIncomingRequests.collectAsState()
    val scrollState = rememberScrollState()
    val pendingCount = allRequests?.size ?: 0

    // 1. AUTO-DISMISS: If the specific request we are viewing disappears or is handled elsewhere
    LaunchedEffect(allRequests) {
        val requests = allRequests
        if (requests != null && requests.none { it.id == requestId }) {
            onHandled()
        }
    }

    // 2. LOAD PROFILE
    LaunchedEffect(senderId) {
        val profile = repository.getUserPublicProfile(senderId)
        if (profile != null) {
            screenState = ConsentScreenState.Success(
                name = profile["name"] as? String ?: "Unknown User",
                joinedAt = profile["joinedAt"] as? Long ?: System.currentTimeMillis()
            )
        } else {
            screenState = ConsentScreenState.Error
        }
    }

    Scaffold(containerColor = Color.White) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = screenState) {
                is ConsentScreenState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PremiumTeal)
                    }
                }
                is ConsentScreenState.Success -> {
                    // 1. Scrollable Content (Header + Card)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    ) {
                        // Header Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(PremiumTeal)
                                .statusBarsPadding()
                                .padding(top = 20.dp, bottom = 16.dp, start = 24.dp, end = 24.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Connection request",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Someone wants to monitor your health",
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        
                        ConsentContent(
                            name = state.name,
                            joinedAt = state.joinedAt,
                            relation = activeRequest?.relation ?: "Family Member",
                            pendingCount = pendingCount
                        )
                        
                        Spacer(modifier = Modifier.height(140.dp)) // Bottom padding cushion
                    }

                    var showRelationPicker by remember { mutableStateOf(false) }

                    // 2. Fixed Bottom Actions
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.White)
                            .padding(20.dp)
                    ) {
                        ConsentActions(
                            onAccept = {
                                showRelationPicker = true
                            },
                            onDecline = {
                                scope.launch {
                                    repository.declineRequest(requestId)
                                    onHandled()
                                }
                            }
                        )
                    }

                    if (showRelationPicker) {
                        ReciprocalRelationPicker(
                            name = state.name,
                            onDismiss = { showRelationPicker = false },
                            onConfirm = { receiverRelation ->
                                scope.launch {
                                    repository.acceptRequest(
                                        requestId = requestId,
                                        senderId = senderId,
                                        receiverId = activeRequest?.receiverId ?: "",
                                        senderRelation = activeRequest?.relation ?: "Family Member",
                                        receiverRelation = receiverRelation
                                    )
                                    showRelationPicker = false
                                    onHandled()
                                }
                            }
                        )
                    }
                }
                is ConsentScreenState.Error -> {
                    ErrorUI(onHandled)
                }
            }
        }
    }
}

@Composable
fun ConsentContent(
    name: String,
    joinedAt: Long,
    relation: String,
    pendingCount: Int
) {
    val months = remember(joinedAt) {
        val diff = System.currentTimeMillis() - joinedAt
        val m = (diff / (1000L * 60 * 60 * 24 * 30)).toInt()
        if (m <= 0) "just recently" else "$m months ago"
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        // User Profile Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            border = BorderStroke(1.dp, PremiumBorder),
            shadowElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(60.dp),
                        shape = CircleShape,
                        color = Color(0xFFE0F2F1)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(name.take(1).uppercase(), fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = PremiumTeal)
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(text = name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1A1A1A))
                        Text(text = "wants to connect as your $relation", fontSize = 14.sp, color = PremiumGreyText)
                        if (pendingCount > 1) {
                            Surface(
                                color = PremiumTeal.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(percent = 50),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = "You have $pendingCount pending requests",
                                    color = PremiumTeal,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        } else {
                            Text(text = "MediSave user · Joined $months", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Permissions Section
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = PremiumCardBg
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "If you accept, $name can see:",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = Color(0xFF333333),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        PermissionItem(
                            title = "Your medicine schedule —",
                            subtitle = "names and times",
                            allowed = true
                        )
                        PermissionItem(
                            title = "Whether you took each dose —",
                            subtitle = "taken or missed",
                            allowed = true
                        )
                        PermissionItem(
                            title = "Your weekly adherence %",
                            subtitle = null,
                            allowed = true
                        )
                        
                        Divider(modifier = Modifier.padding(vertical = 12.dp), color = PremiumBorder.copy(alpha = 0.5f))
                        
                        PermissionItem(
                            title = "Your health conditions —",
                            subtitle = "never shared",
                            allowed = false
                        )
                        PermissionItem(
                            title = "Your personal profile —",
                            subtitle = "never shared",
                            allowed = false
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "You can disconnect anytime from your\nProfile settings",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun ConsentActions(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Column {
        // Action Buttons
        Button(
            onClick = onAccept,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PremiumTeal)
        ) {
            Text("Accept connection", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clickable { onDecline() },
            shape = RoundedCornerShape(16.dp),
            color = PremiumDeclineBg
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("Decline", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = PremiumGreyText)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text("Don't know this person? ", fontSize = 14.sp, color = Color.Gray)
            Text(
                "Report", 
                fontSize = 14.sp, 
                color = PremiumTeal, 
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { /* Report logic */ }
            )
        }
    }
}

@Composable
fun PermissionItem(title: String, subtitle: String?, allowed: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            modifier = Modifier.size(20.dp),
            shape = CircleShape,
            color = if (allowed) PremiumTeal.copy(alpha = 0.15f) else PremiumSoftRed.copy(alpha = 0.1f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (allowed) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null,
                    tint = if (allowed) PremiumTeal else PremiumSoftRed,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (allowed) Color(0xFF333333) else Color(0xFF888888)
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun ErrorUI(onHandled: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Error, contentDescription = null, tint = PremiumSoftRed, modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text("Requester profile not found", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text("This request may have expired or was cancelled.", textAlign = TextAlign.Center, color = Color.Gray)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onHandled, colors = ButtonDefaults.buttonColors(containerColor = PremiumTeal)) {
            Text("Dismiss")
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReciprocalRelationPicker(
    name: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var selectedRelation by remember { mutableStateOf("") }
    var customRelation by remember { mutableStateOf("") }
    val relations = listOf("Mom", "Dad", "Spouse", "Child", "Sibling", "Caregiver", "Custom...")
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp)
        ) {
            Text(
                "How is $name related to you?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "This label is only visible to you.",
                style = MaterialTheme.typography.bodyMedium,
                color = PremiumGreyText
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Relation Chips
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                mainAxisSpacing = 8.dp,
                crossAxisSpacing = 8.dp
            ) {
                relations.forEach { relation ->
                    FilterChip(
                        selected = selectedRelation == relation,
                        onClick = { selectedRelation = relation },
                        label = { Text(relation) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PremiumTeal.copy(alpha = 0.1f),
                            selectedLabelColor = PremiumTeal
                        )
                    )
                }
            }
            
            if (selectedRelation == "Custom...") {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = customRelation,
                    onValueChange = { if (it.length <= 30) customRelation = it },
                    label = { Text("Custom Relation") },
                    placeholder = { Text("e.g. Uncle, Mentor, Nana") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PremiumTeal,
                        focusedLabelColor = PremiumTeal
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = { 
                    val final = if (selectedRelation == "Custom...") customRelation.trim() else selectedRelation
                    if (final.isNotBlank()) onConfirm(final)
                },
                enabled = selectedRelation.isNotBlank() && (selectedRelation != "Custom..." || customRelation.isNotBlank()),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PremiumTeal)
            ) {
                Text("Confirm & Accept", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    mainAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    crossAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.layout.Layout(
        modifier = modifier,
        content = content
    ) { measurables, constraints ->
        val placeholders = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        var rowWidth = 0
        var rowHeight = 0
        var totalHeight = 0
        val rows = mutableListOf<List<androidx.compose.ui.layout.Placeable>>()
        var currentRow = mutableListOf<androidx.compose.ui.layout.Placeable>()
        
        placeholders.forEach { placeable ->
            if (rowWidth + placeable.width > constraints.maxWidth) {
                rows.add(currentRow)
                totalHeight += rowHeight + crossAxisSpacing.roundToPx()
                rowWidth = 0
                rowHeight = 0
                currentRow = mutableListOf()
            }
            currentRow.add(placeable)
            rowWidth += placeable.width + mainAxisSpacing.roundToPx()
            rowHeight = maxOf(rowHeight, placeable.height)
        }
        rows.add(currentRow)
        totalHeight += rowHeight
        
        layout(constraints.maxWidth, totalHeight) {
            var y = 0
            rows.forEach { row ->
                var x = 0
                var maxHeight = 0
                row.forEach { placeable ->
                    placeable.place(x, y)
                    x += placeable.width + mainAxisSpacing.roundToPx()
                    maxHeight = maxOf(maxHeight, placeable.height)
                }
                y += maxHeight + crossAxisSpacing.roundToPx()
            }
        }
    }
}
