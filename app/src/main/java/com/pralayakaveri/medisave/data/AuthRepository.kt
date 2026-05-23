package com.pralayakaveri.medisave.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.pralayakaveri.medisave.util.ConnectionCodeGenerator
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val userDao: UserDao
) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }

    suspend fun signIn(email: String, password: String): Result<String> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val userId = result.user?.uid ?: return Result.failure(Exception("Login failed"))
            
            // Sync from Firestore to local
            val userDoc = db.collection("users").document(userId).get().await()
            if (userDoc.exists()) {
                val remoteUser = UserEntity(
                    userId = userId,
                    name = userDoc.getString("name") ?: "User",
                    email = auth.currentUser?.email ?: userDoc.getString("email") ?: "",
                    phone = userDoc.getString("phone") ?: auth.currentUser?.phoneNumber ?: "",
                    age = userDoc.getString("age") ?: "25",
                    gender = userDoc.getString("gender") ?: "Not Specified",
                    conditions = emptyList(),
                    language = userDoc.getString("language") ?: "English",
                    connectionCode = userDoc.getString("connectionCode") ?: ""
                )
                userDao.insert(remoteUser)
            }
            
            Result.success(userId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncUserToFirestore(user: UserEntity) {
        val userData = hashMapOf(
            "name" to user.name,
            "email" to user.email,
            "phone" to user.phone,
            "connectionCode" to user.connectionCode,
            "age" to user.age,
            "gender" to user.gender,
            "language" to user.language,
            "lastActiveAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        db.collection("users").document(user.userId)
            .set(userData, com.google.firebase.firestore.SetOptions.merge())
            .await()
    }

    suspend fun signUp(email: String, password: String, userEntity: UserEntity): Result<String> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val userId = result.user?.uid ?: return Result.failure(Exception("Registration failed"))
            
            val connectionCode = ConnectionCodeGenerator.generateUniqueCode()
            val finalUser = userEntity.copy(userId = userId, connectionCode = connectionCode)
            userDao.insert(finalUser)
            
            // Sync to Firestore with createdAt
            val initialData = hashMapOf(
                "name" to finalUser.name,
                "email" to finalUser.email,
                "phone" to finalUser.phone,
                "connectionCode" to finalUser.connectionCode,
                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            db.collection("users").document(userId).set(initialData, com.google.firebase.firestore.SetOptions.merge()).await()
            syncUserToFirestore(finalUser)
            
            
            Result.success(userId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signInWithGoogle(idToken: String): Result<String> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            val firebaseUser = result.user ?: return Result.failure(Exception("Google Sign-In failed"))
            
            // Check if user exists in local Room
            val localUser = userDao.getUserById(firebaseUser.uid)
            if (localUser == null) {
                // First time: Create automatic UserEntity
                val connectionCode = ConnectionCodeGenerator.generateUniqueCode()
                val newUser = UserEntity(
                    userId = firebaseUser.uid,
                    name = firebaseUser.displayName ?: "User",
                    email = firebaseUser.email ?: "",
                    phone = firebaseUser.phoneNumber ?: "",
                    age = "25",
                    gender = "Not Specified",
                    conditions = emptyList(),
                    language = "English",
                    connectionCode = connectionCode
                )
                userDao.insert(newUser)
                syncUserToFirestore(newUser)
            } else if (localUser.connectionCode.isEmpty()) {
                // Retroactively generate code if missing
                val code = ConnectionCodeGenerator.generateUniqueCode()
                val updatedUser = localUser.copy(connectionCode = code)
                userDao.insert(updatedUser)
                syncUserToFirestore(updatedUser)
            }
            
            Result.success(firebaseUser.uid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    suspend fun signOut() {
        auth.signOut()
    }

    suspend fun ensureAnonymousAuth(): String? {
        val currentUser = auth.currentUser
        if (currentUser != null) return currentUser.uid

        return try {
            val result = auth.signInAnonymously().await()
            result.user?.uid
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    suspend fun updateProfile(name: String): Result<Unit> {
        return try {
            val user = auth.currentUser ?: return Result.failure(Exception("Not authenticated"))
            val localUser = userDao.getUserById(user.uid) ?: return Result.failure(Exception("User not found"))
            
            val updatedUser = localUser.copy(name = name)
            userDao.insert(updatedUser)
            
            // Sync to Firestore
            val userData = mapOf(
                "name" to name,
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            db.collection("users").document(user.uid).set(userData, com.google.firebase.firestore.SetOptions.merge()).await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun changePassword(current: String, new: String): Result<Unit> {
        return try {
            val user = auth.currentUser ?: return Result.failure(Exception("Not authenticated"))
            val email = user.email ?: return Result.failure(Exception("Email missing"))
            
            // Re-authenticate
            val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, current)
            user.reauthenticate(credential).await()
            
            // Update password
            user.updatePassword(new).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getLoginProviders(): List<String> {
        return auth.currentUser?.providerData?.map { it.providerId } ?: emptyList()
    }

    suspend fun reauthenticate(password: String): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(Exception("User not found"))
        val email = user.email ?: return Result.failure(Exception("Email not found"))
        val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, password)
        
        return try {
            user.reauthenticate(credential).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reauthenticateWithGoogle(idToken: String): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(Exception("User not found"))
        val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
        
        return try {
            user.reauthenticate(credential).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateFcmTokens(userId: String, token: String) {
        val userRef = db.collection("users").document(userId)
        
        db.runTransaction { transaction ->
            val snapshot = transaction.get(userRef)
            
            // Safety check: Don't update tokens if account is being deleted
            if (snapshot.getBoolean("deletionInProgress") == true) return@runTransaction
            
            @Suppress("UNCHECKED_CAST")
            val currentTokens = snapshot.get("fcmTokens") as? MutableList<String> ?: mutableListOf()
            
            if (!currentTokens.contains(token)) {
                currentTokens.add(token)
                val finalTokens = if (currentTokens.size > 5) currentTokens.takeLast(5) else currentTokens
                transaction.set(userRef, mapOf("fcmTokens" to finalTokens), com.google.firebase.firestore.SetOptions.merge())
            }
        }.await()
    }

    suspend fun deleteUserFirestoreData(userId: String) {
        val userRef = db.collection("users").document(userId)
        
        android.util.Log.i("DeleteAccount", "Starting recursive deletion for user: $userId")
        
        // STEP 0: Set Deletion Lock
        userRef.update("deletionInProgress", true).await()
        android.util.Log.i("DeleteAccount", "STEP_0_COMPLETE: Deletion lock active")

        // STEP 1: Delete Medicines (Subcollection)
        val medicines = userRef.collection("medicines").get().await()
        if (!medicines.isEmpty) {
            android.util.Log.d("DeleteAccount", "Found ${medicines.size()} medicines to delete")
            deleteInChunks(medicines.documents.map { it.reference })
        }
        android.util.Log.i("DeleteAccount", "STEP_1_COMPLETE: Medicines purged")

        // STEP 2: Delete Dose Logs (Subcollection)
        val logs = db.collection("doseLogs").document(userId).collection("logs").get().await()
        if (!logs.isEmpty) {
            android.util.Log.d("DeleteAccount", "Found ${logs.size()} dose logs to delete")
            deleteInChunks(logs.documents.map { it.reference })
        }
        android.util.Log.i("DeleteAccount", "STEP_2_COMPLETE: Dose logs purged")

        // STEP 3: Delete Active Connections
        val activeConnA = db.collection("active_connections").whereEqualTo("userA", userId).get().await()
        val activeConnB = db.collection("active_connections").whereEqualTo("userB", userId).get().await()
        val allActive = (activeConnA.documents + activeConnB.documents).distinctBy { it.id }
        if (allActive.isNotEmpty()) {
            android.util.Log.d("DeleteAccount", "Found ${allActive.size} active connections to delete")
            deleteInChunks(allActive.map { it.reference })
        }
        android.util.Log.i("DeleteAccount", "STEP_3_COMPLETE: Active connections severed")

        // STEP 4: Delete Connection Requests
        val requestsSent = db.collection("connections").whereEqualTo("senderId", userId).get().await()
        val requestsReceived = db.collection("connections").whereEqualTo("receiverId", userId).get().await()
        val allRequests = (requestsSent.documents + requestsReceived.documents).distinctBy { it.id }
        if (allRequests.isNotEmpty()) {
            android.util.Log.d("DeleteAccount", "Found ${allRequests.size} connection requests to delete")
            deleteInChunks(allRequests.map { it.reference })
        }
        android.util.Log.i("DeleteAccount", "STEP_4_COMPLETE: Connection requests purged")

        // STEP 5: Delete User Profile Document
        userRef.delete().await()
        android.util.Log.i("DeleteAccount", "STEP_5_COMPLETE: User profile document deleted")
        
        android.util.Log.i("DeleteAccount", "Full recursive Firestore deletion completed successfully")
    }

    private suspend fun deleteInChunks(refs: List<com.google.firebase.firestore.DocumentReference>) {
        refs.chunked(500).forEach { chunk ->
            db.runBatch { batch ->
                chunk.forEach { batch.delete(it) }
            }.await()
        }
    }
}

