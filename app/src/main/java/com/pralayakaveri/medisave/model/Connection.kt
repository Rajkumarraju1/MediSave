package com.pralayakaveri.medisave.model

data class Connection(
    val id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val relation: String = "", // Legacy fallback
    val labels: Map<String, String> = emptyMap(), // Asymmetric labels { uid: "Dad", ... }
    val status: String = "pending", // "pending", "accepted", "revoked"
    val timestamp: Long = System.currentTimeMillis(),
    val notified: Boolean = false,
    val handledBySender: Boolean = false
)
