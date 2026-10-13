package cz.dcervenka.choretracker.core.common

import cz.dcervenka.choretracker.core.model.error.AppError

sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Error(val error: AppError, val cause: Throwable? = null) : AppResult<Nothing>
}

typealias EmptyResult = AppResult<Unit>
