package com.example.homesip

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class HomeSipCallUiInfo(
    val phone: String = "",
    val contactName: String? = null,
    val smartCall: Boolean = false
)

/**
 * UI-only state for the in-app SIP call screen.
 * This object never touches SIP, Linphone, TLS, SRTP or call lifecycle state.
 */
object HomeSipCallUiState {
    private val _info = MutableStateFlow(HomeSipCallUiInfo())
    val info: StateFlow<HomeSipCallUiInfo> = _info

    fun prepare(phone: String, contactName: String? = null, smartCall: Boolean = false) {
        _info.value = HomeSipCallUiInfo(
            phone = phone.trim(),
            contactName = contactName?.trim()?.takeIf { it.isNotBlank() },
            smartCall = smartCall
        )
    }
}
