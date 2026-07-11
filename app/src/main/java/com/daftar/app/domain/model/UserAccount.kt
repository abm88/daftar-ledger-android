package com.daftar.app.domain.model

/** A signed-in saraf's identity. Credentials never leave the data layer. */
data class UserAccount(
    val id: String,
    val name: String,
    val email: String,
    val createdAtMillis: Long,
) {
    val firstName: String get() = name.substringBefore(' ')
}
