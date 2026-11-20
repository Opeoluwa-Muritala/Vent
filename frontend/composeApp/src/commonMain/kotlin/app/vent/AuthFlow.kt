package app.vent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.MarkEmailRead
import app.vent.ui.theme.heroTint
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vent.api.EventDto
import app.vent.composeapp.generated.resources.Res
import app.vent.composeapp.generated.resources.event_poster_collage
import app.vent.composeapp.generated.resources.events_wordmark
import app.vent.data.VentApi
import app.vent.ui.theme.VentTheme
import app.vent.ui.theme.spacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
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
    val logoInk = MaterialTheme.colorScheme.primary
    val heroTint = MaterialTheme.colorScheme.heroTint
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
    ) {
        Image(painterResource(Res.drawable.event_poster_collage), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            0.4f to heroTint.copy(alpha = 0f), 1f to heroTint,
        )))
        Column(Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            VentMark(logoInk, Modifier.size(spacing.huge))
            Icon(painterResource(Res.drawable.events_wordmark), "events",
                Modifier.width(spacing.huge * 3).height(spacing.xxxl), tint = MaterialTheme.colorScheme.onSurface)
            Text("Your summit pass", style = MaterialTheme.typography.headlineLarge)
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().imePadding().padding(spacing.lg)
                .widthIn(max = spacing.compactBreakpoint).fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = heroTint.copy(alpha = .88f),
            border = BorderStroke(spacing.xs / 4, MaterialTheme.colorScheme.onSurface.copy(alpha = .12f)),
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                OutlinedTextField(state.email, onEmail, Modifier.fillMaxWidth().heightIn(min = spacing.action), label = { Text("Email") },
                    leadingIcon = { Icon(painterResource(app.vent.ui.theme.EventIcons.mail), null) },
                    shape = MaterialTheme.shapes.small,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.secondary),
                    singleLine = true, enabled = !state.loading,
                    isError = state.error != null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done))
                state.error?.let { ErrorText(it) }
                Button(onSubmit, Modifier.fillMaxWidth().height(spacing.action), enabled = !state.loading) { if (state.loading) CircularProgressIndicator(Modifier.size(spacing.xl), strokeWidth = spacing.xs) else Text("Send code") }
                Text("We send a six-digit code. No password.", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun VentMark(ink: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    val live = MaterialTheme.colorScheme.tertiary
    androidx.compose.foundation.Canvas(modifier.semantics { contentDescription = "events" }) {
        val unit = size.minDimension / 96f
        drawArc(ink, 45f, 315f, false, Offset(26f * unit, 26f * unit),
            Size(44f * unit, 44f * unit), style = Stroke(9f * unit, cap = StrokeCap.Round))
        drawLine(ink, Offset(30f * unit, 48f * unit), Offset(70f * unit, 48f * unit),
            9f * unit, StrokeCap.Round)
        drawCircle(live, 6f * unit, Offset(78f * unit, 18f * unit))
    }
}

@Composable private fun CodeScreen(state: AuthUiState, onCode: (String) -> Unit, onVerify: () -> Unit, onBack: () -> Unit, onResend: () -> Unit) {
    val spacing = MaterialTheme.spacing
    Scaffold(topBar = { Box(Modifier.safeDrawingPadding().padding(top = spacing.lg)) {
        IconButton(onBack, Modifier.minimumInteractiveComponentSize()) { Icon(Icons.Outlined.ArrowBack, "Change email") }
    } }) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.xxl), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
            Spacer(Modifier.height(spacing.xxl))
            Surface(Modifier.size(spacing.heroIcon), shape = CircleShape,
                border = BorderStroke(spacing.xs / 4, MaterialTheme.colorScheme.tertiary)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(app.vent.ui.theme.EventIcons.mailOpen), "Email sent",
                        tint = if (state.error == null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error)
                }
            }
            Text(if (state.localAttendee) "Enter your code" else "Check your email", style = MaterialTheme.typography.headlineLarge)
            Text(
                if (state.localAttendee) "Enter the six-digit on-device code."
                else "If ${state.email} is registered, we sent a 6-digit code. It expires in 10 minutes.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SignInCodeField(state.code, onCode, enabled = !state.loading, isError = state.error != null)
            state.error?.let { ErrorText(it) }
            Button(onVerify, Modifier.fillMaxWidth().height(spacing.action), enabled = state.code.length == 6 && !state.loading) { if (state.loading) CircularProgressIndicator(Modifier.size(spacing.xl), strokeWidth = spacing.xs) else Text("Verify") }
            if (state.code.length < 6) Text("Enter all six digits to continue.",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!state.localAttendee) {
                TextButton(onResend, enabled = !state.loading && state.resendSeconds == 0) { Text(if (state.resendSeconds > 0) "Resend code in 0:${state.resendSeconds.toString().padStart(2, '0')}" else "Send a new code") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Wrong email?"); TextButton(onBack) { Text("Change it") } }
            Spacer(Modifier.height(spacing.xxl))
        }
    }
}

@Composable
private fun SignInCodeField(value: String, onValueChange: (String) -> Unit, enabled: Boolean, isError: Boolean) {
    val spacing = MaterialTheme.spacing
    val scheme = MaterialTheme.colorScheme
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        modifier = Modifier.widthIn(max = spacing.compactBreakpoint).fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = "Six digit sign-in code" },
        decorationBox = { inner ->
            Box {
                Row(Modifier.fillMaxWidth().clearAndSetSemantics {},
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    repeat(6) { index ->
                        val active = focused && index == value.length.coerceAtMost(5)
                        Surface(
                            Modifier.weight(1f).height(spacing.touch),
                            shape = MaterialTheme.shapes.small,
                            color = if (active) scheme.secondary.copy(alpha = .12f) else scheme.surface,
                            border = BorderStroke(spacing.xs / 4, when {
                                isError -> scheme.error
                                active -> scheme.secondary
                                else -> scheme.outline
                            }),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(value.getOrNull(index)?.toString().orEmpty(), style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                }
                Box(Modifier.matchParentSize().alpha(0f)) { inner() }
            }
        },
    )
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
@Preview @Composable private fun EmailLoadingPreview() { VentTheme { EmailScreen(AuthUiState(loading = true), {}, {}) } }
@Preview @Composable private fun CodeLightPreview() { VentTheme(darkTheme = false) { CodeScreen(AuthUiState(step = AuthStep.CODE, code = "123"), {}, {}, {}, {}) } }
@Preview @Composable private fun CodeErrorLightPreview() { VentTheme(darkTheme = false) { CodeScreen(AuthUiState(step = AuthStep.CODE, error = "That code didn't match. Try again or request a new one."), {}, {}, {}, {}) } }
@Preview @Composable private fun EmailErrorDarkPreview() { VentTheme(darkTheme = true) { EmailScreen(AuthUiState(email = "bad", error = "That email doesn't look right."), {}, {}) } }
@Preview @Composable private fun CodePreview() { VentTheme { CodeScreen(AuthUiState(step = AuthStep.CODE, email = "ada@company.com", code = "123"), {}, {}, {}, {}) } }
@Preview @Composable private fun CodeErrorDarkPreview() { VentTheme(darkTheme = true) { CodeScreen(AuthUiState(step = AuthStep.CODE, email = "ada@company.com", error = "That code isn't right. 3 tries left."), {}, {}, {}, {}) } }
