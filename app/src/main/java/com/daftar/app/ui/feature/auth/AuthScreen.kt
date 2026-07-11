package com.daftar.app.ui.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.daftar.app.domain.repository.AuthResult
import com.daftar.app.domain.usecase.LoginUserUseCase
import com.daftar.app.domain.usecase.RegisterUserUseCase
import com.daftar.app.ui.common.MonoLabel
import com.daftar.app.ui.common.SubmitButton
import com.daftar.app.ui.common.ToastCenter
import com.daftar.app.ui.common.ToastIcon
import com.daftar.app.ui.common.dashedBorder
import com.daftar.app.ui.theme.DaftarColors
import com.daftar.app.ui.theme.Fraunces
import com.daftar.app.ui.theme.Inter
import com.daftar.app.ui.theme.JetBrainsMono
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthMode { LOGIN, SIGNUP }

data class AuthUiState(
    val mode: AuthMode = AuthMode.LOGIN,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val showPassword: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
) {
    val canSubmit: Boolean
        get() = !busy && email.isNotBlank() && password.isNotBlank() &&
            (mode == AuthMode.LOGIN || name.isNotBlank())
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val loginUser: LoginUserUseCase,
    private val registerUser: RegisterUserUseCase,
    private val toastCenter: ToastCenter,
) : ViewModel() {

    private val state = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = state.asStateFlow()

    fun setName(value: String) = state.update { it.copy(name = value, error = null) }
    fun setEmail(value: String) = state.update { it.copy(email = value, error = null) }
    fun setPassword(value: String) = state.update { it.copy(password = value, error = null) }
    fun togglePasswordVisibility() = state.update { it.copy(showPassword = !it.showPassword) }

    /** Switch login <-> signup, keeping the typed email like the prototype. */
    fun switchMode(mode: AuthMode) = state.update { AuthUiState(mode = mode, email = it.email) }

    fun submit() {
        val form = state.value
        if (!form.canSubmit) return
        state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = when (form.mode) {
                AuthMode.LOGIN -> loginUser(form.email, form.password)
                AuthMode.SIGNUP -> registerUser(form.name, form.email, form.password)
            }
            when (result) {
                is AuthResult.Success -> {
                    // The session flow flips the auth gate; just greet the saraf.
                    state.value = AuthUiState()
                    toastCenter.show(
                        when (form.mode) {
                            AuthMode.LOGIN -> "Welcome back, ${result.user.firstName}"
                            AuthMode.SIGNUP -> "Account created · ${result.user.firstName}"
                        },
                        ToastIcon.CHECK,
                    )
                }
                is AuthResult.Failure -> state.update {
                    it.copy(
                        busy = false,
                        error = result.message,
                        // The prototype clears the password after a failed sign-in.
                        password = if (form.mode == AuthMode.LOGIN) "" else it.password,
                    )
                }
            }
        }
    }
}

