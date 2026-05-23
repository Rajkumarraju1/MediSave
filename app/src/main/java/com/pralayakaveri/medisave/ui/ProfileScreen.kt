package com.pralayakaveri.medisave.ui

import android.R.attr.fontWeight
import android.R.attr.text
import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LinkOff
import com.pralayakaveri.medisave.ui.theme.*
import com.pralayakaveri.medisave.data.AuthRepository
import com.pralayakaveri.medisave.data.MedicineRepository
import androidx.compose.ui.geometry.CornerRadius
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.BorderStroke
import androidx.lifecycle.AndroidViewModel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Language
import androidx.compose.animation.core.*
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Settings
import com.google.firebase.auth.FirebaseAuth
import android.util.Log
import androidx.room.withTransaction
import com.pralayakaveri.medisave.model.Connection
import com.google.firebase.firestore.ListenerRegistration
import com.pralayakaveri.medisave.model.MemberType

// Colors based on specs
val ElderPurpleBg = Color(0xFFF3E5F5)
val ElderPurpleText = Color(0xFF673AB7)
val ChildBlueBg = Color(0xFFE3F2FD)
val ChildBlueText = Color(0xFF1976D2)
val AmberWarning = Color(0xFFF57C00)
val SoftAmberBg = Color(0xFFFFF3E0)

sealed class ProfileUiState {
    object Loading : ProfileUiState()
    object Empty : ProfileUiState()
    data class Success(
        val members: List<MemberUiModel>,
        val activeMedicineCount: Int,
        val primaryAdherence: Int,
        val connectedMemberCount: Int
    ) : ProfileUiState()
}

data class MemberUiModel(
    val id: String,
    val name: String,
    val relation: String,
    val type: MemberType,
    val adherence: Int,
    val lastActiveAt: Long,
    val age: String,
    val condition: String,
    val email: String = "",
    val connectionId: String = ""
)



@Composable
fun getTimeAgo(lastActiveAt: Long): String {
    if (lastActiveAt <= 0L) return "Offline"
    val diff = System.currentTimeMillis() - lastActiveAt
    return when {
        diff < 5 * 60 * 1000 -> "Online"
        diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)} min ago"
        diff < 6 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)} hr ago"
        else -> "Offline"
    }
}

