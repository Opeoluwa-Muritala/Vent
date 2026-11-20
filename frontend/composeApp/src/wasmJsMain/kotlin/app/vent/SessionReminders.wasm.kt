@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package app.vent

@kotlin.js.JsFun("() => { const now = new Date(); return now.getHours() * 60 + now.getMinutes(); }")
private external fun browserMinuteOfDay(): Int

internal actual fun localMinuteOfDay(): Int = browserMinuteOfDay()
