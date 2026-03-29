package cz.dcervenka.choretracker.core.common

sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Error(val message: String, val cause: Throwable? = null) : AppResult<Nothing>
}

typealias EmptyResult = AppResult<Unit>

inline fun <T> AppResult<T>.getOrNull(): T? = when (this) {
    is AppResult.Success -> value
    is AppResult.Error -> null
}
