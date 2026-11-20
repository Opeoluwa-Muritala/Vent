package app.vent

internal actual fun localMinuteOfDay(): Int {
    val time = java.time.LocalTime.now()
    return time.hour * 60 + time.minute
}
