package com.pralayakaveri.medisave.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.pralayakaveri.medisave.model.Pharmacy
import com.pralayakaveri.medisave.ui.theme.*
import com.pralayakaveri.medisave.viewmodel.MapUiState
import com.pralayakaveri.medisave.viewmodel.MapViewModel
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.with
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.google.maps.android.compose.CameraMoveStartedReason

enum class MapMode { PHARMACIES, GENERICS }

enum class MapSheetState { COLLAPSED, PEEK, EXPANDED }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class, androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel = viewModel(),
    initialMode: MapMode = MapMode.PHARMACIES
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val userLocation by viewModel.userLocation.collectAsState()
    
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF0B0F0C)
    val TextPrimary = if (isDark) MaterialTheme.colorScheme.onBackground else com.pralayakaveri.medisave.ui.theme.TextPrimary
    val TextSecondary = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else com.pralayakaveri.medisave.ui.theme.TextSecondary
    val PrimaryGreen = if (isDark) MaterialTheme.colorScheme.primary else com.pralayakaveri.medisave.ui.theme.PrimaryGreen
    val PrimaryGreenDark = if (isDark) MaterialTheme.colorScheme.primaryContainer else com.pralayakaveri.medisave.ui.theme.PrimaryGreenDark
    val DividerGray = if (isDark) MaterialTheme.colorScheme.outlineVariant else com.pralayakaveri.medisave.ui.theme.DividerGray
    val surfaceColor = if (isDark) MaterialTheme.colorScheme.surface else Color.White
    val containerBgColor = if (isDark) MaterialTheme.colorScheme.background else Color(0xFFF9F9F9)
    val searchQuery by viewModel.searchQuery.collectAsState()
    
    var mapMode by rememberSaveable { mutableStateOf(initialMode) }
    var selectedPharmacy by remember { mutableStateOf<Pharmacy?>(null) }
    var mapLoaded by remember { mutableStateOf(false) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(12.9716, 77.5946), 12f)
    }

    // Permission handling
    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    LaunchedEffect(locationPermissionState.status.isGranted, mapLoaded) {
        if (locationPermissionState.status.isGranted && mapLoaded) {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        val currentLatLng = LatLng(it.latitude, it.longitude)
                        viewModel.updateLocation(currentLatLng, context)
                        coroutineScope.launch {
                            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(currentLatLng, 14f))
                        }
                    }
                }
            } catch (e: SecurityException) {
                Log.e("MapScreen", "Location permission not granted", e)
            }
        }
    }

    var sheetState by rememberSaveable { mutableStateOf(MapSheetState.PEEK) }
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val expandedHeight = screenHeight * 0.82f

    val targetHeight = when {
        selectedPharmacy != null -> 220.dp
        sheetState == MapSheetState.COLLAPSED -> 72.dp
        sheetState == MapSheetState.PEEK -> 350.dp
        else -> expandedHeight
    }

    val bottomHeight by animateDpAsState(
        targetValue = targetHeight,
        label = "BottomHeightAnimation"
    )

    Column(modifier = Modifier.fillMaxSize().background(if (isDark) MaterialTheme.colorScheme.background else Color.White)) {
        // Mode Selector Toggle
        MapModeToggle(
            currentMode = mapMode,
            onModeChange = { mapMode = it }
        )

        AnimatedContent(
            targetState = mapMode,
            transitionSpec = {
                if (targetState == MapMode.GENERICS) {
                    slideInVertically { it } + fadeIn() with slideOutVertically { -it } + fadeOut()
                } else {
                    slideInVertically { -it } + fadeIn() with slideOutVertically { it } + fadeOut()
                }.using(SizeTransform(clip = false))
            },
            modifier = Modifier.weight(1f)
        ) { mode ->
            when (mode) {
                MapMode.PHARMACIES -> {
                    PharmacyMapContent(
                        viewModel = viewModel,
                        uiState = uiState,
                        userLocation = userLocation,
                        cameraPositionState = cameraPositionState,
                        locationPermissionState = locationPermissionState,
                        selectedPharmacy = selectedPharmacy,
                        onPharmacySelect = { selectedPharmacy = it },
                        bottomHeight = bottomHeight,
                        mapLoaded = mapLoaded,
                        onMapLoaded = { mapLoaded = true },
                        sheetState = sheetState,
                        onSheetStateChange = { sheetState = it }
                    )
                }
                MapMode.GENERICS -> {
                    FindGenericContent(
                        onNavigateToPharmacy = { query ->
                            mapMode = MapMode.PHARMACIES
                            viewModel.updateSearchQuery(query)
                            userLocation?.let { 
                                viewModel.searchPharmaciesByText(query, it, context) 
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PharmacyMapContent(
    viewModel: MapViewModel,
    uiState: MapUiState,
    userLocation: LatLng?,
    cameraPositionState: CameraPositionState,
    locationPermissionState: com.google.accompanist.permissions.PermissionState,
    selectedPharmacy: Pharmacy?,
    onPharmacySelect: (Pharmacy?) -> Unit,
    bottomHeight: androidx.compose.ui.unit.Dp,
    mapLoaded: Boolean,
    onMapLoaded: () -> Unit,
    sheetState: MapSheetState,
    onSheetStateChange: (MapSheetState) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val isDark = MaterialTheme.colorScheme.background == Color(0xFF0B0F0C)
    val TextPrimary = if (isDark) MaterialTheme.colorScheme.onBackground else com.pralayakaveri.medisave.ui.theme.TextPrimary
    val TextSecondary = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else com.pralayakaveri.medisave.ui.theme.TextSecondary
    val PrimaryGreen = if (isDark) MaterialTheme.colorScheme.primary else com.pralayakaveri.medisave.ui.theme.PrimaryGreen
    val PrimaryGreenDark = if (isDark) MaterialTheme.colorScheme.primaryContainer else com.pralayakaveri.medisave.ui.theme.PrimaryGreenDark
    val surfaceColor = if (isDark) MaterialTheme.colorScheme.surface else Color.White
    val DividerGray = if (isDark) MaterialTheme.colorScheme.outlineVariant else com.pralayakaveri.medisave.ui.theme.DividerGray

    val mapProperties = remember(isDark) {
        MapProperties(
            mapStyleOptions = if (isDark) {
                try {
                    com.google.android.gms.maps.model.MapStyleOptions.loadRawResourceStyle(context, com.pralayakaveri.medisave.R.raw.map_dark)
                } catch (e: Exception) {
                    android.util.Log.e("MapScreen", "Failed to load dark map style resource", e)
                    null
                }
            } else null
        )
    }

    LaunchedEffect(cameraPositionState.isMoving) {
        if (selectedPharmacy == null &&
            cameraPositionState.isMoving &&
            cameraPositionState.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE
        ) {
            onSheetStateChange(MapSheetState.COLLAPSED)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!locationPermissionState.status.isGranted) {
            LocationPermissionLauncher(onRequestPermission = { locationPermissionState.launchPermissionRequest() })
        } else {
            // Full Screen Map
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = mapProperties,
                uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = true),
                onMapLoaded = onMapLoaded,
                onMapClick = { onPharmacySelect(null) }
            ) {
                userLocation?.let {
                    Marker(state = MarkerState(position = it), title = "Your Location")
                }

                if (uiState is MapUiState.Success) {
                    uiState.pharmacies.forEach { pharmacy ->
                        val isSelected = selectedPharmacy?.id == pharmacy.id
                        Marker(
                            state = MarkerState(position = pharmacy.location),
                            title = pharmacy.name,
                            snippet = pharmacy.distance,
                            icon = if (isSelected)
                                BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                            else
                                BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED),
                            onClick = {
                                onPharmacySelect(pharmacy)
                                if (mapLoaded) {
                                    coroutineScope.launch {
                                        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(pharmacy.location, 15f), 800)
                                    }
                                }
                                true
                            }
                        )
                    }
                }
            }

            // Header Overlay
            Box(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
                val currentQuery by viewModel.searchQuery.collectAsState()
                MapHeader(
                    searchQuery = currentQuery,
                    onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                    onSearchTriggered = { query ->
                        if (query.isNotBlank()) {
                            userLocation?.let { loc ->
                                viewModel.searchPharmaciesByText(query, loc, context, fallbackToNearby = true)
                            }
                        }
                    }
                )
            }

            // Loading/Error Overlays
            when (uiState) {
                is MapUiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { 
                    CircularProgressIndicator(color = PrimaryGreen) 
                }
                is MapUiState.Error -> ErrorOverlay(
                    message = uiState.message,
                    onRetry = { userLocation?.let { viewModel.fetchNearbyPharmacies(it, context) } }
                )
                else -> {}
            }

            // Bottom Content Overlay
            Surface(
                modifier = if (selectedPharmacy != null) {
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .wrapContentHeight()
                        .heightIn(min = 220.dp)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .height(bottomHeight)
                },
                shadowElevation = 24.dp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = surfaceColor
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column {
                        AnimatedVisibility(visible = selectedPharmacy == null, enter = fadeIn(), exit = fadeOut()) {
                            Column(modifier = Modifier.fillMaxSize().padding(top = 16.dp)) {
                                var dragOffset by remember { mutableStateOf(0f) }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .pointerInput(Unit) {
                                            detectVerticalDragGestures(
                                                onDragStart = { dragOffset = 0f },
                                                onDragEnd = {
                                                    if (dragOffset < -50f) {
                                                        val nextState = when (sheetState) {
                                                            MapSheetState.COLLAPSED -> MapSheetState.PEEK
                                                            MapSheetState.PEEK -> MapSheetState.EXPANDED
                                                            MapSheetState.EXPANDED -> MapSheetState.EXPANDED
                                                        }
                                                        onSheetStateChange(nextState)
                                                    } else if (dragOffset > 50f) {
                                                        val nextState = when (sheetState) {
                                                            MapSheetState.EXPANDED -> MapSheetState.PEEK
                                                            MapSheetState.PEEK -> MapSheetState.COLLAPSED
                                                            MapSheetState.COLLAPSED -> MapSheetState.COLLAPSED
                                                        }
                                                        onSheetStateChange(nextState)
                                                    }
                                                },
                                                onVerticalDrag = { _, dragAmount ->
                                                    dragOffset += dragAmount
                                                }
                                            )
                                        }
                                        .clickable {
                                            val nextState = when (sheetState) {
                                                MapSheetState.COLLAPSED -> MapSheetState.PEEK
                                                MapSheetState.PEEK -> MapSheetState.EXPANDED
                                                MapSheetState.EXPANDED -> MapSheetState.COLLAPSED
                                            }
                                            onSheetStateChange(nextState)
                                        }
                                        .padding(bottom = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(40.dp)
                                            .height(4.dp)
                                            .clip(CircleShape)
                                            .background(DividerGray)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val countText = if (uiState is MapUiState.Success) " (${uiState.pharmacies.size})" else ""
                                    Text(
                                        text = "Nearby Pharmacies$countText",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                if (sheetState != MapSheetState.COLLAPSED) {
                                    if (uiState is MapUiState.Success) {
                                        if (uiState.isFallback) {
                                            Text(
                                                text = "No direct matches found. Showing nearby pharmacies.",
                                                fontSize = 12.sp,
                                                color = TextSecondary,
                                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                                            )
                                        }
                                        val list = uiState.pharmacies
                                        if (list.isEmpty()) {
                                            EmptyPharmaciesView()
                                        } else {
                                            LazyColumn(modifier = Modifier.weight(1f)) {
                                                items(list) { pharmacy ->
                                                    PharmacyListItem(
                                                        pharmacy = pharmacy,
                                                        onClick = {
                                                            onPharmacySelect(pharmacy)
                                                            if (mapLoaded) {
                                                                coroutineScope.launch {
                                                                    cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(pharmacy.location, 15f), 800)
                                                                }
                                                            }
                                                        }
                                                    )
                                                    Divider(color = DividerGray, modifier = Modifier.padding(horizontal = 24.dp))
                                                }
                                            }
                                        }
                                    } else if (uiState is MapUiState.Loading) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(color = PrimaryGreen)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Column {
                        AnimatedVisibility(visible = selectedPharmacy != null, enter = fadeIn(), exit = fadeOut()) {
                            selectedPharmacy?.let { pharmacy ->
                                SelectedPharmacyCard(pharmacy = pharmacy, onClose = { onPharmacySelect(null) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MapModeToggle(currentMode: MapMode, onModeChange: (MapMode) -> Unit) {
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF0B0F0C)
    val TextSecondary = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else com.pralayakaveri.medisave.ui.theme.TextSecondary
    val PrimaryGreen = if (isDark) MaterialTheme.colorScheme.primary else com.pralayakaveri.medisave.ui.theme.PrimaryGreen
    val surfaceColor = if (isDark) MaterialTheme.colorScheme.surface else Color.White
    val toggleBgColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFF1F1F1)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = surfaceColor,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(toggleBgColor)
                .padding(4.dp)
        ) {
            MapModeButton(
                text = "Pharmacies",
                isSelected = currentMode == MapMode.PHARMACIES,
                onClick = { onModeChange(MapMode.PHARMACIES) },
                modifier = Modifier.weight(1f)
            )
            MapModeButton(
                text = "Generics",
                isSelected = currentMode == MapMode.GENERICS,
                onClick = { onModeChange(MapMode.GENERICS) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun MapModeButton(text: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF0B0F0C)
    val TextSecondary = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else com.pralayakaveri.medisave.ui.theme.TextSecondary
    val PrimaryGreen = if (isDark) MaterialTheme.colorScheme.primary else com.pralayakaveri.medisave.ui.theme.PrimaryGreen
    val buttonBgColor = if (isDark) MaterialTheme.colorScheme.surface else Color.White

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) buttonBgColor else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) PrimaryGreen else TextSecondary
        )
    }
}
@Composable
fun MapHeader(
    searchQuery: String = "", 
    onSearchQueryChange: (String) -> Unit = {},
    onSearchTriggered: (String) -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF0B0F0C)
    val TextPrimary = if (isDark) MaterialTheme.colorScheme.onBackground else com.pralayakaveri.medisave.ui.theme.TextPrimary
    val TextSecondary = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else com.pralayakaveri.medisave.ui.theme.TextSecondary
    val PrimaryGreen = if (isDark) MaterialTheme.colorScheme.primary else com.pralayakaveri.medisave.ui.theme.PrimaryGreen
    val DividerGray = if (isDark) MaterialTheme.colorScheme.outlineVariant else com.pralayakaveri.medisave.ui.theme.DividerGray
    val surfaceColor = if (isDark) MaterialTheme.colorScheme.surface else Color.White
    val containerColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFF9F9F9)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = surfaceColor,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
    ) {
        Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 12.dp)) {
            Text("Nearby pharmacies", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                placeholder = { Text("Search location or pharmacy") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryGreen,
                    unfocusedBorderColor = DividerGray,
                    focusedContainerColor = containerColor,
                    unfocusedContainerColor = containerColor
                ),
                maxLines = 1,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus()
                        onSearchTriggered(searchQuery)
                    },
                    onDone = {
                        focusManager.clearFocus()
                        onSearchTriggered(searchQuery)
                    }
                )
            )
        }
    }
}

@Composable
fun PharmacyListItem(pharmacy: Pharmacy, onClick: () -> Unit) {
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF0B0F0C)
    val textPrimary = if (isDark) MaterialTheme.colorScheme.onBackground else com.pralayakaveri.medisave.ui.theme.TextPrimary
    val textSecondary = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else com.pralayakaveri.medisave.ui.theme.TextSecondary
    val primaryGreen = if (isDark) MaterialTheme.colorScheme.primary else com.pralayakaveri.medisave.ui.theme.PrimaryGreen
    val primaryGreenDark = if (isDark) MaterialTheme.colorScheme.primary else com.pralayakaveri.medisave.ui.theme.PrimaryGreenDark
    val openBg = if (isDark) primaryGreen.copy(alpha = 0.15f) else Color(0xFFE8F5E9)
    val closedBg = if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFF5F5F5)

    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape).background(if (pharmacy.isOpen) openBg else closedBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = null, tint = if (pharmacy.isOpen) primaryGreen else textSecondary)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(pharmacy.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textPrimary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(pharmacy.address, fontSize = 12.sp, color = textSecondary, maxLines = 1)
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(pharmacy.distance, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = primaryGreenDark)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (pharmacy.isOpen) "• Open" else "• Closed", fontSize = 12.sp, color = if (pharmacy.isOpen) primaryGreen else Color.Red)
                pharmacy.rating?.let {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("• ⭐ $it", fontSize = 12.sp, color = Color(0xFFFFB300))
                }
            }
        }
    }
}

