package com.pralayakaveri.medisave.util

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.channels.ProducerScope

object FirestoreUtils {
    /**
     * Centrally handles Firestore snapshot listener errors.
     * Logs the error safely, handles specific codes (like PERMISSION_DENIED or UNAVAILABLE),
     * and gracefully completes the coroutine flow instead of propagating exceptions.
     *
     * @param tag The tag for logging (e.g. repository name).
     * @param action Description of the query/action being performed.
     * @param error The exception passed by the Firestore listener, if any.
     * @param scope The ProducerScope of the callbackFlow.
     * @return true if an error was handled, false otherwise.
     */
    fun handleListenerError(
        tag: String,
        action: String,
        error: FirebaseFirestoreException?,
        scope: ProducerScope<*>
    ): Boolean {
        if (error != null) {
            val code = error.code
            val message = error.message ?: "No details provided"
            
            when (code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                    Log.w(tag, "Firestore PERMISSION_DENIED in $action: $message. Degrading gracefully to offline/local cache.")
                }
                FirebaseFirestoreException.Code.UNAVAILABLE -> {
                    Log.i(tag, "Firestore UNAVAILABLE (offline/network loss) in $action. Seamless offline operation active.")
                }
                else -> {
                    Log.e(tag, "Firestore error ($code) in $action: $message", error)
                }
            }
            
            // Gracefully complete the flow rather than throwing close(error)
            try {
                scope.close()
            } catch (e: Exception) {
                Log.e(tag, "Failed to close flow gracefully inside $action error handler", e)
            }
            return true
        }
        return false
    }
}
