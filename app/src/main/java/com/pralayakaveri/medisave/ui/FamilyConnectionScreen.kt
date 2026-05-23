package com.pralayakaveri.medisave.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pralayakaveri.medisave.data.AppDatabase
import com.pralayakaveri.medisave.data.FamilyConnectionRepository
import com.pralayakaveri.medisave.data.UserEntity
import com.pralayakaveri.medisave.model.Connection
import com.pralayakaveri.medisave.ui.theme.BrandingGreen
import com.pralayakaveri.medisave.ui.theme.PrimaryGreen
import com.pralayakaveri.medisave.ui.theme.TextPrimary
import com.pralayakaveri.medisave.ui.theme.TextSecondary
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.IOException

// Color Overrides for exact match
val StatusGrayBg = Color(0xFFF5F5F5)
val OrangeConsentBg = Color(0xFFFFF7E6)
val OrangeIconColor = Color(0xFFFAAD14)
val ErrorRedBorder = Color(0xFFFF4D4F)
val ErrorRedBg = Color(0xFFFFF1F0)
val FilledGreenBg = Color(0xFFE6F7ED)

sealed class FamilyConnectionUiState {
    object Idle : FamilyConnectionUiState()
    data class Searching(val code: String) : FamilyConnectionUiState()
    data class Found(val user: UserEntity) : FamilyConnectionUiState()
    data class NotFound(val code: String) : FamilyConnectionUiState()
}

sealed class SendEvent {
    object Success : SendEvent()
    data class Error(val message: String) : SendEvent()
}

