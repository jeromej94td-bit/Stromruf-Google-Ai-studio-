package com.example.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues

class MailAccountManager(private val context: Context) {
    private val settings = SecureIntegrationSettings(context)

    fun buildGmailLoginIntent(): Intent {
        val serviceConfig = AuthorizationServiceConfiguration(
            Uri.parse("https://accounts.google.com/o/oauth2/v2/auth"),
            Uri.parse("https://oauth2.googleapis.com/token")
        )
        val clientId = settings.getGoogleClientId() ?: MailOAuthConfig.GOOGLE_CLIENT_ID
        val request = AuthorizationRequest.Builder(
            serviceConfig,
            clientId,
            ResponseTypeValues.CODE,
            Uri.parse(MailOAuthConfig.REDIRECT_URI)
        )
            .setScopes(MailOAuthConfig.GOOGLE_SCOPES)
            .setPrompt("select_account")
            .build()

        return AuthorizationService(context).getAuthorizationRequestIntent(request)
    }

    fun buildOutlookLoginIntent(): Intent {
        val serviceConfig = AuthorizationServiceConfiguration(
            Uri.parse("https://login.microsoftonline.com/common/oauth2/v2.0/authorize"),
            Uri.parse("https://login.microsoftonline.com/common/oauth2/v2.0/token")
        )
        val clientId = settings.getMicrosoftClientId() ?: MailOAuthConfig.MICROSOFT_CLIENT_ID
        val request = AuthorizationRequest.Builder(
            serviceConfig,
            clientId,
            ResponseTypeValues.CODE,
            Uri.parse(MailOAuthConfig.REDIRECT_URI)
        )
            .setScopes(MailOAuthConfig.MICROSOFT_SCOPES)
            .setPrompt("select_account")
            .build()

        return AuthorizationService(context).getAuthorizationRequestIntent(request)
    }

    fun disconnectGmail() = settings.clearGoogleTokens()
    fun disconnectOutlook() = settings.clearMicrosoftTokens()

    fun isGmailConnected(): Boolean = settings.getGoogleAccessToken() != null
    fun isOutlookConnected(): Boolean = settings.getMicrosoftAccessToken() != null

    fun handleAuthorizationResponse(
        intent: Intent,
        provider: String,
        onDone: (success: Boolean, errorMessage: String?) -> Unit
    ) {
        val response = AuthorizationResponse.fromIntent(intent)
        val exception = AuthorizationException.fromIntent(intent)

        if (exception != null) {
            onDone(false, exception.errorDescription ?: exception.message)
            return
        }

        if (response == null) {
            onDone(false, "OAuth Login wurde abgebrochen oder ist unvollstaendig.")
            return
        }

        val authService = AuthorizationService(context)
        authService.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, tokenException ->
            if (tokenException != null) {
                onDone(false, tokenException.errorDescription ?: tokenException.message)
                authService.dispose()
                return@performTokenRequest
            }

            val accessToken = tokenResponse?.accessToken
            if (accessToken.isNullOrBlank()) {
                onDone(false, "Kein Access Token erhalten.")
                authService.dispose()
                return@performTokenRequest
            }

            when (provider.lowercase()) {
                "gmail", "google" -> settings.saveGoogleTokens(accessToken, tokenResponse.refreshToken)
                "outlook", "microsoft" -> settings.saveMicrosoftTokens(accessToken, tokenResponse.refreshToken)
                else -> {
                    onDone(false, "Unbekannter Mailanbieter: $provider")
                    authService.dispose()
                    return@performTokenRequest
                }
            }

            settings.saveDefaultMailProvider(provider.lowercase())
            onDone(true, null)
            authService.dispose()
        }
    }
}
