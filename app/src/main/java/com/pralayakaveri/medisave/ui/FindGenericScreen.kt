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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pralayakaveri.medisave.data.MedicineEntity
import com.pralayakaveri.medisave.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindGenericContent(viewModel: GenericViewModel = viewModel(), onNavigateToPharmacy: ((String) -> Unit)? = null) {
    var searchQuery by remember { mutableStateOf("") }
    val searchResults by viewModel.searchResults.collectAsState()
    val selectedMedicine by viewModel.selectedMedicine.collectAsState()
    val alternatives by viewModel.alternatives.collectAsState()

    val isDark = MaterialTheme.colorScheme.background == Color(0xFF0B0F0C)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) MaterialTheme.colorScheme.background else Color.White)
    ) {
        // ... (Header and search field)
        FindGenericHeader(
            searchQuery = searchQuery,
            onSearch = { query ->
                searchQuery = query
                if (query.isBlank()) viewModel.selectMedicine(null)
                viewModel.searchBrand(query)
            },
            onClear = {
                searchQuery = ""
                viewModel.searchBrand("")
                viewModel.selectMedicine(null)
            },
            showRecent = selectedMedicine == null
        )

        // Inner Scrollable Content
        if (selectedMedicine == null) {
            if (searchResults.isEmpty() && searchQuery.isNotEmpty()) {
                GenericEmptyState()
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    items(searchResults) { medicine ->
                        MedicineSearchResultItem(medicine) {
                            searchQuery = medicine.brandName
                            viewModel.selectMedicine(medicine)
                        }
                    }
                }
            }
        } else {
            // ... (Detail view implementation remains the same)
            FindGenericDetails(selectedMedicine!!, alternatives, onNavigateToPharmacy)
        }
    }
}

@Composable
fun FindGenericHeader(
    searchQuery: String,
    onSearch: (String) -> Unit,
    onClear: () -> Unit,
    showRecent: Boolean
) {
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF0B0F0C)
    val headerBg = if (isDark) MaterialTheme.colorScheme.surface else PrimaryGreen
    val titleColor = if (isDark) MaterialTheme.colorScheme.onSurface else Color.White
    val searchContainerColor = if (isDark) MaterialTheme.colorScheme.background else Color.White
    val searchTextColor = if (isDark) MaterialTheme.colorScheme.onSurface else TextPrimary
    val placeholderColor = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else TextSecondary
    val pillBgColor = if (isDark) MaterialTheme.colorScheme.background else Color.White.copy(alpha = 0.2f)
    val pillTextColor = if (isDark) MaterialTheme.colorScheme.onSurface else Color.White
    val cursorColor = if (isDark) MaterialTheme.colorScheme.primary else PrimaryGreen

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(headerBg)
            .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 12.dp)
    ) {
        Text(
            text = "Find generic medicine",
            fontSize = 22.sp,
            color = titleColor,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearch,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            placeholder = { Text("Crocin 500mg", color = placeholderColor) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = placeholderColor) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = searchTextColor)
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = searchContainerColor,
                unfocusedContainerColor = searchContainerColor,
                focusedTextColor = searchTextColor,
                unfocusedTextColor = searchTextColor,
                cursorColor = cursorColor
            )
        )

        if (showRecent) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Paracetamol", "Ibuprofen", "Cetirizine").forEach { term ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(pillBgColor)
                            .clickable { onSearch(term) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(text = term, color = pillTextColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun GenericEmptyState() {
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF0B0F0C)
    val TextPrimary = if (isDark) MaterialTheme.colorScheme.onBackground else com.pralayakaveri.medisave.ui.theme.TextPrimary
    val TextSecondary = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else com.pralayakaveri.medisave.ui.theme.TextSecondary

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🔍", fontSize = 48.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "No results found",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            "Try searching for the official salt name or a popular brand.",
            textAlign = TextAlign.Center,
            color = TextSecondary,
            fontSize = 14.sp
        )
    }
}