@OptIn(FlowPreview::class)
class ConnectionSearchViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = FamilyConnectionRepository()
    private val authUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser

    private val _inputCode = MutableStateFlow("")
    val inputCode: StateFlow<String> = _inputCode.asStateFlow()

    private val _uiState = MutableStateFlow<FamilyConnectionUiState>(FamilyConnectionUiState.Idle)
    val uiState: StateFlow<FamilyConnectionUiState> = _uiState.asStateFlow()

    private val _selectedRelation = MutableStateFlow<String?>(null)
    val selectedRelation: StateFlow<String?> = _selectedRelation.asStateFlow()

    private val _customRelationInput = MutableStateFlow("")
    val customRelationInput: StateFlow<String> = _customRelationInput.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _sendEvent = MutableSharedFlow<SendEvent>(replay = 0, extraBufferCapacity = 1)
    val sendEvent: SharedFlow<SendEvent> = _sendEvent.asSharedFlow()

    private val _myCode = MutableStateFlow("")
    val myCode: StateFlow<String> = _myCode.asStateFlow()

    private var sendJob: Job? = null
    private var lastClickAt = 0L

    init {
        val uid = authUser?.uid
        if (uid != null) {
            viewModelScope.launch {
                val db = AppDatabase.getDatabase(application)
                db.userDao().getUserFlow(uid).collect { user ->
                    _myCode.value = user?.connectionCode ?: ""
                }
            }
        }

        // Search logic with 300ms debounce
        viewModelScope.launch {
            _inputCode
                .debounce(300)
                .collect { code ->
                    if (code.length == 6) {
                        performSearch(code)
                    } else if (code.isEmpty()) {
                        _uiState.value = FamilyConnectionUiState.Idle
                    } else {
                        _uiState.value = FamilyConnectionUiState.Searching(code)
                    }
                }
        }
    }

    fun onCodeChange(newCode: String) {
        if (newCode.length <= 6) {
            val upperCode = newCode.uppercase()
            _inputCode.value = upperCode
            // Reset state if less than 6
            if (upperCode.length < 6) {
                _uiState.value = if (upperCode.isEmpty()) FamilyConnectionUiState.Idle else FamilyConnectionUiState.Searching(upperCode)
                _selectedRelation.value = null
            }
        }
    }

    fun updateRelation(relation: String) {
        _selectedRelation.value = relation
        if (relation != "Custom...") {
            _customRelationInput.value = ""
        }
    }

    fun onCustomRelationChange(input: String) {
        if (input.length <= 30) {
            _customRelationInput.value = input
        }
    }

    private suspend fun performSearch(code: String) {
        _uiState.value = FamilyConnectionUiState.Searching(code)
        
        try {
            // Prevent searching for yourself
            if (code == _myCode.value) {
                _uiState.value = FamilyConnectionUiState.NotFound(code)
                return
            }

            val user = repo.findUserByCode(code)
            if (user != null) {
                _uiState.value = FamilyConnectionUiState.Found(user)
            } else {
                _uiState.value = FamilyConnectionUiState.NotFound(code)
            }
        } catch (e: Exception) {
            _uiState.value = FamilyConnectionUiState.NotFound(code)
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun sendRequest() {
        val now = System.currentTimeMillis()
        if (now - lastClickAt < 500) return
        lastClickAt = now

        if (_isSending.value) return

        val currentState = _uiState.value
        val baseRelation = _selectedRelation.value
        val customRelation = _customRelationInput.value
        
        val finalRelation = if (baseRelation == "Custom...") customRelation.trim() else baseRelation

        // Validation
        if (currentState !is FamilyConnectionUiState.Found) {
            _errorMessage.value = "Please find a user first"
            return
        }
        if (finalRelation.isNullOrBlank()) {
            _errorMessage.value = "Please select or enter a relation"
            return
        }

        sendJob?.cancel()
        sendJob = viewModelScope.launch(Dispatchers.Main) {
            withContext(Dispatchers.Main) {
                _isSending.value = true
                _errorMessage.value = null
            }

            try {
                val result = withTimeout(10000) {
                    repo.sendRequest(currentState.user.userId, finalRelation!!)
                }

                if (!isActive) return@launch

                if (result.isSuccess) {
                    withContext(Dispatchers.Main) {
                        _errorMessage.value = null
                        _sendEvent.tryEmit(SendEvent.Success)
                    }
                } else {
                    val msg = result.exceptionOrNull()?.let { mapError(it) } ?: "Failed to send request"
                    withContext(Dispatchers.Main) {
                        _errorMessage.value = msg
                        _sendEvent.tryEmit(SendEvent.Error(msg))
                    }
                }
            } catch (e: TimeoutCancellationException) {
                if (!isActive) return@launch
                val msg = "Request timed out. Please try again."
                withContext(Dispatchers.Main) {
                    _errorMessage.value = msg
                    _sendEvent.tryEmit(SendEvent.Error(msg))
                }
            } catch (e: Exception) {
                if (!isActive) return@launch
                val msg = mapError(e)
                withContext(Dispatchers.Main) {
                    _errorMessage.value = msg
                    _sendEvent.tryEmit(SendEvent.Error(msg))
                }
            } finally {
                if (isActive) {
                    withContext(Dispatchers.Main) {
                        _isSending.value = false
                    }
                }
            }
        }
    }

    private fun mapError(e: Throwable): String {
        return when (e) {
            is TimeoutCancellationException -> "Request timed out. Please try again."
            is IOException -> "No internet connection"
            is com.google.firebase.firestore.FirebaseFirestoreException -> {
                when (e.code) {
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED -> 
                        "Access denied. You do not have permission to send this connection request."
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE,
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> 
                        "Network connection is unavailable. Please check your settings."
                    else -> "A database error occurred. Please try again later."
                }
            }
            else -> "An unexpected connection error occurred. Please try again later."
        }
    }

    fun reset() {
        _inputCode.value = ""
        _uiState.value = FamilyConnectionUiState.Idle
        _selectedRelation.value = null
        _errorMessage.value = null
    }

    override fun onCleared() {
        sendJob?.cancel()
        super.onCleared()
    }
}

fun shareViaWhatsApp(context: Context, code: String) {
    val message = "Connect with me on MediSave! My connection code is: $code"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message)
    }
    
    // Explicitly target WhatsApp if available
    intent.setPackage("com.whatsapp")
    
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback: Generic chooser
        val chooser = Intent.createChooser(intent, "Share via")
        context.startActivity(chooser)
    }
}

