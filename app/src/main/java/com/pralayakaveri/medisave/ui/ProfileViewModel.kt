package com.pralayakaveri.medisave.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.pralayakaveri.medisave.data.AppDatabase
import com.pralayakaveri.medisave.data.AuthRepository
import com.pralayakaveri.medisave.data.FamilyConnectionRepository
import com.pralayakaveri.medisave.data.UserEntity
import com.pralayakaveri.medisave.model.Connection
import com.pralayakaveri.medisave.model.Medicine
import com.pralayakaveri.medisave.model.MemberType
import com.pralayakaveri.medisave.util.AdherenceCalculator
import com.pralayakaveri.medisave.util.DateUtils
import com.pralayakaveri.medisave.util.ScheduleUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.channels.awaitClose

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val localDb = AppDatabase.getDatabase(application)
    private val connectionRepo = FamilyConnectionRepository()
    private val authRepo = AuthRepository(localDb.userDao())
    private val preferenceManager = com.pralayakaveri.medisave.data.PreferenceManager(application)
    
    val primaryUser: StateFlow<UserEntity?> = localDb.userDao().getPrimaryUserFlow()
        .onEach { Log.d("PROFILE_DEBUG", "Primary User flow emitted in StateFlow: ${it?.name}") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _incomingRequests = MutableStateFlow<List<Connection>>(emptyList())
    val incomingRequests: StateFlow<List<Connection>> = _incomingRequests.asStateFlow()

    private val _acceptedConnections = MutableStateFlow<List<Connection>>(emptyList())
    val acceptedConnections: StateFlow<List<Connection>> = _acceptedConnections.asStateFlow()



    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _snackbarMessages = MutableSharedFlow<String>()
    val snackbarMessages: SharedFlow<String> = _snackbarMessages.asSharedFlow()

    // Real-time status map for connected members
    private val remoteMemberStatus = MutableStateFlow<Map<String, MemberStatusData>>(emptyMap())
    private val remoteMemberMedicines = MutableStateFlow<Map<String, List<Medicine>>>(emptyMap())
    private val activeListeners = mutableMapOf<String, ListenerRegistration>()
    private var authJob: kotlinx.coroutines.Job? = null

    data class MemberStatusData(
        val name: String,
        val adherence: Int,
        val lastActiveAt: Long,
        val age: String,
        val condition: String
    )

    private val refreshTrigger = MutableStateFlow(System.currentTimeMillis())

    private fun getAuthenticatedUserFlow(): Flow<String?> = callbackFlow {
        val auth = FirebaseAuth.getInstance()
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val uid = firebaseAuth.currentUser?.uid
            trySend(uid)
        }
        auth.addAuthStateListener(listener)
        awaitClose {
            auth.removeAuthStateListener(listener)
        }
    }

    init {
        viewModelScope.launch {
            getAuthenticatedUserFlow().collect { uid ->
                // Cancel previous session jobs and clear status maps
                authJob?.cancel()
                authJob = null
                
                // Remove all active profile status listeners
                activeListeners.values.forEach { it.remove() }
                activeListeners.clear()
                remoteMemberStatus.value = emptyMap()
                remoteMemberMedicines.value = emptyMap()
                _acceptedConnections.value = emptyList()
                _incomingRequests.value = emptyList()

                if (uid != null) {
                    Log.d("PROFILE_DEBUG", "Auth stabilized in ProfileViewModel. uid: $uid")
                    val firebaseUser = FirebaseAuth.getInstance().currentUser ?: return@collect
                    val email = firebaseUser.email ?: ""

                    authJob = viewModelScope.launch {
                        // 1. Ensure user exists in Room (seeded if necessary)
                        ensureUserExists(uid, email)

                        // 2. Setup Remote Member Status Listeners
                        launch {
                            connectionRepo.observeAcceptedConnections(uid)
                                .catch { e ->
                                    Log.e("PROFILE_DEBUG", "Error observing accepted connections", e)
                                    _snackbarMessages.emit("Error loading connections: ${e.message}")
                                }
                                .collect { connections ->
                                    _acceptedConnections.value = connections
                                    syncMemberListeners(uid, connections)
                                }
                        }

                        // 3. Setup Remote Member Medicines Flow (Reactive Listener)
                        launch {
                            _acceptedConnections.flatMapLatest { connections ->
                                val memberIds = connections.map { if (it.senderId == uid) it.receiverId else it.senderId }
                                if (memberIds.isEmpty()) {
                                    flowOf(emptyMap<String, List<Medicine>>())
                                } else {
                                    combine(memberIds.map { id -> 
                                        connectionRepo.listenToMemberMedicines(id)
                                            .catch { e ->
                                                Log.e("PROFILE_DEBUG", "Error listening to medicines for member $id", e)
                                                emit(emptyList())
                                            }
                                            .map { id to it }
                                    }) { pairs ->
                                        pairs.toMap()
                                    }
                                }
                            }
                            .catch { e ->
                                Log.e("PROFILE_DEBUG", "Error in member medicines flatMapLatest pipeline", e)
                            }
                            .collect { medicinesMap ->
                                remoteMemberMedicines.value = medicinesMap
                            }
                        }

                        // 4. Setup Incoming Connection Requests Flow
                        launch {
                            connectionRepo.observeIncomingRequests(uid)
                                .catch { e ->
                                    Log.e("PROFILE_DEBUG", "Error observing incoming requests", e)
                                    _snackbarMessages.emit("Error loading requests: ${e.message}")
                                }
                                .collect { requests ->
                                    _incomingRequests.value = requests
                                }
                        }
                    }
                } else {
                    Log.d("PROFILE_DEBUG", "User logged out. Profile collectors stopped.")
                }
            }
        }
    }


    fun refresh() {
        refreshTrigger.value = System.currentTimeMillis()
    }

    // Reactive UI State Pipeline
    val uiState: StateFlow<ProfileUiState> = combine(
        primaryUser,
        localDb.medicineReminderDao().observeAllReminders().onStart { emit(emptyList()) },
        _acceptedConnections,
        remoteMemberStatus,
        remoteMemberMedicines,
        refreshTrigger
    ) { args: Array<Any?> ->
        val primaryUser = args[0] as? UserEntity
        val localMedicines = args[1] as List<com.pralayakaveri.medisave.data.MedicineReminderEntity>
        val acceptedConnections = args[2] as List<Connection>
        val remoteStates = args[3] as Map<String, MemberStatusData>
        val remoteMedsMap = args[4] as Map<String, List<Medicine>>
        val activeProfileId = "primary"
        // args[5] is refreshTrigger

        if (primaryUser == null) {
            return@combine ProfileUiState.Loading
        }
        
        val user = primaryUser
        val currentUserId = user.userId
        val anchorTime = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata"))
        val uis = mutableListOf<MemberUiModel>()

        // 1. PRIMARY USER (YOU)
        val primaryMeds = localMedicines.filter { it.profileId == activeProfileId }.map { it.toMedicine() }
        
        val primaryReport = AdherenceCalculator.calculateReport(primaryMeds, anchorTime = anchorTime)
        val todayStr = anchorTime.toLocalDate().toString()
        
        if (com.pralayakaveri.medisave.BuildConfig.DEBUG) {
            Log.d("DEBUG_ADHERENCE", "screen=Profile | profileId=$activeProfileId | meds=${primaryMeds.size} | total=${primaryReport.todayStats.total} | taken=${primaryReport.todayStats.taken} | adherence=${primaryReport.adherencePercentage}%")
        }
        
        uis.add(MemberUiModel(
            id = currentUserId,
            name = user.name,
            relation = "You",
            type = MemberType.PRIMARY,
            adherence = primaryReport.adherencePercentage ?: 0,
            lastActiveAt = System.currentTimeMillis(),
            age = user.age,
            condition = user.conditions.joinToString(", ").take(25).ifEmpty { "Healthy" },
            email = user.email
        ))
        
        // Log individual dose validity for primary user
        if (com.pralayakaveri.medisave.BuildConfig.DEBUG) {
            primaryMeds.forEach { med ->
                med.times.forEach { t ->
                    val isValid = ScheduleUtils.isDoseValid(med, todayStr, t)
                    val status = med.getStatusAt(todayStr, t)
                    Log.d("DEBUG_ADHERENCE", "Dose: ${med.name} at $t | Valid: $isValid | Status: $status")
                }
            }
        }

        // 2. CONNECTED MEMBERS (Firestore)
        acceptedConnections.forEach { conn ->
            val otherId = if (conn.senderId == currentUserId) conn.receiverId else conn.senderId
            val status = remoteStates[otherId]
            val remoteMeds = remoteMedsMap[otherId] ?: emptyList()
            val report = AdherenceCalculator.calculateReport(remoteMeds, anchorTime = anchorTime)
            
            if (status != null) {
                uis.add(MemberUiModel(
                    id = otherId,
                    name = status.name,
                    relation = conn.relation, 
                    type = MemberType.CONNECTED,
                    adherence = report.adherencePercentage ?: 0,
                    lastActiveAt = status.lastActiveAt,
                    age = status.age,
                    condition = status.condition,
                    email = "",
                    connectionId = conn.id
                ))
            }
        }

        // Manual members removed

        if (uis.isEmpty()) {
            ProfileUiState.Empty
        } else {
            // 4. STATS STANDARDIZATION
            val todayStrISO = anchorTime.toLocalDate().toString()
            val activeMedsCount = localMedicines
                .filter { it.profileId == activeProfileId }
                .count { it.startDate <= todayStrISO }

            ProfileUiState.Success(
                members = uis,
                activeMedicineCount = activeMedsCount,
                primaryAdherence = primaryReport.adherencePercentage ?: 0,
                connectedMemberCount = acceptedConnections.size
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfileUiState.Loading)

    // Remove or Update summaryStats to avoid compilation errors if used
    val summaryStats: StateFlow<Pair<Int, Int>> = uiState.map { state ->
        if (state is ProfileUiState.Success) {
            Pair(state.members.size, state.primaryAdherence)
        } else {
            Pair(0, 0)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Pair(0, 0))

    private fun syncMemberListeners(currentUserId: String, connections: List<Connection>) {
        val connectedUserIds = connections.map { 
            if (it.senderId == currentUserId) it.receiverId else it.senderId 
        }.toSet()

        val toRemove = activeListeners.keys.minus(connectedUserIds)
        toRemove.forEach { userId ->
            activeListeners[userId]?.remove()
            activeListeners.remove(userId)
            val currentMap = remoteMemberStatus.value.toMutableMap()
            currentMap.remove(userId)
            remoteMemberStatus.value = currentMap
        }

        val toAdd = connectedUserIds.minus(activeListeners.keys)
        toAdd.forEach { userId ->
            val listener = FirebaseFirestore.getInstance()
                .collection("users").document(userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    if (snapshot == null || !snapshot.exists()) return@addSnapshotListener

                    val data = MemberStatusData(
                        name = snapshot.getString("name") ?: "User",
                        adherence = snapshot.getLong("averageAdherence")?.toInt() ?: 100,
                        lastActiveAt = snapshot.getTimestamp("lastActiveAt")?.toDate()?.time ?: 0L,
                        age = snapshot.getString("age") ?: "--",
                        condition = snapshot.getString("conditions") ?: "" 
                    )
                    
                    val currentMap = remoteMemberStatus.value.toMutableMap()
                    currentMap[userId] = data
                    remoteMemberStatus.value = currentMap
                }
            activeListeners[userId] = listener
        }
    }

    override fun onCleared() {
        super.onCleared()
        activeListeners.values.forEach { it.remove() }
        activeListeners.clear()
    }


    fun revokeConnection(currentUserId: String, targetUserId: String) {
        viewModelScope.launch {
            connectionRepo.removeConnection(currentUserId, targetUserId)
        }
    }

    private suspend fun ensureUserExists(uid: String, email: String) {
        localDb.withTransaction {
            val existing = localDb.userDao().getUserById(uid)
            if (existing == null) {
                val connectionCode = com.pralayakaveri.medisave.util.ConnectionCodeGenerator.generateUniqueCode()
                val newUser = UserEntity(
                    userId = uid,
                    name = email.split("@").firstOrNull() ?: "User",
                    email = email,
                    phone = "",
                    age = "25",
                    gender = "Not Specified",
                    conditions = emptyList(),
                    language = "English",
                    connectionCode = connectionCode
                )
                localDb.userDao().insert(newUser)
            } else if (existing.connectionCode.isEmpty()) {
                val connectionCode = com.pralayakaveri.medisave.util.ConnectionCodeGenerator.generateUniqueCode()
                localDb.userDao().insert(existing.copy(connectionCode = connectionCode))
            }
        }
    }

    // addMember (manual) removed

    fun updateProfileName(newName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = authRepo.updateProfile(newName)
            _isLoading.value = false
            if (result.isSuccess) {
                _snackbarMessages.emit("Profile updated successfully")
            } else {
                _snackbarMessages.emit("Error: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun changePassword(current: String, new: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = authRepo.changePassword(current, new)
            _isLoading.value = false
            if (result.isSuccess) {
                _snackbarMessages.emit("Password changed successfully")
            } else {
                _snackbarMessages.emit("Error: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun getProviders(): List<String> = authRepo.getLoginProviders()

    fun updateMember(id: String, relation: String) {
        viewModelScope.launch {
            try {
                connectionRepo.updateConnectionLabel(id, relation)
                _snackbarMessages.emit("Member updated successfully")
            } catch (e: Exception) {
                _snackbarMessages.emit("Update failed: ${e.message}")
            }
        }
    }

    fun deleteMember(memberId: String) {
        viewModelScope.launch {
            try {
                connectionRepo.disconnectMember(memberId)
                _snackbarMessages.emit("Member removed successfully")
            } catch (e: Exception) {
                _snackbarMessages.emit("Removal failed: ${e.message}")
            }
        }
    }
}
