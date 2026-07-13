package com.daftar.app.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class ToastIcon {
    CHECK, CROSS, MINUS, SHIELD, SEND, MESSAGE, PHONE, PRINTER, COPY, DOWNLOAD, REFRESH,
    SCALE, HASH, PERSON_ADD, ARROW_UP, ARROW_DOWN, TREND_UP, TREND_DOWN, LOGOUT;

    val vector: ImageVector
        get() = when (this) {
            CHECK -> Icons.Rounded.Check
            CROSS -> Icons.Rounded.Close
            MINUS -> Icons.Rounded.Remove
            SHIELD -> Icons.Rounded.Shield
            SEND -> Icons.AutoMirrored.Rounded.Send
            MESSAGE -> Icons.Rounded.ChatBubble
            PHONE -> Icons.Rounded.Phone
            PRINTER -> Icons.Rounded.Print
            COPY -> Icons.Rounded.ContentCopy
            DOWNLOAD -> Icons.Rounded.Download
            REFRESH -> Icons.Rounded.Refresh
            SCALE -> Icons.Rounded.Balance
            HASH -> Icons.Rounded.Tag
            PERSON_ADD -> Icons.Rounded.PersonAdd
            ARROW_UP -> Icons.Rounded.ArrowUpward
            ARROW_DOWN -> Icons.Rounded.ArrowDownward
            TREND_UP -> Icons.AutoMirrored.Rounded.TrendingUp
            TREND_DOWN -> Icons.AutoMirrored.Rounded.TrendingDown
            LOGOUT -> Icons.AutoMirrored.Rounded.Logout
        }
}

data class ToastData(val message: String, val icon: ToastIcon = ToastIcon.CHECK)

/**
 * App-wide transient toast, styled like the prototype's ink pill. ViewModels
 * inject it; the root composable renders whatever is current.
 */
@Singleton
class ToastCenter @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var dismissJob: Job? = null

    private val _current = MutableStateFlow<ToastData?>(null)
    val current: StateFlow<ToastData?> = _current

    fun show(message: String, icon: ToastIcon = ToastIcon.CHECK) {
        _current.value = ToastData(message, icon)
        dismissJob?.cancel()
        dismissJob = scope.launch {
            delay(2400)
            _current.value = null
        }
    }
}

/** Lets deep composables raise toasts without threading callbacks everywhere. */
val LocalToaster = staticCompositionLocalOf<(String, ToastIcon) -> Unit> { { _, _ -> } }