@Composable
fun FamilyConnectionScreen(
    onBack: () -> Unit,
    viewModel: ConnectionSearchViewModel = viewModel()
) {
    val context = LocalContext.current
    val inputCode by viewModel.inputCode.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val selectedRelation by viewModel.selectedRelation.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val myCode by viewModel.myCode.collectAsState()
    
    val snackbarHostState = remember { SnackbarHostState() }
    val hasNavigated = rememberSaveable { mutableStateOf(false) }
    
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current

    // Clear error on input change
    LaunchedEffect(inputCode, selectedRelation) {
        viewModel.clearError()
    }

    // Lifecycle-aware event collection
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(viewModel.sendEvent, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.sendEvent.collectLatest { event ->
                when (event) {
                    is SendEvent.Success -> {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar("Connection request sent successfully")
                        delay(400)
                        if (!hasNavigated.value) {
                            hasNavigated.value = true
                            onBack()
                        }
                    }
                    is SendEvent.Error -> {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(event.message)
                    }
                }
            }
        }
    }

    // Initial focus on screen load with safe delay
    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
    }

    // Restore focus on state resets/errors (Idle / NotFound)
    LaunchedEffect(uiState) {
        if (uiState is FamilyConnectionUiState.Idle || uiState is FamilyConnectionUiState.NotFound) {
            delay(100)
            if (!listState.isScrollInProgress) {
                focusRequester.requestFocus()
            }
        }
    }

    // Disable back during sending
    BackHandler(enabled = isSending) {
        // No-op to lock UI
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(BrandingGreen)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Enter connection code",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .imePadding(),
            contentPadding = PaddingValues(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text(
                    text = "Ask your family member for their 6-letter code",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "They can find it in Profile → Family connection",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
            }

            // OTP Input Item (Validated Layering Fix)
            item {
                CodeInputSection(
                    code = inputCode,
                    onCodeChange = { viewModel.onCodeChange(it) },
                    focusRequester = focusRequester,
                    listState = listState,
                    keyboardController = keyboardController
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // State Based Card with smooth transitions
            item {
                Box(modifier = Modifier.animateContentSize()) {
                    AnimatedContent(
                        targetState = uiState,
                        transitionSpec = { 
                            (fadeIn(animationSpec = tween(300)) togetherWith 
                             fadeOut(animationSpec = tween(300))).using(SizeTransform(clip = false))
                        },
                        label = "UIStateAnimation"
                    ) { state ->
                        when (state) {
                            is FamilyConnectionUiState.Idle -> Spacer(modifier = Modifier.height(80.dp))
                            is FamilyConnectionUiState.Searching -> SearchingCard(state.code)
                            is FamilyConnectionUiState.Found -> FoundUserCard(state.user)
                            is FamilyConnectionUiState.NotFound -> NotFoundCard(
                                code = state.code,
                                onTryAgain = { viewModel.reset() },
                                onWhatsApp = { shareViaWhatsApp(context, state.code) }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Relation Selector
            if (uiState is FamilyConnectionUiState.Found) {
                item {
                    val customRelation by viewModel.customRelationInput.collectAsState()
                    RelationSelector(
                        selectedRelation = selectedRelation,
                        customRelation = customRelation,
                        onRelationSelect = { viewModel.updateRelation(it) },
                        onCustomRelationChange = { viewModel.onCustomRelationChange(it) }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
                
                item {
                    ConsentCard()
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            // Action Button
            item {
                val isCodeValid = inputCode.length == 6
                val isEnabled = !isSending && uiState is FamilyConnectionUiState.Found && selectedRelation != null && isCodeValid
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = { viewModel.sendRequest() },
                        enabled = isEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryGreen,
                            disabledContainerColor = Color(0xFFA5D6A7).copy(alpha = 0.5f)
                        )
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Send connection request",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                    
                    // Inline Error Display
                    AnimatedVisibility(
                        visible = errorMessage != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        errorMessage?.let {
                            Text(
                                text = it,
                                color = Color.Red,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(top = 8.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(text = "Don't have a code? ", fontSize = 14.sp, color = TextSecondary)
                    Text(
                        text = "Ask them to share it via WhatsApp",
                        fontSize = 14.sp,
                        color = PrimaryGreen,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(enabled = !isSending) { 
                            if (myCode.isNotEmpty()) {
                                shareViaWhatsApp(context, myCode)
                            }
                        }
                    )
                }
            }
        }

        // Premium UX Loading Overlay
        if (isSending) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.1f))
                    .semantics { contentDescription = "Sending connection request" }
                    .clickable(enabled = false) { }, // Intercept clicks
                contentAlignment = Alignment.Center
            ) {
                // Background intentionally left empty to let button spinner show through OR add secondary spinner
            }
        }
    }
}

@Composable
fun CodeInputSection(
    code: String,
    onCodeChange: (String) -> Unit,
    focusRequester: FocusRequester,
    listState: LazyListState,
    keyboardController: androidx.compose.ui.platform.SoftwareKeyboardController?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        contentAlignment = Alignment.Center
    ) {
        // UI (Below)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.wrapContentSize()
        ) {
            val cursorIndex = code.length
            (0 until 6).forEach { index ->
                val char = code.getOrNull(index)
                val isActive = cursorIndex == index
                val isFilled = char != null

                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isFilled) FilledGreenBg else Color.White,
                    border = BorderStroke(
                        width = 2.dp,
                        color = when {
                            isActive -> PrimaryGreen
                            isFilled -> PrimaryGreen.copy(alpha = 0.5f)
                            else -> Color.Gray.copy(alpha = 0.3f)
                        }
                    )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = char?.toString() ?: if (isActive) "_" else "",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isFilled) PrimaryGreen else Color.LightGray
                        )
                    }
                }
            }
        }

        // INPUT (TOP LAYER)
        BasicTextField(
            value = TextFieldValue(
                text = code,
                selection = TextRange(code.length)
            ),
            onValueChange = {
                onCodeChange(it.text)
            },
            modifier = Modifier
                .matchParentSize()
                .focusRequester(focusRequester)
                .focusable()
                .onFocusChanged {
                    if (it.isFocused) {
                        keyboardController?.show()
                    }
                },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                keyboardType = KeyboardType.Ascii
            ),
            singleLine = true,
            textStyle = TextStyle(color = Color.Transparent),
            cursorBrush = SolidColor(Color.Transparent)
        )
    }
}

