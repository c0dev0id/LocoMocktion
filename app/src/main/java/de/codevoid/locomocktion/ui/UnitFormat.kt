package de.codevoid.locomocktion.ui

enum class UnitSystem { Metric, Imperial }

const val KM_TO_MI: Float = 0.621371f
const val MPH_TO_KMH: Float = 1.609344f

fun speedUnitLabel(system: UnitSystem): String = when (system) {
    UnitSystem.Metric -> "km/h"
    UnitSystem.Imperial -> "mph"
}

fun distanceUnitLabel(system: UnitSystem): String = when (system) {
    UnitSystem.Metric -> "km"
    UnitSystem.Imperial -> "mi"
}

fun kmhToDisplay(kmh: Float, system: UnitSystem): Float = when (system) {
    UnitSystem.Metric -> kmh
    UnitSystem.Imperial -> kmh * KM_TO_MI
}

fun displayToKmh(value: Float, system: UnitSystem): Float = when (system) {
    UnitSystem.Metric -> value
    UnitSystem.Imperial -> value * MPH_TO_KMH
}

fun kmToDisplay(km: Float, system: UnitSystem): Float = when (system) {
    UnitSystem.Metric -> km
    UnitSystem.Imperial -> km * KM_TO_MI
}

fun displayToKm(value: Float, system: UnitSystem): Float = when (system) {
    UnitSystem.Metric -> value
    UnitSystem.Imperial -> value / KM_TO_MI
}

fun formatSpeed(kmh: Float, system: UnitSystem): String =
    "%.0f %s".format(kmhToDisplay(kmh, system), speedUnitLabel(system))

fun formatDistanceKm(km: Double, system: UnitSystem, decimals: Int = 2): String {
    val display = when (system) {
        UnitSystem.Metric -> km
        UnitSystem.Imperial -> km * KM_TO_MI
    }
    return "%.${decimals}f %s".format(display, distanceUnitLabel(system))
}

fun parseSpeedInput(str: String, system: UnitSystem): Float? =
    str.toFloatOrNull()?.let { displayToKmh(it, system) }

fun parseDistanceInput(str: String, system: UnitSystem): Float? =
    str.toFloatOrNull()?.let { displayToKm(it, system) }
