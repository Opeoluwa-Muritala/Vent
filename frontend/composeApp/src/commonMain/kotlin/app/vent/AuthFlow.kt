package app.vent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vent.api.EventDto
import app.vent.data.VentApi
import app.vent.ui.theme.VentTheme
import app.vent.ui.theme.spacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview

enum class AuthStep { EMAIL, CODE, EVENT_PICKER, AUTHENTICATED }

data class AuthUiState(
    val step: AuthStep = AuthStep.EMAIL,
    val email: String = "",
    val code: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val events: List<EventDto> = emptyList(),
    val resendSeconds: Int = 0,
    val localAttendee: Boolean = false,
    val organizer: Boolean = false,
    val signInGeneration: Int = 0,
)

class AuthViewModel(
    internal val api: VentApi,
    private val localStore: app.vent.data.VentLocalStore? = null,
    private val allowLocalOrganizer: Boolean = false,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = mutableState.asStateFlow()

    fun updateEmail(value: String) = mutableState.update { it.copy(email = value, error = null) }
    fun updateCode(value: String) {
        val digits = value.filter(Char::isDigit).take(6)
        mutableState.update { it.copy(code = digits, error = null) }
        if (digits.length == 6) verify()
    }
    fun requestCode() {
        if (state.value.localAttendee) return
        val email = state.value.email.trim()
        val error = when {
            email.isEmpty() -> "Enter your email."
            !EMAIL.matches(email) -> "That email doesn't look right."
            else -> null
        }
        if (error != null) return mutableState.update { it.copy(error = error) }
        launchRequest {
            api.requestOtp(email)
            mutableState.update { it.copy(step = AuthStep.CODE, loading = false, error = null, resendSeconds = 30) }
            startResendCountdown()
        }
    }
    fun verify() {
        val current = state.value
        if (current.step != AuthStep.CODE || current.code.length != 6 || current.loading) return
        if (current.localAttendee) {
            val organizer = allowLocalOrganizer && current.email == LOCAL_ORGANIZER_EMAIL
            if (current.code != (if (organizer) "123456" else "000000")) {
                mutableState.update { it.copy(code = "", error = "Incorrect on-device code.") }
                return
            }
            mutableState.update { it.copy(step = AuthStep.AUTHENTICATED, code = "", error = null, organizer = organizer) }
            return
        }
        launchRequest {
            api.verifyOtp(state.value.email, state.value.code)
            val organizer = api.isOrganizer()
            val events = api.events()
            mutableState.update {
                it.copy(step = if (events.size > 1) AuthStep.EVENT_PICKER else AuthStep.AUTHENTICATED, events = events, loading = false, error = null, organizer = organizer)
            }
        }
    }
    fun changeEmail() = mutableState.update { AuthUiState(email = it.email, signInGeneration = it.signInGeneration) }
    fun signInLocally() {
        val email = state.value.email.trim().lowercase()
        if (state.value.step != AuthStep.EMAIL || state.value.loading) return
        if (email == LOCAL_ORGANIZER_EMAIL && !allowLocalOrganizer) {
            requestCode()
            return
        }
        launchRequest {
            val registered = localStore?.load()?.settings?.registeredAttendees.orEmpty()
            mutableState.value = if (email in registered || (allowLocalOrganizer && email == LOCAL_ORGANIZER_EMAIL)) {
                AuthUiState(step = AuthStep.CODE, email = email, localAttendee = true, signInGeneration = state.value.signInGeneration)
            } else state.value.copy(loading = false, error = "This email is not registered on this device.")
        }
    }
    fun chooseEvent(event: EventDto) = mutableState.update {
        if (it.step == AuthStep.EVENT_PICKER && event in it.events) {
            it.copy(step = AuthStep.AUTHENTICATED, events = listOf(event))
        } else it
    }
    fun signOut() {
        api.clearSession()
        mutableState.update { AuthUiState(signInGeneration = it.signInGeneration + 1) }
    }

    private fun launchRequest(block: suspend () -> Unit) {
        if (state.value.loading) return
        mutableState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { block() }.onFailure {
                mutableState.update { current -> current.copy(loading = false, code = if (current.step == AuthStep.CODE) "" else current.code, error = "Can't reach the server. Check your connection and try again.") }
            }
        }
    }
    private fun startResendCountdown() {
        viewModelScope.launch {
            while (state.value.resendSeconds > 0 && state.value.step == AuthStep.CODE) {
                delay(1_000)
                mutableState.update { it.copy(resendSeconds = (it.resendSeconds - 1).coerceAtLeast(0)) }
            }
        }
    }
    private companion object {
        const val LOCAL_ORGANIZER_EMAIL = "organizer@vent.demo"
        val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}

@Composable
fun AuthFlow(state: AuthUiState, onEmail: (String) -> Unit, onCode: (String) -> Unit, onRequest: () -> Unit, onVerify: () -> Unit, onBack: () -> Unit, onEvent: (EventDto) -> Unit, onDemo: () -> Unit) {
    when (state.step) {
        AuthStep.EMAIL -> EmailScreen(state, onEmail, onDemo)
        AuthStep.CODE -> CodeScreen(state, onCode, onVerify, onBack, onRequest)
        AuthStep.EVENT_PICKER -> EventPicker(state.events, onEvent)
        AuthStep.AUTHENTICATED -> Unit
    }
}