@Composable
fun ProfileScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToDetail: (String, String, String) -> Unit,
    onNavigateToConnection: () -> Unit,
    onNavigateToCode: () -> Unit,
    onNavigateToAccount: () -> Unit,
    onNavigateToConsent: (String, String) -> Unit,
    viewModel: ProfileViewModel = viewModel()
) {
    val profiles by viewModel.uiState.collectAsState()
    val incomingRequests by viewModel.incomingRequests.collectAsState()
    val summary by viewModel.summaryStats.collectAsState()
    val primaryUser by viewModel.primaryUser.collectAsState()
    
    var selectedMemberForEdit by remember { mutableStateOf<MemberUiModel?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<MemberUiModel?>(null) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    when (val state = profiles) {
        is ProfileUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryGreen)
            }
        }

        is ProfileUiState.Empty -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No profiles found. Please login again.")
            }
        }

        is ProfileUiState.Success -> {
            val user = state.members.firstOrNull { it.type == MemberType.PRIMARY }
            if (user == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("User data not found.")
                }
                return@ProfileScreen
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                // 1. Unified Green Header Block
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                            .background(BrandingGreen)
                    ) {
                        Column(
                            modifier = Modifier
                                .statusBarsPadding()
                                .padding(16.dp)
                        ) {
                            // Profile Info Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clickable { onNavigateToAccount() },
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.onPrimary
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = user.name.getOrNull(0)?.toString()?.uppercase()
                                                ?: "?",
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = user.name,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        maxLines = 2,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    val conditionText = user.condition.take(15)
                                    Text(
                                        text = "Age ${user.age.takeIf { it.isNotBlank() } ?: "--"} · $conditionText",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Spacer(modifier = Modifier.height(20.dp))

                            // Simple Connection Code Entry Point
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToCode() },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        modifier = Modifier.size(40.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Share,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "My Connection Code",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Text(
                                            text = "Share this code to connect with family",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                        )
                                    }

                                    Icon(
                                        Icons.Default.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Avatar Row
                            androidx.compose.foundation.lazy.LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(state.members.size) { index ->
                                    val profile = state.members[index]
                                    TrayAvatar(
                                        initial = profile.name.getOrNull(0)?.toString()
                                            ?.uppercase() ?: "?",
                                        label = if (profile.type == MemberType.PRIMARY) "You" else profile.name.lowercase()
                                            .split(" ").first(),
                                        onClick = {
                                            if (profile.type == MemberType.PRIMARY) {
                                                onNavigateToAccount()
                                            } else {
                                                onNavigateToDetail(
                                                    profile.id,
                                                    profile.name,
                                                    profile.connectionId
                                                )
                                            }
                                        }
                                    )
                                }
                                item {
                                    AddTrayButton(onClick = onNavigateToConnection)
                                }
                            }
                        }
                    }
                }


                // Family Members Section Label
                item {
                    Text(
                        text = "FAMILY MEMBERS",
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 20.dp,
                            bottom = 12.dp
                        )
                    )
                }

                // Member Cards
                items(state.members.size) { index ->
                    val profile = state.members[index]
                    MemberCard(
                        profile = profile,
                        onView = {
                            if (profile.type == MemberType.PRIMARY) {
                                onNavigateToAccount()
                            } else {
                                onNavigateToDetail(profile.id, profile.name, profile.connectionId)
                            }
                        },
                        onEdit = { selectedMemberForEdit = profile }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Additional Action Sections
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
                        AddMemberDashedButton(onClick = onNavigateToConnection)
                        Spacer(modifier = Modifier.height(20.dp))
                        PremiumBanner()
                        Spacer(modifier = Modifier.height(16.dp))
                        SettingsEntry(onClick = onNavigateToSettings)

                        val pendingCount = incomingRequests.size
                        if (pendingCount > 0) {
                            Spacer(modifier = Modifier.height(10.dp))
                            ConnectionRequestsSettingsEntry(
                                count = pendingCount,
                                onClick = {
                                    val latest =
                                        incomingRequests.sortedByDescending { it.timestamp }
                                            .firstOrNull()
                                    if (latest != null) {
                                        onNavigateToConsent(latest.id, latest.senderId)
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(100.dp))
                    }
                }
            }
        }
    }

    // --- EDIT MEMBER BOTTOM SHEET ---
    selectedMemberForEdit?.let { member ->
        EditMemberBottomSheet(
            member = member,
            onDismiss = { selectedMemberForEdit = null },
            onUpdate = { rel ->
                viewModel.updateMember(member.id, rel)
                selectedMemberForEdit = null
            },
            onDelete = { showDeleteConfirm = member }
        )
    }

    // --- DELETE CONFIRMATION DIALOG ---
    showDeleteConfirm?.let { member ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Remove Connection?") },
            text = { 
                Text("Are you sure you want to disconnect from ${member.name}? You will no longer be able to see their adherence or receive alerts.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteMember(member.id)
                        showDeleteConfirm = null
                        selectedMemberForEdit = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("DISCONNECT")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("CANCEL")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditMemberBottomSheet(
    member: MemberUiModel,
    onDismiss: () -> Unit,
    onUpdate: (String) -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    
    var name by remember { mutableStateOf(member.name) }
    var relation by remember { mutableStateOf(member.relation) }
    var age by remember { mutableStateOf(member.age) }
    var condition by remember { mutableStateOf(member.condition) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Member Connection",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.LinkOff,
                        contentDescription = "Remove",
                        tint = Color.Red
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Profile summary card (Read-only for connected accounts)
            Surface(
                color = PrimaryGreen.copy(alpha = 0.05f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, PrimaryGreen.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color = PrimaryGreen.copy(alpha = 0.1f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                member.name.firstOrNull()?.toString()?.uppercase() ?: "?",
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = PrimaryGreen
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(member.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            "Linked Account · Age ${member.age}", 
                            fontSize = 12.sp, 
                            color = TextSecondary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            // Connected member editing (Labels only)
            OutlinedTextField(
                value = relation,
                onValueChange = { relation = it },
                label = { Text("How are they related to you?") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = { onUpdate(relation) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
            ) {
                Text("SAVE CHANGES", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun TrayAvatar(initial: String, label: String, onClick: () -> Unit) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable(onClick = onClick)
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.2f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = initial,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
    }
}

@Composable
fun AddTrayButton(onClick: () -> Unit) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .drawBehind {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.5f),
                            style = Stroke(
                                width = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            )
                        )
                    }
                    .clickable { onClick() },
                contentAlignment = Alignment.Center
            ) {
                Text("+", fontSize = 20.sp, color = MaterialTheme.colorScheme.onPrimary)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Add", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimary)
    }
}


@Composable
fun PulsingLiveIndicator() {
        val infiniteTransition =
            androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = androidx.compose.animation.core.tween(
                    1000,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                ),
                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "alpha"
        )
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.4f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = androidx.compose.animation.core.tween(
                    1000,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                ),
                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "scale"
        )

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(16.dp)) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .graphicsLayer(scaleX = scale, scaleY = scale, alpha = alpha)
                    .clip(CircleShape)
                    .background(Color(0xFF4CAF50).copy(alpha = 0.4f))
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4CAF50))
            )
        }
    }

