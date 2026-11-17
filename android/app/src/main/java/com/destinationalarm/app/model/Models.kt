package com.destinationalarm.app.model

data class Fix(
    val lat: Double,
    val lon: Double,
    val acc: Float,
    val speed: Float,
    val t: Long
)

data class Trip(
    val lat: Double,
    val lon: Double,
    val name: String,
    val radiusM: Int = 500,
    val minsBefore: Int = 0,
    val mode: String = "DRIVE"
)
