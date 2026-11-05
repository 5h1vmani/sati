package com.padakanti.sati.log

/** Ids from an imported file are limited, because a very long id can break the database queries. */
object IdRules {
    const val MAX_LENGTH = 64

    /** 1 to 64 characters: ASCII letters, digits, "-" and "_". */
    fun valid(id: String): Boolean =
        id.length in 1..MAX_LENGTH && id.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_' }
}
