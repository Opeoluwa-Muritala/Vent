package app.vent

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSDate

internal actual fun localMinuteOfDay(): Int {
    val time = NSCalendar.currentCalendar.components(NSCalendarUnitHour or NSCalendarUnitMinute, NSDate())
    return time.hour.toInt() * 60 + time.minute.toInt()
}
