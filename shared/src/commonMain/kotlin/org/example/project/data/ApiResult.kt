package org.example.project.data

/**
 * A call's outcome. The data layer never throws at its callers — a screen that forgets
 * a try/catch would otherwise crash the app on a flaky connection.
 */
sealed class ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>()
    data class Failure(val failure: ApiFailure) : ApiResult<Nothing>()

    val valueOrNull: T?
        get() = (this as? Success)?.value

    val failureOrNull: ApiFailure?
        get() = (this as? Failure)?.failure

    inline fun <R> map(transform: (T) -> R): ApiResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }

    inline fun onSuccess(action: (T) -> Unit): ApiResult<T> {
        if (this is Success) action(value)
        return this
    }

    inline fun onFailure(action: (ApiFailure) -> Unit): ApiResult<T> {
        if (this is Failure) action(failure)
        return this
    }
}