@Composable private fun EmailScreen(state: AuthUiState, onEmail: (String) -> Unit, onSubmit: () -> Unit) {
    val spacing = MaterialTheme.spacing
    val logoInk = MaterialTheme.colorScheme.onPrimary
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).safeDrawingPadding().imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(spacing.lg).widthIn(max = spacing.compactBreakpoint).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primary) {
                androidx.compose.foundation.Canvas(Modifier.size(spacing.heroIcon).semantics { contentDescription = "Vent" }) {
                    val stroke = size.width * .10f
                    val point = androidx.compose.ui.geometry.Offset(size.width * .5f, size.height * .73f)
                    drawLine(logoInk, androidx.compose.ui.geometry.Offset(size.width * .26f, size.height * .30f), point, stroke, androidx.compose.ui.graphics.StrokeCap.Round)
                    drawLine(logoInk, point, androidx.compose.ui.geometry.Offset(size.width * .74f, size.height * .30f), stroke, androidx.compose.ui.graphics.StrokeCap.Round)
                }
            }
            OutlinedTextField(state.email, onEmail, Modifier.fillMaxWidth(), label = { Text("Email") },
                leadingIcon = { Icon(Icons.Outlined.Email, null) }, singleLine = true, enabled = !state.loading,
                isError = state.error != null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done))
            state.error?.let { ErrorText(it) }
            Button(onSubmit, Modifier.fillMaxWidth().height(spacing.action), enabled = !state.loading) { if (state.loading) CircularProgressIndicator(Modifier.size(spacing.xl), strokeWidth = spacing.xs) else Text("Send code") }
        }
    }
}

@Composable private fun CodeScreen(state: AuthUiState, onCode: (String) -> Unit, onVerify: () -> Unit, onBack: () -> Unit, onResend: () -> Unit) {
    val spacing = MaterialTheme.spacing
    Scaffold(topBar = { IconButton(onBack, Modifier.minimumInteractiveComponentSize()) { Icon(Icons.Outlined.ArrowBack, "Change email") } }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).imePadding().padding(horizontal = spacing.xxl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
            Spacer(Modifier.weight(1f))
            Surface(Modifier.size(spacing.heroIcon), shape = MaterialTheme.shapes.extraLarge, border = BorderStroke(spacing.xs, MaterialTheme.colorScheme.primary)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Check, "Email sent") } }
            Text(if (state.localAttendee) "Enter your code" else "Check your email", style = MaterialTheme.typography.headlineLarge)
            Text(
                if (state.localAttendee) "Enter the six-digit on-device code."
                else "If ${state.email} is registered, we sent a 6-digit code. It expires in 10 minutes.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BasicTextField(value = state.code, onValueChange = onCode, enabled = !state.loading, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done), modifier = Modifier.semantics { contentDescription = "Six digit sign-in code" }, decorationBox = { inner ->
                Box { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.xs)) { repeat(6) { index -> OutlinedCard(Modifier.weight(1f).height(spacing.touch), border = BorderStroke(spacing.xs, if (state.error == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.error)) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(state.code.getOrNull(index)?.toString().orEmpty(), style = MaterialTheme.typography.titleLarge) } } } }; Box(Modifier.size(spacing.none)) { inner() } }
            })
            state.error?.let { ErrorText(it) }
            Button(onVerify, Modifier.fillMaxWidth().height(spacing.action), enabled = state.code.length == 6 && !state.loading) { if (state.loading) CircularProgressIndicator(Modifier.size(spacing.xl), strokeWidth = spacing.xs) else Text("Verify") }
            if (!state.localAttendee) {
                TextButton(onResend, enabled = !state.loading && state.resendSeconds == 0) { Text(if (state.resendSeconds > 0) "Resend code in 0:${state.resendSeconds.toString().padStart(2, '0')}" else "Send a new code") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Wrong email?"); TextButton(onBack) { Text("Change it") } }
            Spacer(Modifier.weight(1f))
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable private fun EventPicker(events: List<EventDto>, onEvent: (EventDto) -> Unit) {
    ModalBottomSheet(onDismissRequest = {}) { Column(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.xxl), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
        Text("Choose your event", style = MaterialTheme.typography.headlineSmall)
        events.forEach { event -> ListItem(headlineContent = { Text(event.title) }, supportingContent = { Text("${event.startsAt.take(10)} · Registered event") }, modifier = Modifier.clickable { onEvent(event) }.minimumInteractiveComponentSize()) }
    } }
}

@Composable private fun ErrorText(message: String) { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) { Icon(Icons.Outlined.ErrorOutline, "Error", tint = MaterialTheme.colorScheme.error); Text(message, color = MaterialTheme.colorScheme.error) } }

@Preview @Composable private fun EmailPreview() { VentTheme { EmailScreen(AuthUiState(), {}, {}) } }
@Preview @Composable private fun EmailErrorDarkPreview() { VentTheme(darkTheme = true) { EmailScreen(AuthUiState(email = "bad", error = "That email doesn't look right."), {}, {}) } }
@Preview @Composable private fun CodePreview() { VentTheme { CodeScreen(AuthUiState(step = AuthStep.CODE, email = "ada@company.com", code = "123"), {}, {}, {}, {}) } }
@Preview @Composable private fun CodeErrorDarkPreview() { VentTheme(darkTheme = true) { CodeScreen(AuthUiState(step = AuthStep.CODE, email = "ada@company.com", error = "That code isn't right. 3 tries left."), {}, {}, {}, {}) } }
