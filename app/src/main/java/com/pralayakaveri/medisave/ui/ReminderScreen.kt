package com.pralayakaveri.medisave.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.util.Calendar
import com.pralayakaveri.medisave.ui.theme.*
import com.pralayakaveri.medisave.util.formatTime
import com.pralayakaveri.medisave.viewmodel.ReminderViewModel
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReminderScreen(
    navController: NavController,
    viewModel: ReminderViewModel = viewModel()
) {
    val scrollState = rememberScrollState()
    val inputBg = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    val context = LocalContext.current

    val forms = listOf("Tablet", "Capsule", "Syrup", "Injection")
    val daysShort = listOf("M", "T", "W", "T", "F", "S", "S")

    // Navigation Effect
    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collectLatest {
            navController.popBackStack()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Add reminder", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .padding(start = 16.dp, end = 8.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(inputBg)
                            .clickable { navController.popBackStack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                },
                actions = {
                    val isValid = viewModel.medicineName.value.isNotBlank() && 
                                  viewModel.selectedTimes.isNotEmpty() && 
                                  viewModel.selectedDays.isNotEmpty()
                    
                    TextButton(
                        onClick = { if (isValid) viewModel.saveReminder() },
                        enabled = isValid
                    ) {
                        Text(
                            "Save", 
                            color = if (isValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), 
                            fontWeight = FontWeight.Bold, 
                            fontSize = 14.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Medicine Name
            SectionLabel("MEDICINE NAME")
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = viewModel.medicineName.value,
                onValueChange = { viewModel.medicineName.value = it },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                placeholder = { Text("e.g. Atorvastatin 10mg", color = TextSecondary.copy(alpha=0.5f)) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha=0.08f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Quantity & Form
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(0.4f)) {
                    SectionLabel("TABLETS PER DOSE")
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(inputBg)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "${viewModel.quantity.value}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Add, 
                                contentDescription = "Inc", 
                                modifier = Modifier.size(20.dp).clickable { 
                                    if (viewModel.quantity.value < 3) viewModel.quantity.value++ 
                                }, 
                                tint = PrimaryGreen
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier
                                .width(12.dp)
                                .height(2.dp)
                                .background(if (viewModel.quantity.value > 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                                .clickable { if (viewModel.quantity.value > 1) viewModel.quantity.value-- })
                        }
                    }
                }
                Column(modifier = Modifier.weight(0.6f)) {
                    SectionLabel("FORM")
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(inputBg)
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        var expanded by remember { mutableStateOf(false) }
                        Text(
                            text = viewModel.selectedForm.value, 
                            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
                            fontSize = 16.sp, 
                            fontWeight = FontWeight.Medium, 
                            color = TextPrimary
                        )
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            forms.forEach { form ->
                                DropdownMenuItem(
                                    text = { Text(form) },
                                    onClick = { 
                                        viewModel.selectedForm.value = form
                                        expanded = false 
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            
            val dailyDose = viewModel.quantity.value * viewModel.selectedTimes.size
            if (viewModel.selectedTimes.isNotEmpty()) {
                Text(
                    text = "You will take $dailyDose ${viewModel.selectedForm.value.lowercase()}(s) per day", 
                    fontSize = 13.sp, 
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )
            }
            if (viewModel.quantity.value > 2) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = AlertOrangeText, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("This seems higher than usual. Please confirm.", fontSize = 12.sp, color = AlertOrangeText, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Reminder Times
            SectionLabel("REMINDER TIMES")
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                viewModel.selectedTimes.forEach { time ->
                    TimePill(
                        time = time, 
                        onRemove = { viewModel.removeTime(time) }
                    )
                }
                
                // Add Time Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .drawBehind {
                            drawRoundRect(
                                color = PrimaryGreen,
                                style = Stroke(
                                    width = 1.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                ),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
                            )
                        }
                        .clickable { 
                            context.findActivity()?.let { activity ->
                                showMaterialTimePicker(activity) { formattedTime ->
                                    viewModel.addTime(formattedTime)
                                }
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text("+ Add time", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Repeat Days
            SectionLabel("REPEAT DAYS")
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                daysShort.forEachIndexed { index, day ->
                    val dayInt = index + 1
                    val isSelected = viewModel.selectedDays.contains(dayInt)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else inputBg)
                            .clickable { viewModel.toggleDay(dayInt) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = day,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Instructions
            SectionLabel("INSTRUCTIONS")
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = viewModel.instructions.value,
                onValueChange = { viewModel.instructions.value = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. After lunch", color = TextSecondary.copy(alpha=0.5f)) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryGreen,
                    unfocusedBorderColor = dividerColor,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = inputBg
                )
            )

            Spacer(modifier = Modifier.height(24.dp))
            SectionLabel("START TRACKING FROM")
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                listOf("Today" to false, "Tomorrow" to true).forEach { (label, value) ->
                    val isSelected = viewModel.isStartTomorrow.value == value
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else inputBg)
                            .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(12.dp))
                            .clickable { viewModel.isStartTomorrow.value = value },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (label == "Today") Icons.Default.Today else Icons.Default.EventNote,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (isSelected) PrimaryGreen else TextSecondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label, 
                                fontSize = 14.sp, 
                                fontWeight = FontWeight.Bold, 
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Extra Settings
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, dividerColor, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Pill count tracker", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Switch(
                        checked = viewModel.pillTrackerEnabled.value,
                        onCheckedChange = { viewModel.pillTrackerEnabled.value = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.onPrimary, checkedTrackColor = MaterialTheme.colorScheme.primary)
                    )
                }

                if (viewModel.pillTrackerEnabled.value) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            SectionLabel("PILLS IN HAND", size = 10.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = viewModel.pillsInHand.value,
                                onValueChange = { viewModel.pillsInHand.value = it },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = inputBg, unfocusedBorderColor = Color.Transparent)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            SectionLabel("ALERT WHEN (≤)", size = 10.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = viewModel.alertThreshold.value,
                                onValueChange = { viewModel.alertThreshold.value = it },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = inputBg, unfocusedBorderColor = Color.Transparent)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            
            ToggleSetting(
                title = "Push notification",
                subtitle = "Ring + vibrate",
                checked = viewModel.pushNotificationEnabled.value,
                onCheckedChange = { viewModel.pushNotificationEnabled.value = it }
            )
            
            Divider(color = dividerColor, modifier = Modifier.padding(vertical = 12.dp))
            
            var expanded by remember { mutableStateOf(false) }
            val options = listOf(
                Triple("5 minutes", 5, true),
                Triple("10 minutes", 10, true),
                Triple("15 minutes", 15, true),
                Triple("30 minutes", 30, true),
                Triple("1 hour", 60, true),
                Triple("Never", 60, false)
            )
            val currentSelectedText = when {
                !viewModel.caregiverAlertEnabled.value -> "Never"
                viewModel.gracePeriodMinutes.value == 60 -> "1 hour"
                else -> "${viewModel.gracePeriodMinutes.value} minutes"
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Alert Family If Not Taken Within", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Dynamic nudge & caregiver delay", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    Box {
                        Text(
                            text = currentSelectedText,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(inputBg)
                                .clickable { expanded = true }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            color = PrimaryGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            options.forEach { (label, minutes, enabled) ->
                                DropdownMenuItem(
                                    text = { Text(label, fontWeight = FontWeight.Medium) },
                                    onClick = {
                                        viewModel.gracePeriodMinutes.value = minutes
                                        viewModel.caregiverAlertEnabled.value = enabled
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Save Reminder button
            if (viewModel.errorMessage.value.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically, 
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = "Error", tint = Color.Red, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(viewModel.errorMessage.value, color = Color.Red, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = { viewModel.saveReminder() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                enabled = viewModel.medicineName.value.isNotBlank()
            ) {
                Text("Save reminder", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

fun Context.findActivity(): AppCompatActivity? = when (this) {
    is AppCompatActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun showMaterialTimePicker(
    activity: AppCompatActivity,
    onTimeSelected: (String) -> Unit
) {
    val calendar = Calendar.getInstance(java.util.Locale.getDefault())
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val minute = calendar.get(Calendar.MINUTE)

    val picker = MaterialTimePicker.Builder()
        .setTimeFormat(TimeFormat.CLOCK_12H)
        .setHour(hour)
        .setMinute(minute)
        .setTitleText("Select Medication Time")
        .build()

    picker.addOnPositiveButtonClickListener {
        val h = picker.hour.toString().padStart(2, '0')
        val m = picker.minute.toString().padStart(2, '0')
        onTimeSelected("$h:$m")
    }

    picker.show(activity.supportFragmentManager, "MATERIAL_TIME_PICKER")
}

@Composable
fun SectionLabel(text: String, size: androidx.compose.ui.unit.TextUnit = 11.sp) {
    Text(text, fontSize = size, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
}

@Composable
fun ToggleSetting(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary, 
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
fun TimePill(time: String, onRemove: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(TakenGreenBg)
            .border(1.dp, PrimaryGreen, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = formatTime(time), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = PrimaryGreen)
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                Icons.Default.Close, 
                contentDescription = "Remove", 
                modifier = Modifier.size(14.dp).clickable { onRemove() }, 
                tint = PrimaryGreen
            )
        }
    }
}
