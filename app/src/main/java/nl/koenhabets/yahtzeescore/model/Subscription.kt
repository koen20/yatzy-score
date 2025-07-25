package nl.koenhabets.yahtzeescore.model

import kotlinx.serialization.Serializable

@Serializable
data class Subscription(
    val userId: String,
    var name: String?,
    var lastSeen: Long?
)

