package com.pralayakaveri.medisave.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pralayakaveri.medisave.ui.theme.*
import com.pralayakaveri.medisave.viewmodel.AuthViewModel
import com.pralayakaveri.medisave.viewmodel.RegisterState
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.input.VisualTransformation

@Composable
fun LoginScreen(
    onNavigateToRegister: () -> Unit,
    onLoginSuccess: () -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    val email by viewModel.loginEmail.collectAsState()
    val password by viewModel.loginPassword.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.authError.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()

    var passwordVisible by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val googleSignInClient = remember {
        val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("444031037154-6fvdce30pe18nf57eil9hqbqaj0el1k6.apps.googleusercontent.com")
            .requestEmail()
            .build()
        com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            account.idToken?.let { viewModel.handleGoogleSignIn(it) }
        } catch (e: Exception) {
            // Handle error
        }
    }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) onLoginSuccess()
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(60.dp))
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = "Logo", tint = Color.White, modifier = Modifier.size(40.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("MediSave", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Text("Never miss a dose. Save on medicines.", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
            
            Spacer(modifier = Modifier.height(40.dp))
            
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Welcome back", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Sign in to your account", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    OutlinedTextField(
                        value = email,
                        onValueChange = { viewModel.updateLoginEmail(it) },
                        label = { Text("EMAIL OR PHONE") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = password,
                        onValueChange = { viewModel.updateLoginPassword(it) },
                        label = { Text("PASSWORD") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingIcon = {
                            val icon = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    
                    Text(
                        "Forgot password?",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.End).padding(top = 8.dp).clickable { }
                    )
                    
                    if (error != null) {
                        Text(error!!, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Button(
                        onClick = { viewModel.login() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        enabled = !isLoading,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                        } else {
                            Text("Sign in", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = DividerGray)
                        Text(" or continue with ", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 8.dp))
                        HorizontalDivider(modifier = Modifier.weight(1f), color = DividerGray)
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    OutlinedButton(
                        onClick = { launcher.launch(googleSignInClient.signInIntent) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, DividerGray)
                    ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AccountCircle, contentDescription = null, tint = PrimaryGreen, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Continue with Google", color = TextPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FeatureChip("Offline first")
                        FeatureChip("Data encrypted")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FeatureChip("No ads")
                    
                    Spacer(modifier = Modifier.height(40.dp))
                    
                    Row {
                        Text("Don't have an account? ", color = TextSecondary, fontSize = 14.sp)
                        Text(
                            "Create one",
                            color = PrimaryGreen,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onNavigateToRegister() }
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun FeatureChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(TakenGreenBg)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(PrimaryGreen))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text, color = PrimaryGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onBackToLogin: () -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    var step by remember { mutableStateOf(1) }
    val state by viewModel.registerState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.authError.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) onRegisterSuccess()
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(40.dp))
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = "Logo", tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text("MediSave", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text("Create your account", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)

            Spacer(modifier = Modifier.height(24.dp))
            
            // Progress Bar
            val progress = if (step == 1) 0.5f else 1.0f
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Step $step of 2 — ${if(step == 1) "Personal details" else "Health profile"}", color = Color.White, fontSize = 11.sp)
                    Text("${(progress * 100).toInt()}%", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.2f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                if (step == 1) {
                    RegisterStep1(
                        onContinue = { name, phone, email, pass ->
                            viewModel.updateRegisterBasic(name, phone, email, pass)
                            step = 2
                        },
                        onBack = onBackToLogin,
                        initialData = state
                    )
                } else {
                    RegisterStep2(
                        onFinish = { age, gender, conditions, language ->
                            viewModel.updateRegisterHealth(age, gender, conditions, language)
                            viewModel.register()
                        },
                        onBack = { step = 1 },
                        isLoading = isLoading,
                        error = error,
                        initialData = state
                    )
                }
            }
        }
    }
}

@Composable
fun RegisterStep1(
    onContinue: (String, String, String, String) -> Unit,
    onBack: () -> Unit,
    initialData: RegisterState
) {
    var name by remember { mutableStateOf<String>(initialData.name) }
    var phone by remember { mutableStateOf<String>(initialData.phone) }
    var email by remember { mutableStateOf<String>(initialData.email) }
    var password by remember { mutableStateOf<String>(initialData.password) }
    var passwordVisible by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())
    ) {
        Text("Your details", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text("We keep your health data private and secure", fontSize = 14.sp, color = TextSecondary)
        
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("FULL NAME") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = PrimaryGreen) },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryGreen, focusedLabelColor = PrimaryGreen)
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = phone,
            onValueChange = { if(it.length <= 10) phone = it },
            label = { Text("PHONE NUMBER") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            prefix = { Text("+91 ") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryGreen, focusedLabelColor = PrimaryGreen)
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("EMAIL") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = PrimaryGreen) },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryGreen, focusedLabelColor = PrimaryGreen)
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("CREATE PASSWORD") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = PrimaryGreen) },
            trailingIcon = {
                val icon = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                IconButton(onClick = { passwordVisible = !passwordVisible }) { Icon(icon, contentDescription = null) }
            },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryGreen, focusedLabelColor = PrimaryGreen)
        )
        
        if (password.isNotEmpty() && password.length < 8) {
            Text("Weak — min 8 characters", color = Color.Red, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.Top) {
            Checkbox(
                checked = checked,
                onCheckedChange = { checked = it },
                colors = CheckboxDefaults.colors(checkedColor = PrimaryGreen)
            )
            Text(
                "I agree to the Terms of Service and Privacy Policy. My health data will never be sold.",
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { onContinue(name, phone, email, password) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = name.isNotBlank() && email.isNotBlank() && password.length >= 8 && checked,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
        ) {
            Text("Continue →", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            "Already have an account? Sign in",
            modifier = Modifier.fillMaxWidth().clickable { onBack() },
            textAlign = TextAlign.Center,
            fontSize = 14.sp,
            color = TextSecondary
        )
    }
}

@Composable
fun RegisterStep2(
    onFinish: (String, String, List<String>, String) -> Unit,
    onBack: () -> Unit,
    isLoading: Boolean,
    error: String?,
    initialData: RegisterState
) {
    var age by remember { mutableStateOf<String>(initialData.age) }
    var gender by remember { mutableStateOf<String>(initialData.gender) }
    
    val defaultConditions = listOf("Type 2 Diabetes", "Hypertension", "Asthma", "Heart", "Thyroid")
    val allConditions = remember { mutableStateListOf<String>().apply { addAll(defaultConditions) } }
    val selectedConditions = remember { mutableStateListOf<String>().apply { addAll(initialData.conditions) } }
    
    var language by remember { mutableStateOf<String>(initialData.language) }
    var showAddDialog by remember { mutableStateOf(false) }
    var customConditionInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())
    ) {
        Text("Health profile", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text("Helps personalise your reminders (optional)", fontSize = 14.sp, color = TextSecondary)
        
        Spacer(modifier = Modifier.height(32.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(
                value = age,
                onValueChange = { if(it.all { char -> char.isDigit() }) age = it },
                label = { Text("AGE") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryGreen, focusedLabelColor = PrimaryGreen)
            )
            
            var expandedGen by remember { mutableStateOf(false) }
            @OptIn(ExperimentalMaterial3Api::class)
            ExposedDropdownMenuBox(
                expanded = expandedGen,
                onExpandedChange = { expandedGen = !expandedGen },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = gender,
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("GENDER") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedGen) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                        focusedBorderColor = PrimaryGreen,
                        focusedLabelColor = PrimaryGreen
                    ),
                    modifier = Modifier.menuAnchor(),
                    shape = RoundedCornerShape(12.dp)
                )
                ExposedDropdownMenu(
                    expanded = expandedGen,
                    onDismissRequest = { expandedGen = false }
                ) {
                    listOf("Male", "Female", "Other").forEach {
                        DropdownMenuItem(
                            text = { Text(it) },
                            onClick = {
                                gender = it
                                expandedGen = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("HEALTH CONDITIONS (OPTIONAL)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
        Spacer(modifier = Modifier.height(8.dp))
        
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            allConditions.forEach { condition ->
                val isSelected = selectedConditions.contains(condition)
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (isSelected) selectedConditions.remove(condition)
                        else selectedConditions.add(condition)
                    },
                    label = { Text(condition) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TakenGreenBg,
                        selectedLabelColor = PrimaryGreen
                    ),
                    shape = RoundedCornerShape(50)
                )
            }
            // Add Custom Chip button
            AssistChip(
                onClick = { showAddDialog = true },
                label = { Text("+ Add") },
                shape = RoundedCornerShape(50),
                colors = AssistChipDefaults.assistChipColors(labelColor = PrimaryGreen),
                border = BorderStroke(1.dp, PrimaryGreen)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("PREFERRED LANGUAGE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
        Spacer(modifier = Modifier.height(8.dp))
        
        var expandedLang by remember { mutableStateOf(false) }
        @OptIn(ExperimentalMaterial3Api::class)
        ExposedDropdownMenuBox(
            expanded = expandedLang,
            onExpandedChange = { expandedLang = !expandedLang }
        ) {
            OutlinedTextField(
                value = language,
                onValueChange = { },
                readOnly = true,
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                shape = RoundedCornerShape(12.dp),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedLang) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                    focusedBorderColor = PrimaryGreen,
                    focusedLabelColor = PrimaryGreen
                )
            )
            ExposedDropdownMenu(
                expanded = expandedLang,
                onDismissRequest = { expandedLang = false }
            ) {
                listOf("English", "Hindi", "Marathi", "Tamil").forEach {
                    DropdownMenuItem(
                        text = { Text(it) },
                        onClick = {
                            language = it
                            expandedLang = false
                        }
                    )
                }
            }
        }
        
        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Add Condition") },
                text = {
                    OutlinedTextField(
                        value = customConditionInput,
                        onValueChange = { customConditionInput = it },
                        placeholder = { Text("e.g. Arthritis") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (customConditionInput.isNotBlank()) {
                            if (!allConditions.contains(customConditionInput)) {
                                allConditions.add(customConditionInput)
                            }
                            if (!selectedConditions.contains(customConditionInput)) {
                                selectedConditions.add(customConditionInput)
                            }
                            customConditionInput = ""
                            showAddDialog = false
                        }
                    }) {
                        Text("Add", color = PrimaryGreen)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (error != null) {
            Text(error, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(bottom = 16.dp))
        }

        Button(
            onClick = { onFinish(age, gender, selectedConditions, language) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = !isLoading,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text("Create account", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        TextButton(onClick = { onBack() }, modifier = Modifier.fillMaxWidth()) {
            Text("← Back to step 1", color = TextSecondary)
        }
    }
}
