package com.pralayakaveri.medisave.data

sealed class ResourceState<out T> {
    object Loading : ResourceState<Nothing>()
    data class Success<out T>(val data: T) : ResourceState<T>()
    object Empty : ResourceState<Nothing>()
    object PermissionDenied : ResourceState<Nothing>()
    object NetworkFailure : ResourceState<Nothing>()
    data class Error(val message: String?) : ResourceState<Nothing>()
}
