package com.example.agent

import android.net.sip.SipRegistrationListener
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnSipEngineRegistrationApiTest {
    @Test
    fun registration_listener_uses_the_android_sip_callback_contract() {
        val listener = object : SipRegistrationListener {
            override fun onRegistering(localProfileUri: String?) = Unit
            override fun onRegistrationDone(localProfileUri: String?, expiryTime: Long) = Unit
            override fun onRegistrationFailed(
                localProfileUri: String?,
                errorCode: Int,
                errorMessage: String?
            ) = Unit
        }

        assertTrue(listener is SipRegistrationListener)
    }
}
