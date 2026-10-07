package io.thernal.archkit.core.domain.extension

import kotlin.enums.enumEntries

/** The entry named like this string, ignoring case, or null: for backend strings that map to an enum. */
inline fun <reified T : Enum<T>> String.asEnum(): T? {
    return enumEntries<T>().firstOrNull { entry -> entry.name.equals(other = this, ignoreCase = true) }
}
