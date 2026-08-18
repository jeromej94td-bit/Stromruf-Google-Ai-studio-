package com.example.ui

import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.EnergyGold
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.VoltBlue
import com.example.util.SupabaseAuthClient
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onAuthSuccess: (token: String, email: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var isSignUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showGoogleWebView by remember { mutableStateOf(false) }

    val backgroundBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF030E0A), // Extremely deep green-charcoal
                Color(0xFF070B0A), // Deep dark black
                Color(0xFF040605)  // Terminal dark
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DarkSurface)
                .border(1.dp, EnergyGold.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Branding / Logo
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .background(Color(0xFF031410), RoundedCornerShape(32.dp))
                        .border(2.dp, Color(0xFF00FF87), RoundedCornerShape(32.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            painter = painterResource(id = com.example.R.drawable.ic_launcher_background),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp))
                        )
                        Image(
                            painter = painterResource(id = com.example.R.drawable.ic_launcher_foreground),
                            contentDescription = "Stromruf Logo",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "⚡ STROMRUF ⚡",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00FF87),
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isSignUp) "Neues Konto erstellen" else "Willkommen zurück",
                    fontSize = 14.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Email Input
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; errorMessage = "" },
                label = { Text("E-Mail-Adresse", color = Color(0xFF94A3B8)) },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = EnergyGold) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EnergyGold,
                    unfocusedBorderColor = Color(0xFF1C2C27),
                    cursorColor = EnergyGold,
                    focusedLabelColor = EnergyGold
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )

            // Password Input
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; errorMessage = "" },
                label = { Text("Passwort", color = Color(0xFF94A3B8)) },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = EnergyGold) },
                visualTransformation = PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EnergyGold,
                    unfocusedBorderColor = Color(0xFF1C2C27),
                    cursorColor = EnergyGold,
                    focusedLabelColor = EnergyGold
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )

            // Confirm Password Input (only in SignUp mode)
            AnimatedVisibility(visible = isSignUp) {
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; errorMessage = "" },
                    label = { Text("Passwort bestätigen", color = Color(0xFF94A3B8)) },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = EnergyGold) },
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EnergyGold,
                        unfocusedBorderColor = Color(0xFF1C2C27),
                        cursorColor = EnergyGold,
                        focusedLabelColor = EnergyGold
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Error Display
            if (errorMessage.isNotEmpty()) {
                Text(
                    text = errorMessage,
                    color = Color.Red,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Main Action Button (Sign In / Sign Up)
            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        errorMessage = "Bitte füllen Sie alle Felder aus."
                        return@Button
                    }
                    if (isSignUp && password != confirmPassword) {
                        errorMessage = "Die Passwörter stimmen nicht überein."
                        return@Button
                    }
                    
                    loading = true
                    errorMessage = ""
                    coroutineScope.launch {
                        val result = if (isSignUp) {
                            SupabaseAuthClient.signUp(email, password)
                        } else {
                            SupabaseAuthClient.signIn(email, password)
                        }

                        loading = false
                        when (result) {
                            is SupabaseAuthClient.AuthResult.Success -> {
                                SupabaseAuthClient.saveSession(context, result.token, result.refreshToken, result.email)
                                onAuthSuccess(result.token, result.email)
                            }
                            is SupabaseAuthClient.AuthResult.Error -> {
                                errorMessage = result.message
                            }
                        }
                    }
                },
                enabled = !loading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = EnergyGold,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (loading) {
                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                } else {
                    Text(
                        text = if (isSignUp) "Registrieren" else "Anmelden",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Divider "or"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f).height(1.dp).background(Color(0xFF1C2C27)))
                Text(
                    text = "oder",
                    color = Color(0xFF64748B),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Box(modifier = Modifier.weight(1f).height(1.dp).background(Color(0xFF1C2C27)))
            }

            // Google Login Button
            OutlinedButton(
                onClick = { showGoogleWebView = true },
                shape = RoundedCornerShape(8.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // SVG path Google G logo representation
                    Text("G  ", fontWeight = FontWeight.Black, color = VoltBlue, fontSize = 18.sp)
                    Text(
                        text = "Mit Google anmelden",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Developer Bypass Button
            Button(
                onClick = {
                    onAuthSuccess("developer-bypass-token", "developer@stromruf.de")
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1E293B),
                    contentColor = Color(0xFF00FF87)
                ),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00FF87).copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🛠️  ", fontSize = 16.sp)
                    Text(
                        text = "Developer Login (Schnellstart)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Footer Switch Link
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isSignUp) "Bereits ein Konto? " else "Noch kein Konto? ",
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp
                )
                Text(
                    text = if (isSignUp) "Anmelden" else "Registrieren",
                    color = EnergyGold,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        isSignUp = !isSignUp
                        errorMessage = ""
                    }
                )
            }
        }
    }

    // Google OAuth webview dialog
    fun completeGoogleLogin(url: String): Boolean {
        if (!url.contains("#access_token=")) return false

        val parameters = url.substringAfter("#")
            .split("&")
            .mapNotNull { value ->
                val separator = value.indexOf('=')
                if (separator <= 0) null
                else value.substring(0, separator) to
                    android.net.Uri.decode(value.substring(separator + 1))
            }
            .toMap()

        val accessToken = parameters["access_token"].orEmpty()
        if (accessToken.isBlank()) {
            errorMessage = "Google-Anmeldung lieferte keine gültige Sitzung."
            return false
        }

        val refreshToken = parameters["refresh_token"].orEmpty()
        val sessionEmail =
            SupabaseAuthClient.getTokenClaim(accessToken, "email")
                ?: "Google User"

        SupabaseAuthClient.saveSession(
            context,
            accessToken,
            refreshToken,
            sessionEmail
        )

        onAuthSuccess(accessToken, sessionEmail)
        showGoogleWebView = false
        return true
    }

    if (showGoogleWebView) {
        Dialog(
            onDismissRequest = { showGoogleWebView = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.0.0 Mobile Safari/537.36"
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val url = request?.url?.toString() ?: ""
                                    if (completeGoogleLogin(url)) {
                                        view?.stopLoading()
                                        return true
                                    }
                                    return false
                                }

                                override fun onPageStarted(
                                    view: WebView?,
                                    url: String?,
                                    favicon: android.graphics.Bitmap?
                                ) {
                                    super.onPageStarted(view, url, favicon)
                                    val currentUrl = url ?: ""
                                    if (completeGoogleLogin(currentUrl)) {
                                        view?.stopLoading()
                                    }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    val currentUrl = url ?: ""
                                    completeGoogleLogin(currentUrl)
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    val url = request?.url?.toString() ?: ""
                                    if (url.contains("localhost") || url.contains("#access_token=")) {
                                        // Ignore connection errors to localhost redirect URI
                                        return
                                    }
                                    super.onReceivedError(view, request, error)
                                }
                            }
                            loadUrl("https://yepluyipizbbrgoffqdq.supabase.co/auth/v1/authorize?provider=google")
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Floating close button
                IconButton(
                    onClick = { showGoogleWebView = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Abbrechen",
                        tint = Color.White
                    )
                }
            }
        }
    }
}
