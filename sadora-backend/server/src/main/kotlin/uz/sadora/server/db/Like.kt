package uz.sadora.server.db

/**
 * Makes a typed search term safe inside a LIKE pattern.
 *
 * Exposed parameterises the value, so there is no injection — but `%` and `_` keep
 * their meaning as wildcards, and a term of `%` matched every row. Postgres reads a
 * backslash as the escape character by default, so the three characters are prefixed
 * with one.
 */
fun String.escapeLike(): String =
    replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
