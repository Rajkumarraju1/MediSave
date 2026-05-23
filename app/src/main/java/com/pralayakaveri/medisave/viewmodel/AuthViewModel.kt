package com.pralayakaveri.medisave.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pralayakaveri.medisave.data.*
import com.pralayakaveri.medisave.model.Connection
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class RegisterState(
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val password: String = "",
    val age: String = "",
    val gender: String = "Male",
    val conditions: List<String> = emptyList(),
    val language: String = "English",
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed class AuthState {
    object Loading : AuthState()
    object Authenticated : AuthState()
    object Unauthenticated : AuthState()
}

class AuthViewModel(
    application: Application,
    private val savedStateHandle: androidx.lifecycle.SavedStateHandle
) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val authRepo = AuthRepository(database.userDao())
    private val prefManager = PreferenceManager(application)
    private val familyRepo = FamilyConnectionRepository()

    private val _loginEmail = MutableStateFlow("")
    val loginEmail = _loginEmail.asStateFlow()

    private val _loginPassword = MutableStateFlow("")
    val loginPassword = _loginPassword.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError = _authError.asStateFlow()

    private val _registerState = MutableStateFlow(RegisterState())
    val registerState = _registerState.asStateFlow()

    private val _isLoggedIn = prefManager.isLoggedIn.stateIn(
        viewModelScope, SharingStarted.Eagerly, false
    )
    val isLoggedIn = _isLoggedIn

    val authState: StateFlow<AuthState> = prefManager.isLoggedIn
        .map { if (it) AuthState.Authenticated else AuthState.Unauthenticated }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.Loading)

    private val _activeIncomingRequest = MutableStateFlow<Connection?>(null)
    val activeIncomingRequest = _activeIncomingRequest.asStateFlow()

    private val _allIncomingRequests = MutableStateFlow<List<Connection>?>(null)
    val allIncomingRequests = _allIncomingRequests.asStateFlow()

    private val _acceptedSentRequest = MutableStateFlow<Connection?>(null)
    val acceptedSentRequest = _acceptedSentRequest.asStateFlow()

    // Using SavedStateHandle for process death survival
    val lastHandledRequestId: StateFlow<String?> = savedStateHandle.getStateFlow("last_handled_request_id", null)

    fun markRequestHandled(requestId: String) {
        savedStateHandle["last_handled_request_id"] = requestId
    }

    private var incomingRequestListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var outgoingAcceptedListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var authStateListener: com.google.firebase.auth.FirebaseAuth.AuthStateListener? = null

    init {
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        authStateListener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { firebaseAuth ->
            val userId = firebaseAuth.currentUser?.uid
            incomingRequestListener?.remove()
            incomingRequestListener = null
            outgoingAcceptedListener?.remove()
            outgoingAcceptedListener = null
            if (userId != null) {
                android.util.Log.d("AUTH_STABILIZATION", "FirebaseAuth stabilized with UID: $userId")
                startConnectionListeners(userId)
                syncFcmToken(userId)
                
                // Sync local profile to Firestore to ensure 'phone' is present for caregivers
                viewModelScope.launch {
                    database.userDao().getUserById(userId)?.let { localUser ->
                        authRepo.syncUserToFirestore(localUser)
                    }
                }
            } else {
                android.util.Log.d("AUTH_STABILIZATION", "FirebaseAuth unauthenticated or signed out")
                _activeIncomingRequest.value = null
                _acceptedSentRequest.value = null
            }
        }
        auth.addAuthStateListener(authStateListener!!)
    }

    private fun startConnectionListeners(userId: String) {
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        android.util.Log.d("USER_DEBUG", "Starting listeners for UID: $userId")
        
        // 1. Listen for INCOMING pending requests (Device B logic)
        incomingRequestListener = db.collection("connections")
            .whereEqualTo("receiverId", userId)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                
                if (snapshot == null || snapshot.isEmpty) {
                    _activeIncomingRequest.value = null
                    return@addSnapshotListener
                }

                val requests = snapshot.documents.mapNotNull { doc -> mapToConnection(doc) }
                _allIncomingRequests.value = requests
                _activeIncomingRequest.value = requests.sortedByDescending { it.timestamp }.firstOrNull()
            }

        // 2. Listen for OUTGOING newly accepted requests (Device A logic)
        viewModelScope.launch {
            android.util.Log.d("CONNECTION_DEBUG", "Starting outgoing accepted listener...")

            outgoingAcceptedListener = db.collection("connections")
                .whereEqualTo("senderId", userId)
                .whereEqualTo("status", "accepted")
                .whereIn("handledBySender", listOf(false, null)) // Handle both false and missing (null)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(1)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        android.util.Log.e("CONNECTION_DEBUG", "Listener failed", error)
                        return@addSnapshotListener
                    }
                    
                    val size = snapshot?.size() ?: 0
                    android.util.Log.d("CONNECTION_DEBUG", "Listener triggered: $size")

                    val doc = snapshot?.documents?.firstOrNull()
                    if (doc != null) {
                        val connection = mapToConnection(doc)
                        val handled = doc.get("handledBySender") as? Boolean ?: false
                        
                        android.util.Log.d("CONNECTION_DEBUG", "Detected accepted request. ID: ${doc.id}, handledBySender: $handled")
                        
                        // ✅ ONE-TIME EVENT PATTERN: Only emit if current state is null
                        if (connection != null && connection.id != lastHandledRequestId.value && !handled) {
                            if (_acceptedSentRequest.value == null) {
                                // 1. Update local guard immediately to prevent double-trigger
                                markRequestHandled(connection.id)
                                
                                // 2. EMIT UI EVENT (UI will handle consumption and Firestore update)
                                _acceptedSentRequest.value = connection
                            }
                        }
                    }
                    // ❌ REMOVED: Automatic null-reset (UI will call consumeAcceptedSentRequest instead)
                }
        }
    }

    private fun mapToConnection(doc: com.google.firebase.firestore.DocumentSnapshot): Connection? {
        return try {
            Connection(
                id = doc.id,
                senderId = doc.getString("senderId") ?: "",
                receiverId = doc.getString("receiverId") ?: "",
                relation = doc.getString("relation") ?: "",
                status = doc.getString("status") ?: "pending",
                timestamp = doc.getLong("timestamp") ?: 0L,
                notified = doc.getBoolean("notified") ?: false,
                handledBySender = doc.getBoolean("handledBySender") ?: false
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun syncFcmToken(userId: String) {
        viewModelScope.launch {
            try {
                com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val token = task.result
                        viewModelScope.launch {
                            authRepo.updateFcmTokens(userId, token)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AuthViewModel", "FCM Token sync failed", e)
            }
        }
    }

    fun consumeIncomingRequest() {
        _activeIncomingRequest.value = null
    }

    fun consumeAcceptedSentRequest() {
        _acceptedSentRequest.value = null
    }

    fun markRequestAsHandledInFirestore(requestId: String) {
        viewModelScope.launch {
            familyRepo.markRequestAsHandled(requestId)
        }
    }

    fun updateLoginEmail(email: String) {
        _loginEmail.value = email
    }

    fun updateLoginPassword(password: String) {
        _loginPassword.value = password
    }

    fun updateRegisterBasic(name: String, phone: String, email: String, pass: String) {
        _registerState.update { it.copy(name = name, phone = phone, email = email, password = pass) }
    }

    fun updateRegisterHealth(age: String, gender: String, conditions: List<String>, language: String) {
        _registerState.update { it.copy(age = age, gender = gender, conditions = conditions, language = language) }
    }

    fun login() {
        if (_loginEmail.value.isBlank() || _loginPassword.value.isBlank()) {
            _authError.value = "Please fill in all fields"
            return
        }
        
        viewModelScope.launch {
            _isLoading.value = true
            _authError.value = null
            
            val result = authRepo.signIn(_loginEmail.value, _loginPassword.value)
            result.onSuccess { userId: String ->
                prefManager.saveLastLoginTime(System.currentTimeMillis())
                prefManager.saveSession(true, userId)
                syncFcmToken(userId)
                _isLoading.value = false
            }.onFailure { e: Throwable ->
                _authError.value = formatAuthError(e)
                _isLoading.value = false
            }
        }
    }

    fun register() {
        val state = _registerState.value
        viewModelScope.launch {
            _isLoading.value = true
            _authError.value = null
            
            val userEntity = UserEntity(
                userId = "", // Will be filled by repo
                name = state.name,
                phone = state.phone,
                email = state.email,
                age = state.age,
                gender = state.gender,
                conditions = state.conditions,
                language = state.language
            )
            
            val result = authRepo.signUp(state.email, state.password, userEntity)
            result.onSuccess { userId: String ->
                prefManager.saveSession(true, userId)
                syncFcmToken(userId)
                _isLoading.value = false
            }.onFailure { e: Throwable ->
                _authError.value = formatAuthError(e)
                _isLoading.value = false
            }
        }
    }

    fun handleGoogleSignIn(idToken: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _authError.value = null
            
            val result = authRepo.signInWithGoogle(idToken)
            result.onSuccess { userId: String ->
                prefManager.saveLastLoginTime(System.currentTimeMillis())
                prefManager.saveSession(true, userId)
                _isLoading.value = false
            }.onFailure { e: Throwable ->
                _authError.value = formatAuthError(e)
                _isLoading.value = false
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            // 1. Clear Listeners BEFORE Firebase sign-out
            incomingRequestListener?.remove()
            incomingRequestListener = null
            outgoingAcceptedListener?.remove()
            outgoingAcceptedListener = null
            
            // 2. Sign out Firebase
            com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
            
            // 3. Clear Session & Prefs
            prefManager.clearAllData()
            
            // 4. Clear Room Database safely on IO thread
            val app = getApplication<android.app.Application>()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                AppDatabase.getDatabase(app).clearAllTables()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        incomingRequestListener?.remove()
        outgoingAcceptedListener?.remove()
        authStateListener?.let {
            com.google.firebase.auth.FirebaseAuth.getInstance().removeAuthStateListener(it)
        }
    }

    fun clearError() {
        _authError.value = null
    }

    private fun formatAuthError(e: Throwable): String {
        val msg = e.message ?: "An unknown error occurred"
        return when {
            msg.contains("invalid-credential", ignoreCase = true) -> "Invalid email or password"
            msg.contains("email-already-in-use", ignoreCase = true) -> "Email already exists"
            msg.contains("network-request-failed", ignoreCase = true) -> "Network error. Please check your connection."
            else -> "Authentication failed. Please check your credentials or network and try again."
        }
    }
}