@Composable
fun SelectedPharmacyCard(pharmacy: Pharmacy, onClose: () -> Unit) {
    val context = LocalContext.current
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF0B0F0C)
    val textPrimary = if (isDark) MaterialTheme.colorScheme.onBackground else com.pralayakaveri.medisave.ui.theme.TextPrimary
    val textSecondary = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else com.pralayakaveri.medisave.ui.theme.TextSecondary
    val primaryGreen = if (isDark) MaterialTheme.colorScheme.primary else com.pralayakaveri.medisave.ui.theme.PrimaryGreen
    val primaryGreenDark = if (isDark) MaterialTheme.colorScheme.primary else com.pralayakaveri.medisave.ui.theme.PrimaryGreenDark
    val openBg = if (isDark) primaryGreen.copy(alpha = 0.15f) else Color(0xFFE8F5E9)
    val closedBg = if (isDark) Color(0xFFDC2626).copy(alpha = 0.15f) else Color(0xFFFFEBEE)

    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = pharmacy.name, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = textPrimary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = pharmacy.address, fontSize = 14.sp, color = textSecondary)
            }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close", tint = textSecondary) }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (pharmacy.isOpen) openBg else closedBg).padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(text = if (pharmacy.isOpen) "OPEN NOW" else "CLOSED", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (pharmacy.isOpen) primaryGreenDark else Color.Red)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = pharmacy.distance, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = primaryGreen)
        }
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = {
                val intentUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${pharmacy.location.latitude},${pharmacy.location.longitude}&travelmode=driving")
                val mapIntent = Intent(Intent.ACTION_VIEW, intentUri).apply {
                    setPackage("com.google.android.apps.maps")
                }
                try {
                    context.startActivity(mapIntent)
                } catch (e: android.content.ActivityNotFoundException) {
                    val fallbackIntent = Intent(Intent.ACTION_VIEW, intentUri)
                    context.startActivity(fallbackIntent)
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = primaryGreen)
        ) {
            Text("Get Directions", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun LocationPermissionLauncher(onRequestPermission: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(64.dp), tint = PrimaryGreen)
        Spacer(modifier = Modifier.height(24.dp))
        Text("Location Access Required", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        Text("We need your location to find nearby pharmacies for you.", textAlign = TextAlign.Center, color = TextSecondary)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onRequestPermission, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)) {
            Text("Grant Permission", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ErrorOverlay(message: String, onRetry: () -> Unit) {
    Surface(modifier = Modifier.padding(16.dp), color = Color(0xFFFFEBEE), shape = RoundedCornerShape(12.dp), shadowElevation = 4.dp) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = message, modifier = Modifier.weight(1f), color = Color.Red, fontSize = 14.sp)
            IconButton(onClick = onRetry) { Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = Color.Red) }
        }
    }
}

@Composable
fun EmptyPharmaciesView() {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("No pharmacies found", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
        Text("Try expanding your search or checking another area.", fontSize = 12.sp, color = TextSecondary)
    }
}
