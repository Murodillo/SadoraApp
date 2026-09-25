package uz.sadora.server.db

/**
 * Enums are stored as their lowercase name, which is also their `@SerialName` on the
 * wire — `TRYING_TO_CONCEIVE` is `trying_to_conceive` in both places. Keeping the two
 * spellings identical means the database is readable without a lookup table.
 */
fun Enum<*>.dbValue(): String = name.lowercase()

inline fun <reified E : Enum<E>> enumFromDb(value: String?): E? =
    value?.let { stored -> enumValues<E>().firstOrNull { it.name.equals(stored, ignoreCase = true) } }

/** Unrecognised stored values fall back rather than crash — a stale row is not an outage. */
inline fun <reified E : Enum<E>> enumFromDb(value: String?, fallback: E): E =
    enumFromDb<E>(value) ?: fallback