/** Login / signup gate, mirroring the prototype's auth screens. */
@Composable
fun AuthScreen(viewModel: AuthViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isSignup = state.mode == AuthMode.SIGNUP

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DaftarColors.Paper)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 22.dp),
    ) {
        // Header — logo mark, title, bilingual subtitle
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp, bottom = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(DaftarColors.Ink),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isSignup) Icons.Rounded.PersonAdd else Icons.Rounded.Key,
                    contentDescription = null,
                    tint = DaftarColors.GoldSoft,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = if (isSignup) "Open your Daftar" else "Welcome back",
                style = TextStyle(
                    fontFamily = Fraunces,
                    fontWeight = FontWeight.Medium,
                    fontSize = 26.sp,
                    color = DaftarColors.Ink,
                ),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (isSignup) "Create your saraf account · نوی حساب" else "Sign in to your Daftar · بېرته راشئ",
                style = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.sp, color = DaftarColors.Muted),
            )
        }

        LocalAccountsBanner()
        Spacer(Modifier.height(14.dp))

        state.error?.let { error ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DaftarColors.Red.copy(alpha = 0.08f))
                    .border(1.dp, DaftarColors.Red.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.Shield, contentDescription = null, tint = DaftarColors.Red, modifier = Modifier.size(13.dp))
                Text(
                    text = error,
                    style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = DaftarColors.Red),
                )
            }
            Spacer(Modifier.height(14.dp))
        }

        if (isSignup) {
            AuthField(
                label = "Your name · ستاسو نوم",
                icon = Icons.Rounded.Person,
                value = state.name,
                onValueChange = viewModel::setName,
                placeholder = "e.g. Haji Rahmat",
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next,
                ),
            )
            Spacer(Modifier.height(14.dp))
        }

        AuthField(
            label = "Email · بریښنالیک",
            icon = Icons.Rounded.Mail,
            value = state.email,
            onValueChange = viewModel::setEmail,
            placeholder = "you@example.com",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
        )
        Spacer(Modifier.height(14.dp))

        AuthField(
            label = "Password · پټنوم",
            icon = Icons.Rounded.Lock,
            value = state.password,
            onValueChange = viewModel::setPassword,
            placeholder = if (isSignup) "At least 6 characters" else "Enter password",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
            visualTransformation = if (state.showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailing = {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .clickable { viewModel.togglePasswordVisibility() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (state.showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = "Toggle password",
                        tint = DaftarColors.Muted,
                        modifier = Modifier.size(15.dp),
                    )
                }
            },
            hint = if (isSignup) "Minimum 6 characters" else null,
        )

        Spacer(Modifier.height(18.dp))
        SubmitButton(
            label = when {
                state.busy && isSignup -> "Creating…"
                state.busy -> "Signing in…"
                isSignup -> "Create account · جوړ کړئ"
                else -> "Sign in · ننوځئ"
            },
            icon = if (isSignup) Icons.Rounded.PersonAdd else Icons.Rounded.Key,
            enabled = state.canSubmit,
            onClick = viewModel::submit,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isSignup) "Already have an account?" else "No account yet?",
                style = TextStyle(fontFamily = Inter, fontSize = 12.sp, color = DaftarColors.Muted),
            )
            Text(
                text = if (isSignup) " Sign in" else " Create one",
                style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DaftarColors.Copper),
                modifier = Modifier.clickable {
                    viewModel.switchMode(if (isSignup) AuthMode.LOGIN else AuthMode.SIGNUP)
                },
            )
        }
    }
}

/** Gold dashed notice — accounts live on this device, there is no backend yet. */
@Composable
private fun LocalAccountsBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .dashedBorder(DaftarColors.Gold.copy(alpha = 0.4f), 1.dp, 10.dp)
            .background(DaftarColors.Gold.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Rounded.Shield, contentDescription = null, tint = DaftarColors.Gold, modifier = Modifier.size(14.dp))
        Column {
            Text(
                text = "LOCAL ACCOUNTS",
                style = TextStyle(
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    letterSpacing = 0.25.em,
                    color = DaftarColors.Gold,
                ),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Accounts are stored on this device only.",
                style = TextStyle(fontFamily = Inter, fontSize = 10.sp, color = DaftarColors.Muted),
            )
        }
    }
}

/** Labelled auth input matching the prototype's bordered field, copper on focus. */
@Composable
private fun AuthField(
    label: String,
    icon: ImageVector,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
    hint: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(bottom = 6.dp),
        ) {
            Icon(icon, contentDescription = null, tint = DaftarColors.Muted, modifier = Modifier.size(11.dp))
            MonoLabel(label, fontSize = 10, letterSpacing = 0.15)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(11.dp))
                .background(if (focused) DaftarColors.Paper else DaftarColors.PaperSoft)
                .border(
                    1.5.dp,
                    if (focused) DaftarColors.Copper else DaftarColors.LineStrong,
                    RoundedCornerShape(11.dp),
                )
                .padding(start = 14.dp, end = if (trailing != null) 4.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 13.dp),
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = TextStyle(fontFamily = Inter, fontSize = 14.sp, color = DaftarColors.MutedLight),
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = TextStyle(
                        fontFamily = Inter,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = DaftarColors.Ink,
                    ),
                    singleLine = true,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    visualTransformation = visualTransformation,
                    interactionSource = interactionSource,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            trailing?.invoke()
        }
        if (hint != null) {
            Text(
                text = hint,
                style = TextStyle(
                    fontFamily = JetBrainsMono,
                    fontSize = 10.sp,
                    letterSpacing = 0.05.em,
                    color = DaftarColors.Muted,
                ),
                textAlign = TextAlign.Start,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
    }
}
