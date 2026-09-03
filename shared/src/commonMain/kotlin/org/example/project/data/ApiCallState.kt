package org.example.project.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The busy flag and the last error, in one place.
 *
 * Every controller needs the same three lines around every call — set busy, clear the
 * old error, put the new one somewhere the screen can read it — and the second copy of
 * that is where a `finally` goes missing and a button stays disabled forever.
 */
class ApiCallState {

    /** True while a call is in flight, so buttons can disable themselves. */
    var busy by mutableStateOf(false)
        private set

    /** The last failure, already turned into something a person can read. */
    var error by mutableStateOf<String?>(null)
        private set

    fun clearError() {
        error = null
    }

    /**
     * Runs one call and returns its value, or null on failure.
     *
     * [silent] is for background refreshes: they still clear busy, but a failure does
     * not raise a banner over whatever the user is currently doing.
     */
    suspend fun <T> run(silent: Boolean = false, block: suspend () -> ApiResult<T>): T? {
        busy = true
        if (!silent) error = null
        return try {
            when (val result = block()) {
                is ApiResult.Success -> result.value
                is ApiResult.Failure -> {
                    if (!silent) error = result.failure.readable()
                    null
                }
            }
        } finally {
            busy = false
        }
    }
}
