package com.example.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnSipConfigTest {
    @Test
    fun easybellDefaults_arePreparedWithoutSecrets() {
        val config = OwnSipConfig()
        assertEquals("voip.easybell.de", config.registrar)
        assertEquals(5060, config.port)
        assertEquals("UDP", config.transport)
        assertTrue(config.isComplete().not())
        assertTrue(config.password.isEmpty())
    }
}