@Composable
fun FindGenericDetails(selectedMedicine: MedicineEntity, alternatives: List<MedicineEntity>, onNavigateToPharmacy: ((String) -> Unit)? = null) {
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF0B0F0C)
    val TextPrimary = if (isDark) MaterialTheme.colorScheme.onBackground else com.pralayakaveri.medisave.ui.theme.TextPrimary
    val TextSecondary = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else com.pralayakaveri.medisave.ui.theme.TextSecondary
    val SoftYellowBg = if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFFBF9F4)
    val WarningBg = if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFFAF7EE)

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SoftYellowBg)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("SALT COMPOSITION", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        val safeComposition = buildString {
                            append(selectedMedicine.saltComposition)
                            if (selectedMedicine.strength.isNotEmpty()) {
                                append(" ")
                                append(selectedMedicine.strength)
                            }
                        }
                        Text(safeComposition, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFE8EAF6))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Schedule H", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3F51B5))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("YOU SEARCHED", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
            SelectedBrandCard(medicine = selectedMedicine)
            Spacer(modifier = Modifier.height(24.dp))
            Text("CHEAPER GENERICS — SAME SALT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
        }
        
        val filteredAlts = alternatives.filter { it.id != selectedMedicine.id }
        if (filteredAlts.isNotEmpty()) {
            items(filteredAlts) { alt ->
                GenericAlternativeCard(
                    alternative = alt, 
                    selectedMedicine = selectedMedicine,
                    onNavigateToPharmacy = onNavigateToPharmacy
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(WarningBg)
                    .padding(16.dp)
            ) {
                Text(
                    text = "Always confirm with your doctor or pharmacist before switching. All generics listed are CDSCO-approved.",
                    fontSize = 12.sp,
                    color = TextPrimary,
                    lineHeight = 18.sp
                )
            }
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

// ... existing helper composables (MedicineSearchResultItem, SelectedBrandCard, GenericAlternativeCard) remain unchanged below

@Composable
fun MedicineSearchResultItem(medicine: MedicineEntity, onClick: () -> Unit) {
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF0B0F0C)
    val TextPrimary = if (isDark) MaterialTheme.colorScheme.onBackground else com.pralayakaveri.medisave.ui.theme.TextPrimary
    val TextSecondary = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else com.pralayakaveri.medisave.ui.theme.TextSecondary
    val DividerGray = if (isDark) MaterialTheme.colorScheme.outline else com.pralayakaveri.medisave.ui.theme.DividerGray

    Column(modifier = Modifier
        .fillMaxWidth()
        .clickable { onClick() }
        .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Text(text = medicine.brandName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(text = medicine.saltComposition, fontSize = 12.sp, color = TextSecondary)
        Divider(color = DividerGray, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
fun SelectedBrandCard(medicine: MedicineEntity) {
    val RedBg = Color(0xFFFDECEA)
    val RedBorder = Color(0xFFE57373)
    val RedText = Color(0xFFB71C1C)
    
    val packSize = medicine.packSize ?: -1
    val packText = if (packSize > 0) "$packSize tablets" else "Pack size unknown"
    val perTabletPrice = if (packSize > 0) String.format("%.2f", medicine.price / packSize.toDouble()) else null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(RedBg)
            .border(1.dp, RedBorder, RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Branded", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RedText)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = medicine.brandName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = RedText)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = "${medicine.manufacturer} · $packText", fontSize = 12.sp, color = RedText)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(text = "₹${String.format("%.0f", medicine.price)}", fontSize = 24.sp, fontWeight = FontWeight.Medium, color = RedText)
            if (perTabletPrice != null) {
                Text(text = "₹$perTabletPrice/tablet", fontSize = 11.sp, color = RedText)
            }
        }
    }
}

@Composable
fun GenericAlternativeCard(alternative: MedicineEntity, selectedMedicine: MedicineEntity, onNavigateToPharmacy: ((String) -> Unit)? = null) {
    val GreenBg = Color(0xFFE5F5EC)
    val GreenBorder = Color(0xFF5BC195)
    val DarkGreenText = Color(0xFF0F6849)
    
    val altPackSize = alternative.packSize ?: -1
    val basePackSize = selectedMedicine.packSize ?: -1

    val altPerTablet = if (altPackSize > 0) alternative.price / altPackSize.toDouble() else null
    val basePerTablet = if (basePackSize > 0) selectedMedicine.price / basePackSize.toDouble() else null

    val savingsPercent = if (altPerTablet != null && basePerTablet != null && basePerTablet > 0) {
        (((basePerTablet - altPerTablet) / basePerTablet) * 100).toInt()
    } else if (selectedMedicine.price > 0 && (altPackSize == basePackSize || (altPackSize <= 0 && basePackSize <= 0))) {
        (((selectedMedicine.price - alternative.price) / selectedMedicine.price) * 100).toInt()
    } else {
        0
    }

    val packText = if (altPackSize > 0) "$altPackSize tab" else "Pack"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GreenBg)
            .border(1.dp, GreenBorder, RoundedCornerShape(12.dp))
            .clickable { 
                if (onNavigateToPharmacy != null) {
                    val compositionQuery = buildString {
                        append(alternative.saltComposition)
                        if (alternative.strength.isNotEmpty()) {
                            append(" ")
                            append(alternative.strength)
                        }
                        append(" pharmacy")
                    }
                    onNavigateToPharmacy(compositionQuery)
                }
            }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (savingsPercent > 0) {
                Text(text = "Save $savingsPercent%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkGreenText)
                Spacer(modifier = Modifier.height(2.dp))
            }
            Text(text = alternative.brandName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DarkGreenText)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = "${alternative.manufacturer} · Generic · $packText", fontSize = 12.sp, color = DarkGreenText.copy(alpha = 0.8f))
            
            if (alternative.strength.isNotEmpty()) {
                Text(text = "Same strength (${alternative.strength})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkGreenText.copy(alpha = 0.9f))
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(DarkGreenText.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(text = "WHO-GMP", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = DarkGreenText)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(DarkGreenText.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(text = "Jan Aushadhi", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = DarkGreenText)
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(text = "₹${String.format("%.0f", alternative.price)}", fontSize = 24.sp, fontWeight = FontWeight.Medium, color = DarkGreenText)
            if (altPerTablet != null) {
                Text(text = "₹${String.format("%.2f", altPerTablet)}/tablet", fontSize = 11.sp, color = DarkGreenText.copy(alpha = 0.8f))
            }
        }
    }
}
