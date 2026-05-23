package com.pralayakaveri.medisave.util

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

object ConnectionCodeGenerator {
    private val charPool : List<Char> = ('A'..'Z').toList()

    fun generateCode(): String {
        return (1..6)
            .map { i -> kotlin.random.Random.nextInt(0, charPool.size) }
            .map(charPool::get)
            .joinToString("")
    }

    suspend fun generateUniqueCode(): String {
        val db = FirebaseFirestore.getInstance()
        var code: String
        var isUnique: Boolean
        
        do {
            code = generateCode()
            val query = db.collection("users")
                .whereEqualTo("connectionCode", code)
                .get()
                .await()
            isUnique = query.isEmpty
        } while (!isUnique)
        
        return code
    }
}
