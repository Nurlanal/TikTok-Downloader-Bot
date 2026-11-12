package az.rafiqmarket.core.util

import az.rafiqmarket.core.network.dto.LatLngDto

/** Serverdəki alqoritmin eynisi (ray casting). Son qərarı HƏMİŞƏ server verir. */
fun pointInPolygon(lat: Double, lng: Double, polygon: List<LatLngDto>): Boolean {
    if (polygon.size < 3) return false
    var inside = false
    var j = polygon.size - 1
    for (i in polygon.indices) {
        val xi = polygon[i].lng; val yi = polygon[i].lat
        val xj = polygon[j].lng; val yj = polygon[j].lat
        if ((yi > lat) != (yj > lat) && lng < (xj - xi) * (lat - yi) / (yj - yi) + xi) inside = !inside
        j = i
    }
    return inside
}

fun polygonCenter(polygon: List<LatLngDto>): LatLngDto? =
    if (polygon.isEmpty()) null else LatLngDto(polygon.map { it.lat }.average(), polygon.map { it.lng }.average())
