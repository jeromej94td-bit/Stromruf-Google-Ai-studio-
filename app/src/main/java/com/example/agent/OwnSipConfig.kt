package com.example.agent

import java.util.UUID

data class OwnSipConfig(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String = "Easybell",
    val username: String = "",
    val password: String = "",
    val registrar: String = "voip.easybell.de",
    val proxy: String = "voip.easybell.de",
    val port: Int = 5060,
    val transport: String = "UDP",
    val callerId: String = "",
    val recordingEnabled: Boolean = true
) {
    fun isComplete(): Boolean = username.isNotBlank() && password.isNotBlank() && registrar.isNotBlank()
}