@Composable
fun SearchingCard(code: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = StatusGrayBg
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.WatchLater,
                    contentDescription = null,
                    tint = PrimaryGreen,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "Finding: $code",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Waiting for last character...",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
fun FoundUserCard(user: UserEntity) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(2.dp, PrimaryGreen)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = PrimaryGreen.copy(alpha = 0.1f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = user.name.firstOrNull()?.toString()?.uppercase() ?: "?",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryGreen
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = user.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(text = "MediSave user · Code: ${user.connectionCode}", fontSize = 12.sp, color = TextSecondary)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = Color.LightGray.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(16.dp))
            
            PermissionItem(isAllowed = true, text = "Medicine schedule access")
            PermissionItem(isAllowed = true, text = "Weekly adherence")
            PermissionItem(isAllowed = false, text = "No personal health data")
        }
    }
}

@Composable
fun PermissionItem(isAllowed: Boolean, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isAllowed) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            tint = if (isAllowed) PrimaryGreen else Color.Red.copy(alpha = 0.6f),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, fontSize = 13.sp, color = TextSecondary)
    }
}

@Composable
fun NotFoundCard(code: String, onTryAgain: () -> Unit, onWhatsApp: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = ErrorRedBg,
        border = BorderStroke(1.dp, ErrorRedBorder.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Error, contentDescription = null, tint = ErrorRedBorder)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Code not found", fontWeight = FontWeight.Bold, color = ErrorRedBorder)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "Possible reasons:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(text = "• Typing error in code\n• Code has expired or refreshed\n• User is not on MediSave", fontSize = 13.sp, color = TextSecondary, lineHeight = 20.sp)
            
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onTryAgain,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color.LightGray)
                ) {
                    Text("Try again", color = TextPrimary)
                }
                Button(
                    onClick = onWhatsApp,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                ) {
                    Text("WhatsApp", color = Color.White)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RelationSelector(
    selectedRelation: String?,
    customRelation: String,
    onRelationSelect: (String) -> Unit,
    onCustomRelationChange: (String) -> Unit
) {
    val relations = listOf("Mom", "Dad", "Spouse", "Child", "Sibling", "Caregiver", "Custom...")
    
    Column {
        Text(
            text = "RELATION",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            relations.forEach { relation ->
                val isSelected = selectedRelation == relation
                Surface(
                    modifier = Modifier.clickable { onRelationSelect(relation) },
                    shape = RoundedCornerShape(50),
                    color = if (isSelected) BrandingGreen else Color.White,
                    border = if (isSelected) null else BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.6f))
                ) {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            text = relation,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else TextSecondary
                        )
                    }
                }
            }
        }

        if (selectedRelation == "Custom...") {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = customRelation,
                onValueChange = { if (it.length <= 30) onCustomRelationChange(it) },
                label = { Text("How are they related to you?") },
                placeholder = { Text("e.g. Grandma, Uncle, Nana") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandingGreen,
                    focusedLabelColor = BrandingGreen
                )
            )
        }
    }
}

@Composable
fun ConsentCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = OrangeConsentBg
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = OrangeIconColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "They will receive a request and must accept before you can see their data.",
                fontSize = 13.sp,
                color = Color(0xFF855D10),
                lineHeight = 18.sp
            )
        }
    }
}
