package com.pralayakaveri.medisave.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pralayakaveri.medisave.data.FamilyConnectionRepository
import com.pralayakaveri.medisave.ui.theme.*
import kotlinx.coroutines.launch

class ConnectionSuccessViewModel : ViewModel() {
    private val repository = FamilyConnectionRepository()
    
    var uiState by mutableStateOf<SuccessUiState>(SuccessUiState.Loading)
        private set

    fun loadMemberData(requestId: String, receiverId: String, relation: String) {
        viewModelScope.launch {
            val profile = repository.getUserPublicProfile(receiverId)
            val stats = repository.getMemberWeeklyStats(receiverId)
            
            uiState = SuccessUiState.Success(
                name = profile?.get("name") as? String ?: "User",
                relation = relation,
                medicinesCount = stats.medicineCount,
                adherence = stats.adherence,
                missedCount = stats.missedCount,
                receiverId = receiverId,
                requestId = requestId
            )
        }
    }

        }


sealed class SuccessUiState {
    object Loading : SuccessUiState()
    data class Success(
        val name: String,
        val relation: String,
        val medicinesCount: Int,
        val adherence: Int,
        val missedCount: Int,
        val receiverId: String,
        val requestId: String
    ) : SuccessUiState()
}

@Composable
fun ConnectionSuccessScreen(
    requestId: String,
    receiverId: String,
    relation: String,
    onViewDashboard: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ConnectionSuccessViewModel = viewModel()
) {
    val authViewModel: com.pralayakaveri.medisave.viewmodel.AuthViewModel = viewModel()
    
    LaunchedEffect(requestId) {
        viewModel.loadMemberData(requestId, receiverId, relation)
        // Mark as handled to prevent re-showing
        if (requestId != "fcm_accepted") {
            authViewModel.markRequestAsHandledInFirestore(requestId)
        }
    }

    val isDark = MaterialTheme.colorScheme.background == Color(0xFF0B0F0C)

    Scaffold(containerColor = if (isDark) MaterialTheme.colorScheme.background else Color.White) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = viewModel.uiState) {
                is SuccessUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = PrimaryGreen)
                }
                is SuccessUiState.Success -> {
                    SuccessContent(
                        state = state,
                        onViewDashboard = {
                            onViewDashboard(state.receiverId)
                        },
                        onBack = {
                            onBack()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SuccessContent(
    state: SuccessUiState.Success,
    onViewDashboard: () -> Unit,
    onBack: () -> Unit
) {
    val displayRelation = state.relation.lowercase().replaceFirstChar { it.uppercase() }
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF0B0F0C)
    val TextPrimary = if (isDark) MaterialTheme.colorScheme.onBackground else com.pralayakaveri.medisave.ui.theme.TextPrimary
    val TextSecondary = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else com.pralayakaveri.medisave.ui.theme.TextSecondary
    val PrimaryGreen = if (isDark) MaterialTheme.colorScheme.primary else com.pralayakaveri.medisave.ui.theme.PrimaryGreen
    val DividerGray = if (isDark) MaterialTheme.colorScheme.outlineVariant else com.pralayakaveri.medisave.ui.theme.DividerGray
    val LightGrayBg = if (isDark) MaterialTheme.colorScheme.surface else com.pralayakaveri.medisave.ui.theme.LightGrayBg
    
    // AUTO-REDIRECT after 3 seconds
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(3000)
        onViewDashboard()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        
        // 1. Top Section - Success Icon
        Surface(
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            color = TakenGreenBg
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = PrimaryGreen,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Connected to ${state.name} ($displayRelation)",
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary,
            letterSpacing = (-0.5).sp,
            textAlign = TextAlign.Center
        )
        Text(
            text = "You'll be notified if anything needs attention",
            fontSize = 15.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp).padding(top = 8.dp)
        )
        
        Spacer(modifier = Modifier.height(40.dp))
        
        // 2. User Summary Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = LightGrayBg,
            border = BorderStroke(1.dp, DividerGray)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(52.dp),
                        shape = CircleShape,
                        color = PrimaryGreen.copy(alpha = 0.1f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = state.name.take(1).uppercase(),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = PrimaryGreen
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = state.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
                        Text(text = "$displayRelation · ${state.medicinesCount} medicines", fontSize = 14.sp, color = TextSecondary)
                    }
                    
                    Surface(
                        color = PrimaryGreen.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(percent = 50)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Box(modifier = Modifier.size(6.dp).background(PrimaryGreen, CircleShape))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Live", color = PrimaryGreen, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Stats Row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatBox(label = "Medicines", value = state.medicinesCount.toString(), modifier = Modifier.weight(1f))
                    StatBox(label = "Adherence", value = "${state.adherence}%", color = Color(0xFFF19D38), modifier = Modifier.weight(1f))
                    StatBox(
                        label = "Missed (Wk)", 
                        value = state.missedCount.toString(), 
                        color = if (state.missedCount > 0) Color(0xFFE53935) else TextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 3. Alert Info Box
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = AlertOrangeBg.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, AlertOrangeBg)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = AlertOrangeText, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "You'll receive push alerts if ${state.name.split(" ").first()} misses any medication doses.",
                    fontSize = 13.sp,
                    color = AlertOrangeText,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // 4. Primary Action Button
        Button(
            onClick = onViewDashboard,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
        ) {
            Text(
                text = "View Dashboard", 
                fontSize = 16.sp, 
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // 5. Secondary Action Button
        TextButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(text = "Go to Profile", color = TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun StatBox(label: String, value: String, modifier: Modifier = Modifier, color: Color = Color.Black) {
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF0B0F0C)
    val defaultValColor = if (color == Color.Black) {
        if (isDark) MaterialTheme.colorScheme.onBackground else Color.Black
    } else color
    val labelColor = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else Color.Gray
    val cardBg = if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color.White

    Surface(
        modifier = modifier.height(80.dp),
        shape = RoundedCornerShape(16.dp),
        color = cardBg
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(8.dp)
        ) {
            Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = defaultValColor)
            Text(text = label, fontSize = 11.sp, color = labelColor, textAlign = TextAlign.Center, lineHeight = 14.sp)
        }
    }
}