@Composable
fun MemberCard(
        profile: MemberUiModel,
        onView: () -> Unit,
        onEdit: () -> Unit
    ) {
        val isPrimary = profile.type == MemberType.PRIMARY
        val isLive = profile.type == MemberType.CONNECTED || isPrimary

        val backgroundColor =
            if (isLive) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface
        val borderColor =
            if (isLive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(
                alpha = 0.4f
            )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clickable { if (isLive) onView() else onEdit() },
            shape = RoundedCornerShape(20.dp),
            color = backgroundColor,
            border = BorderStroke(1.dp, borderColor)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color = if (isLive) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = profile.name.getOrNull(0)?.toString()?.uppercase() ?: "?",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isLive) PrimaryGreen else TextPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = profile.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            if (isPrimary) {
                                StatusBadge(
                                    text = "You",
                                    color = Color(0xFFE8F5E9),
                                    textColor = PrimaryGreen
                                )
                            } else {
                                Surface(
                                    color = PrimaryGreen,
                                    shape = RoundedCornerShape(percent = 50)
                                ) {
                                    Text(
                                        text = profile.relation.lowercase(),
                                        modifier = Modifier.padding(
                                            horizontal = 8.dp,
                                            vertical = 2.dp
                                        ),
                                        fontSize = 10.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                if (isLive) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.background(
                                            Color(0xFFE8F5E9),
                                            shape = RoundedCornerShape(percent = 50)
                                        ).padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        PulsingLiveIndicator()
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "Live",
                                            fontSize = 10.sp,
                                            color = PrimaryGreen,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        val statusText = getTimeAgo(profile.lastActiveAt)
                        Text(
                            text = if (isLive) {
                                "Age ${profile.age} · ${profile.condition.takeIf { it.isNotBlank() } ?: "Healthy"} · Last synced $statusText"
                            } else {
                                "Age ${profile.age} · ${profile.condition.takeIf { it.isNotBlank() } ?: "Healthy"}"
                            },
                            fontSize = 12.sp,
                            color = TextSecondary.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }

                    TextButton(
                        onClick = { if (isLive || isPrimary) onView() else onEdit() },
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Text(
                            text = if (isLive) "View" else "Edit",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryGreen
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Adherence this week",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    LinearProgressIndicator(
                        progress = { profile.adherence / 100f },
                        modifier = Modifier.weight(1f).height(8.dp).clip(CircleShape),
                        color = if (profile.adherence >= 80) PrimaryGreen else Color(0xFFFFA000),
                        trackColor = Color(0xFFEEEEEE),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "${profile.adherence}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (profile.adherence >= 80) PrimaryGreen else Color(0xFFFFA000)
                    )
                }
            }
    }
}

@Composable
fun StatusBadge(text: String, color: Color, textColor: Color) {
        Surface(color = color, shape = RoundedCornerShape(percent = 50)) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
        }
    }

@Composable
fun AddMemberDashedButton(onClick: () -> Unit) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRoundRect(
                        color = Color.LightGray,
                        style = Stroke(
                            width = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx())
                    )
                }
                .clickable { onClick() },
            color = Color.Transparent,
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text("+", fontSize = 18.sp, color = TextSecondary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Connect with family",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
            }
        }
    }

@Composable
fun PremiumBanner() {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFFFFF3E0),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = Color(0xFFFFA000),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Upgrade to Premium",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        "Unlimited members · PDF reports · Priority alerts",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                Surface(color = Color(0xFFFFA000), shape = RoundedCornerShape(8.dp)) {
                    Text(
                        text = "₹49/mo",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }

@Composable
fun SettingsEntry(onClick: () -> Unit) {
        Surface(
            modifier = Modifier.fillMaxWidth().clickable { onClick() },
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFEEEEEE))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF5F5F5)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Settings",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        "Notifications, language, account",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color.LightGray
                )
            }
        }
    }

@Composable
fun ConnectionRequestsSettingsEntry(count: Int, onClick: () -> Unit) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() },
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFEEEEEE))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF5F5F5)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Connection Requests",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "$count pending request${if (count > 1) "s" else ""}",
                        fontSize = 11.sp,
                        color = PrimaryGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color.LightGray
                )
            }
        }
    }


