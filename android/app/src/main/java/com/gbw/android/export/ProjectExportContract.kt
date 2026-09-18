package com.gbw.android.export

internal object ProjectExportContract {
    const val GUITAR_ROLE = "guitar"
    val BACKING_ROLES = listOf("drums", "bass", "other", "vocals", "piano")
    val ALL_ROLES = BACKING_ROLES + GUITAR_ROLE

    fun validateStemRoles(roles: Set<String>): Boolean =
        roles == ALL_ROLES.toSet()
}
