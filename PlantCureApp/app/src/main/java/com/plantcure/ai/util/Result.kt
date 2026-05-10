package com.plantcure.ai.util

/**
 * A sealed class representing the result of an operation.
 * Used across all repositories and ViewModels for consistent error handling.
 *
 * Usage:
 *   when (result) {
 *       is Result.Loading -> showLoading()
 *       is Result.Success -> showData(result.data)
 *       is Result.Error -> showError(result.message)
 *   }
 */
sealed class Result<out T> {
    data object Loading : Result<Nothing>()
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String, val exception: Throwable? = null) : Result<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    val isLoading: Boolean get() = this is Loading

    /**
     * Returns the data if this is a Success, or null otherwise.
     */
    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }

    /**
     * Returns the data if this is a Success, or the default value otherwise.
     */
    fun getOrDefault(default: @UnsafeVariance T): T = when (this) {
        is Success -> data
        else -> default
    }

    /**
     * Maps the data inside a Success result.
     */
    fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Loading -> Loading
        is Success -> Success(transform(data))
        is Error -> Error(message, exception)
    }

    companion object {
        /**
         * Wraps a suspending block in a try-catch, returning Result.
         */
        suspend fun <T> runCatching(block: suspend () -> T): Result<T> {
            return try {
                Success(block())
            } catch (e: Exception) {
                Error(e.message ?: "Unknown error", e)
            }
        }
    }
}
