package com.destinationalarm.app.model

/** A place the user saved locally (Home / Work / custom). Never leaves the device. */
data class SavedPlace(
    val id: String,
    val label: String,
    val name: String,
    val lat: Double,
    val lon: Double
) {
    val emoji: String
        get() = when (label.lowercase()) {
            "home" -> "🏠"
            "work" -> "🏢"
            "gym" -> "💪"
            else -> "⭐"
        }
}
