package app.vent.data

actual fun createVentApi(): VentApi = VentApiClient("http://10.0.2.2:8080")
