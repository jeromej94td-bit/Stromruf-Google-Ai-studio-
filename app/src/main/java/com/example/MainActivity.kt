package com.example

import com.example.util.CustomerNumberExtractor
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.media.RingtoneManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import android.content.Context
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontStyle
import androidx.lifecycle.lifecycleScope
import com.example.database.AppDatabase
import com.example.database.ContactEntity
import com.example.database.FollowUpEntity
import com.example.repository.StromrufRepository
import com.example.ui.design.pulsatingAura
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.LocalThemeConfig
import com.example.ui.theme.getThemeStyleConfig
import com.example.ui.screens.StromrufShell
import com.example.viewmodel.ActiveCall
import com.example.viewmodel.StromrufViewModel
import com.example.viewmodel.StromrufViewModelFactory
import com.example.viewmodel.WrapUpData
import com.example.util.ContactsUtil
import com.example.util.SecureIntegrationSettings
import com.example.util.TelegramClient
import com.example.util.OpenAiClient
import com.example.util.MailAccountManager
import com.example.util.SupabaseAuthClient
import com.example.util.SupabaseDbClient
import com.example.ui.screens.McpUrlRow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

private fun cleanPhoneNumber(phone: String): String {
    val sb = StringBuilder(phone.length)
    for (i in 0 until phone.length) {
        val c = phone[i]
        if (c in '0'..'9') {
            sb.append(c)
        }
    }
    return sb.toString()
}

fun normalizePhoneNumberFast(phone: String): String {
    val clean = cleanPhoneNumber(phone)
    if (clean.isEmpty()) return ""
    var res = clean
    if (res.startsWith("0049")) {
        res = res.substring(4)
    } else if (res.startsWith("49")) {
        res = res.substring(2)
    }
    var startIdx = 0
    while (startIdx < res.length && res[startIdx] == '0') {
        startIdx++
    }
    return if (startIdx > 0) res.substring(startIdx) else res
}

fun arePhoneNumbersMatching(p1: String, p2: String): Boolean {
    val clean1 = cleanPhoneNumber(p1)
    val clean2 = cleanPhoneNumber(p2)
    if (clean1.isEmpty() || clean2.isEmpty()) return false
    
    if (clean1 == clean2) return true
    
    fun normalize(s: String): String {
        var res = s
        if (res.startsWith("0049")) res = res.substring(4)
        else if (res.startsWith("49")) res = res.substring(2)
        while (res.startsWith("0")) {
            res = res.substring(1)
        }
        return res
    }
    
    val norm1 = normalize(clean1)
    val norm2 = normalize(clean2)
    if (norm1.isNotEmpty() && norm2.isNotEmpty() && norm1 == norm2) return true
    
    if (norm1.length >= 7 && norm2.length >= 7) {
        if (norm1.endsWith(norm2) || norm2.endsWith(norm1)) return true
    }
    return false
}

class MainActivity : ComponentActivity() {

    companion object {
        var isActivityResumed = false
    }

    private var clipboardListener: android.content.ClipboardManager.OnPrimaryClipChangedListener? = null
    private var isProgrammaticCopy = false
    private lateinit var mailAccountManager: com.example.util.MailAccountManager

    private suspend fun buildEffectiveHotBoxLists(
        context: android.content.Context,
        localDao: com.example.database.StromrufDao,
        existingLists: Collection<String>
    ): LinkedHashSet<String> {

        val result = linkedSetOf<String>()

        fun addClean(values: Iterable<String>) {
            values.forEach { value ->
                val clean = value.trim()
                if (clean.isNotEmpty()) {
                    result.add(clean)
                }
            }
        }

        // 1. Bereits in der App bekannte Listen behalten.
        addClean(existingLists)

        // 2. Explizite Hotbox-Listen aus Supabase laden.
        try {
            val remoteLists =
                com.example.util.SupabaseDbClient.fetchHotBoxLists(context)

            addClean(remoteLists)
        } catch (e: Exception) {
            android.util.Log.e(
                "HotBoxSync",
                "Hotbox list table could not be loaded",
                e
            )
        }

        // 3. WICHTIG:
        // Hotbox-Namen zusätzlich direkt aus den Kontakten rekonstruieren.
        // Dadurch erscheinen auch Hotboxen, die über MCP / ChatGPT /
        // Backend erstellt wurden, selbst wenn hot_box_lists im Android
        // Client wegen Cache/RLS/Sync noch nicht verfügbar ist.
        try {
            val contactLists = localDao
                .getAllContactsList()
                .asSequence()
                .filter { it.isHotBox }
                .mapNotNull {
                    it.hotBoxListName
                        ?.trim()
                        ?.takeIf { name -> name.isNotEmpty() }
                }
                .distinct()
                .toList()

            addClean(contactLists)
        } catch (e: Exception) {
            android.util.Log.e(
                "HotBoxSync",
                "Could not derive Hotbox lists from contacts",
                e
            )
        }

        return result
    }
    private val viewModel: StromrufViewModel by viewModels {
        StromrufViewModelFactory(StromrufRepository(this, AppDatabase.getDatabase(this).stromrufDao()))
    }

    private val alertReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == "com.example.ACTION_FOLLOW_UP_ALERT") {
                val id = intent.getStringExtra("FOLLOWUP_ID") ?: ""
                val name = intent.getStringExtra("CONTACT_NAME") ?: "Rückruf"
                val phone = intent.getStringExtra("CONTACT_PHONE") ?: ""
                if (phone.isNotEmpty()) {
                    viewModel.activeIncomingAlert.value = com.example.viewmodel.IncomingAlert(id, name, phone)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = android.content.IntentFilter("com.example.ACTION_FOLLOW_UP_ALERT")
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            alertReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(alertReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    
    @android.annotation.SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mailAccountManager = com.example.util.MailAccountManager(this)
        handleOAuthIntent(intent)
        enableEdgeToEdge()

        // Configure activity to show on top of lockscreen and turn screen on for incoming calls
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(android.content.Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        // Silence any ringing alarms when the user enters the app
        com.example.receiver.AlarmSoundPlayer.stopRinging()

        // AgentRuntime Initialisierung
        runCatching { com.example.agent.AgentRuntime.init(applicationContext) }

        // Ask for runtime permissions
        val permissionsToRequest = mutableListOf<String>()
        permissionsToRequest.add(android.Manifest.permission.RECORD_AUDIO)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            permissionsToRequest.add(android.Manifest.permission.BLUETOOTH_CONNECT)
        }
        permissionsToRequest.add(android.Manifest.permission.READ_CONTACTS)
        permissionsToRequest.add(android.Manifest.permission.READ_CALL_LOG)
        permissionsToRequest.add(android.Manifest.permission.CALL_PHONE)
        permissionsToRequest.add(android.Manifest.permission.READ_PHONE_STATE)

        requestPermissions(permissionsToRequest.toTypedArray(), 101)

        // Register the background schedule receiver to tie VM persistence commands to OS Alarm services
        viewModel.onScheduleAlarm = { id, name, phone, dueAt ->
            com.example.receiver.FollowUpAlarmScheduler.scheduleAlarm(this, id, name, phone, dueAt)
        }
        viewModel.onCancelAlarm = { id ->
            com.example.receiver.FollowUpAlarmScheduler.cancelAlarm(this, id)
        }

        // Check if application was launched from a notification "Anrufen" action click
        intent?.let { checkCallNotificationIntent(it) }

        // Observe toast trigger events from ViewModel / remote API triggers
        lifecycleScope.launch {
            viewModel.showToastTrigger.collect { message ->
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            }
        }

        // ---- TELEFONIE: Kern-Integration — echten Anruf über das Android-System starten ----
        // Ohne diesen Block zeigt die App nur die Anruf-Maske, wählt aber NICHT.
        lifecycleScope.launch {
            viewModel.dialIntentTrigger.collectLatest { phone ->
                try {
                    // Convert international format (+49...) to standard German local format (0...) for maximum compatibility
                    // with Windows Smartphone-Link / Link to Windows and legacy PBX/VoIP routing.
                    var cleanPhone = phone.replace("[^\\d+]".toRegex(), "")
                    if (cleanPhone.startsWith("+49")) {
                        cleanPhone = "0" + cleanPhone.substring(3)
                    } else if (cleanPhone.startsWith("0049")) {
                        cleanPhone = "0" + cleanPhone.substring(4)
                    } else if (cleanPhone.startsWith("49") && !cleanPhone.startsWith("+")) {
                        cleanPhone = "0" + cleanPhone.substring(2)
                    }
                    if (cleanPhone.startsWith("049") && cleanPhone.length > 3) {
                        cleanPhone = "0" + cleanPhone.substring(3)
                    }
                    
                    android.util.Log.d("MainActivity", "Dialing number formatted: original=$phone, dialed=$cleanPhone")
                    
                    val uri = android.net.Uri.parse("tel:$cleanPhone")
                    val telecomManager = getSystemService(android.content.Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
                    
                    if (checkSelfPermission(android.Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        if (telecomManager != null && com.example.util.ContactsUtil.isDefaultDialer(this@MainActivity)) {
                            // Als Standard-Dialer: Anruf direkt über TelecomManager platzieren (verhindert Auswahldialog)
                            telecomManager.placeCall(uri, null)
                        } else {
                            // Wenn nicht Standard-Dialer: ACTION_CALL verwenden (System übernimmt)
                            val callIntent = android.content.Intent(android.content.Intent.ACTION_CALL, uri).apply {
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(callIntent)
                        }
                    } else {
                        // Ohne Berechtigung: ACTION_DIAL verwenden (öffnet Wählpad)
                        val callIntent = android.content.Intent(android.content.Intent.ACTION_DIAL, uri).apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(callIntent)
                    }
                } catch (e: Exception) {
                    Toast.makeText(
                        this@MainActivity,
                        "Anruf konnte nicht gestartet werden: ${e.localizedMessage}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        // Clipboard sync for Hotbox contacts
        lifecycleScope.launch {
            viewModel.copyToClipboardTrigger.collectLatest { digitsOnly ->
                try {
                    isProgrammaticCopy = true
                    val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Kundennummer", digitsOnly)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(
                        this@MainActivity,
                        "Kundennummer $digitsOnly kopiert! 📋",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    isProgrammaticCopy = false
                    android.util.Log.e("MainActivity", "Failed to copy Kundennummer to clipboard: ${e.localizedMessage}")
                }
            }
        }

        setContent {
            val context = LocalContext.current
            val prefs = remember { context.getSharedPreferences("stromruf_prefs", Context.MODE_PRIVATE) }
            var appThemeSetting by remember { mutableStateOf("dark") }
            var backgroundStyleSetting by remember { mutableStateOf("platinum_metal") }
            var screenBrightnessSetting by remember { mutableStateOf(-1f) }
            var clipboardBubblePositionSetting by remember { mutableStateOf("bottom_left") }
            var clipboardBubbleOnLocalCopySetting by remember { mutableStateOf(false) }
            var supabaseToken by remember { mutableStateOf(com.example.util.SupabaseAuthClient.getSessionToken(context)) }
            var hotBoxCloudReady by remember { mutableStateOf(false) }
            
            LaunchedEffect(Unit) {
                appThemeSetting = prefs.getString("app_theme", "dark") ?: "dark"
                backgroundStyleSetting = prefs.getString("background_style", "platinum_metal") ?: "platinum_metal"
                screenBrightnessSetting = prefs.getFloat("screen_brightness", -1f)
                clipboardBubblePositionSetting = prefs.getString("clipboard_bubble_position", "bottom_left") ?: "bottom_left"
                clipboardBubbleOnLocalCopySetting = prefs.getBoolean("clipboard_bubble_on_local_copy", false)
                
                // Load Hotbox lists
                val savedOrdered = prefs.getString("hotbox_campaign_lists_ordered", null)
                var savedLists = if (savedOrdered != null) {
                    savedOrdered.split(",").filter { it.isNotBlank() && it != "Standard Hotbox" && it != "Passive Hotbox" }.toSet()
                } else {
                    prefs.getStringSet("hotbox_campaign_lists", emptySet())?.filter { it != "Standard Hotbox" && it != "Passive Hotbox" }?.toSet() ?: emptySet()
                }
                if (savedLists.isEmpty()) {
                    savedLists = setOf("Hotbox")
                }

                // Load last selected list
                val savedSelectedStr = prefs.getString("last_selected_hotbox_lists", null)
                val savedSelected = if (!savedSelectedStr.isNullOrBlank()) {
                    savedSelectedStr.split(",").filter { it.isNotBlank() && it != "Standard Hotbox" && it != "Passive Hotbox" && it in savedLists }.toSet()
                } else {
                    emptySet()
                }

                viewModel.initializeHotBoxLists(savedLists, savedSelected)
            }
            
            val hotBoxLists by viewModel.hotBoxLists.collectAsState()
            LaunchedEffect(hotBoxLists, hotBoxCloudReady) {
                val orderedListsStr = hotBoxLists.joinToString(",")
                prefs.edit()
                    .putStringSet("hotbox_campaign_lists", hotBoxLists)
                    .putString("hotbox_campaign_lists_ordered", orderedListsStr)
                    .apply()
            }
            
            LaunchedEffect(supabaseToken) {
                if (supabaseToken != null) {
                    val activeToken = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        if (com.example.util.SupabaseAuthClient.isTokenExpired(supabaseToken!!)) {
                            com.example.util.SupabaseAuthClient.refreshSession(context) ?: supabaseToken
                        } else {
                            supabaseToken
                        }
                    }
                    if (activeToken != supabaseToken) {
                        supabaseToken = activeToken
                    }
                    
                    viewModel.startCommandPolling(context)

                    val localDao = com.example.database.AppDatabase.getDatabase(context).stromrufDao()
                    val success = com.example.util.SupabaseDbClient.syncAllDown(
                        context = context,
                        localDao = localDao
                    )
                    if (!success) {
                        android.widget.Toast.makeText(
                            context,
                            "Cloud-Synchronisierung fehlgeschlagen. Lokale Daten bleiben erhalten.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }

                    // --- Load Hotbox lists from Supabase & contacts after successful sync ---
                    val effectiveLists = buildEffectiveHotBoxLists(
                        context = context,
                        localDao = localDao,
                        existingLists = viewModel.hotBoxLists.value
                    )
                    if (effectiveLists.isNotEmpty()) {
                        viewModel.setHotBoxLists(effectiveLists)
                    }
                    hotBoxCloudReady = true

                    while (true) {
                        kotlinx.coroutines.delay(15_000)
                        com.example.util.SupabaseDbClient.refreshLocalCache(
                            context,
                            localDao
                        )

                        // Poll Hotbox lists every 15 seconds
                        val latestEffectiveLists = buildEffectiveHotBoxLists(
                            context = context,
                            localDao = localDao,
                            existingLists = viewModel.hotBoxLists.value
                        )
                        if (
                            latestEffectiveLists.isNotEmpty() &&
                            latestEffectiveLists.toSet() != viewModel.hotBoxLists.value.toSet()
                        ) {
                            viewModel.setHotBoxLists(latestEffectiveLists)
                        }
                    }
                } else {
                    hotBoxCloudReady = false
                }
            }
            
            @Suppress("ContextCastToActivity")
            val activity = LocalContext.current as? android.app.Activity
            LaunchedEffect(screenBrightnessSetting) {
                activity?.window?.attributes = activity?.window?.attributes?.apply {
                    screenBrightness = if (screenBrightnessSetting < 0) {
                        android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    } else {
                        screenBrightnessSetting
                    }
                }
            }
            
            val isDarkTheme = when (appThemeSetting) {
                "light" -> false
                "dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            
            MyApplicationTheme(darkTheme = isDarkTheme, themeStyle = backgroundStyleSetting) {
                if (supabaseToken == null) {
                    com.example.ui.AuthScreen(
                        onAuthSuccess = { token, email ->
                            supabaseToken = token
                        }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Scaffold(
                            modifier = Modifier.fillMaxSize()
                        ) { innerPadding ->
                            StromrufMainDashboard(
                                viewModel = viewModel,
                                modifier = Modifier.padding(innerPadding),
                                appTheme = appThemeSetting,
                                onThemeChange = { newTheme ->
                                    appThemeSetting = newTheme
                                    prefs.edit().putString("app_theme", newTheme).apply()
                                },
                                backgroundStyle = backgroundStyleSetting,
                                onBackgroundStyleChange = { newStyle ->
                                    backgroundStyleSetting = newStyle
                                    prefs.edit().putString("background_style", newStyle).apply()
                                },
                                screenBrightness = screenBrightnessSetting,
                                onBrightnessChange = { newBrightness ->
                                    screenBrightnessSetting = newBrightness
                                    prefs.edit().putFloat("screen_brightness", newBrightness).apply()
                                },
                                clipboardBubblePosition = clipboardBubblePositionSetting,
                                onClipboardBubblePositionChange = { newPos ->
                                    clipboardBubblePositionSetting = newPos
                                    prefs.edit().putString("clipboard_bubble_position", newPos).apply()
                                },
                                clipboardBubbleOnLocalCopy = clipboardBubbleOnLocalCopySetting,
                                onClipboardBubbleOnLocalCopyChange = { enabled ->
                                    clipboardBubbleOnLocalCopySetting = enabled
                                    prefs.edit().putBoolean("clipboard_bubble_on_local_copy", enabled).apply()
                                },
                                onSignOut = {
                                    com.example.util.SupabaseAuthClient.clearSession(context)
                                    supabaseToken = null
                                }
                            )
                        }

                        val clipboardState by viewModel.clipboardBubbleState.collectAsState()
                        if (clipboardState != null) {
                            val state = clipboardState!!
                            val currentTheme = com.example.ui.theme.LocalThemeConfig.current
                            var bubbleOffsetX by remember(state) { mutableStateOf(0f) }
                            var bubbleOffsetY by remember(state) { mutableStateOf(0f) }
                            LaunchedEffect(state) {
                                kotlinx.coroutines.delay(15000L)
                                viewModel.clearClipboardBubble()
                            }

                            // Full screen background tap to dismiss
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable(
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        viewModel.clearClipboardBubble()
                                    }
                            )

                            Card(
                                modifier = Modifier
                                    .align(
                                        when (clipboardBubblePositionSetting) {
                                            "bottom_left" -> Alignment.BottomStart
                                            "bottom_right" -> Alignment.BottomEnd
                                            "bottom_center" -> Alignment.BottomCenter
                                            "top_left" -> Alignment.TopStart
                                            "top_right" -> Alignment.TopEnd
                                            "top_center" -> Alignment.TopCenter
                                            "center" -> Alignment.Center
                                            else -> Alignment.BottomCenter
                                        }
                                    )
                                    .padding(
                                        start = if (clipboardBubblePositionSetting == "bottom_left" || clipboardBubblePositionSetting == "top_left") 16.dp else 8.dp,
                                        end = if (clipboardBubblePositionSetting == "bottom_right" || clipboardBubblePositionSetting == "top_right") 16.dp else 8.dp,
                                        top = if (clipboardBubblePositionSetting.startsWith("top")) 96.dp else 8.dp,
                                        bottom = if (clipboardBubblePositionSetting.startsWith("bottom")) 96.dp else 8.dp
                                    )
                                    .offset { IntOffset(bubbleOffsetX.roundToInt(), bubbleOffsetY.roundToInt()) }
                                    .pointerInput(Unit) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            bubbleOffsetX += dragAmount.x
                                            bubbleOffsetY += dragAmount.y
                                        }
                                    }
                                    .wrapContentSize()
                                    .animateContentSize(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = currentTheme.cardBackground.copy(alpha = 0.95f)
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                                border = BorderStroke(1.2.dp, currentTheme.primaryColor)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 6.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (state.isCustomerNumber) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(currentTheme.primaryColor.copy(alpha = 0.15f))
                                                .clickable {
                                                    viewModel.clearClipboardBubble()
                                                    viewModel.openQuickSaveDialog(customerNo = state.text)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = "Kundennummer",
                                                tint = currentTheme.primaryColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        Column(
                                            modifier = Modifier.padding(horizontal = 2.dp),
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = "Kd-Nr: ${state.text}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        // Divider
                                        Box(
                                            modifier = Modifier
                                                .height(16.dp)
                                                .width(1.dp)
                                                .background(Color.White.copy(alpha = 0.15f))
                                        )

                                        // Speichern button
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(currentTheme.primaryColor)
                                                .clickable {
                                                    viewModel.clearClipboardBubble()
                                                    viewModel.openQuickSaveDialog(customerNo = state.text)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Speichern",
                                                tint = Color(0xFF0F172A),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else {
                                        // Call button (Green circle)
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF10B981))
                                                .clickable {
                                                    val phoneToCall = state.text
                                                    viewModel.clearClipboardBubble()
                                                    viewModel.initiateCall(phoneToCall, "Kunde aus Zwischenablage")
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Call,
                                                contentDescription = "Anrufen",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        // Text with phone number or prompt
                                        Column(
                                            modifier = Modifier.padding(horizontal = 2.dp),
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = "Anrufen: ${state.text}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        // Divider
                                        Box(
                                            modifier = Modifier
                                                .height(16.dp)
                                                .width(1.dp)
                                                .background(Color.White.copy(alpha = 0.15f))
                                        )

                                        // Speichern button
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(currentTheme.primaryColor.copy(alpha = 0.15f))
                                                .clickable {
                                                    viewModel.clearClipboardBubble()
                                                    viewModel.openQuickSaveDialog(phone = state.text)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PersonAdd,
                                                contentDescription = "Speichern",
                                                tint = currentTheme.primaryColor,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }

                                    // Divider
                                    Box(
                                        modifier = Modifier
                                            .height(16.dp)
                                            .width(1.dp)
                                            .background(Color.White.copy(alpha = 0.15f))
                                    )

                                    // Dismiss button
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.08f))
                                            .clickable {
                                                viewModel.clearClipboardBubble()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Schließen",
                                            tint = Color.White.copy(alpha = 0.8f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Top-right countdown circular timer
                        val saveNumberPhone by viewModel.saveNumberBubblePhone.collectAsState()
                        if (saveNumberPhone != null) {
                            val phoneToSave = saveNumberPhone!!
                            val currentTheme = com.example.ui.theme.LocalThemeConfig.current
                            var remainingTime by remember(phoneToSave) { mutableStateOf(7000L) }
                            LaunchedEffect(phoneToSave) {
                                val startTime = System.currentTimeMillis()
                                while (remainingTime > 0) {
                                    val elapsed = System.currentTimeMillis() - startTime
                                    remainingTime = (7000L - elapsed).coerceAtLeast(0L)
                                    kotlinx.coroutines.delay(16L) // ~60fps smooth animation
                                }
                                viewModel.clearSaveNumberBubble()
                            }

                            Card(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 110.dp, end = 16.dp)
                                    .wrapContentSize()
                                    .clickable {
                                        viewModel.clearSaveNumberBubble()
                                        viewModel.openQuickSaveDialog(phone = phoneToSave)
                                    },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = currentTheme.cardBackground
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                                border = BorderStroke(1.dp, currentTheme.primaryColor.copy(alpha = 0.8f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(
                                            progress = remainingTime / 7000f,
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.5.dp,
                                            color = currentTheme.primaryColor,
                                            trackColor = Color.White.copy(alpha = 0.1f)
                                        )
                                        Text(
                                            text = "${(remainingTime / 1000 + 1).coerceAtMost(7)}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                            color = Color.White
                                        )
                                    }

                                    Column(verticalArrangement = Arrangement.Center) {
                                        Text(
                                            text = "Kundennummer speichern?",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = phoneToSave,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = Color.White.copy(alpha = 0.7f)
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.1f))
                                            .clickable {
                                                viewModel.clearSaveNumberBubble()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Schließen",
                                            tint = Color.White,
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // QuickSave Dialog
                        val quickSaveState by viewModel.quickSaveDialogState.collectAsState()
                        if (quickSaveState != null) {
                            val qState = quickSaveState!!
                            val currentTheme = com.example.ui.theme.LocalThemeConfig.current
                            var localPhone by remember(qState.phone) { mutableStateOf(qState.phone) }
                            var localCustomerNo by remember(qState.customerNo) { mutableStateOf(qState.customerNo) }
                            var localName by remember(qState.name) { mutableStateOf(qState.name) }

                            LaunchedEffect(qState.phone) { localPhone = qState.phone }
                            LaunchedEffect(qState.customerNo) { localCustomerNo = qState.customerNo }
                            LaunchedEffect(qState.name) { localName = qState.name }

                            androidx.compose.ui.window.Dialog(
                                onDismissRequest = { viewModel.closeQuickSaveDialog() }
                            ) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    shape = RoundedCornerShape(24.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = currentTheme.cardBackground
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                                    border = BorderStroke(2.dp, currentTheme.primaryColor)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(currentTheme.primaryColor.copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PersonAdd,
                                                    contentDescription = null,
                                                    tint = currentTheme.primaryColor,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Column {
                                                Text(
                                                    text = "Kunde speichern 💾",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Kopieren Sie Kundennummer und Name nacheinander, um automatisch zu speichern!",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                    color = Color.White.copy(alpha = 0.6f)
                                                )
                                            }
                                        }

                                        OutlinedTextField(
                                            value = localPhone,
                                            onValueChange = { localPhone = it },
                                            label = { Text("Telefonnummer", color = Color.White.copy(alpha = 0.6f)) },
                                            textStyle = TextStyle(color = Color.White),
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = currentTheme.primaryColor,
                                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                                cursorColor = currentTheme.primaryColor
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        OutlinedTextField(
                                            value = localCustomerNo,
                                            onValueChange = { 
                                                localCustomerNo = it 
                                                viewModel.updateQuickSaveDialog(customerNo = it)
                                            },
                                            label = { Text("Kundennummer", color = Color.White.copy(alpha = 0.6f)) },
                                            textStyle = TextStyle(color = Color.White),
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = currentTheme.primaryColor,
                                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                                cursorColor = currentTheme.primaryColor
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        OutlinedTextField(
                                            value = localName,
                                            onValueChange = { 
                                                localName = it 
                                                viewModel.updateQuickSaveDialog(name = it)
                                            },
                                            label = { Text("Name des Kunden", color = Color.White.copy(alpha = 0.6f)) },
                                            textStyle = TextStyle(color = Color.White),
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = currentTheme.primaryColor,
                                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                                cursorColor = currentTheme.primaryColor
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Button(
                                                onClick = { viewModel.closeQuickSaveDialog() },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color.White.copy(alpha = 0.08f)
                                                ),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text("Abbrechen", color = Color.White)
                                            }

                                            Button(
                                                onClick = {
                                                    if (localName.isNotBlank() && localCustomerNo.isNotBlank()) {
                                                        viewModel.addManualContact(
                                                            name = localName,
                                                            phone = localPhone,
                                                            company = "Kd.-Nr: $localCustomerNo",
                                                            email = ""
                                                        )
                                                        viewModel.saveNeukunde(
                                                            customerNumber = localCustomerNo,
                                                            phone = localPhone,
                                                            customerName = localName
                                                        )
                                                        viewModel.closeQuickSaveDialog()
                                                        Toast.makeText(this@MainActivity, "Kunde $localName ($localCustomerNo) erfolgreich gespeichert! 💾", Toast.LENGTH_LONG).show()
                                                    } else {
                                                        Toast.makeText(this@MainActivity, "Bitte Name und Kundennummer eingeben!", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = currentTheme.primaryColor
                                                ),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text(
                                                    text = "Speichern",
                                                    color = Color(0xFF0F172A),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        com.example.service.DialerInCallService.isAppInForeground = true
        isActivityResumed = true
        // Silence alarms on app gain focus
        com.example.receiver.AlarmSoundPlayer.stopRinging()
        // Automatically check and record call duration on return
        checkAndAutoRecordCall()
        viewModel.syncSystemCallLogs(this)
        viewModel.isDefaultDialer.value = com.example.util.ContactsUtil.isDefaultDialer(this)

        // Clipboard observer setup
        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        if (clipboard != null) {
            val listener = android.content.ClipboardManager.OnPrimaryClipChangedListener {
                checkClipboardForPhoneNumber()
            }
            clipboardListener = listener
            clipboard.addPrimaryClipChangedListener(listener)
        }
        // Also check immediately upon resume (in case they copied while app was in background)
        window.decorView.postDelayed({
            checkClipboardForPhoneNumber()
        }, 500)
    }

    override fun onPause() {
        super.onPause()
        com.example.service.DialerInCallService.isAppInForeground = false
        isActivityResumed = false

        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        clipboardListener?.let {
            clipboard?.removePrimaryClipChangedListener(it)
        }
        clipboardListener = null
    }

    private fun isDeviceInActiveCall(): Boolean {
        try {
            // 1. InCallService
            if (com.example.service.DialerInCallService.activeCall.value != null) return true
            val activeState = com.example.service.DialerInCallService.activeCallState.value
            if (activeState == android.telecom.Call.STATE_ACTIVE ||
                activeState == android.telecom.Call.STATE_DIALING ||
                activeState == android.telecom.Call.STATE_CONNECTING ||
                activeState == android.telecom.Call.STATE_RINGING ||
                activeState == android.telecom.Call.STATE_HOLDING) {
                return true
            }

            // 2. TelecomManager
            val telecomManager = getSystemService(android.content.Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    if (telecomManager?.isInCall() == true || telecomManager?.isInManagedCall() == true) {
                        return true
                    }
                }
            }

            // 3. TelephonyManager
            val telephonyManager = getSystemService(android.content.Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
            if (telephonyManager != null && telephonyManager.callState != android.telephony.TelephonyManager.CALL_STATE_IDLE) {
                return true
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error checking active call state: ${e.localizedMessage}")
        }
        return false
    }

    private fun checkAndAutoRecordCall() {
        val activeCall = viewModel.activeCall.value ?: return
        
        // If simulation mode is active, the call is simulated entirely in the app UI, so do not auto-record upon app resume.
        if (viewModel.isSimulationModeEnabled.value) {
            return
        }

        // If device is in call, do NOT record it yet!
        if (isDeviceInActiveCall()) {
            android.util.Log.d("MainActivity", "checkAndAutoRecordCall: Device is still in an active call. Skipping auto-record.")
            return
        }

        val elapsedMs = System.currentTimeMillis() - activeCall.startTime
        if (elapsedMs < 4000) {
            // Der Anruf wurde gerade erst gestartet. Noch nicht erfassen!
            return
        }
        val elapsedSec = elapsedMs / 1000
        
        var wasAnswered: Boolean? = null
        var finalDurationSec = elapsedSec
        
        if (com.example.util.ContactsUtil.hasCallLogPermission(this)) {
            val recentCalls = com.example.util.ContactsUtil.readRecentCalls(this)
            val matchingCall = recentCalls.find { call ->
                val normalizedCallPhone = call.number.replace("[^0-9+]".toRegex(), "")
                val normalizedActivePhone = activeCall.phone.replace("[^0-9+]".toRegex(), "")
                val phoneMatches = (normalizedCallPhone.length >= 6 && normalizedActivePhone.length >= 6 && 
                                  (normalizedCallPhone.endsWith(normalizedActivePhone) || normalizedActivePhone.endsWith(normalizedCallPhone)))
                val timeMatches = call.date >= (activeCall.startTime - 60_000L)
                phoneMatches && timeMatches
            }
            if (matchingCall != null) {
                finalDurationSec = maxOf(elapsedSec, matchingCall.duration)
                if (matchingCall.duration > 0 || elapsedSec > 10) {
                    wasAnswered = true
                }
            } else if (elapsedSec > 10) {
                wasAnswered = true
            }
        } else if (elapsedSec > 10) {
            wasAnswered = true
        }
        
        val durationToSave = finalDurationSec
        
        viewModel.autoRecordActiveCall(durationToSave, wasAnswered)
        
        val durationLabel = if (durationToSave > 0) "$durationToSave Sek." else "Nicht erreicht"
        Toast.makeText(this, "Anruf automatisch erfasst! 🎯 ($durationLabel)", Toast.LENGTH_LONG).show()
    }

    private fun checkClipboardForPhoneNumber() {
        try {
            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager ?: return
            if (clipboard.hasPrimaryClip()) {
                val clipData = clipboard.primaryClip ?: return
                if (clipData.itemCount > 0) {
                    val text = clipData.getItemAt(0).text?.toString() ?: ""
                    val cleaned = text.trim()
                    if (cleaned.isNotEmpty()) {
                        // 1. Check if call mask (OngoingCallDialog) or active call is open/active
                        val isCallActive = (viewModel.activeCall.value != null) ||
                                           (com.example.service.DialerInCallService.activeCall.value != null) ||
                                           (com.example.service.DialerInCallService.activeCallNumber.value.isNotBlank())
                        val custNoSixDigits = Regex("""(?<!\d)([79]\d{5})(?!\d)""").find(cleaned)?.value
                        if (isCallActive && custNoSixDigits != null) {
                            if (viewModel.wrapUpData.value.customerNumber != custNoSixDigits) {
                                viewModel.setWrapUpCustomerNumber(custNoSixDigits)
                                runOnUiThread {
                                    Toast.makeText(this, "Kundennummer $custNoSixDigits automatisch übernommen 📋", Toast.LENGTH_SHORT).show()
                                }
                            }
                            return
                        }

                        // Check if local copying is disabled and it wasn't a programmatic copy
                        val prefs = getSharedPreferences("stromruf_prefs", android.content.Context.MODE_PRIVATE)
                        val onLocalCopy = prefs.getBoolean("clipboard_bubble_on_local_copy", false)
                        if (!onLocalCopy && !isProgrammaticCopy) {
                            return
                        }
                        isProgrammaticCopy = false

                        // Check if QuickSave dialog is active first!
                        val quickSaveActive = viewModel.quickSaveDialogState.value != null
                        if (quickSaveActive) {
                            // Check if this single copy contains a customer number (typically ~6 digits, 9/7 starting) and name!
                            val extractedCustNo = CustomerNumberExtractor.extractCustomerNumber(cleaned)
                            if (extractedCustNo != null && cleaned.any { it.isLetter() }) {
                                val extractedName = cleaned.replace(extractedCustNo, "")
                                    .replace(Regex("[(),\\-_|]"), " ")
                                    .replace(Regex("\\s+"), " ")
                                    .trim()
                                if (extractedName.isNotBlank() && extractedName.length >= 2) {
                                    viewModel.updateQuickSaveDialog(customerNo = extractedCustNo, name = extractedName)
                                }
                            } else if (extractedCustNo != null) {
                                viewModel.updateQuickSaveDialog(customerNo = extractedCustNo)
                            } else if (cleaned.any { it.isLetter() }) {
                                viewModel.updateQuickSaveDialog(name = cleaned)
                            }
                            
                            // Check if now ready for auto-save
                            val current = viewModel.quickSaveDialogState.value
                            if (current != null && current.name.isNotBlank() && current.customerNo.isNotBlank()) {
                                val savedName = current.name
                                val savedCustNo = current.customerNo
                                viewModel.addManualContact(
                                    name = savedName,
                                    phone = current.phone,
                                    company = "Kd.-Nr: $savedCustNo",
                                    email = ""
                                )
                                viewModel.saveNeukunde(
                                    customerNumber = savedCustNo,
                                    phone = current.phone,
                                    customerName = savedName
                                )
                                viewModel.closeQuickSaveDialog()
                                runOnUiThread {
                                    Toast.makeText(this, "Kunde $savedName ($savedCustNo) erfolgreich gespeichert! 💾", Toast.LENGTH_LONG).show()
                                }
                            }
                            return // Done processing for QuickSave dialog!
                        }

                        // Normal clipboard bubble trigger
                        val isCustomerNo = CustomerNumberExtractor.isCustomerNumber(cleaned)
                        val isPotential = if (isCustomerNo) {
                            true
                        } else {
                            isPotentialPhoneNumber(cleaned)
                        }
                        
                        if (isPotential) {
                            val lastPopped = getSharedPreferences("stromruf_prefs", android.content.Context.MODE_PRIVATE)
                                .getString("last_clipboard_popped", "")
                            if (lastPopped != cleaned) {
                                viewModel.showClipboardBubble(cleaned, isCustomerNumber = isCustomerNo)
                                getSharedPreferences("stromruf_prefs", android.content.Context.MODE_PRIVATE)
                                    .edit()
                                    .putString("last_clipboard_popped", cleaned)
                                    .apply()
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun isPotentialPhoneNumber(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        val validChars = trimmed.all { it.isDigit() || it == '+' || it == ' ' || it == '-' || it == '/' || it == '(' || it == ')' }
        if (!validChars) return false
        val digitCount = trimmed.count { it.isDigit() }
        return digitCount in 5..20
    }

    override fun onNewIntent(intent: Intent) {
        handleOAuthIntent(intent)
        super.onNewIntent(intent)
        setIntent(intent)
        
        // Force screen wake and lockscreen show on new intent (e.g. for incoming calls)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(android.content.Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        com.example.receiver.AlarmSoundPlayer.stopRinging()
        checkCallNotificationIntent(intent)
    }

    
    

    private fun handleOAuthIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "com.aistudio.stromruf.gkrfws") return

        val provider = when {
            data.toString().contains("gmail", ignoreCase = true) -> "gmail"
            data.toString().contains("google", ignoreCase = true) -> "gmail"
            data.toString().contains("outlook", ignoreCase = true) -> "outlook"
            data.toString().contains("microsoft", ignoreCase = true) -> "outlook"
            else -> com.example.util.SecureIntegrationSettings(this).getDefaultMailProvider()
        }

        mailAccountManager.handleAuthorizationResponse(intent, provider) { success, error ->
            runOnUiThread {
                android.widget.Toast.makeText(
                    this,
                    if (success) "Mailkonto verbunden." else "Mailkonto konnte nicht verbunden werden: ${error ?: "Unbekannter Fehler"}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun checkCallNotificationIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_DIAL || intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_CALL) {
            val data = intent.data
            if (data != null && data.scheme == "tel") {
                val phone = data.schemeSpecificPart
                if (!phone.isNullOrBlank()) {
                    intent.action = null // Prevent triggering again on configuration changes
                    intent.data = null
                    viewModel.initiateCall(phone)
                    return
                }
            }
        }

        if (intent.getBooleanExtra("SHOW_INCOMING_ALERT", false)) {
            val id = intent.getStringExtra("FOLLOWUP_ID") ?: ""
            val name = intent.getStringExtra("CONTACT_NAME") ?: "Rückruf"
            val phone = intent.getStringExtra("CONTACT_PHONE") ?: ""
            if (!phone.isNullOrBlank()) {
                intent.removeExtra("SHOW_INCOMING_ALERT")
                viewModel.activeIncomingAlert.value = com.example.viewmodel.IncomingAlert(id, name, phone)
            }
        } else {
            val phone = intent.getStringExtra("CALL_IMMEDIATELY")
            val name = intent.getStringExtra("CALL_IMMEDIATELY_NAME") ?: "Rückruf"
            if (!phone.isNullOrBlank()) {
                viewModel.initiateCall(phone, name)
            }
        }
    }
}

// Global German Outome helper metadata mapping
data class OutcomeMeta(val label: String, val color: Color)

fun getOutcomeMeta(key: String?): OutcomeMeta {
    return when (key) {
        "erreicht_interesse" -> OutcomeMeta("Erreicht – Interesse", Color(0xFF10B981))
        "erreicht_abschluss" -> OutcomeMeta("Erreicht – Abschluss", Color(0xFF0D9488))
        "erreicht_kein_interesse" -> OutcomeMeta("Erreicht – kein Interesse", Color(0xFFEF4444))
        "nicht_erreicht" -> OutcomeMeta("Nicht erreicht", Color(0xFFF59E0B))
        "falsche_nummer" -> OutcomeMeta("Falsche Nummer", Color(0xFF64748B))
        else -> OutcomeMeta("Unbekannt / –", Color(0xFF94A3B8))
    }
}

// Short relative date helper
private val sdfDateTimeInstance = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)
private val sdfDateInstance = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)

fun fmtDateTime(timestamp: Long): String {
    synchronized(sdfDateTimeInstance) {
        return sdfDateTimeInstance.format(Date(timestamp))
    }
}

fun fmtDate(timestamp: Long): String {
    synchronized(sdfDateInstance) {
        return sdfDateInstance.format(Date(timestamp))
    }
}

@Composable
fun Modifier.futuristic3DBackground(style: String): Modifier {
    val config = com.example.ui.theme.getThemeStyleConfig(style)
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition()
    
    // Smooth animation for light positions
    val animOffset1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(15000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        )
    )
    val animOffset2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(18000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        )
    )

    return this.then(
        Modifier.drawBehind {
            drawRect(color = config.baseBackground)
            
            // Pulsating organic gradient (Mesh-like)
            drawRect(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(config.glowColor1.copy(alpha = 0.35f), androidx.compose.ui.graphics.Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width * (0.2f + 0.6f * animOffset1), size.height * (0.1f + 0.3f * animOffset2)),
                    radius = size.maxDimension * 0.8f
                )
            )
            
            drawRect(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(config.glowColor2.copy(alpha = 0.30f), androidx.compose.ui.graphics.Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width * (0.8f - 0.4f * animOffset2), size.height * (0.7f + 0.2f * animOffset1)),
                    radius = size.maxDimension * 0.9f
                )
            )
            
            // Add a subtle tech grid but no hard circles
            val gridSpacing = 44.dp.toPx()
            for (x in 0..size.width.toInt() step gridSpacing.toInt()) {
                drawLine(
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.015f),
                    start = androidx.compose.ui.geometry.Offset(x.toFloat(), 0f),
                    end = androidx.compose.ui.geometry.Offset(x.toFloat(), size.height),
                    strokeWidth = 1f
                )
            }
            for (y in 0..size.height.toInt() step gridSpacing.toInt()) {
                drawLine(
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.015f),
                    start = androidx.compose.ui.geometry.Offset(0f, y.toFloat()),
                    end = androidx.compose.ui.geometry.Offset(size.width, y.toFloat()),
                    strokeWidth = 1f
                )
            }
        }
    )
}

@Composable
fun StromrufMainDashboard(
    viewModel: StromrufViewModel,
    modifier: Modifier = Modifier,
    appTheme: String = "dark",
    onThemeChange: (String) -> Unit = {},
    screenBrightness: Float = -1f,
    onBrightnessChange: (Float) -> Unit = {},
    backgroundStyle: String = "platinum_metal",
    onBackgroundStyleChange: (String) -> Unit = {},
    clipboardBubblePosition: String = "bottom_left",
    onClipboardBubblePositionChange: (String) -> Unit = {},
    clipboardBubbleOnLocalCopy: Boolean = false,
    onClipboardBubbleOnLocalCopyChange: (Boolean) -> Unit = {},
    onSignOut: (() -> Unit)? = null
) {
    // Observe flows
    val contacts by viewModel.contacts.collectAsState()
    val activeFollowUps by viewModel.activeFollowUps.collectAsState()
    val callLogs by viewModel.callLogs.collectAsState()

    val searchQuery by viewModel.contactSearchQuery.collectAsState()
    val activeCall by viewModel.activeCall.collectAsState()
    val showWrapUpDialog by viewModel.showWrapUpDialog.collectAsState()
    val wrapUpData by viewModel.wrapUpData.collectAsState()
    val activeIncomingAlert by viewModel.activeIncomingAlert.collectAsState()

    val isAutoCallActive by viewModel.isAutoCallActive.collectAsState()
    val isAutoCallPaused by viewModel.isAutoCallPaused.collectAsState()
    val isSimulationModeEnabled by viewModel.isSimulationModeEnabled.collectAsState()

    val realCallActive = com.example.service.DialerInCallService.activeCall.value != null
    val activeNumber = com.example.service.DialerInCallService.activeCallNumber.value
    val activeName = com.example.service.DialerInCallService.activeCallName.value

    var lastCallNumber by remember { mutableStateOf("") }
    var lastCallName by remember { mutableStateOf("") }
    var lastCallActive by remember { mutableStateOf(false) }

    LaunchedEffect(realCallActive) {
        if (realCallActive) {
            var num = activeNumber
            var name = activeName
            if (num.isBlank()) {
                kotlinx.coroutines.delay(150)
                num = com.example.service.DialerInCallService.activeCallNumber.value
                name = com.example.service.DialerInCallService.activeCallName.value
            }
            if (num.isNotBlank()) {
                lastCallNumber = num
                lastCallName = name
                lastCallActive = true
                viewModel.initializeWrapUpForActiveCall(num, name)
            }
        } else {
            if (lastCallActive && lastCallNumber.isNotBlank()) {
                viewModel.startWrapUpForDirectCall(lastCallNumber, lastCallName, com.example.service.DialerInCallService.callDurationSeconds.value)
                lastCallActive = false
                lastCallNumber = ""
                lastCallName = ""
            }
        }
    }

    // Gauge counters states
    val totalCallsCount by viewModel.totalCallsToday.collectAsState()
    val pendingFollowUpsCount by viewModel.pendingFollowUpsCount.collectAsState()
    val reachabilityPercent by viewModel.reachabilityRateToday.collectAsState()
    val hotBoxLists by viewModel.hotBoxLists.collectAsState()

    // Manual Dialog states
    var showAddContactDialog by remember { mutableStateOf(false) }
    var showImportContactsDialog by remember { mutableStateOf(false) }
    var contactToEdit by remember { mutableStateOf<ContactEntity?>(null) }
    var showAddFollowUpDialog by remember { mutableStateOf(false) }
    var preselectedFollowUpDate by remember { mutableStateOf<Long?>(null) }
    var showRingtonePickerDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var selectedRingtoneTitle by remember { mutableStateOf(com.example.receiver.AlarmSoundPlayer.getSelectedRingtoneTitle(context)) }

    // Standard dialer and call permission states
    var isDefaultDialer by remember { mutableStateOf(com.example.util.ContactsUtil.isDefaultDialer(context)) }
    var isCallPermissionGranted by remember { mutableStateOf(com.example.util.ContactsUtil.hasCallPermission(context)) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isDefaultDialer = com.example.util.ContactsUtil.isDefaultDialer(context)
                isCallPermissionGranted = com.example.util.ContactsUtil.hasCallPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val callPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        isCallPermissionGranted = granted
    }

    LaunchedEffect(isDefaultDialer) {
        viewModel.isDefaultDialer.value = isDefaultDialer
    }

    // Settings & Alarm specific preferences
    val prefs = remember { context.getSharedPreferences("stromruf_prefs", Context.MODE_PRIVATE) }
    var alarmEnabledSetting by remember { mutableStateOf(true) }
    var autoCallDelaySecondsSetting by remember { mutableStateOf(5) }
    var preferredAudioDeviceSetting by remember { mutableStateOf("earpiece") }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showAiCallScreen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        alarmEnabledSetting = prefs.getBoolean("alarm_enabled", true)
        autoCallDelaySecondsSetting = prefs.getInt("hotbox_delay_seconds", 5)
        preferredAudioDeviceSetting = prefs.getString("preferred_audio_device", "earpiece") ?: "earpiece"
        viewModel.setAutoCallDelaySeconds(autoCallDelaySecondsSetting)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .futuristic3DBackground(backgroundStyle)
    ) {
        var showAddNeukundeDialog by remember { mutableStateOf(false) }

        StromrufShell(
            viewModel = viewModel,
            onAddContact = { showAddContactDialog = true },
            onAddFollowUp = { 
                preselectedFollowUpDate = null
                showAddFollowUpDialog = true 
            },
            onAddFollowUpFor = {
                contactToEdit = it
                preselectedFollowUpDate = null
                showAddFollowUpDialog = true
            },
            onImportContacts = { showImportContactsDialog = true },
            onEditContact = { contactToEdit = it },
            onAddNeukunde = { showAddNeukundeDialog = true },
            onOpenSettings = { showSettingsDialog = true },
            onOpenAiChat = { showAiCallScreen = true }
        )

        if (showAddNeukundeDialog) {
            AddNeukundeDialog(
                onDismiss = { showAddNeukundeDialog = false },
                onConfirm = { customerNumber, phone, customerName, company, email, deliveryAddress, meterNumber, consumption, energyType, routine ->
                    viewModel.saveNeukunde(
                        customerNumber = customerNumber,
                        phone = phone,
                        customerName = customerName,
                        company = company,
                        email = email,
                        deliveryAddress = deliveryAddress,
                        meterNumber = meterNumber,
                        consumption = consumption,
                        energyType = energyType,
                        routine = routine
                    )
                    showAddNeukundeDialog = false
                }
            )
        }

        // Incoming Call Management
        val realCallActive = com.example.service.DialerInCallService.activeCall.value != null
        val appCallActive = activeCall != null
        val activeCallState = com.example.service.DialerInCallService.activeCallState.value
        val isIncomingCall = realCallActive && activeCallState == android.telecom.Call.STATE_RINGING
        
        var isIncomingCallMinimized by remember { mutableStateOf(false) }
        var isOngoingCallMinimized by remember { mutableStateOf(false) }
        var bubbleOffsetX by remember { mutableStateOf(0f) }
        var bubbleOffsetY by remember { mutableStateOf(0f) }
        
        val callActive = realCallActive || appCallActive
        LaunchedEffect(callActive) {
            if (!callActive) {
                isOngoingCallMinimized = false
                bubbleOffsetX = 0f
                bubbleOffsetY = 0f
            }
        }
        
        LaunchedEffect(isIncomingCall) {
            if (!isIncomingCall) {
                isIncomingCallMinimized = false
            }
        }

        // Bottom Overlay Banner if minimized
        if (isIncomingCall && isIncomingCallMinimized) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
            ) {
                IncomingCallBottomOverlay(
                    contactName = com.example.service.DialerInCallService.activeCallName.value,
                    contactPhone = com.example.service.DialerInCallService.activeCallNumber.value,
                    contactCompany = com.example.service.DialerInCallService.activeCallCompany.value,
                    contactReason = com.example.service.DialerInCallService.activeCallReason.value,
                    onAnswer = {
                        com.example.service.DialerInCallService.answerCall()
                    },
                    onDecline = {
                        com.example.service.DialerInCallService.declineCall()
                    },
                    onClick = {
                        isIncomingCallMinimized = false
                    }
                )
            }
        }

        // Full Screen Incoming Call Screen (Ringing & not minimized)
        if (isIncomingCall && !isIncomingCallMinimized) {
            IncomingCallScreen(
                contactName = com.example.service.DialerInCallService.activeCallName.value,
                contactPhone = com.example.service.DialerInCallService.activeCallNumber.value,
                contactCompany = com.example.service.DialerInCallService.activeCallCompany.value,
                contactReason = com.example.service.DialerInCallService.activeCallReason.value,
                contactNotes = com.example.service.DialerInCallService.activeCallNotes.value,
                onAnswer = {
                    com.example.service.DialerInCallService.answerCall()
                },
                onDecline = {
                    com.example.service.DialerInCallService.declineCall()
                },
                onMinimize = {
                    isIncomingCallMinimized = true
                }
            )
        }

        // Ongoing Call Immersive Screen / Dialog (Only for non-ringing active/dialing/etc calls and not minimized)
        if ((realCallActive || appCallActive) && !showWrapUpDialog && !isIncomingCall && !isOngoingCallMinimized) {
            val contactName = if (realCallActive) {
                com.example.service.DialerInCallService.activeCallName.value
            } else {
                activeCall?.name ?: ""
            }
            val contactPhone = if (realCallActive) {
                com.example.service.DialerInCallService.activeCallNumber.value
            } else {
                activeCall?.phone ?: ""
            }
            
            val matchingContact = remember(contacts, contactPhone) {
                contacts.find { arePhoneNumbersMatching(it.phone, contactPhone) }
            }

            OngoingCallDialog(
                contactName = contactName,
                contactPhone = contactPhone,
                onHangUp = { finalDuration ->
                    val savePhone = if (contactPhone.isNotBlank()) contactPhone else (wrapUpData.phone.ifBlank { lastCallNumber })
                    val saveName = if (contactName.isNotBlank()) contactName else (wrapUpData.name.ifBlank { lastCallName })
                    lastCallActive = false
                    lastCallNumber = ""
                    lastCallName = ""
                    com.example.service.DialerInCallService.hangUp()
                    viewModel.clearActiveCall()
                    viewModel.startWrapUpForDirectCall(savePhone, saveName, finalDuration)
                },
                isAutoCallActive = isAutoCallActive,
                onHangUpAndPause = { finalDuration ->
                    val savePhone = if (contactPhone.isNotBlank()) contactPhone else (wrapUpData.phone.ifBlank { lastCallNumber })
                    val saveName = if (contactName.isNotBlank()) contactName else (wrapUpData.name.ifBlank { lastCallName })
                    lastCallActive = false
                    lastCallNumber = ""
                    lastCallName = ""
                    viewModel.pauseAutoCall()
                    com.example.service.DialerInCallService.hangUp()
                    viewModel.clearActiveCall()
                    viewModel.startWrapUpForDirectCall(savePhone, saveName, finalDuration)
                },
                wrapUpData = wrapUpData,
                onNameChange = { viewModel.setWrapUpName(it) },
                onCustomerNumberChange = { viewModel.setWrapUpCustomerNumber(it) },
                onCompanyChange = { viewModel.setWrapUpCompany(it) },
                onNoteChange = { viewModel.setWrapUpNote(it) },
                onCallReasonChange = { viewModel.setWrapUpCallReason(it) },
                onToggleOffset = { viewModel.toggleWrapUpOffset(it) },
                onOutcomeChange = { viewModel.setWrapUpOutcome(it) },
                contact = matchingContact,
                recentCallLogs = callLogs,
                onForceClose = {
                    lastCallActive = false
                    lastCallNumber = ""
                    lastCallName = ""
                    com.example.service.DialerInCallService.hangUp()
                    viewModel.clearActiveCall()
                },
                onMinimize = {
                    isOngoingCallMinimized = true
                },
                onAddToHotbox = { name, phone ->
                    if (matchingContact != null) {
                        viewModel.toggleHotBox(matchingContact.id)
                    } else {
                        viewModel.importContactFromSystem(name, phone) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onSaveInCallData = {
                    viewModel.saveCurrentInCallData(contactPhone)
                }
            )
        }

        // Full Screen Conversation Wrap-up Dialog & Follow-up Scheduler
        if (showWrapUpDialog) {
            WrapUpDialog(
                viewModel = viewModel,
                data = wrapUpData,
                onValueChange = { viewModel.updateWrapUpFields(it.name, it.company, it.email, it.customerNumber) },
                onOutcomeChange = { viewModel.setWrapUpOutcome(it) },
                onSaveContactChange = { viewModel.setWrapUpSaveContact(it) },
                onNoteChange = { viewModel.setWrapUpNote(it) },
                onCallReasonChange = { viewModel.setWrapUpCallReason(it) },
                onToggleOffset = { viewModel.toggleWrapUpOffset(it) },
                onAddCustomDate = { viewModel.addCustomFollowUpDate(it) },
                onRemoveCustomDate = { viewModel.removeCustomFollowUpDate(it) },
                onCancel = { viewModel.cancelWrapUp() },
                onSave = {
                    val custNo = wrapUpData.customerNumber.trim().takeIf { it.isNotBlank() }
                    val contactName = if (wrapUpData.name.isNotBlank()) wrapUpData.name else (if (custNo != null) "Kunde $custNo" else "Kunde (${wrapUpData.phone})")
                    if (wrapUpData.saveContact || wrapUpData.name.isNotBlank() || custNo != null) {
                        if (com.example.util.ContactsUtil.hasWriteContactsPermission(context) && wrapUpData.phone.isNotBlank()) {
                            val success = com.example.util.ContactsUtil.saveContactToSystemDirectly(context, contactName, wrapUpData.phone)
                            if (success) {
                                android.widget.Toast.makeText(context, "Kontakt im Telefonbuch gespeichert! 💾", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    viewModel.saveWrapUp()
                }
            )
        }

        // Beautiful incoming follow up reminder overlay from bottom of display
        activeIncomingAlert?.let { alert ->
            Dialog(
                onDismissRequest = { /* Don't dismiss by clicking outside */ },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF00FF87).copy(alpha = 0.3f), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                            .clickable(enabled = true, onClick = {}, interactionSource = remember { MutableInteractionSource() }, indication = null),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Pulley bar design asset
                            Box(
                                modifier = Modifier
                                    .width(45.dp)
                                    .height(4.dp)
                                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
                            )
                            
                            Spacer(modifier = Modifier.height(20.dp))
                            
                            // Visual Badge for alarm
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(Color(0xFFEF4444).copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = "Wiedervorlage fällig! 🎯",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = alert.name,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF00FF87)),
                                textAlign = TextAlign.Center
                            )
                            
                            Text(
                                text = alert.phone,
                                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.6f)),
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            // Actions Row
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // 1. Anrufen Button (Primary Action)
                                Button(
                                    onClick = {
                                        com.example.receiver.AlarmSoundPlayer.stopRinging()
                                        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                                        notificationManager?.cancel(alert.id.hashCode())
                                        viewModel.initiateCall(alert.phone, alert.name, callType = "rueckruf")
                                        viewModel.activeIncomingAlert.value = null
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Jetzt anrufen", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // 2. Snooze 10 Min Button
                                    Button(
                                        onClick = {
                                            com.example.receiver.AlarmSoundPlayer.stopRinging()
                                            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                                            notificationManager?.cancel(alert.id.hashCode())
                                            
                                            val snoozeTime = System.currentTimeMillis() + 10 * 60 * 1000
                                            viewModel.rescheduleFollowUp(alert.id, snoozeTime)
                                            viewModel.activeIncomingAlert.value = null
                                            Toast.makeText(context, "Um 10 Minuten verschoben ⏰", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.weight(1f).height(44.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("In 10 Min", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                    }
                                    
                                    // 3. Beenden Button
                                    Button(
                                        onClick = {
                                            com.example.receiver.AlarmSoundPlayer.stopRinging()
                                            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                                            notificationManager?.cancel(alert.id.hashCode())
                                            viewModel.activeIncomingAlert.value = null
                                        },
                                        modifier = Modifier.weight(1f).height(44.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Beenden", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }



        // Add contact form overlay
        if (showAddContactDialog) {
            ContactDialog(
                title = "Neuer Kontakt",
                hotBoxLists = hotBoxLists,
                initialHotBoxListName = viewModel.selectedHotBoxListName.value,
                onDismiss = { showAddContactDialog = false },
                onConfirm = { name, phone, company, email, isHotBox, startHour, endHour, weekdays, consumption, zipCode, energyType, hotBoxListName ->
                    viewModel.addManualContact(name, phone, company, email, isHotBox, startHour, endHour, weekdays, null, hotBoxListName, consumption, zipCode, energyType)
                    showAddContactDialog = false
                }
            )
        }

        if (showImportContactsDialog) {
            SystemContactImportDialog(
                viewModel = viewModel,
                onDismiss = { showImportContactsDialog = false }
            )
        }

        // Edit contact form overlay
        contactToEdit?.let { contact ->
            ContactDialog(
                title = "Kontakt bearbeiten",
                initialName = contact.name,
                initialPhone = contact.phone,
                initialCompany = contact.company ?: "",
                initialEmail = contact.email ?: "",
                initialIsHotBox = contact.isHotBox,
                initialHotBoxStartHour = contact.hotBoxStartHour,
                initialHotBoxEndHour = contact.hotBoxEndHour,
                initialHotBoxWeekdays = contact.hotBoxWeekdays,
                initialConsumption = contact.consumption,
                initialZipCode = contact.zipCode,
                initialEnergyType = contact.energyType,
                hotBoxLists = hotBoxLists,
                initialHotBoxListName = contact.hotBoxListName ?: viewModel.selectedHotBoxListName.value,
                onDismiss = { contactToEdit = null },
                onConfirm = { name, phone, company, email, isHotBox, startHour, endHour, weekdays, consumption, zipCode, energyType, hotBoxListName ->
                    val updated = contact.copy(
                        name = name,
                        phone = phone,
                        company = company.takeIf { it.isNotBlank() },
                        email = email.takeIf { it.isNotBlank() },
                        isHotBox = isHotBox,
                        hotBoxListName = if (isHotBox) hotBoxListName else null,
                        hotBoxStartHour = if (isHotBox) startHour else null,
                        hotBoxEndHour = if (isHotBox) endHour else null,
                        hotBoxWeekdays = if (isHotBox) weekdays else null,
                        consumption = consumption,
                        zipCode = zipCode,
                        energyType = energyType
                    )
                    viewModel.editContact(updated)
                    contactToEdit = null
                }
            )
        }

        if (showAddFollowUpDialog) {
            AddFollowUpDialog(
                contacts = contacts,
                initialDueAt = preselectedFollowUpDate,
                onDismiss = { showAddFollowUpDialog = false },
                onConfirm = { name, phone, note, dueAt, callReason ->
                    viewModel.addManualFollowUp(name, phone, note, dueAt, callReason)
                    showAddFollowUpDialog = false
                }
            )
        }

        if (showRingtonePickerDialog) {
            RingtonePickerDialog(
                onDismiss = { showRingtonePickerDialog = false },
                onConfirm = { uri, title ->
                    com.example.receiver.AlarmSoundPlayer.saveSelectedRingtone(context, uri, title)
                    selectedRingtoneTitle = title
                    showRingtonePickerDialog = false
                }
            )
        }

        if (showSettingsDialog) {
            SettingsDialog(
                onDismiss = { showSettingsDialog = false },
                appTheme = appTheme,
                onThemeChange = onThemeChange,
                bgStyle = backgroundStyle,
                onBgStyleChange = onBackgroundStyleChange,
                screenBrightness = screenBrightness,
                onBrightnessChange = onBrightnessChange,
                alarmEnabled = alarmEnabledSetting,
                onAlarmToggle = { enabled ->
                    alarmEnabledSetting = enabled
                    prefs.edit().putBoolean("alarm_enabled", enabled).apply()
                },
                selectedRingtoneTitle = selectedRingtoneTitle,
                onSelectRingtoneClick = {
                    showRingtonePickerDialog = true
                },
                autoCallDelaySeconds = autoCallDelaySecondsSetting,
                onAutoCallDelaySecondsChange = { secs ->
                    autoCallDelaySecondsSetting = secs
                    prefs.edit().putInt("hotbox_delay_seconds", secs).apply()
                    viewModel.setAutoCallDelaySeconds(secs)
                },
                preferredAudioDevice = preferredAudioDeviceSetting,
                onPreferredAudioDeviceChange = { device ->
                    preferredAudioDeviceSetting = device
                    prefs.edit().putString("preferred_audio_device", device).apply()
                    com.example.service.DialerInCallService.instance?.applyPreferredAudioRoute()
                },
                clipboardBubblePosition = clipboardBubblePosition,
                onClipboardBubblePositionChange = onClipboardBubblePositionChange,
                clipboardBubbleOnLocalCopy = clipboardBubbleOnLocalCopy,
                onClipboardBubbleOnLocalCopyChange = onClipboardBubbleOnLocalCopyChange,
                onSignOut = onSignOut,
                isSimulationModeEnabled = isSimulationModeEnabled,
                onSimulationModeToggle = { enabled ->
                    viewModel.setSimulationModeEnabled(enabled)
                },
                isDefaultDialer = isDefaultDialer,
                isCallPermissionGranted = isCallPermissionGranted,
                onRequestDefaultDialer = {
                    com.example.util.ContactsUtil.requestDefaultDialer(context as android.app.Activity, 1201)
                },
                onRequestCallPermission = {
                    callPermissionLauncher.launch(android.Manifest.permission.CALL_PHONE)
                }
            )
        }

        if (showAiCallScreen) {
            com.example.ui.AiAnrufScreen(
                viewModel = viewModel,
                onDismiss = { showAiCallScreen = false }
            )
        }

        // Top-right controls (Settings)
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { showSettingsDialog = true }
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Einstellungen",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Minimierter Anruf-Tropfen (Floating Bubble) in der unteren linken Ecke
        if (callActive && !showWrapUpDialog && !isIncomingCall && isOngoingCallMinimized) {
            val elapsedSeconds = if (realCallActive) {
                com.example.service.DialerInCallService.callDurationSeconds.value
            } else {
                0L
            }
            
            val transition = rememberInfiniteTransition(label = "pulse_drop")
            val pulseScale by transition.animateFloat(
                initialValue = 0.95f,
                targetValue = 1.08f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1400, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scale"
            )
            val pulseAlpha by transition.animateFloat(
                initialValue = 0.7f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1400, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "alpha"
            )

            Card(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset { IntOffset(bubbleOffsetX.roundToInt(), bubbleOffsetY.roundToInt()) }
                    .padding(start = 16.dp, bottom = 96.dp) // Safe distance from bottom navigation bar and screen boundary
                    .size(68.dp)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var totalDrag = 0f
                            drag(down.id) { change ->
                                val dragAmount = change.positionChange()
                                bubbleOffsetX += dragAmount.x
                                bubbleOffsetY += dragAmount.y
                                totalDrag += kotlin.math.sqrt(dragAmount.x * dragAmount.x + dragAmount.y * dragAmount.y)
                                change.consume()
                            }
                            if (totalDrag < 15f) {
                                isOngoingCallMinimized = false
                            }
                        }
                    },
                shape = RoundedCornerShape(
                    topStart = 4.dp, // Tear drop shape
                    topEnd = 32.dp,
                    bottomStart = 32.dp,
                    bottomEnd = 32.dp
                ),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                border = BorderStroke(2.dp, Color(0xFF00FF87).copy(alpha = pulseAlpha)),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Laufender Anruf",
                            tint = Color(0xFF00FF87),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        if (elapsedSeconds > 0) {
                            val mins = elapsedSeconds / 60
                            val secs = elapsedSeconds % 60
                            Text(
                                text = String.format("%02d:%02d", mins, secs),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text = "Aktiv",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
        
        ProximityScreenShield(isCallActive = callActive && !isIncomingCall)
    }
}

@Composable
fun StromrufHeader(
    totalCalls: Int,
    pendingFollowUps: Int,
    reachability: Int
) {
    val infiniteTransition = rememberInfiniteTransition(label = "strom_anim")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_glow"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF031410), // Cyber Deep Green
                        Color(0xFF080D0C)  // Dark carbon graphite
                    )
                )
            )
            .drawBehind {
                // Futuristic thin electric current line at the bottom
                drawLine(
                    color = Color(0xFF00FF87).copy(alpha = 0.8f * pulseGlow),
                    start = androidx.compose.ui.geometry.Offset(0f, size.height),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                    strokeWidth = 1.5.dp.toPx()
                )
            }
            .padding(top = 10.dp, bottom = 8.dp, start = 12.dp, end = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Pulsing electric current/lightning bolt graphic (Stromruf Logo)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF031410), RoundedCornerShape(10.dp))
                        .border(1.2.dp, Color(0xFF00FF87).copy(alpha = 0.8f * pulseGlow), RoundedCornerShape(10.dp))
                        .padding(3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            painter = painterResource(id = com.example.R.drawable.ic_launcher_background),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                        )
                        Image(
                            painter = painterResource(id = com.example.R.drawable.ic_launcher_foreground),
                            contentDescription = "Stromruf Logo",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Column {
                    Text(
                        text = "STROMRUF",
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            letterSpacing = 2.sp,
                            color = Color(0xFF00FF87) // Futuristic Neon Green
                        )
                    )
                    Text(
                        text = "Digitaler Netz-Vertrieb",
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 9.sp,
                            letterSpacing = 0.5.sp,
                            color = Color(0xFF64748B)
                        )
                    )
                }
            }
            
            // Electricity grid status pulse ring
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .background(Color(0xFF052E16).copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFF10B981).copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(Color(0xFF00FF87), shape = CircleShape)
                        .drawBehind {
                            drawCircle(
                                color = Color(0xFF00FF87),
                                radius = size.minDimension * pulseGlow * 1.5f,
                                alpha = 0.4f * (1f - pulseGlow)
                            )
                        }
                )
                Text(
                    text = "NETZ OK",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00FF87)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Digital Power meters indicators row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            DigitalMeter(
                label = "ANRUFE HEUTE",
                value = String.format("%04d", totalCalls),
                color = Color(0xFF00FF87)
            )
            DigitalMeter(
                label = "FÄLLIG JETZT",
                value = String.format("%04d", pendingFollowUps),
                color = if (pendingFollowUps > 0) Color(0xFFEF4444) else Color(0xFF10B981)
            )
            DigitalMeter(
                label = "ERREICH-QUOTE",
                value = String.format("%02d%%", reachability),
                color = Color(0xFF00F0FF)
            )
        }
    }
}

@Composable
fun DigitalMeter(
    label: String,
    value: String,
    color: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "meter_glow")
    val borderPulse by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "border_pulse"
    )

    Column(
        modifier = Modifier
            .background(Color(0xFF030706), shape = RoundedCornerShape(10.dp))
            .border(
                width = 1.dp,
                color = color.copy(alpha = borderPulse),
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .width(90.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 7.5.sp,
                letterSpacing = 0.5.sp,
                color = Color(0xFF475569)
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                letterSpacing = 1.sp
            )
        )
    }
}

@Composable
fun StromrufBottomNavigationBar(
    activeTab: String,
    onTabSelected: (String) -> Unit,
    activeFollowUpsCount: Int
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        modifier = Modifier.navigationBarsPadding()
    ) {
        NavigationBarItem(
            selected = activeTab == "anruf",
            onClick = { onTabSelected("anruf") },
            icon = { Icon(Icons.Default.Phone, contentDescription = "Direktwahl & Fällig") },
            label = { Text("Anrufen") }
        )
        NavigationBarItem(
            selected = activeTab == "wiedervorlagen",
            onClick = { onTabSelected("wiedervorlagen") },
            icon = {
                BadgedBox(
                    badge = {
                        if (activeFollowUpsCount > 0) {
                            Badge(containerColor = MaterialTheme.colorScheme.error) {
                                Text(activeFollowUpsCount.toString())
                            }
                        }
                    }
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = "Wiedervorlagen")
                }
            },
            label = { Text("Wiedervorlage") }
        )
        NavigationBarItem(
            selected = activeTab == "kalender",
            onClick = { onTabSelected("kalender") },
            icon = { Icon(Icons.Default.DateRange, contentDescription = "Kalender") },
            label = { Text("Kalender") }
        )
        NavigationBarItem(
            selected = activeTab == "historie",
            onClick = { onTabSelected("historie") },
            icon = { Icon(Icons.Default.List, contentDescription = "Aktivität & Historie") },
            label = { Text("Historie") }
        )
        NavigationBarItem(
            selected = activeTab == "kontakte",
            onClick = { onTabSelected("kontakte") },
            icon = { Icon(Icons.Default.Star, contentDescription = "Hotbox") },
            label = { Text("Hotbox") }
        )
    }
}

// --- TAB CONTENTS ---

@Composable
fun AnrufTabContent(
    viewModel: StromrufViewModel,
    contacts: List<ContactEntity>,
    activeFollowUps: List<FollowUpEntity>,
    quickName: String,
    onQuickNameChange: (String) -> Unit,
    quickPhone: String,
    onQuickPhoneChange: (String) -> Unit
) {
    val context = LocalContext.current
    var showImportContactsDialog by remember { mutableStateOf(false) }
    var showHotBoxImportDialog by remember { mutableStateOf(false) }
    
    var showAddNeukundeDialog by remember { mutableStateOf(false) }
    var dialogCustomerNumber by remember { mutableStateOf("") }
    var dialogPhone by remember { mutableStateOf("") }

    val writeContactsPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (quickName.isNotBlank() && quickPhone.isNotBlank()) {
                val success = com.example.util.ContactsUtil.saveContactToSystemDirectly(context, quickName, quickPhone)
                if (success) {
                    Toast.makeText(context, "Kontakt \"$quickName\" erfolgreich direkt im Telefonbuch gespeichert! 💾", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Fehler beim direkten Speichern. Öffne Kontaktemanager...", Toast.LENGTH_SHORT).show()
                    com.example.util.ContactsUtil.saveContactViaIntent(context, quickName, quickPhone)
                }
            }
        } else {
            Toast.makeText(context, "Berechtigung verweigert. Öffne vorausgefüllte System-Kontakte...", Toast.LENGTH_SHORT).show()
            com.example.util.ContactsUtil.saveContactViaIntent(context, quickName, quickPhone)
        }
    }

    // Auto-prefill contact name from device contacts
    LaunchedEffect(quickPhone) {
        if (quickPhone.isNotBlank() && quickPhone.length >= 3) {
            val matchedName = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                ContactsUtil.lookupContactName(context, quickPhone)
            }
            if (matchedName != null && quickName.isBlank()) {
                onQuickNameChange(matchedName)
            }
        }
    }

    val focusManager = LocalFocusManager.current

    val todayPending = remember(activeFollowUps) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfToday = cal.timeInMillis
        
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        val endOfToday = cal.timeInMillis

        activeFollowUps.filter { it.dueAt in startOfToday..endOfToday }
    }

    // Overlay Permission state
    var overlayPermissionGranted by remember { 
        mutableStateOf(if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            android.provider.Settings.canDrawOverlays(context)
        } else {
            true
        })
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                overlayPermissionGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    android.provider.Settings.canDrawOverlays(context)
                } else {
                    true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Default Dialer & Phone permission states
    var isDefaultDialer by remember { mutableStateOf(ContactsUtil.isDefaultDialer(context)) }
    var isCallPermissionGranted by remember { mutableStateOf(ContactsUtil.hasCallPermission(context)) }

    @Suppress("ContextCastToActivity")
    val activity = LocalContext.current as? android.app.Activity

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isDefaultDialer = ContactsUtil.isDefaultDialer(context)
                isCallPermissionGranted = ContactsUtil.hasCallPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        isCallPermissionGranted = granted
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Direktwahl Card (at the very top)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, Color(0xFF00FF87).copy(alpha = 0.15f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Direktwahl",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00FF87)
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Live matching contacts logic
                val searchQuery = if (quickPhone.isNotBlank()) {
                    quickPhone
                } else if (quickName.isNotBlank() && quickName.length >= 1) {
                    quickName
                } else {
                    ""
                }

                var matchingSystem by remember { mutableStateOf<List<com.example.util.ContactsUtil.SystemContact>>(emptyList()) }
                LaunchedEffect(searchQuery) {
                    if (com.example.util.ContactsUtil.hasContactsPermission(context) && searchQuery.isNotBlank()) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val list = com.example.util.ContactsUtil.searchSystemContacts(context, searchQuery).take(3)
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                matchingSystem = list
                            }
                        }
                    } else {
                        matchingSystem = emptyList()
                    }
                }

                val matchingLocal = remember(searchQuery, contacts) {
                    if (searchQuery.isBlank()) emptyList() else {
                        contacts.filter { 
                            it.phone.contains(searchQuery, ignoreCase = true) || 
                            it.name.contains(searchQuery, ignoreCase = true) ||
                            (it.company != null && it.company.contains(searchQuery, ignoreCase = true)) ||
                            (it.callReason != null && it.callReason.contains(searchQuery, ignoreCase = true))
                        }.take(3)
                    }
                }

                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = quickName,
                            onValueChange = onQuickNameChange,
                            label = { Text("Kundenname (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
                            val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
                            LaunchedEffect(Unit) {
                                focusRequester.requestFocus()
                                keyboardController?.show()
                            }
                            OutlinedTextField(
                                value = quickPhone,
                                onValueChange = onQuickPhoneChange,
                                label = { Text("Telefonnummer *") },
                                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                                trailingIcon = {
                                    if (quickPhone.isNotBlank()) {
                                        IconButton(onClick = { onQuickPhoneChange("") }) {
                                            Icon(Icons.Default.Close, contentDescription = "Leeren")
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(8.dp)
                            )

                            FilledTonalButton(
                                onClick = {
                                    dialogPhone = quickPhone
                                    dialogCustomerNumber = "KD-${(10000..99999).random()}"
                                    showAddNeukundeDialog = true
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFF00FF87).copy(alpha = 0.15f),
                                    contentColor = Color(0xFF00FF87)
                                ),
                                modifier = Modifier
                                    .height(56.dp) // align with OutlinedTextField height
                                    .align(Alignment.CenterVertically)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PersonAdd,
                                        contentDescription = "Als Neukunde anlegen",
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Neu",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (showAddNeukundeDialog) {
                            AlertDialog(
                                onDismissRequest = { showAddNeukundeDialog = false },
                                title = {
                                    Text(
                                        text = "Neukunde anlegen 👤",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                text = {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            text = "Gib die Kundennummer für den Neukunden ein:",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White.copy(alpha = 0.7f)
                                        )

                                        OutlinedTextField(
                                            value = dialogCustomerNumber,
                                            onValueChange = { dialogCustomerNumber = it },
                                            label = { Text("Kundennummer") },
                                            placeholder = { Text("z.B. KD-12345") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Color(0xFF00FF87),
                                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                                focusedLabelColor = Color(0xFF00FF87),
                                                unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                                            )
                                        )

                                        OutlinedTextField(
                                            value = dialogPhone,
                                            onValueChange = { dialogPhone = it },
                                            label = { Text("Telefonnummer") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Color(0xFF00FF87),
                                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                                focusedLabelColor = Color(0xFF00FF87),
                                                unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                                            )
                                        )
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            if (dialogCustomerNumber.isBlank() || dialogPhone.isBlank()) {
                                                Toast.makeText(context, "Bitte füllen Sie beide Felder aus!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                viewModel.saveNeukunde(
                                                    customerNumber = dialogCustomerNumber.trim(),
                                                    phone = dialogPhone.trim()
                                                )
                                                showAddNeukundeDialog = false
                                                Toast.makeText(context, "Neukunde erfolgreich angelegt! 🚀", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF87))
                                    ) {
                                        Text("Speichern", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                                    }
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = { showAddNeukundeDialog = false }
                                    ) {
                                        Text("Abbrechen", color = Color.White.copy(alpha = 0.6f))
                                    }
                                },
                                containerColor = Color(0xFF1E293B)
                            )
                        }
                    }

                    // Floating Card that goes UPWARDS
                    if (searchQuery.isNotBlank() && (matchingLocal.isNotEmpty() || matchingSystem.isNotEmpty())) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)),
                            border = BorderStroke(1.5.dp, Color(0xFF00FF87)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .offset(y = (-68).dp)
                                .fillMaxWidth()
                                .heightIn(max = 260.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Gefundene Kontakte:",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF00FF87)
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))

                                // Show Stromruf App contacts
                                matchingLocal.forEach { local ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                        border = BorderStroke(1.dp, Color(0xFF00FF87).copy(alpha = 0.3f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onQuickPhoneChange(local.phone)
                                                onQuickNameChange(local.name)
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = local.name,
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                                                )
                                                Text(
                                                    text = local.phone,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                )
                                                if (!local.callReason.isNullOrBlank()) {
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = "Anrufgrund: ${local.callReason}",
                                                        color = Color(0xFF00FF87),
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                                    )
                                                }
                                            }
                                            SuggestionChip(
                                                onClick = {
                                                    onQuickPhoneChange(local.phone)
                                                    onQuickNameChange(local.name)
                                                },
                                                label = { Text("App-Kontakt", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                                colors = SuggestionChipDefaults.suggestionChipColors(
                                                    labelColor = Color(0xFF00FF87),
                                                    containerColor = Color(0xFF00FF87).copy(alpha = 0.1f)
                                                ),
                                                border = BorderStroke(1.dp, Color(0xFF00FF87).copy(alpha = 0.4f))
                                            )
                                        }
                                    }
                                }

                                // Show System contacts
                                matchingSystem.forEach { sys ->
                                    val isAlreadyLocal = matchingLocal.any { 
                                        it.phone.replace(" ", "").replace("-", "") == sys.phone.replace(" ", "").replace("-", "") 
                                    }
                                    if (!isAlreadyLocal) {
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    onQuickPhoneChange(sys.phone)
                                                    onQuickNameChange(sys.name)
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = sys.name,
                                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                                                    )
                                                    Text(
                                                        text = sys.phone,
                                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    )
                                                    if (!sys.tag.isNullOrEmpty()) {
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                            text = "Tag: ${sys.tag}",
                                                            color = Color(0xFF00FF87),
                                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                                        )
                                                    }
                                                }
                                                SuggestionChip(
                                                    onClick = {
                                                        onQuickPhoneChange(sys.phone)
                                                        onQuickNameChange(sys.name)
                                                    },
                                                    label = { Text("Telefonbuch", fontSize = 10.sp) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Direct saving buttons (System / App)
                if (quickPhone.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. Save to System Contacts
                        Button(
                            onClick = {
                                if (quickName.isBlank()) {
                                    Toast.makeText(context, "Bitte einen Namen eingeben, um zu speichern ✍️", Toast.LENGTH_LONG).show()
                                } else {
                                    if (com.example.util.ContactsUtil.hasWriteContactsPermission(context)) {
                                        val success = com.example.util.ContactsUtil.saveContactToSystemDirectly(context, quickName, quickPhone)
                                        if (success) {
                                            Toast.makeText(context, "Kontakt \"$quickName\" erfolgreich direkt im Telefonbuch gespeichert! 💾", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "Fehler beim direkten Speichern. Öffne Kontaktemanager...", Toast.LENGTH_SHORT).show()
                                            com.example.util.ContactsUtil.saveContactViaIntent(context, quickName, quickPhone)
                                        }
                                    } else {
                                        writeContactsPermissionLauncher.launch(android.Manifest.permission.WRITE_CONTACTS)
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Im Telefonbuch speichern", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        // 2. Save to Stromruf app contacts database
                        val cleanQuickPhone = remember(quickPhone) { quickPhone.replace(" ", "").replace("-", "") }
                        val alreadyLocal = remember(contacts, cleanQuickPhone) {
                            if (cleanQuickPhone.isBlank()) false else {
                                contacts.any {
                                    it.phone.replace(" ", "").replace("-", "") == cleanQuickPhone
                                }
                            }
                        }
                        if (!alreadyLocal) {
                            Button(
                                onClick = {
                                    if (quickName.isBlank()) {
                                        Toast.makeText(context, "Bitte einen Namen eingeben, um zu speichern ✍️", Toast.LENGTH_LONG).show()
                                    } else {
                                        viewModel.addManualContact(
                                            name = quickName,
                                            phone = quickPhone,
                                            company = "",
                                            email = ""
                                        )
                                        Toast.makeText(context, "Kontakt \"$quickName\" in Stromruf gespeichert! ⚡", Toast.LENGTH_LONG).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF0F172A),
                                    contentColor = Color(0xFF00FF87)
                                ),
                                border = BorderStroke(1.dp, Color(0xFF00FF87).copy(alpha = 0.3f)),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF00FF87))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("In Stromruf speichern", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Dialer Keypad Grid
                val keys = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("*", "0", "#")
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    keys.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            row.forEach { digit ->
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                                        .clickable {
                                            onQuickPhoneChange(quickPhone + digit)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = digit,
                                        style = TextStyle(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Bottom Dialer Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Backspace Button
                        IconButton(
                            onClick = {
                                if (quickPhone.isNotEmpty()) {
                                    onQuickPhoneChange(quickPhone.dropLast(1))
                                }
                            },
                            enabled = quickPhone.isNotEmpty(),
                            modifier = Modifier
                                .size(54.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Löschen",
                                tint = if (quickPhone.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }

                        // Big Green Call Button
                        Button(
                            onClick = {
                                if (quickPhone.isNotBlank()) {
                                    focusManager.clearFocus()
                                    viewModel.initiateCall(quickPhone, quickName.takeIf { it.isNotBlank() })
                                }
                            },
                            enabled = quickPhone.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981),
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = CircleShape,
                            modifier = Modifier.size(64.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = "Anrufen", tint = Color.White, modifier = Modifier.size(28.dp))
                        }

                        // Clear All Button
                        IconButton(
                            onClick = { onQuickPhoneChange("") },
                            enabled = quickPhone.isNotEmpty(),
                            modifier = Modifier
                                .size(54.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Leeren",
                                tint = if (quickPhone.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }
        }

        // 2. Standard-Telefon-App & Berechtigungen Card (moved second)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
            ),
            border = BorderStroke(1.dp, Color(0xFF00FF87).copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = null,
                            tint = Color(0xFF00FF87),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Standard-Telefon-App",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00FF87),
                                fontSize = 15.sp
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isDefaultDialer) Color(0xFF10B981).copy(alpha = 0.15f)
                                else Color(0xFFEAB308).copy(alpha = 0.15f)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isDefaultDialer) "Standard" else "Inaktiv",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDefaultDialer) Color(0xFF10B981) else Color(0xFFEAB308)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Um direkt aus Stromruf zu telefonieren und Anrufe automatisch zu dokumentieren, legen Sie Stromruf als Standard-Telefon-App fest und erteilen Sie das Telefonrecht.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!isCallPermissionGranted) {
                        Button(
                            onClick = {
                                permissionLauncher.launch(android.Manifest.permission.CALL_PHONE)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF87), contentColor = Color.Black)
                        ) {
                            Text("Telefonrecht erteilen", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (!isDefaultDialer) {
                        Button(
                            onClick = {
                                activity?.let {
                                    ContactsUtil.requestDefaultDialer(it, 1201)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Als Standard setzen", fontSize = 11.sp)
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF10B981).copy(alpha = 0.15f))
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Direktwahl ist aktiv", color = Color(0xFF10B981), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // System Contacts / Call Logs Integration
        var selectedSubTab by remember { mutableStateOf("contacts") } // contacts or calllog
            var contactSearchQuery by remember { mutableStateOf("") }
            
            var systemContactsList by remember { mutableStateOf(emptyList<ContactsUtil.SystemContact>()) }
            var recentCallsList by remember { mutableStateOf(emptyList<ContactsUtil.CallLogEntry>()) }
            
            // Reload when tab changes or search query changes
            LaunchedEffect(selectedSubTab, contactSearchQuery) {
                if (selectedSubTab == "contacts") {
                    systemContactsList = ContactsUtil.searchSystemContacts(context, contactSearchQuery)
                } else {
                    recentCallsList = ContactsUtil.readRecentCalls(context)
                }
            }

            // Also reload if we resume activity
            val lifecycleOwnerForIntegration = androidx.lifecycle.compose.LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwnerForIntegration) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        if (selectedSubTab == "contacts") {
                            systemContactsList = ContactsUtil.searchSystemContacts(context, contactSearchQuery)
                        } else {
                            recentCallsList = ContactsUtil.readRecentCalls(context)
                        }
                    }
                }
                lifecycleOwnerForIntegration.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwnerForIntegration.lifecycle.removeObserver(observer)
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, Color(0xFF00FF87).copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Netz-Kontakte & Protokolle",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00FF87)
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Sub-tab switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selectedSubTab == "contacts") MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { selectedSubTab = "contacts" }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = if (selectedSubTab == "contacts") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    "Systemkontakte",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedSubTab == "contacts") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selectedSubTab == "calllog") MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { selectedSubTab = "calllog" }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Phone,
                                    contentDescription = null,
                                    tint = if (selectedSubTab == "calllog") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    "Kürzliche Anrufe",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedSubTab == "calllog") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (selectedSubTab == "contacts") {
                        val hasPermission = ContactsUtil.hasContactsPermission(context)
                        if (!hasPermission) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "Berechtigung für Kontakte wird benötigt, um Ihr Adressbuch zu durchsuchen.",
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = {
                                        (context as? android.app.Activity)?.requestPermissions(
                                            arrayOf(android.Manifest.permission.READ_CONTACTS), 102
                                        )
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Berechtigung erteilen", fontSize = 12.sp)
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = contactSearchQuery,
                                onValueChange = { contactSearchQuery = it },
                                label = { Text("Name suchen...") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            if (systemContactsList.isEmpty()) {
                                Text(
                                    "Keine Kontakte gefunden.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    systemContactsList.take(8).forEach { contact ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    onQuickNameChange(contact.name)
                                                    onQuickPhoneChange(contact.phone)
                                                    Toast.makeText(context, "${contact.name} für Direktwahl übernommen! 🎯", Toast.LENGTH_SHORT).show()
                                                }
                                                .padding(vertical = 8.dp, horizontal = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = contact.name,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                                Text(
                                                    text = contact.phone,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                            Icon(
                                                Icons.Default.ArrowForward,
                                                contentDescription = "Übernehmen",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.5.dp)
                                    }
                                }
                            }
                        }
                    } else {
                        val hasPermission = ContactsUtil.hasCallLogPermission(context)
                        if (!hasPermission) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "Berechtigung für die Anrufliste wird benötigt, um kürzliche Telefonanrufe einzusehen.",
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = {
                                        (context as? android.app.Activity)?.requestPermissions(
                                            arrayOf(android.Manifest.permission.READ_CALL_LOG), 103
                                        )
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Berechtigung erteilen", fontSize = 12.sp)
                                }
                            }
                        } else {
                            if (recentCallsList.isEmpty()) {
                                Text(
                                    "Keine kürzlichen Anrufe in der Liste.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    recentCallsList.take(10).forEach { call ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    onQuickNameChange(call.name ?: "")
                                                    onQuickPhoneChange(call.number)
                                                    Toast.makeText(context, "${call.name ?: call.number} für Direktwahl übernommen! 🎯", Toast.LENGTH_SHORT).show()
                                                }
                                                .padding(vertical = 8.dp, horizontal = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = call.name ?: "Unbekannter Kontakt",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    val callTypeLabel = when (call.type) {
                                                        android.provider.CallLog.Calls.INCOMING_TYPE -> "Eingehend"
                                                        android.provider.CallLog.Calls.OUTGOING_TYPE -> "Ausgehend"
                                                        android.provider.CallLog.Calls.MISSED_TYPE -> "Entgangen"
                                                        else -> "Anruf"
                                                    }
                                                    val callColor = when (call.type) {
                                                        android.provider.CallLog.Calls.INCOMING_TYPE -> Color(0xFF10B981)
                                                        android.provider.CallLog.Calls.OUTGOING_TYPE -> Color(0xFF3B82F6)
                                                        else -> Color(0xFFEF4444)
                                                    }
                                                    Text(
                                                        text = callTypeLabel,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = callColor
                                                    )
                                                    Text(
                                                        text = call.number,
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                            }
                                            Icon(
                                                Icons.Default.ArrowForward,
                                                contentDescription = "Übernehmen",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.5.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

        // Fällig heute Section
        Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.DateRange, 
                    contentDescription = null, 
                    tint = Color(0xFF00FF87),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Heute fällige Rückrufe (${todayPending.size})",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
            }

        if (todayPending.isEmpty()) {
            Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Keine fälligen Rückrufe. Saubere Liste.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    )
                }
        } else {
            todayPending.forEach { followup ->
                FollowUpCard(
                    followup = followup,
                    overdue = followup.dueAt < System.currentTimeMillis(),
                    onCallClick = { viewModel.initiateCall(followup.contactPhone, followup.contactName, followup.contactId, callType = "rueckruf") },
                    onCompleteClick = { viewModel.completeFollowUp(followup.id) },
                    onDeleteClick = { viewModel.deleteFollowUp(followup.id) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, Color(0xFF00FF87).copy(alpha = 0.15f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AddCircle,
                        contentDescription = null,
                        tint = Color(0xFF00FF87),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Kontakte & Leads importieren 📥",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00FF87)
                        )
                    )
                }

                Text(
                    text = "Importieren Sie neue Kontakte aus Ihren Telefonkontakten oder fügen Sie eine Bulk-Liste mit Telefonnummern hinzu.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showImportContactsDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Aus Systemkontakten", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { showHotBoxImportDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0F172A),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFF00FF87)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Bulk-Import (Hotbox)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (showImportContactsDialog) {
            SystemContactImportDialog(
                viewModel = viewModel,
                onDismiss = { showImportContactsDialog = false }
            )
        }

        if (showHotBoxImportDialog) {
            HotBoxImportDialog(
                onDismiss = { showHotBoxImportDialog = false },
                onImport = { rawText ->
                    viewModel.importNumbersToHotBox(rawText) { _, created, skipped ->
                        val msg = if (skipped > 0) {
                            "$created neue Leads erstellt ($skipped bereits vorhandene übersprungen). 📂"
                        } else {
                            "$created neue Leads erstellt! 🚀"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        showHotBoxImportDialog = false
                    }
                }
            )
        }
    }
}

@Composable
fun WiedervorlagenTabContent(
    viewModel: StromrufViewModel,
    activeFollowUps: List<FollowUpEntity>,
    onAddFollowUpClick: () -> Unit,
    onSelectRingtoneClick: () -> Unit,
    selectedRingtoneTitle: String
) {
    val grouped = remember(activeFollowUps) {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfToday = cal.timeInMillis
        
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        val endOfToday = cal.timeInMillis

        val endOfWeek = startOfToday + 7 * 24 * 60 * 60 * 1000 - 1

        val overdueList = activeFollowUps.filter { it.dueAt < startOfToday }
        val todayList = activeFollowUps.filter { it.dueAt in startOfToday..endOfToday }
        val weekList = activeFollowUps.filter { it.dueAt in (endOfToday + 1)..endOfWeek }
        val laterList = activeFollowUps.filter { it.dueAt > endOfWeek }

        linkedMapOf(
            "Überfällig" to overdueList,
            "Heute fällig" to todayList,
            "Diese Woche" to weekList,
            "Später" to laterList
        )
    }

    val totalCount = activeFollowUps.size

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(16.dp))
        
        // Beautiful Material 3 Action Row for Adding & Sound Configuration
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // "+" Button to add manual Follow-Up
            Button(
                onClick = onAddFollowUpClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF87), contentColor = Color(0xFF040A08)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Wiedervorlage hinzufügen")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Hinzufügen", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            
            // "Wecker-Ton" button to configure sound
            OutlinedButton(
                onClick = onSelectRingtoneClick,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFF00FF87).copy(alpha = 0.3f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00FF87)),
                modifier = Modifier
                    .weight(1.2f)
                    .height(48.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Weckton auswählen",
                    tint = Color(0xFF00FF87),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column(horizontalAlignment = Alignment.Start) {
                    Text("Wecker-Ton", fontSize = 9.sp, color = Color(0xFF64748B))
                    Text(
                        text = selectedRingtoneTitle,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        if (totalCount == 0) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Check, 
                        contentDescription = null, 
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Keine fälligen Wiedervorlagen mehr offen.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                grouped.forEach { (header, list) ->
                    if (list.isNotEmpty()) {
                        item {
                            val tintColor = when (header) {
                                "Überfällig" -> Color(0xFFEF4444)
                                "Heute fällig" -> Color(0xFF00FF87)
                                else -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            }
                            Text(
                                text = "$header (${list.size})",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    color = tintColor
                                ),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }

                        items(list, key = { it.id }) { followup ->
                            FollowUpCard(
                                followup = followup,
                                overdue = header == "Überfällig",
                                onCallClick = { viewModel.initiateCall(followup.contactPhone, followup.contactName, followup.contactId, callType = "rueckruf") },
                                onCompleteClick = { viewModel.completeFollowUp(followup.id) },
                                onDeleteClick = { viewModel.deleteFollowUp(followup.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HotBoxImportDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit
) {
    val context = LocalContext.current
    var pastedText by remember { mutableStateOf("") }
    
    val fileLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val text = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                pastedText = text
                Toast.makeText(context, "Datei geladen! Klicken Sie auf Importieren.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Fehler: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Excel / Nummernliste importieren 📂",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                
                Text(
                    text = "Fügen Sie eine Liste von Rufnummern ein oder laden Sie eine Datei (CSV/TXT). Wir suchen automatisch nach allen Nummern und aktivieren sie in der Hot Box.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                OutlinedTextField(
                    value = pastedText,
                    onValueChange = { pastedText = it },
                    placeholder = { Text("Nummern hier einfügen (z.B. 01721234567, +491519876543, ...)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                )
                
                Button(
                    onClick = { fileLauncher.launch("*/*") },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Aus Datei importieren (CSV/TXT) 📄")
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Abbrechen")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (pastedText.isNotBlank()) {
                                onImport(pastedText)
                            } else {
                                Toast.makeText(context, "Bitte Text oder Nummern eingeben!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Text("Importieren")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KontakteTabContent(
    viewModel: StromrufViewModel,
    contacts: List<ContactEntity>,
    searchQuery: String,
    onAddContactClick: () -> Unit,
    onImportContactsClick: () -> Unit,
    onEditContactClick: (ContactEntity) -> Unit,
    onContactSelect: (ContactEntity) -> Unit
) {
    // Local confirmation state for inline deletion
    var confirmDeleteId by remember { mutableStateOf<String?>(null) }
    var activeSubTab by remember { mutableStateOf("hotbox") } // "alle" or "hotbox"
    val contactsListState = androidx.compose.foundation.lazy.rememberLazyListState()
    var showHotBoxImportDialog by remember { mutableStateOf(false) }
    var isCockpitCollapsed by remember { mutableStateOf(false) }
    var isNextHotBoxContactCollapsed by remember { mutableStateOf(false) }
    var showOnlyUncalled by remember { mutableStateOf(false) }
    var contactForReachabilityEdit by remember { mutableStateOf<ContactEntity?>(null) }
    val isAutoCallActive by viewModel.isAutoCallActive.collectAsState()
    val autoCallCountdown by viewModel.autoCallCountdown.collectAsState()
    val lastCalledHotBoxContactId by viewModel.lastCalledHotBoxContactId.collectAsState()
    val nextHotBoxContactId by viewModel.nextHotBoxContactId.collectAsState()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("stromruf_prefs", android.content.Context.MODE_PRIVATE) }

    val hotBoxLists by viewModel.hotBoxLists.collectAsState()
    val selectedHotBoxList by viewModel.selectedHotBoxListName.collectAsState()
    val selectedHotBoxListNames by viewModel.selectedHotBoxListNames.collectAsState()
    LaunchedEffect(selectedHotBoxListNames) {
        val selectedStr = selectedHotBoxListNames.joinToString(",")
        prefs.edit().putString("last_selected_hotbox_lists", selectedStr).apply()
    }
    var showCreateListDialog by remember { mutableStateOf(false) }
    var newListNameInput by remember { mutableStateOf("") }
    var showDeleteListConfirmation by remember { mutableStateOf<String?>(null) }

    var showPromisedAnnahmen by remember { mutableStateOf(false) }
    val promisedAnnahmen by viewModel.promisedAnnahmen.collectAsState()
    val isPromisedThroughCallActive by viewModel.isPromisedThroughCallActive.collectAsState()
    val promisedThroughCallCountdown by viewModel.promisedThroughCallCountdown.collectAsState()

    var promisedCustomerNumber by remember { mutableStateOf("") }
    var promisedName by remember { mutableStateOf("") }
    var promisedPhone by remember { mutableStateOf("") }

    // Keep track of contact IDs that are or were in HotBox during this screen session
    val keptInHotBoxIds = remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(contacts) {
        val currentHotBox = contacts.filter { it.isHotBox }.map { it.id }.toSet()
        keptInHotBoxIds.value = keptInHotBoxIds.value + currentHotBox
    }

    val filtered = remember(contacts, searchQuery, activeSubTab, selectedHotBoxListNames, keptInHotBoxIds.value, showOnlyUncalled, nextHotBoxContactId) {
        contacts.filter {
            val cleanQuery = searchQuery.replace("[^\\d]".toRegex(), "")
            val cleanPhone = it.phone.replace("[^\\d]".toRegex(), "")
            val matchesPhone = if (cleanQuery.isNotEmpty()) {
                cleanPhone.contains(cleanQuery) || it.phone.contains(searchQuery)
            } else {
                it.phone.contains(searchQuery)
            }
            val matchesQuery = it.name.contains(searchQuery, ignoreCase = true) ||
                    matchesPhone ||
                    (it.company ?: "").contains(searchQuery, ignoreCase = true)
            val matchesSubTab = if (activeSubTab == "hotbox") {
                val isMatchingList = viewModel.getEffectiveHotBoxListName(it.hotBoxListName) in selectedHotBoxListNames
                
                val belongsToCampaign = (it.isHotBox && isMatchingList || keptInHotBoxIds.value.contains(it.id))
                val matchesUncalled = if (showOnlyUncalled) !it.hasBeenCalledInHotCycle else true
                belongsToCampaign && matchesUncalled
            } else {
                true
            }
            matchesQuery && matchesSubTab
        }.sortedWith(compareBy<ContactEntity> {
            if (activeSubTab == "hotbox" && it.id == nextHotBoxContactId) 0 else 1
        }.thenBy { it.name.lowercase() })
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        if (showPromisedAnnahmen) {
            // --- Versprochene Annahmen View ---
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                IconButton(onClick = { showPromisedAnnahmen = false }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Zurück", tint = Color.White)
                }
                Text(
                    text = "Versprochene Annahmen",
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White,
                        letterSpacing = 0.5.sp
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Add form card
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Neuen Kunden eintragen 📝",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF00FF87)
                    )

                    OutlinedTextField(
                        value = promisedCustomerNumber,
                        onValueChange = { promisedCustomerNumber = it },
                        label = { Text("Kundennummer", color = Color.White.copy(alpha = 0.6f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00FF87),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedLabelColor = Color(0xFF00FF87),
                            unfocusedLabelColor = Color.White,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = promisedName,
                        onValueChange = { promisedName = it },
                        label = { Text("Name", color = Color.White.copy(alpha = 0.6f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00FF87),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedLabelColor = Color(0xFF00FF87),
                            unfocusedLabelColor = Color.White,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = promisedPhone,
                        onValueChange = { promisedPhone = it },
                        label = { Text("Telefonnummer", color = Color.White.copy(alpha = 0.6f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00FF87),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedLabelColor = Color(0xFF00FF87),
                            unfocusedLabelColor = Color.White,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            if (promisedCustomerNumber.isNotBlank() && promisedPhone.isNotBlank()) {
                                viewModel.savePromisedAnnahme(promisedCustomerNumber, promisedName, promisedPhone)
                                promisedCustomerNumber = ""
                                promisedName = ""
                                promisedPhone = ""
                            } else {
                                Toast.makeText(context, "Kundennummer und Telefonnummer erforderlich!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF87)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Eintragen", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Autopilot Control Cockpit
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                border = BorderStroke(1.dp, Color(0xFF00FF87).copy(alpha = 0.3f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Autopilot: Durchrufen 🤖",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Wartezeit nach Auflegen: 3 Sek.",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Reset Called status
                            IconButton(
                                onClick = { viewModel.resetPromisedAnnahmenCalled() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Zurücksetzen", tint = Color(0xFF00FF87))
                            }

                            Button(
                                onClick = { viewModel.setPromisedThroughCallActive(!isPromisedThroughCallActive) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isPromisedThroughCallActive) Color(0xFFEF4444) else Color(0xFF00FF87)
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(
                                    text = if (isPromisedThroughCallActive) {
                                        val secs = promisedThroughCallCountdown
                                        if (secs != null) "STOP (${secs}s) 🛑" else "STOPPEN 🛑"
                                    } else "STARTEN 🚀",
                                    color = if (isPromisedThroughCallActive) Color.White else Color.Black,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // List of promised customers
            Text(
                text = "Kundenliste (${promisedAnnahmen.size}):",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (promisedAnnahmen.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Keine versprochenen Annahmen eingetragen.",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                ) {
                    items(promisedAnnahmen, key = { it.id }) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            border = BorderStroke(1.dp, if (item.isCalled) Color.White.copy(alpha = 0.1f) else Color(0xFF00FF87).copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = item.name.ifBlank { "Unbekannt" },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color.White
                                        )
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    if (item.isCalled) Color.White.copy(alpha = 0.1f) else Color(0xFF00FF87).copy(alpha = 0.15f),
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (item.isCalled) "Angerufen" else "Offen",
                                                color = if (item.isCalled) Color.White.copy(alpha = 0.6f) else Color(0xFF00FF87),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Knd.-Nr.: ${item.customerNumber}",
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                    Text(
                                        text = "Tel: ${item.phone}",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Call button
                                    IconButton(
                                        onClick = {
                                            viewModel.updatePromisedAnnahmeStatus(item.id, true)
                                            viewModel.initiateCall(item.phone, item.name, callType = "promised_annahme")
                                        },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(Color(0xFF10B981), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Phone,
                                            contentDescription = "Anrufen",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Delete button
                                    IconButton(
                                        onClick = { viewModel.deletePromisedAnnahme(item.id) },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(Color.Red.copy(alpha = 0.15f), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Löschen",
                                            tint = Color.Red,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        } else {
            val hideHeaderForFullscreen = activeSubTab == "hotbox" && isNextHotBoxContactCollapsed

            if (hideHeaderForFullscreen) {
                androidx.activity.compose.BackHandler {
                    isNextHotBoxContactCollapsed = false
                    isCockpitCollapsed = false
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF991B1B)), // Beautiful Crimson/Red for Hotbox
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Color(0xFF00FF87), CircleShape)
                            )
                            Text(
                                text = "Vollbildmodus aktiv 🔥 (Zurück-Taste für Normal)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Autopilot Start/Stop button directly in the Vollbild banner!
                            Box(
                                modifier = Modifier
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isAutoCallActive) Color(0xFFEF4444) else Color(0xFF10B981))
                                    .combinedClickable(
                                        onClick = { viewModel.setAutoCallActive(!isAutoCallActive) },
                                        onLongClick = {
                                            val targetContact = contacts.find { it.id == nextHotBoxContactId } ?: contacts.firstOrNull { it.isHotBox }
                                            if (targetContact != null) {
                                                val digitsOnly = CustomerNumberExtractor.extractCustomerNumber(
                                                    targetContact.name,
                                                    targetContact.company,
                                                    targetContact.callReason,
                                                    targetContact.phone
                                                ) ?: ""
                                                if (digitsOnly.isNotEmpty()) {
                                                    try {
                                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                        val clip = android.content.ClipData.newPlainText("Kundennummer", digitsOnly)
                                                        clipboard.setPrimaryClip(clip)
                                                        Toast.makeText(context, "Kundennummer $digitsOnly kopiert! 📋", Toast.LENGTH_SHORT).show()
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Fehler beim Kopieren: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                    }
                                                } else {
                                                    Toast.makeText(context, "Keine Kundennummer im Namen gefunden! ⚠️", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                Toast.makeText(context, "Kein Hotbox-Kontakt vorhanden! ⚠️", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isAutoCallActive) "STOP 🛑" else "START 🤖",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAutoCallActive) Color.White else Color.Black
                                )
                            }

                            Button(
                                onClick = { 
                                    isNextHotBoxContactCollapsed = false 
                                    isCockpitCollapsed = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF87)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Vollbild beenden",
                                    tint = Color.Black,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Ausklappen ↗️",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }

            if (!hideHeaderForFullscreen) {
                Spacer(modifier = Modifier.height(16.dp))

                // Search & Add row
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Optionen",
                            tint = Color.White
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Versprochene Annahmen") },
                            onClick = {
                                showMenu = false
                                showPromisedAnnahmen = true
                            }
                        )
                        if (activeSubTab == "hotbox") {
                            DropdownMenuItem(
                                text = { Text("Importieren 📂") },
                                onClick = {
                                    showMenu = false
                                    showHotBoxImportDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Zyklus resetten 🔄") },
                                onClick = {
                                    showMenu = false
                                    viewModel.resetCurrentHotBoxCycle()
                                    Toast.makeText(context, "Kampagnen-Zyklus manuell zurückgesetzt! 🔄", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.searchContacts(it) },
                    placeholder = { Text("Suchen...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    textStyle = TextStyle(fontSize = 13.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF10B981),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedContainerColor = Color(0xFF1E293B),
                        unfocusedContainerColor = Color(0xFF1E293B),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedPlaceholderColor = Color(0xFF94A3B8),
                        unfocusedPlaceholderColor = Color(0xFF94A3B8),
                        focusedLeadingIconColor = Color(0xFF94A3B8),
                        unfocusedLeadingIconColor = Color(0xFF94A3B8)
                    ),
                    modifier = Modifier
                        .height(40.dp)
                        .weight(1f)
                )

                Button(
                    onClick = onImportContactsClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 40.dp).height(40.dp)
                ) {
                    Icon(Icons.Default.Person, contentDescription = "Aus Systemkontakten importieren", modifier = Modifier.size(18.dp))
                }

                Button(
                    onClick = onAddContactClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 40.dp).height(40.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Plusschaltfläche", modifier = Modifier.size(18.dp))
                }
            }

            // Horizontal scrolling chips of Sub-Tabs
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // First item: "Alle Kontakte"
                item {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (activeSubTab == "alle") Color(0xFF0F172A) else Color.Transparent)
                            .clickable { activeSubTab = "alle" }
                            .padding(horizontal = 14.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Alle Kontakte 👥",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (activeSubTab == "alle") Color.White else Color(0xFF94A3B8)
                        )
                    }
                }
                
                // Then each hotbox list
                items(hotBoxLists.toList()) { listName ->
                    val isSelected = activeSubTab == "hotbox" && selectedHotBoxListNames.contains(listName)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) Color(0xFFEF4444) else Color.Transparent)
                            .combinedClickable(
                                onClick = { 
                                    viewModel.toggleHotBoxListSelection(listName)
                                    activeSubTab = "hotbox" 
                                },
                                onLongClick = {
                                    viewModel.selectHotBoxList(listName)
                                    activeSubTab = "hotbox"
                                }
                            )
                            .padding(horizontal = 14.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$listName 🔥",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (isSelected) Color.White else Color(0xFF94A3B8)
                        )
                    }
                }
                
                // Plus button to add a new list
                item {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF0F172A).copy(alpha = 0.5f))
                            .clickable { showCreateListDialog = true }
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Neue Liste",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Neue Liste",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF10B981)
                            )
                        }
                    }
                }
            }

        if (activeSubTab == "hotbox") {
            val campaignLeads = remember(contacts, selectedHotBoxListNames) {
                contacts.filter {
                    val isMatchingList = viewModel.getEffectiveHotBoxListName(it.hotBoxListName) in selectedHotBoxListNames
                    it.isHotBox && isMatchingList
                }
            }
            val totalCount = campaignLeads.size
            val uncalledCount = campaignLeads.count { !it.hasBeenCalledInHotCycle }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Toggle "Alle Leads"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (!showOnlyUncalled) Color(0xFFEF4444) else Color(0xFF1E293B))
                        .clickable { showOnlyUncalled = false }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Alle Leads ($totalCount) 👥",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color.White
                    )
                }

                // Toggle "Nur Offene"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (showOnlyUncalled) Color(0xFFEF4444) else Color(0xFF1E293B))
                        .clickable { showOnlyUncalled = true }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color(0xFF00FF87), CircleShape)
                        )
                        Text(
                            text = "Nur Offene ($uncalledCount) 🎯",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Tab focuses entirely on Hotbox campaign leads

        // Compact Cockpit and calling card for Hot Box
        if (activeSubTab == "hotbox") {
            val uncalledCount = filtered.count { !it.hasBeenCalledInHotCycle }
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFFCA5A5), RoundedCornerShape(12.dp))
                    .padding(bottom = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isCockpitCollapsed) 0.dp else 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isCockpitCollapsed = !isCockpitCollapsed }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            val selectedListsStr = selectedHotBoxListNames.joinToString(", ")
                            Text(
                                text = "Hot Box: $selectedListsStr 🔥",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF991B1B),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (selectedHotBoxListNames.size == 1 && !isCockpitCollapsed) {
                                IconButton(
                                    onClick = { showDeleteListConfirmation = selectedHotBoxList },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Liste löschen",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.wrapContentWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFEF4444).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "Noch $uncalledCount von ${filtered.size} offen",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEF4444)
                                )
                            }
                            IconButton(
                                onClick = { isCockpitCollapsed = !isCockpitCollapsed },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = if (isCockpitCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                    contentDescription = if (isCockpitCollapsed) "Erweitern" else "Minimieren",
                                    tint = Color(0xFF991B1B),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    if (!isCockpitCollapsed) {
                        // Autopilot cockpit banner
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isAutoCallActive) Color(0xFFEF4444).copy(alpha = 0.1f) else Color.White.copy(alpha = 0.05f),
                                    RoundedCornerShape(8.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isAutoCallActive) Color(0xFFEF4444) else Color.White.copy(alpha = 0.15f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(
                                            if (isAutoCallActive) Color(0xFFEF4444) else Color(0xFF94A3B8),
                                            CircleShape
                                        )
                                )
                                Text(
                                    text = if (isAutoCallActive) {
                                        val secs = autoCallCountdown
                                        if (secs != null) {
                                            "AUTOPILOT AKTIV 🚀 (${secs}s)"
                                        } else {
                                            "AUTOPILOT AKTIV 🚀"
                                        }
                                    } else {
                                        "Autopilot inaktiv"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAutoCallActive) Color(0xFFEF4444) else Color.White.copy(alpha = 0.7f)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isAutoCallActive) Color(0xFFEF4444) else Color(0xFF10B981))
                                    .combinedClickable(
                                        onClick = { viewModel.setAutoCallActive(!isAutoCallActive) },
                                        onLongClick = {
                                            val targetContact = contacts.find { it.id == nextHotBoxContactId } ?: contacts.firstOrNull { it.isHotBox }
                                            if (targetContact != null) {
                                                val digitsOnly = CustomerNumberExtractor.extractCustomerNumber(
                                                    targetContact.name,
                                                    targetContact.company,
                                                    targetContact.callReason,
                                                    targetContact.phone
                                                ) ?: ""
                                                if (digitsOnly.isNotEmpty()) {
                                                    try {
                                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                        val clip = android.content.ClipData.newPlainText("Kundennummer", digitsOnly)
                                                        clipboard.setPrimaryClip(clip)
                                                        Toast.makeText(context, "Kundennummer $digitsOnly kopiert! 📋", Toast.LENGTH_SHORT).show()
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Fehler beim Kopieren: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                    }
                                                } else {
                                                    Toast.makeText(context, "Keine Kundennummer im Namen gefunden! ⚠️", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                Toast.makeText(context, "Kein Hotbox-Kontakt vorhanden! ⚠️", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                    .padding(horizontal = 10.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isAutoCallActive) "STOP 🛑" else "START 🤖",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAutoCallActive) Color.White else Color.Black
                                )
                            }
                        }
                    }
                }
            }
        }

            // Find next hotbox contact and show them at the very top of the list prominently!
            val nextHotBoxContact = remember(contacts, nextHotBoxContactId) {
                contacts.find { it.id == nextHotBoxContactId }
            }
            
            if (activeSubTab == "hotbox" && nextHotBoxContact != null && !hideHeaderForFullscreen) {
                if (isNextHotBoxContactCollapsed) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .combinedClickable(
                                onClick = { isNextHotBoxContactCollapsed = false },
                                onLongClick = { viewModel.skipNextHotBoxContact() }
                            )
                            .border(1.2.dp, Color(0xFF00FF87).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFF00FF87), CircleShape)
                                )
                                Text(
                                    text = "NÄCHSTER HOTBOX-ANRUF 🔥: ${nextHotBoxContact.name}",
                                    color = Color(0xFF00FF87),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Erweitern",
                                tint = Color(0xFF00FF87),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .combinedClickable(
                                onClick = { /* Keep expanded or do nothing on short click */ },
                                onLongClick = { viewModel.skipNextHotBoxContact() }
                            )
                            .border(1.5.dp, Color(0xFF00FF87).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.clickable { isNextHotBoxContactCollapsed = true }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(Color(0xFF00FF87), CircleShape)
                                        )
                                        Text(
                                            text = "NÄCHSTER HOTBOX-ANRUF 🔥",
                                            color = Color(0xFF00FF87),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            letterSpacing = 1.sp
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowUp,
                                            contentDescription = "Einklappen",
                                            tint = Color(0xFF00FF87).copy(alpha = 0.7f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = nextHotBoxContact.name,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (!nextHotBoxContact.company.isNullOrBlank()) {
                                        Text(
                                            text = nextHotBoxContact.company!!,
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = nextHotBoxContact.phone,
                                        color = Color(0xFF00FF87).copy(alpha = 0.8f),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp
                                    )
                                }
                                
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Autopilot Start/Stop button next to the call button!
                                    Box(
                                        modifier = Modifier
                                            .height(48.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isAutoCallActive) Color(0xFFEF4444) else Color(0xFF10B981))
                                            .combinedClickable(
                                                onClick = { viewModel.setAutoCallActive(!isAutoCallActive) },
                                                onLongClick = {
                                                    val targetContact = contacts.find { it.id == nextHotBoxContactId } ?: contacts.firstOrNull { it.isHotBox }
                                                    if (targetContact != null) {
                                                        val digitsOnly = CustomerNumberExtractor.extractCustomerNumber(
                                                            targetContact.name,
                                                            targetContact.company,
                                                            targetContact.callReason,
                                                            targetContact.phone
                                                        ) ?: ""
                                                        if (digitsOnly.isNotEmpty()) {
                                                            try {
                                                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                                val clip = android.content.ClipData.newPlainText("Kundennummer", digitsOnly)
                                                                clipboard.setPrimaryClip(clip)
                                                                Toast.makeText(context, "Kundennummer $digitsOnly kopiert! 📋", Toast.LENGTH_SHORT).show()
                                                            } catch (e: Exception) {
                                                                Toast.makeText(context, "Fehler beim Kopieren: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                            }
                                                        } else {
                                                            Toast.makeText(context, "Keine Kundennummer im Namen gefunden! ⚠️", Toast.LENGTH_SHORT).show()
                                                        }
                                                    } else {
                                                        Toast.makeText(context, "Kein Hotbox-Kontakt vorhanden! ⚠️", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            )
                                            .padding(horizontal = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (isAutoCallActive) "STOP 🛑" else "START 🤖",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isAutoCallActive) Color.White else Color.Black
                                        )
                                    }

                                    Button(
                                        onClick = { viewModel.initiateCall(nextHotBoxContact.phone, nextHotBoxContact.name, nextHotBoxContact.id) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                        shape = CircleShape,
                                        modifier = Modifier.size(48.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Call,
                                            contentDescription = "Jetzt anrufen",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }

                            androidx.compose.material3.HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Hotbox toggle button
                                    IconButton(
                                        onClick = { viewModel.toggleHotBox(nextHotBoxContact.id) },
                                        modifier = Modifier
                                            .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                            .size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = "Hot Box umschalten",
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Reachability button
                                    IconButton(
                                        onClick = { contactForReachabilityEdit = nextHotBoxContact },
                                        modifier = Modifier
                                            .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                            .size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DateRange,
                                            contentDescription = "Erreichbarkeit bearbeiten",
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Edit button
                                    IconButton(
                                        onClick = { onEditContactClick(nextHotBoxContact) },
                                        modifier = Modifier
                                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                            .size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Datensatz bearbeiten",
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Delete button
                                    var showDeleteConfirm by remember { mutableStateOf(false) }
                                    if (showDeleteConfirm) {
                                        Text("Löschen?", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                                        Button(
                                            onClick = {
                                                viewModel.deleteContact(nextHotBoxContact.id)
                                                showDeleteConfirm = false
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 32.dp),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text("Ja", fontSize = 11.sp, color = Color.White)
                                        }
                                        OutlinedButton(
                                            onClick = { showDeleteConfirm = false },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 32.dp),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text("Nein", fontSize = 11.sp, color = Color.White)
                                        }
                                    } else {
                                        IconButton(
                                            onClick = { showDeleteConfirm = true },
                                            modifier = Modifier
                                                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                                .size(34.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Kunde löschen",
                                                tint = Color(0xFFEF4444),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showHotBoxImportDialog) {
            HotBoxImportDialog(
                onDismiss = { showHotBoxImportDialog = false },
                onImport = { rawText ->
                    viewModel.importNumbersToHotBox(rawText) { _, created, skipped ->
                        val msg = if (skipped > 0) {
                            "$created neue Leads erstellt ($skipped bereits vorhandene übersprungen). 📂"
                        } else {
                            "$created neue Leads erstellt! 🚀"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        showHotBoxImportDialog = false
                    }
                }
            )
        }

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (activeSubTab == "hotbox") {
                        "Keine Heiß-Kontakte in der Hot Box 🔥 vorhanden."
                    } else if (contacts.isEmpty()) {
                        "Noch keine Kontakte gespeichert."
                    } else {
                        "Keine Treffer."
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }
        } else {
            LazyColumn(
                state = contactsListState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                if (activeSubTab == "hotbox") {
                    val activeHotBox = filtered.filter { it.isHotBox }
                    val standbyHotBox = filtered.filter { !it.isHotBox }

                    items(activeHotBox, key = { it.id }) { contact ->
                        ContactRow(
                            contact = contact,
                            isConfirmingDelete = confirmDeleteId == contact.id,
                            onCallClick = { viewModel.initiateCall(contact.phone, contact.name, contact.id) },
                            onEditClick = { onEditContactClick(contact) },
                            onDeleteClick = {
                                if (confirmDeleteId == contact.id) {
                                    viewModel.deleteContact(contact.id)
                                    confirmDeleteId = null
                                } else {
                                    confirmDeleteId = contact.id
                                }
                            },
                            onCancelDeleteClick = { confirmDeleteId = null },
                            onToggleHotBox = { viewModel.toggleHotBox(contact.id) },
                            onReachabilityClick = { contactForReachabilityEdit = contact },
                            onContactSelect = { onContactSelect(contact) },
                            isNextUp = contact.id == nextHotBoxContactId,
                            isLastCalled = contact.id == lastCalledHotBoxContactId,
                            hotBoxLists = hotBoxLists,
                            onAddToHotBoxList = { listName -> viewModel.addToHotBoxList(contact.id, listName) }
                        )
                    }

                    if (standbyHotBox.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "Standby Kontakte",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                )
                                Text(
                                    text = "Diese Kontakte wurden aus der Hotbox entfernt, bleiben aber für diese Sitzung hier sichtbar.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                )
                            }
                        }

                        items(standbyHotBox, key = { it.id }) { contact ->
                            ContactRow(
                                contact = contact,
                                isConfirmingDelete = confirmDeleteId == contact.id,
                                onCallClick = { viewModel.initiateCall(contact.phone, contact.name, contact.id) },
                                onEditClick = { onEditContactClick(contact) },
                                onDeleteClick = {
                                    if (confirmDeleteId == contact.id) {
                                        viewModel.deleteContact(contact.id)
                                        confirmDeleteId = null
                                    } else {
                                        confirmDeleteId = contact.id
                                    }
                                },
                                onCancelDeleteClick = { confirmDeleteId = null },
                                onToggleHotBox = { viewModel.toggleHotBox(contact.id) },
                                onReachabilityClick = { contactForReachabilityEdit = contact },
                                onContactSelect = { onContactSelect(contact) },
                                hotBoxLists = hotBoxLists,
                                onAddToHotBoxList = { listName -> viewModel.addToHotBoxList(contact.id, listName) }
                            )
                        }
                    }
                } else {
                    items(filtered, key = { it.id }) { contact ->
                        ContactRow(
                            contact = contact,
                            isConfirmingDelete = confirmDeleteId == contact.id,
                            onCallClick = { viewModel.initiateCall(contact.phone, contact.name, contact.id) },
                            onEditClick = { onEditContactClick(contact) },
                            onDeleteClick = {
                                if (confirmDeleteId == contact.id) {
                                    viewModel.deleteContact(contact.id)
                                    confirmDeleteId = null
                                } else {
                                    confirmDeleteId = contact.id
                                }
                            },
                            onCancelDeleteClick = { confirmDeleteId = null },
                            onToggleHotBox = { viewModel.toggleHotBox(contact.id) },
                            onReachabilityClick = { contactForReachabilityEdit = contact },
                            onContactSelect = { onContactSelect(contact) },
                            hotBoxLists = hotBoxLists,
                            onAddToHotBoxList = { listName -> viewModel.addToHotBoxList(contact.id, listName) }
                        )
                    }
                }
            }
        }

        contactForReachabilityEdit?.let { contact ->
            QuickReachabilityDialog(
                contact = contact,
                onDismiss = { contactForReachabilityEdit = null },
                onSave = { startHour, endHour, weekdays ->
                    val updated = contact.copy(
                        hotBoxStartHour = startHour,
                        hotBoxEndHour = endHour,
                        hotBoxWeekdays = weekdays
                    )
                    viewModel.editContact(updated)
                    contactForReachabilityEdit = null
                }
            )
        }

        if (showCreateListDialog) {
            AlertDialog(
                onDismissRequest = { showCreateListDialog = false },
                title = { Text("Neue Hotbox-Liste erstellen") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Gib einen Namen für deine neue Kampagnenliste ein:")
                        OutlinedTextField(
                            value = newListNameInput,
                            onValueChange = { newListNameInput = it },
                            placeholder = { Text("z.B. Leads Juli") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val trimmedName = newListNameInput.trim()
                            if (trimmedName.isNotEmpty()) {
                                viewModel.addHotBoxList(trimmedName)
                                viewModel.selectHotBoxList(trimmedName)
                                activeSubTab = "hotbox"
                                newListNameInput = ""
                                showCreateListDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Text("Erstellen")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateListDialog = false }) {
                        Text("Abbrechen")
                    }
                }
            )
        }

        showDeleteListConfirmation?.let { listToDelete ->
            AlertDialog(
                onDismissRequest = { showDeleteListConfirmation = null },
                title = { Text("Liste löschen?") },
                text = {
                    Text("Möchtest du die Liste '$listToDelete' wirklich löschen? Alle zugeordneten Kontakte werden aus der Hotbox entfernt.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.removeHotBoxList(listToDelete)
                            showDeleteListConfirmation = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        Text("Löschen", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteListConfirmation = null }) {
                        Text("Abbrechen")
                    }
                }
            )
        }
        }
    }
}

// --- VIEWS COMPONENTS ---

@Composable
fun ActiveCallBanner(
    call: ActiveCall,
    onWrapup: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)), // soft emerald-100/50
        border = BorderStroke(1.dp, Color(0xFF10B981)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF10B981), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Anruf läuft: ${call.name ?: "Unbekannt"}",
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
                    )
                    Text(
                        text = call.phone,
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFF475569))
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF475569))
                ) {
                    Text("Verwerfen", fontSize = 12.sp)
                }
                Button(
                    onClick = onWrapup,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 32.dp)
                ) {
                    Text("Erfassen", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun FollowUpCard(
    followup: FollowUpEntity,
    overdue: Boolean,
    showTimeOnly: Boolean = false,
    onCallClick: () -> Unit,
    onCompleteClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onRescheduleClick: ((Long) -> Unit)? = null
) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (overdue) Color(0xFFFCA5A5) else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = followup.contactName,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = followup.contactPhone,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
                
                // Due date or Time highlight chip
                if (showTimeOnly) {
                    val sdfTime = remember { SimpleDateFormat("HH:mm", Locale.GERMANY) }
                    val timeText = sdfTime.format(Date(followup.dueAt)) + " Uhr"
                    Box(
                        modifier = Modifier
                            .background(
                                if (overdue) Color(0xFFFEF2F2) else Color(0xFFE0F2FE),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .border(
                                1.dp,
                                if (overdue) Color(0xFFFCA5A5) else Color(0xFF3B82F6).copy(alpha = 0.3f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                tint = if (overdue) Color(0xFFEF4444) else Color(0xFF2563EB),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = timeText,
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (overdue) Color(0xFFEF4444) else Color(0xFF2563EB)
                                )
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .background(
                                if (overdue) Color(0xFFFEF2F2) else Color(0xFFFEF3C7),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = fmtDateTime(followup.dueAt),
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (overdue) Color(0xFFEF4444) else Color(0xFFB45309)
                            )
                        )
                    }
                }
            }

            if (!followup.callReason.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .background(Color(0xFF10B981).copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = followup.callReason,
                        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                    )
                }
            }

            if (!followup.note.isNullOrBlank()) {
                var isExpanded by remember { mutableStateOf(false) }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = followup.note,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    ),
                    maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                    overflow = if (isExpanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExpanded = !isExpanded }
                        .animateContentSize()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action row buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onCallClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Anrufen")
                }

                // Completed (abgehakt) button
                IconButton(
                    onClick = onCompleteClick,
                    modifier = Modifier
                        .border(1.dp, Color(0xFFD1D5DB), RoundedCornerShape(8.dp))
                        .size(40.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Als erledigt markieren", tint = Color(0xFF10B981))
                }

                // Reschedule Kalender Button
                if (onRescheduleClick != null) {
                    IconButton(
                        onClick = {
                            val currentCalendar = Calendar.getInstance().apply { timeInMillis = followup.dueAt }
                            val dateDialog = DatePickerDialog(
                                context,
                                null,
                                currentCalendar.get(Calendar.YEAR),
                                currentCalendar.get(Calendar.MONTH),
                                currentCalendar.get(Calendar.DAY_OF_MONTH)
                            )
                            dateDialog.datePicker.init(
                                currentCalendar.get(Calendar.YEAR),
                                currentCalendar.get(Calendar.MONTH),
                                currentCalendar.get(Calendar.DAY_OF_MONTH)
                            ) { _, year, month, dayOfMonth ->
                                dateDialog.dismiss()
                                TimePickerDialog(
                                    context,
                                    { _, hourOfDay, minute ->
                                        val cal = Calendar.getInstance().apply {
                                            set(Calendar.YEAR, year)
                                            set(Calendar.MONTH, month)
                                            set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                            set(Calendar.HOUR_OF_DAY, hourOfDay)
                                            set(Calendar.MINUTE, minute)
                                            set(Calendar.SECOND, 0)
                                            set(Calendar.MILLISECOND, 0)
                                        }
                                        onRescheduleClick(cal.timeInMillis)
                                    },
                                    currentCalendar.get(Calendar.HOUR_OF_DAY),
                                    currentCalendar.get(Calendar.MINUTE),
                                    true
                                ).show()
                            }
                            dateDialog.show()
                        },
                        modifier = Modifier
                            .border(1.dp, Color(0xFFD1D5DB), RoundedCornerShape(8.dp))
                            .size(40.dp)
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = "Verschieben", tint = Color(0xFF3B82F6))
                    }
                }

                // Delete followup button
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier
                        .border(1.dp, Color(0xFFD1D5DB), RoundedCornerShape(8.dp))
                        .size(40.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Wiedervorlage löschen", tint = Color(0xFFEF4444))
                }
            }
        }
    }
}

@Composable
fun ContactRow(
    contact: ContactEntity,
    isConfirmingDelete: Boolean,
    onCallClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onCancelDeleteClick: () -> Unit,
    onToggleHotBox: () -> Unit,
    onReachabilityClick: () -> Unit,
    onContactSelect: () -> Unit,
    isNextUp: Boolean = false,
    isLastCalled: Boolean = false,
    hotBoxLists: Set<String> = emptySet(),
    onAddToHotBoxList: (String) -> Unit = {}
) {
    val meta = remember(contact.lastOutcome) { getOutcomeMeta(contact.lastOutcome) }
    val context = LocalContext.current

    val isNextUpGlow = isNextUp && contact.isHotBox
    val isLastCalledGlow = isLastCalled && contact.isHotBox

    val borderColor = remember(isNextUpGlow, isLastCalledGlow) {
        when {
            isNextUpGlow -> Color(0xFF00FF87)
            isLastCalledGlow -> Color(0xFF00F0FF)
            else -> Color(0xFFE2E8F0)
        }
    }
    val borderWidth = if (isNextUpGlow || isLastCalledGlow) 2.dp else 1.dp

    val cardBackground = remember(isNextUpGlow, isLastCalledGlow) {
        when {
            isNextUpGlow -> Color(0xFF00FF87).copy(alpha = 0.04f)
            isLastCalledGlow -> Color(0xFF00F0FF).copy(alpha = 0.04f)
            else -> Color(0xFF1E293B)
        }
    }

    val lastCallText = remember(contact.lastCallAt, meta) {
        contact.lastCallAt?.let { "Letzter Anruf: ${fmtDateTime(it)} · ${meta.label}" }
    }

    val timeText = remember(contact.hotBoxStartHour, contact.hotBoxEndHour) {
        if (contact.hotBoxStartHour != null && contact.hotBoxEndHour != null) {
            val startStr = com.example.util.ContactsUtil.formatMinutesToTimeString(contact.hotBoxStartHour)
            val endStr = com.example.util.ContactsUtil.formatMinutesToTimeString(contact.hotBoxEndHour)
            "$startStr - $endStr Uhr"
        } else "Ganzteilig"
    }

    val daysText = remember(contact.hotBoxWeekdays) {
        formatWeekdays(contact.hotBoxWeekdays)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        modifier = Modifier
            .fillMaxWidth()
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
            .pointerInput(contact) {
                detectTapGestures(
                    onLongPress = {
                        val digitsOnly = CustomerNumberExtractor.extractCustomerNumber(
                            contact.name,
                            contact.company,
                            contact.callReason,
                            contact.phone
                        ) ?: ""
                        if (digitsOnly.isNotEmpty()) {
                            try {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Kundennummer", digitsOnly)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Kundennummer $digitsOnly kopiert! 📋", Toast.LENGTH_SHORT).show()
                            } catch (e: java.lang.Exception) {
                                Toast.makeText(context, "Fehler beim Kopieren: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "Keine Kundennummer im Namen gefunden! ⚠️", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onTap = {
                        onContactSelect()
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular initial Avatar
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFFF1F5F9), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = contact.name.take(1).uppercase(),
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF475569))
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = contact.name,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (contact.isHotBox) {
                            if (contact.hasBeenCalledInHotCycle) {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF10B981).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                        .border(1.dp, Color(0xFF10B981), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "ANGERUFEN ✓",
                                        color = Color(0xFF10B981),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFEF4444).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                        .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "OFFEN ⭕",
                                        color = Color(0xFFEF4444),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        if (isNextUpGlow) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF00FF87).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                    .border(1.dp, Color(0xFF00FF87), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "NÄCHSTER 🎯",
                                    color = Color(0xFF00FF87),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (isLastCalledGlow) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF00F0FF).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                    .border(1.dp, Color(0xFF00F0FF), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "ZULETZT 🔄",
                                    color = Color(0xFF00F0FF),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    
                    // Technical layout
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = contact.phone,
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFF64748B))
                        )
                        if (!contact.company.isNullOrBlank()) {
                            Text(
                                text = "· ${contact.company}",
                                style = TextStyle(fontSize = 12.sp, color = Color(0xFF64748B)),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (contact.isHotBox) {
                        val hasTimeLimit = contact.hotBoxStartHour != null && contact.hotBoxEndHour != null
                        val hasDayLimit = !contact.hotBoxWeekdays.isNullOrBlank()
                        if (hasTimeLimit || hasDayLimit) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "Erreichbar: $daysText ($timeText)",
                                    style = TextStyle(fontSize = 11.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }

                    // Last Call history status
                    if (lastCallText != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = lastCallText,
                            style = TextStyle(fontSize = 11.sp, color = meta.color, fontWeight = FontWeight.Medium)
                        )
                    }

                    if (!contact.callReason.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFF1F5F9))
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = contact.callReason,
                                style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            androidx.compose.material3.HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
            Spacer(modifier = Modifier.height(8.dp))

            // Action section row at the bottom right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isConfirmingDelete) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Löschen?", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                        Button(
                            onClick = onDeleteClick,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 32.dp),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("Ja", fontSize = 11.sp, color = Color.White)
                        }
                        OutlinedButton(
                            onClick = onCancelDeleteClick,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 32.dp),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("Nein", fontSize = 11.sp)
                        }
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var showListSelectorMenu by remember { mutableStateOf(false) }

                        Box {
                            IconButton(
                                onClick = {
                                    if (contact.isHotBox) {
                                        onToggleHotBox()
                                    } else {
                                        showListSelectorMenu = true
                                    }
                                },
                                modifier = Modifier
                                    .border(1.dp, if (contact.isHotBox) Color(0xFFEF4444).copy(alpha = 0.3f) else Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                    .size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Hot Box umschalten",
                                    tint = if (contact.isHotBox) Color(0xFFEF4444) else Color(0xFF94A3B8),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showListSelectorMenu,
                                onDismissRequest = { showListSelectorMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("In Hotbox-Liste aufnehmen 🔥", fontWeight = FontWeight.Bold, color = Color(0xFFEF4444), fontSize = 11.sp) },
                                    onClick = {},
                                    enabled = false
                                )
                                val lists = if (hotBoxLists.isEmpty()) setOf("Hotbox") else hotBoxLists
                                lists.forEach { listName ->
                                    DropdownMenuItem(
                                        text = { Text(listName, fontSize = 13.sp) },
                                        onClick = {
                                            onAddToHotBoxList(listName)
                                            showListSelectorMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        if (contact.isHotBox) {
                            IconButton(
                                onClick = onReachabilityClick,
                                modifier = Modifier
                                    .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                    .size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = "Erreichbarkeit bearbeiten",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = onCallClick,
                            modifier = Modifier
                                .background(Color(0xFF10B981), shape = RoundedCornerShape(8.dp))
                                .size(34.dp)
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = "Direkter Kundenanruf", tint = Color.White, modifier = Modifier.size(16.dp))
                        }

                        IconButton(
                            onClick = onEditClick,
                            modifier = Modifier
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                .size(34.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Datensatz bearbeiten", tint = Color(0xFF475569), modifier = Modifier.size(16.dp))
                        }

                        IconButton(
                            onClick = onDeleteClick,
                            modifier = Modifier
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                .size(34.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Kunde löschen", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

// --- FULL SCREEN WRAP-UP SYSTEM DIALOG ---

@Composable
fun WrapUpDialog(
    viewModel: com.example.viewmodel.StromrufViewModel,
    data: WrapUpData,
    onValueChange: (WrapUpData) -> Unit,
    onOutcomeChange: (String) -> Unit,
    onSaveContactChange: (Boolean) -> Unit,
    onNoteChange: (String) -> Unit,
    onCallReasonChange: (String?) -> Unit,
    onToggleOffset: (String) -> Unit,
    onAddCustomDate: (Long) -> Unit,
    onRemoveCustomDate: (Long) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val clipboardManager = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
    }
    var lastCopiedCustomerNumber by remember { mutableStateOf("") }

    val checkClipboard = {
        try {
            if (clipboardManager != null && clipboardManager.hasPrimaryClip()) {
                val clipData = clipboardManager.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    val text = clipData.getItemAt(0)?.text?.toString()?.trim() ?: ""
                    val custNoMatch = Regex("""(?<!\d)([79]\d{5})(?!\d)""").find(text)?.value
                    if (custNoMatch != null && custNoMatch != data.customerNumber && custNoMatch != lastCopiedCustomerNumber) {
                        lastCopiedCustomerNumber = custNoMatch
                        onValueChange(data.copy(customerNumber = custNoMatch, saveContact = true))
                        Toast.makeText(context, "Kundennummer $custNoMatch automatisch übernommen 📋", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    DisposableEffect(clipboardManager) {
        val listener = android.content.ClipboardManager.OnPrimaryClipChangedListener {
            checkClipboard()
        }
        clipboardManager?.addPrimaryClipChangedListener(listener)
        onDispose {
            clipboardManager?.removePrimaryClipChangedListener(listener)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            checkClipboard()
            kotlinx.coroutines.delay(800)
        }
    }

    Dialog(onDismissRequest = onCancel) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .clip(RoundedCornerShape(16.dp)),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Dialog Title Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Anruf erfassen",
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color(0xFF00FF87)
                        )
                    )
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Schließen", tint = Color.White)
                    }
                }

                // Form Scrollable Container
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Contact phone subhead title
                    Text(
                        text = "Telefon: ${data.phone}",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = Color(0xFF64748B)
                        )
                    )

                    // 1. SELECT OUTCOME (ERGEBNIS) - Compact drop-down selection
                    Column {
                        Text(
                            text = "1. Ergebnis",
                            style = TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF64748B),
                                letterSpacing = 0.5.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        var selectedCategory by remember(data.outcome) {
                            mutableStateOf(
                                when {
                                    data.outcome.startsWith("erreicht") -> "erreicht"
                                    data.outcome == "nicht_erreicht" || data.outcome == "falsche_nummer" -> "nicht_erreicht"
                                    else -> ""
                                }
                            )
                        }

                        // 1. Level: Erreicht oder Nicht Erreicht Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (selectedCategory == "erreicht") Color(0xFFD1FAE5) // light emerald
                                        else Color(0xFFF1F5F9) // light slate
                                    )
                                    .border(
                                        2.dp,
                                        if (selectedCategory == "erreicht") Color(0xFF10B981)
                                        else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        selectedCategory = "erreicht"
                                        if (!data.outcome.startsWith("erreicht")) {
                                            onOutcomeChange("")
                                        }
                                    }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Erreicht ✔️",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (selectedCategory == "erreicht") Color(0xFF065F46) else Color(0xFF475569)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (selectedCategory == "nicht_erreicht") Color(0xFFFEE2E2) // light red
                                        else Color(0xFFF1F5F9) // light slate
                                    )
                                    .border(
                                        2.dp,
                                        if (selectedCategory == "nicht_erreicht") Color(0xFFEF4444)
                                        else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        selectedCategory = "nicht_erreicht"
                                        if (data.outcome != "nicht_erreicht" && data.outcome != "falsche_nummer") {
                                            onOutcomeChange("")
                                        }
                                    }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Nicht erreicht ❌",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (selectedCategory == "nicht_erreicht") Color(0xFF991B1B) else Color(0xFF475569)
                                )
                            }
                        }

                        // 2. Level: Sub-Outcomes Choices
                        if (selectedCategory.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Genaueres Ergebnis auswählen:",
                                style = TextStyle(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            val subOutcomes = if (selectedCategory == "erreicht") {
                                listOf(
                                    "erreicht_interesse" to "Interesse 👍",
                                    "erreicht_abschluss" to "Abschluss 🏆",
                                    "erreicht_kein_interesse" to "Kein Interesse 👎"
                                )
                            } else {
                                listOf(
                                    "nicht_erreicht" to "Nicht erreicht ⏳",
                                    "falsche_nummer" to "Falsche Nummer 🚫"
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                subOutcomes.forEach { (key, label) ->
                                    val isSubSelected = data.outcome == key
                                    val meta = getOutcomeMeta(key)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSubSelected) meta.color.copy(alpha = 0.15f)
                                                else Color(0xFFF8FAFC)
                                            )
                                            .border(
                                                1.dp,
                                                if (isSubSelected) meta.color else Color(0xFFE2E8F0),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable { onOutcomeChange(key) }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .background(if (isSubSelected) meta.color else Color(0xFFCBD5E1), CircleShape)
                                        )
                                        Text(
                                            text = label,
                                            fontWeight = if (isSubSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 14.sp,
                                            color = if (isSubSelected) Color(0xFF1E293B) else Color(0xFF64748B)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. CONTACT ACTIONS (SPEICHERN ODER STATUS)
                    Column {
                        Text(
                            text = "2. Kontakt",
                            style = TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF64748B),
                                letterSpacing = 0.5.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (data.existingContact != null) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFECFDF5), shape = RoundedCornerShape(8.dp))
                                        .border(1.dp, Color(0xFF10B981), shape = RoundedCornerShape(8.dp))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF10B981))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${data.existingContact.name} ist im Telefonbuch gespeichert.",
                                        fontSize = 13.sp,
                                        color = Color(0xFF065F46),
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                OutlinedTextField(
                                    value = data.customerNumber,
                                    onValueChange = { onValueChange(data.copy(customerNumber = it)) },
                                    label = { Text("Kundennummer") },
                                    placeholder = { Text("z.B. 912345") },
                                    trailingIcon = {
                                        if (data.customerNumber.isNotBlank()) {
                                            IconButton(onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                                val clip = android.content.ClipData.newPlainText("Kundennummer", data.customerNumber)
                                                clipboard?.setPrimaryClip(clip)
                                                Toast.makeText(context, "Kundennummer kopiert: ${data.customerNumber} 📋", Toast.LENGTH_SHORT).show()
                                            }) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = "Kopieren", tint = Color(0xFF0284C7))
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { onSaveContactChange(!data.saveContact) }
                            ) {
                                Checkbox(
                                    checked = data.saveContact,
                                    onCheckedChange = { onSaveContactChange(it) }
                                )
                                Text("Diesen Kontakt im Telefonbuch speichern", fontSize = 13.sp)
                            }

                            if (data.saveContact) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = data.name,
                                        onValueChange = { onValueChange(data.copy(name = it)) },
                                        label = { Text("Name *") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = data.customerNumber,
                                        onValueChange = { onValueChange(data.copy(customerNumber = it)) },
                                        label = { Text("Kundennummer") },
                                        placeholder = { Text("z.B. 912345") },
                                        trailingIcon = {
                                            if (data.customerNumber.isNotBlank()) {
                                                IconButton(onClick = {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                                    val clip = android.content.ClipData.newPlainText("Kundennummer", data.customerNumber)
                                                    clipboard?.setPrimaryClip(clip)
                                                    Toast.makeText(context, "Kundennummer kopiert: ${data.customerNumber} 📋", Toast.LENGTH_SHORT).show()
                                                }) {
                                                    Icon(Icons.Default.ContentCopy, contentDescription = "Kopieren", tint = Color(0xFF0284C7))
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = data.company,
                                        onValueChange = { onValueChange(data.copy(company = it)) },
                                        label = { Text("Firma / Unternehmen") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = data.email,
                                        onValueChange = { onValueChange(data.copy(email = it)) },
                                        label = { Text("E-Mail") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }
                            }
                        }

                        if (data.existingContact != null || data.saveContact) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { onValueChange(data.copy(isHotBox = !data.isHotBox)) }
                            ) {
                                Checkbox(
                                    checked = data.isHotBox,
                                    onCheckedChange = { onValueChange(data.copy(isHotBox = it)) },
                                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFFEF4444))
                                )
                                Text("In Hot Box 🔥 behalten / hinzufügen (hohe Abschluss-Chance)", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }

                            if (data.isHotBox) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFFEF2F2), RoundedCornerShape(8.dp))
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Aktivitäts-Zeitraum einschränken (nur in dieser Zeit wählen):",
                                        fontSize = 11.sp,
                                        color = Color(0xFF991B1B),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        var startInput by remember(data.hotBoxStartHour) { mutableStateOf(com.example.util.ContactsUtil.formatMinutesToTimeString(data.hotBoxStartHour)) }
                                        var endInput by remember(data.hotBoxEndHour) { mutableStateOf(com.example.util.ContactsUtil.formatMinutesToTimeString(data.hotBoxEndHour)) }

                                        OutlinedTextField(
                                            value = startInput,
                                            onValueChange = { newValue ->
                                                if (newValue.isEmpty() || newValue.all { it.isDigit() || it == ':' }) {
                                                    startInput = newValue
                                                    val parsed = com.example.util.ContactsUtil.parseTimeStringToMinutes(newValue)
                                                    onValueChange(data.copy(hotBoxStartHour = parsed))
                                                }
                                            },
                                            label = { Text("Von (z.B. 10:00)") },
                                            placeholder = { Text("z.B. 10:00") },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                            modifier = Modifier.weight(1f)
                                        )
                                        
                                        OutlinedTextField(
                                            value = endInput,
                                            onValueChange = { newValue ->
                                                if (newValue.isEmpty() || newValue.all { it.isDigit() || it == ':' }) {
                                                    endInput = newValue
                                                    val parsed = com.example.util.ContactsUtil.parseTimeStringToMinutes(newValue)
                                                    onValueChange(data.copy(hotBoxEndHour = parsed))
                                                }
                                            },
                                            label = { Text("Bis (z.B. 13:00)") },
                                            placeholder = { Text("z.B. 13:00") },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Gesprächsdauer (Minutes/Seconds)
                        Text(
                            text = "Gesprächsdauer (Minuten / Sekunden)",
                            style = TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF64748B),
                                letterSpacing = 0.5.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            var minsInput by remember { mutableStateOf((data.durationSeconds / 60).toString()) }
                            var secsInput by remember { mutableStateOf((data.durationSeconds % 60).toString()) }

                            OutlinedTextField(
                                value = minsInput,
                                onValueChange = { newValue ->
                                    if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                                        minsInput = newValue
                                        val m = newValue.toLongOrNull() ?: 0L
                                        val s = secsInput.toLongOrNull() ?: 0L
                                        onValueChange(data.copy(durationSeconds = m * 60 + s))
                                    }
                                },
                                label = { Text("Minuten") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            )

                            OutlinedTextField(
                                value = secsInput,
                                onValueChange = { newValue ->
                                    if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                                        secsInput = newValue
                                        val m = minsInput.toLongOrNull() ?: 0L
                                        val s = newValue.toLongOrNull() ?: 0L
                                        onValueChange(data.copy(durationSeconds = m * 60 + s))
                                    }
                                },
                                label = { Text("Sekunden") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Call Reason Selection (Grund des Anrufs)
                        Text(
                            text = "Grund des Anrufs",
                            style = TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF64748B),
                                letterSpacing = 0.5.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val reasons = listOf(
                            "NK Erstkontakt",
                            "BK FV",
                            "Fehlende Dokumente",
                            "Angebot besprechen",
                            "zum Stand fragen"
                        )
                        
                        // Row 1
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            reasons.take(2).forEach { r ->
                                val isSelected = data.callReason == r
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFF1F5F9))
                                        .border(
                                            1.dp,
                                            if (isSelected) Color(0xFF10B981) else Color(0xFFCBD5E1),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { onCallReasonChange(if (isSelected) null else r) }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = r,
                                        color = if (isSelected) Color(0xFF047857) else Color(0xFF475569),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        // Row 2
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            reasons.drop(2).forEach { r ->
                                val isSelected = data.callReason == r
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFF1F5F9))
                                        .border(
                                            1.dp,
                                            if (isSelected) Color(0xFF10B981) else Color(0xFFCBD5E1),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { onCallReasonChange(if (isSelected) null else r) }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = r,
                                        color = if (isSelected) Color(0xFF047857) else Color(0xFF475569),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Conversation note text input area
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val context = LocalContext.current
                            val coroutineScope = rememberCoroutineScope()
                            var isDictating by remember { mutableStateOf(false) }
                            var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

                            val startSpeech = {
                                isDictating = true
                                try {
                                    if (recognizer == null) {
                                        recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                                    }
                                    val listener = object : RecognitionListener {
                                        override fun onReadyForSpeech(params: Bundle?) {}
                                        override fun onBeginningOfSpeech() {}
                                        override fun onRmsChanged(rmsdB: Float) {}
                                        override fun onBufferReceived(buffer: ByteArray?) {}
                                        override fun onEndOfSpeech() {}
                                        
                                        override fun onError(error: Int) {
                                            if (isDictating) {
                                                coroutineScope.launch {
                                                    kotlinx.coroutines.delay(400)
                                                    if (isDictating) {
                                                        try {
                                                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().language)
                                                                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                                                            }
                                                            recognizer?.startListening(intent)
                                                        } catch (e: Exception) {
                                                            e.printStackTrace()
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        override fun onResults(results: Bundle?) {
                                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                            val resultText = matches?.firstOrNull() ?: ""
                                            if (resultText.isNotBlank()) {
                                                val updated = if (data.note.isBlank()) resultText else "${data.note} $resultText"
                                                onNoteChange(updated)
                                            }
                                            if (isDictating) {
                                                coroutineScope.launch {
                                                    kotlinx.coroutines.delay(300)
                                                    if (isDictating) {
                                                        try {
                                                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().language)
                                                                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                                                            }
                                                            recognizer?.startListening(intent)
                                                        } catch (e: Exception) {
                                                            e.printStackTrace()
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        override fun onPartialResults(partialResults: Bundle?) {}
                                        override fun onEvent(eventType: Int, params: Bundle?) {}
                                    }
                                    recognizer?.setRecognitionListener(listener)
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().language)
                                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                                    }
                                    recognizer?.startListening(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    isDictating = false
                                }
                            }

                            val stopSpeech = {
                                isDictating = false
                                try {
                                    recognizer?.stopListening()
                                    recognizer?.destroy()
                                    recognizer = null
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }

                            DisposableEffect(Unit) {
                                onDispose {
                                    try {
                                        recognizer?.stopListening()
                                        recognizer?.destroy()
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }

                            val micPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                                androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                            ) { isGranted ->
                                if (isGranted) {
                                    startSpeech()
                                } else {
                                    Toast.makeText(context, "Mikrofon-Berechtigung erforderlich", Toast.LENGTH_SHORT).show()
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Gesprächsnotiz:",
                                    style = TextStyle(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFF64748B),
                                        letterSpacing = 0.5.sp
                                    )
                                )
                                
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isDictating) Color(0xFFEF4444).copy(alpha = 0.2f) else Color(0xFFE2E8F0))
                                        .clickable {
                                            if (isDictating) {
                                                stopSpeech()
                                            } else {
                                                val status = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
                                                if (status == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                                    startSpeech()
                                                } else {
                                                    micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                                }
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isDictating) Icons.Default.Close else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = if (isDictating) Color(0xFFEF4444) else Color(0xFF0F766E),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = if (isDictating) "Stop Diktat" else "Diktieren 🎤",
                                        color = if (isDictating) Color(0xFFEF4444) else Color(0xFF0F766E),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = data.note,
                                onValueChange = { onNoteChange(it) },
                                placeholder = { Text("Notiz zum Gespräch hinzufügen...") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3,
                                maxLines = 5
                            )
                        }
                    }

                    // 3. SCHEDULE FUTURE CHANNELS (WIEDERANRUF / WIEDERVORLAGE)
                    Column {
                        Text(
                            text = "3. Wann rufen Sie ihn wieder an? (Optional)",
                            style = TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF64748B),
                                letterSpacing = 0.5.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Preset intervals grid layout (German label match)
                        val presets = listOf(
                            "3h" to "In 3 Stunden",
                            "1d" to "In 1 Tag",
                            "1m" to "In 1 Monat",
                            "1y" to "In 1 Jahr"
                        )

                        Text("Zeitperiode wählen (Mehrfachauswahl erlaubt):", fontSize = 12.sp, color = Color(0xFF64748B))
                        Spacer(modifier = Modifier.height(6.dp))

                        presets.forEach { (key, label) ->
                            val isSelected = data.selectedOffsets.contains(key)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .background(
                                        if (isSelected) Color(0xFF00FF87).copy(alpha = 0.1f) else Color.Transparent,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) Color(0xFF00FF87) else Color(0xFF1C2C27),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable { onToggleOffset(key) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { onToggleOffset(key) },
                                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00FF87), checkmarkColor = Color(0xFF040A08))
                                )
                                Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Custom Scheduler date/time action
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Benutzerdefinierte Termine:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                            
                            // Native Date & Time picker triggering
                            OutlinedButton(
                                onClick = {
                                    val currentCalendar = Calendar.getInstance()
                                    val dateDialog = DatePickerDialog(
                                        context,
                                        null,
                                        currentCalendar.get(Calendar.YEAR),
                                        currentCalendar.get(Calendar.MONTH),
                                        currentCalendar.get(Calendar.DAY_OF_MONTH)
                                    )
                                    dateDialog.datePicker.init(
                                        currentCalendar.get(Calendar.YEAR),
                                        currentCalendar.get(Calendar.MONTH),
                                        currentCalendar.get(Calendar.DAY_OF_MONTH)
                                    ) { _, year, month, dayOfMonth ->
                                        dateDialog.dismiss()
                                        TimePickerDialog(
                                            context,
                                            { _, hourOfDay, minute ->
                                                val cal = Calendar.getInstance().apply {
                                                    set(Calendar.YEAR, year)
                                                    set(Calendar.MONTH, month)
                                                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                                                    set(Calendar.MINUTE, minute)
                                                    set(Calendar.SECOND, 0)
                                                    set(Calendar.MILLISECOND, 0)
                                                }
                                                onAddCustomDate(cal.timeInMillis)
                                            },
                                            currentCalendar.get(Calendar.HOUR_OF_DAY),
                                            currentCalendar.get(Calendar.MINUTE),
                                            true
                                        ).show()
                                    }
                                    dateDialog.show()
                                },
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.defaultMinSize(minHeight = 32.dp, minWidth = 1.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Termin hinzufügen", fontSize = 11.sp)
                            }
                        }

                        // Added custom dates listing
                        if (data.customDates.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                data.customDates.forEach { timestamp ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFF1F5F9), shape = RoundedCornerShape(6.dp))
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF475569))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = fmtDateTime(timestamp),
                                                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                                            )
                                        }
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Entfernen",
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable { onRemoveCustomDate(timestamp) },
                                            tint = Color(0xFFEF4444)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                var showCustomerMessageComposer by remember { mutableStateOf(false) }
                
                Button(
                    onClick = { showCustomerMessageComposer = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Kundennachricht aus Notiz erstellen", fontSize = 14.sp)
                }
                
                if (showCustomerMessageComposer) {
                    androidx.compose.ui.window.Dialog(onDismissRequest = { showCustomerMessageComposer = false }) {
                        com.example.ui.screens.CallNoteComposer(viewModel = viewModel, contact = data.existingContact, onDismiss = { showCustomerMessageComposer = false })
                    }
                }

                // Action Bottom Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Abbrechen")
                    }
                    Button(
                        onClick = {
                            if (data.outcome.isBlank()) {
                                Toast.makeText(context, "Bitte ein Anrufergebnis auswählen.", Toast.LENGTH_SHORT).show()
                            } else if (data.existingContact == null && data.saveContact && data.name.isBlank()) {
                                Toast.makeText(context, "Bitte einen Namen angeben um Kontakt zu speichern.", Toast.LENGTH_SHORT).show()
                            } else {
                                onSave()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Anruf sichern")
                    }
                }
            }
        }
    }
}

// --- WEEKDAY PICKER & FORMATTER FOR REACHABILITY ---

fun formatWeekdays(weekdaysStr: String?): String {
    if (weekdaysStr.isNullOrBlank()) return "Alle Tage"
    val daysMap = mapOf(
        2 to "Mo",
        3 to "Di",
        4 to "Mi",
        5 to "Do",
        6 to "Fr",
        7 to "Sa",
        1 to "So"
    )
    val parsed = weekdaysStr.split(",").mapNotNull { it.trim().toIntOrNull() }.sorted()
    if (parsed.isEmpty() || parsed.size == 7) return "Alle Tage"
    return parsed.mapNotNull { daysMap[it] }.joinToString(", ")
}

@Composable
fun WeekdayPicker(
    selectedDays: String?,
    onDaysChanged: (String?) -> Unit
) {
    val weekdays = listOf(
        Pair(2, "Mo"),
        Pair(3, "Di"),
        Pair(4, "Mi"),
        Pair(5, "Do"),
        Pair(6, "Fr"),
        Pair(7, "Sa"),
        Pair(1, "So")
    )

    val currentSelectedList = remember(selectedDays) {
        selectedDays?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.toSet() ?: emptySet()
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Erreichbare Wochentage (Standard: Alle Tage):",
            fontSize = 11.sp,
            color = Color(0xFF991B1B),
            fontWeight = FontWeight.Bold
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            weekdays.forEach { (dayIndex, dayLabel) ->
                val isSelected = currentSelectedList.contains(dayIndex)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .background(
                            if (isSelected) Color(0xFFEF4444) else Color(0xFFEF4444).copy(alpha = 0.05f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) Color(0xFFEF4444) else Color(0xFFFCA5A5),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            val newList = if (isSelected) {
                                currentSelectedList - dayIndex
                            } else {
                                currentSelectedList + dayIndex
                            }
                            val serialized = if (newList.isEmpty()) null else newList.sorted().joinToString(",")
                            onDaysChanged(serialized)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = dayLabel,
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else Color(0xFF991B1B)
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun QuickReachabilityDialog(
    contact: ContactEntity,
    onDismiss: () -> Unit,
    onSave: (startHour: Int?, endHour: Int?, weekdays: String?) -> Unit
) {
    var startInput by remember { mutableStateOf(com.example.util.ContactsUtil.formatMinutesToTimeString(contact.hotBoxStartHour)) }
    var endInput by remember { mutableStateOf(com.example.util.ContactsUtil.formatMinutesToTimeString(contact.hotBoxEndHour)) }
    var selectedWeekdays by remember { mutableStateOf(contact.hotBoxWeekdays) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Erreichbarkeit einstellen ⏰", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Legen Sie fest, wann ${contact.name} erreichbar ist. Außerhalb dieser Zeiten wird der Kunde in der Hot Box automatisch ausgeblendet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Weekday Picker
                WeekdayPicker(
                    selectedDays = selectedWeekdays,
                    onDaysChanged = { selectedWeekdays = it }
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Hour Input Row
                Text(
                    text = "Aktivitäts-Zeitraum (Uhrzeit):",
                    fontSize = 11.sp,
                    color = Color(0xFF991B1B),
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = startInput,
                        onValueChange = { newValue ->
                            if (newValue.isEmpty() || newValue.all { it.isDigit() || it == ':' }) {
                                startInput = newValue
                            }
                        },
                        label = { Text("Von (z.B. 13:30)") },
                        placeholder = { Text("z.B. 13:30") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        modifier = Modifier.weight(1f)
                    )
                    
                    OutlinedTextField(
                        value = endInput,
                        onValueChange = { newValue ->
                            if (newValue.isEmpty() || newValue.all { it.isDigit() || it == ':' }) {
                                endInput = newValue
                            }
                        },
                        label = { Text("Bis (z.B. 17:00)") },
                        placeholder = { Text("z.B. 17:00") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val start = com.example.util.ContactsUtil.parseTimeStringToMinutes(startInput)
                    val end = com.example.util.ContactsUtil.parseTimeStringToMinutes(endInput)
                    onSave(start, end, selectedWeekdays)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A))
            ) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

// --- FORM DIALOG CREA/EDIT ---

@Composable
fun ContactDialog(
    title: String,
    initialName: String = "",
    initialPhone: String = "",
    initialCompany: String = "",
    initialEmail: String = "",
    initialIsHotBox: Boolean = true,
    initialHotBoxStartHour: Int? = null,
    initialHotBoxEndHour: Int? = null,
    initialHotBoxWeekdays: String? = null,
    initialConsumption: Long? = null,
    initialZipCode: String? = null,
    initialEnergyType: String? = null,
    hotBoxLists: Set<String> = emptySet(),
    initialHotBoxListName: String = "Hotbox",
    onDismiss: () -> Unit,
    onConfirm: (name: String, phone: String, company: String, email: String, isHotBox: Boolean, startHour: Int?, endHour: Int?, weekdays: String?, consumption: Long?, zipCode: String?, energyType: String?, hotBoxListName: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var phone by remember { mutableStateOf(initialPhone) }
    var company by remember { mutableStateOf(initialCompany) }
    var email by remember { mutableStateOf(initialEmail) }
    var isHotBox by remember { mutableStateOf(initialIsHotBox) }
    var startInput by remember { mutableStateOf(com.example.util.ContactsUtil.formatMinutesToTimeString(initialHotBoxStartHour)) }
    var endInput by remember { mutableStateOf(com.example.util.ContactsUtil.formatMinutesToTimeString(initialHotBoxEndHour)) }
    var selectedWeekdays by remember { mutableStateOf(initialHotBoxWeekdays) }
    var consumptionInput by remember { mutableStateOf(initialConsumption?.toString() ?: "") }
    var zipCodeInput by remember { mutableStateOf(initialZipCode ?: "") }
    var energyType by remember { mutableStateOf(initialEnergyType) }
    var selectedListName by remember { mutableStateOf(initialHotBoxListName) }
    var showListDropdown by remember { mutableStateOf(false) }

    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Telefonnummer *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = company,
                    onValueChange = { company = it },
                    label = { Text("Firma") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("E-Mail") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = consumptionInput,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                            consumptionInput = newValue
                        }
                    },
                    label = { Text("Verbrauch (kWh)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = zipCodeInput,
                    onValueChange = { zipCodeInput = it },
                    label = { Text("Postleitzahl") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                // EnergyTypePicker
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Energieart:",
                        fontSize = 11.sp,
                        color = Color(0xFF475569),
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val types = listOf(
                            "Keine" to null,
                            "Strom" to "Strom",
                            "Gas" to "Gas",
                            "BHKW" to "Bhkw",
                            "Beide" to "Beide"
                        )
                        types.forEach { (label, value) ->
                            val isSelected = energyType == value
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .background(
                                        if (isSelected) Color(0xFF0F172A) else Color(0xFFF1F5F9),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        energyType = value
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else Color(0xFF334155),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFEF2F2), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "In die Hotbox aufnehmen 🔥",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEF4444)
                        )
                        Switch(
                            checked = isHotBox,
                            onCheckedChange = { isHotBox = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFEF4444))
                        )
                    }

                    if (isHotBox) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Kampagnen-Liste:",
                                fontSize = 11.sp,
                                color = Color(0xFF991B1B),
                                fontWeight = FontWeight.Bold
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White, RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .clickable { showListDropdown = true }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "$selectedListName 🔥",
                                        color = Color.Black,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Auswählen",
                                        tint = Color(0xFF991B1B)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showListDropdown,
                                    onDismissRequest = { showListDropdown = false },
                                    modifier = Modifier.fillMaxWidth(0.8f)
                                ) {
                                    val finalLists = if (hotBoxLists.isEmpty()) setOf("Hotbox") else hotBoxLists
                                    finalLists.forEach { name ->
                                        DropdownMenuItem(
                                            text = { Text("$name 🔥", fontSize = 13.sp) },
                                            onClick = {
                                                selectedListName = name
                                                showListDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Aktivitäts-Zeitraum einschränken (nur in dieser Zeit wählen):",
                            fontSize = 11.sp,
                            color = Color(0xFF991B1B),
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = startInput,
                                onValueChange = { newValue ->
                                    if (newValue.isEmpty() || newValue.all { it.isDigit() || it == ':' }) {
                                        startInput = newValue
                                    }
                                },
                                label = { Text("Von (z.B. 10:00)") },
                                placeholder = { Text("z.B. 10:00") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                modifier = Modifier.weight(1f)
                            )
                            
                            OutlinedTextField(
                                value = endInput,
                                onValueChange = { newValue ->
                                    if (newValue.isEmpty() || newValue.all { it.isDigit() || it == ':' }) {
                                        endInput = newValue
                                    }
                                },
                                label = { Text("Bis (z.B. 13:00)") },
                                placeholder = { Text("z.B. 13:00") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        WeekdayPicker(
                            selectedDays = selectedWeekdays,
                            onDaysChanged = { selectedWeekdays = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank() || phone.isBlank()) {
                        Toast.makeText(context, "Bitte Name und Telefonnummer ausfüllen.", Toast.LENGTH_SHORT).show()
                    } else {
                        val startHour = com.example.util.ContactsUtil.parseTimeStringToMinutes(startInput)
                        val endHour = com.example.util.ContactsUtil.parseTimeStringToMinutes(endInput)
                        val consumption = consumptionInput.toLongOrNull()
                        val zipCode = zipCodeInput.trim().takeIf { it.isNotBlank() }
                        onConfirm(name, phone, company, email, isHotBox, startHour, endHour, selectedWeekdays, consumption, zipCode, energyType, selectedListName)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A))
            ) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun SystemContactImportDialog(
    viewModel: StromrufViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var systemContactsList by remember { mutableStateOf(emptyList<ContactsUtil.SystemContact>()) }
    var hasPermission by remember { mutableStateOf(ContactsUtil.hasContactsPermission(context)) }

    // Reload system contacts when permission is true or searchQuery changes
    LaunchedEffect(hasPermission, searchQuery) {
        if (hasPermission) {
            systemContactsList = ContactsUtil.searchSystemContacts(context, searchQuery)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Aus Telefonkontakten importieren",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color(0xFF0F172A)
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!hasPermission) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Berechtigung für Kontakte wird benötigt, um Ihr System-Adressbuch zu durchsuchen.",
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            color = Color(0xFF475569)
                        )
                        Button(
                            onClick = {
                                (context as? android.app.Activity)?.requestPermissions(
                                    arrayOf(android.Manifest.permission.READ_CONTACTS), 102
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Berechtigung erteilen")
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Name oder Nummer suchen...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    if (systemContactsList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Keine Kontakte im Adressbuch gefunden.",
                                fontSize = 13.sp,
                                color = Color(0xFF64748B),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(systemContactsList, key = { "${it.name}_${it.phone}" }) { contact ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = contact.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color(0xFF0F172A)
                                        )
                                        Text(
                                            text = contact.phone,
                                            fontSize = 12.sp,
                                            color = Color(0xFF64748B),
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    
                                    var isImported by remember { mutableStateOf(false) }

                                    IconButton(
                                        onClick = {
                                            viewModel.importContactFromSystem(contact.name, contact.phone) { success, msg ->
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                if (success) {
                                                    isImported = true
                                                }
                                            }
                                        },
                                        enabled = !isImported,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(
                                                if (isImported) Color(0xFF10B981) else Color(0xFF0F172A),
                                                CircleShape
                                            )
                                    ) {
                                        Icon(
                                            imageVector = if (isImported) Icons.Default.Check else Icons.Default.Add,
                                            contentDescription = "Importieren",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Fertig")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )

    // Check permission periodically or when active lifecycle resumes
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasPermission = ContactsUtil.hasContactsPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

@Composable
fun TypeStatColumn(
    modifier: Modifier = Modifier,
    title: String,
    reachedCount: Int,
    totalCount: Int,
    durationText: String,
    borderColor: Color
) {
    val rate = if (totalCount == 0) 0 else (reachedCount * 100) / totalCount
    Card(
        modifier = modifier.border(1.dp, borderColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = borderColor
            )
            
            Divider(color = borderColor.copy(alpha = 0.15f), thickness = 1.dp)

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Erreicht",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$reachedCount / $totalCount",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$rate% Quote",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (rate >= 50) Color(0xFF10B981) else Color(0xFFF59E0B)
                )
            }

            Divider(color = borderColor.copy(alpha = 0.15f), thickness = 1.dp)

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Dauer",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = durationText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

data class ChartBarData(
    val label: String,
    val reachedCount: Long,
    val notReachedCount: Long,
    val totalCount: Long
)

fun getFileNameFromUri(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    result = cursor.getString(nameIndex)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result ?: "unbekanntes_dokument"
}

fun saveFileToDownloads(context: Context, fileName: String, localFilePath: String?, textFallback: String) {
    try {
        val resolver = context.contentResolver
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            val mime = when {
                fileName.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
                fileName.endsWith(".png", ignoreCase = true) -> "image/png"
                fileName.endsWith(".jpg", ignoreCase = true) || fileName.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
                fileName.endsWith(".txt", ignoreCase = true) -> "text/plain"
                else -> "application/octet-stream"
            }
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
        }

        val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        } else {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val targetFile = java.io.File(downloadsDir, fileName)
            Uri.fromFile(targetFile)
        }

        if (uri != null) {
            resolver.openOutputStream(uri)?.use { outputStream ->
                if (!localFilePath.isNullOrEmpty()) {
                    val localFile = java.io.File(localFilePath)
                    if (localFile.exists()) {
                        localFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    } else {
                        outputStream.write(textFallback.toByteArray(Charsets.UTF_8))
                    }
                } else {
                    outputStream.write(textFallback.toByteArray(Charsets.UTF_8))
                }
            }
            Toast.makeText(context, "Datei erfolgreich unter Downloads gespeichert! 📥\n($fileName)", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "Fehler beim Erstellen der Datei in Downloads.", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Fehler beim Herunterladen: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
    }
}

fun saveBytesToDownloads(
    context: Context,
    fileName: String,
    bytes: ByteArray
) {
    try {
        val resolver = context.contentResolver
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
        }

        val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        } else {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            Uri.fromFile(java.io.File(downloadsDir, fileName))
        }

        if (uri != null) {
            resolver.openOutputStream(uri)?.use { output ->
                output.write(bytes)
            }
            Toast.makeText(
                context,
                "PDF unter Downloads gespeichert: $fileName",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(
                context,
                "Download konnte nicht erstellt werden.",
                Toast.LENGTH_SHORT
            ).show()
        }
    } catch (e: Exception) {
        Toast.makeText(
            context,
            "Download  fehlgeschlagen: ${e.message}",
            Toast.LENGTH_LONG
        ).show()
    }
}

@Composable
fun HistorieTabContent(
    viewModel: StromrufViewModel,
    callLogs: List<com.example.database.CallLogEntity>
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val contacts by viewModel.contacts.collectAsState()
    val hotBoxLists by viewModel.hotBoxLists.collectAsState()
    var contactToSave by remember { mutableStateOf<com.example.database.CallLogEntity?>(null) }

    val sdfTime = remember { SimpleDateFormat("HH:mm", Locale.GERMANY) }
    val sdfDayGroupLabel = remember { SimpleDateFormat("EEEE, d. MMMM yyyy", Locale.GERMANY) }

    var showAddFollowUpDialogByHistory by remember { mutableStateOf(false) }
    var historyFollowUpInitialName by remember { mutableStateOf("") }
    var historyFollowUpInitialPhone by remember { mutableStateOf("") }

    var showAnnahmenForm by remember { mutableStateOf(false) }
    var showAnnahmenStats by remember { mutableStateOf(false) }
    var customerNumberInput by remember { mutableStateOf("") }
    val annahmen by viewModel.annahmen.collectAsState()

    var showThreeDotsMenu by remember { mutableStateOf(false) }
    var showHistorieAndAbschlüsse by remember { mutableStateOf(false) }
    var showFullscreenStatsDialog by remember { mutableStateOf(false) }
    var showAnnahmeDokumenteDialog by remember { mutableStateOf(false) }
    var showNeukundenDialog by remember { mutableStateOf(false) }

    var neukundeCustomerNumber by remember { mutableStateOf("") }
    var neukundePhone by remember { mutableStateOf("") }
    val neukunden by viewModel.neukunden.collectAsState()

    var showHeisseAngeboteDialog by remember { mutableStateOf(false) }
    var heissAngebotCustomerNumber by remember { mutableStateOf("") }
    var heissAngebotPhone by remember { mutableStateOf("") }
    var heissAngebotNotes by remember { mutableStateOf("") }
    val heisseAngebote by viewModel.heisseAngebote.collectAsState()
    var convertingHeissAngebot by remember { mutableStateOf<com.example.database.HeissAngebotEntity?>(null) }

    var docSearchQuery by remember { mutableStateOf("") }

    val annahmeDokumente by viewModel.annahmeDokumente.collectAsState()
    val newAnnahmeAlert by viewModel.newAnnahmeDocumentAlert.collectAsState()
    var selectedAnnahmeDocId by remember { mutableStateOf<String?>(null) } // for expanding a document in list

    var annahmeType by remember { mutableStateOf("Strom") } // "Strom" or "Gas"
    var customerType by remember { mutableStateOf("Neukunde") } // "Neukunde" or "Bestandskunde"
    var consumptionInput by remember { mutableStateOf("") }
    var termInput by remember { mutableStateOf("3") }

    LaunchedEffect(Unit) {
        viewModel.syncSystemCallLogs(context)
        viewModel.syncAnnahmeDokumenteNow()
    }

    LaunchedEffect(showAnnahmeDokumenteDialog) {
        if (showAnnahmeDokumenteDialog) {
            viewModel.syncAnnahmeDokumenteNow()
        }
    }

    var filterType by remember { mutableStateOf("standard") } // "standard", "monat", "custom"
    var selectedPeriod by remember { mutableStateOf("tag") } // "tag", "woche", "monat", "gesamt"
    var chartMode by remember { mutableStateOf("gesamt") } // "gesamt", "hotbox", "einwaehlen", "rueckruf"
    var chartMetric by remember { mutableStateOf("anzahl") } // "dauer", "anzahl"

    val monthsList = remember {
        val list = mutableListOf<Pair<String, LongRange>>()
        val cal = Calendar.getInstance()
        val startCal = Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val format = SimpleDateFormat("MMMM yyyy", Locale.GERMANY)
        while (startCal.timeInMillis <= cal.timeInMillis) {
            val label = format.format(startCal.time)
            val monthStartCal = Calendar.getInstance().apply {
                timeInMillis = startCal.timeInMillis
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startTs = monthStartCal.timeInMillis
            monthStartCal.add(Calendar.MONTH, 1)
            val endTs = monthStartCal.timeInMillis - 1
            list.add(label to (startTs..endTs))
            startCal.add(Calendar.MONTH, 1)
        }
        list.reverse()
        list
    }

    var selectedMonthIndex by remember { mutableStateOf(0) }
    var customStartDate by remember { mutableStateOf<Long?>(null) }
    var customEndDate by remember { mutableStateOf<Long?>(null) }

    val formatDurationShort = { totalSeconds: Long ->
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        when {
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m ${s}s"
            else -> "${s}s"
        }
    }

    val filteredLogs = remember(callLogs, filterType, selectedPeriod, selectedMonthIndex, customStartDate, customEndDate) {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfToday = cal.timeInMillis

        when (filterType) {
            "standard" -> {
                when (selectedPeriod) {
                    "tag" -> callLogs.filter { it.timestamp >= startOfToday }
                    "woche" -> {
                        val startOfWeek = now - 7L * 24 * 60 * 60 * 1000
                        callLogs.filter { it.timestamp >= startOfWeek }
                    }
                    "monat" -> {
                        val startOfMonth = now - 30L * 24 * 60 * 60 * 1000
                        callLogs.filter { it.timestamp >= startOfMonth }
                    }
                    else -> callLogs
                }
            }
            "monat" -> {
                if (monthsList.isNotEmpty() && selectedMonthIndex in monthsList.indices) {
                    val range = monthsList[selectedMonthIndex].second
                    callLogs.filter { it.timestamp in range }
                } else {
                    callLogs
                }
            }
            "custom" -> {
                val start = customStartDate ?: 0L
                val end = customEndDate?.let {
                    val endCal = Calendar.getInstance().apply {
                        timeInMillis = it
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                        set(Calendar.MILLISECOND, 999)
                    }
                    endCal.timeInMillis
                } ?: Long.MAX_VALUE
                callLogs.filter { it.timestamp in start..end }
            }
            else -> callLogs
        }
    }

    val matchedLogs = remember(filteredLogs, contacts) {
        val contactLookup = mutableMapOf<String, ContactEntity>()
        val suffixLookup = mutableMapOf<String, ContactEntity>()
        
        contacts.forEach { contact ->
            val norm = normalizePhoneNumberFast(contact.phone)
            if (norm.isNotEmpty()) {
                contactLookup[norm] = contact
                if (norm.length >= 7) {
                    suffixLookup[norm.takeLast(7)] = contact
                }
            }
        }
        
        filteredLogs.map { log ->
            val normLog = normalizePhoneNumberFast(log.phone)
            var matchingContact: ContactEntity? = null
            if (normLog.isNotEmpty()) {
                matchingContact = contactLookup[normLog]
                if (matchingContact == null && normLog.length >= 7) {
                    matchingContact = suffixLookup[normLog.takeLast(7)]
                }
            }
            log to matchingContact
        }
    }

    val groupedLogs = remember(matchedLogs) {
        matchedLogs
            .groupBy { (log, _) ->
                val cal = Calendar.getInstance().apply { timeInMillis = log.timestamp }
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            .toSortedMap(reverseOrder())
    }

    val getDayGroupLabel = remember(sdfDayGroupLabel) {
        { startOfDay: Long ->
            val nowCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val todayStart = nowCal.timeInMillis
            
            nowCal.add(Calendar.DAY_OF_YEAR, -1)
            val yesterdayStart = nowCal.timeInMillis
            
            when (startOfDay) {
                todayStart -> "Heute"
                yesterdayStart -> "Gestern"
                else -> {
                    sdfDayGroupLabel.format(Date(startOfDay))
                }
            }
        }
    }

    // Breakdown stats for columns and cards
    val reachedCount = remember(filteredLogs) {
        filteredLogs.count { it.outcome.startsWith("erreicht") }
    }
    val reachabilityRate = remember(filteredLogs) {
        if (filteredLogs.isEmpty()) 0 else (reachedCount * 100) / filteredLogs.size
    }

    val reachedHotbox = remember(filteredLogs) {
        filteredLogs.count { it.callType == "hotbox" && it.outcome.startsWith("erreicht") }
    }
    val reachedEinwaehlen = remember(filteredLogs) {
        filteredLogs.count { it.callType == "einwaehlen" && it.outcome.startsWith("erreicht") }
    }
    val reachedRueckruf = remember(filteredLogs) {
        filteredLogs.count { it.callType == "rueckruf" && it.outcome.startsWith("erreicht") }
    }

    val totalHotbox = remember(filteredLogs) {
        filteredLogs.count { it.callType == "hotbox" }
    }
    val totalEinwaehlen = remember(filteredLogs) {
        filteredLogs.count { it.callType == "einwaehlen" }
    }
    val totalRueckruf = remember(filteredLogs) {
        filteredLogs.count { it.callType == "rueckruf" }
    }

    val durationHotbox = remember(filteredLogs) {
        filteredLogs.filter { it.callType == "hotbox" }.sumOf { it.durationSeconds }
    }
    val durationEinwaehlen = remember(filteredLogs) {
        filteredLogs.filter { it.callType == "einwaehlen" }.sumOf { it.durationSeconds }
    }
    val durationRueckruf = remember(filteredLogs) {
        filteredLogs.filter { it.callType == "rueckruf" }.sumOf { it.durationSeconds }
    }

    // Dynamic chart data depending on period, metric, and filters
    val chartData = remember(
        callLogs, filterType, selectedPeriod, selectedMonthIndex,
        customStartDate, customEndDate, chartMode
    ) {
        val relevantChartLogs = when (chartMode) {
            "gesamt" -> callLogs
            "hotbox" -> callLogs.filter { it.callType == "hotbox" }
            "einwaehlen" -> callLogs.filter { it.callType == "einwaehlen" }
            "rueckruf" -> callLogs.filter { it.callType == "rueckruf" }
            else -> callLogs
        }

        when (filterType) {
            "standard" -> {
                when (selectedPeriod) {
                    "tag" -> {
                        // Group by last 7 days day-by-day
                        val daysList = mutableListOf<ChartBarData>()
                        val sdf = SimpleDateFormat("dd.MM", Locale.GERMANY)
                        for (i in 6 downTo 0) {
                            val dCal = Calendar.getInstance().apply {
                                add(Calendar.DAY_OF_YEAR, -i)
                                set(Calendar.HOUR_OF_DAY, 0)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            val startOfDay = dCal.timeInMillis
                            val endOfDay = startOfDay + 24L * 60 * 60 * 1000 - 1

                            val dayLogs = relevantChartLogs.filter { it.timestamp in startOfDay..endOfDay }
                            val reached = dayLogs.count { it.outcome.startsWith("erreicht") }.toLong()
                            val notReached = dayLogs.size.toLong() - reached

                            val dayLabel = sdf.format(Date(startOfDay))
                            daysList.add(ChartBarData(dayLabel, reached, notReached, dayLogs.size.toLong()))
                        }
                        daysList
                    }
                    "woche" -> {
                        // Wochenansicht: Compare the last 4 calendar weeks!
                        val weeksList = mutableListOf<ChartBarData>()
                        val currentCal = Calendar.getInstance(Locale.GERMANY).apply {
                            firstDayOfWeek = Calendar.MONDAY
                        }
                        val dayOfWeek = currentCal.get(Calendar.DAY_OF_WEEK)
                        val daysSinceMonday = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY
                        val thisMondayCal = Calendar.getInstance(Locale.GERMANY).apply {
                            firstDayOfWeek = Calendar.MONDAY
                            add(Calendar.DAY_OF_YEAR, -daysSinceMonday)
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }

                        val startOfThisWeek = thisMondayCal.timeInMillis

                        for (i in 3 downTo 0) {
                            val weekStartCal = Calendar.getInstance(Locale.GERMANY).apply {
                                timeInMillis = startOfThisWeek
                                add(Calendar.WEEK_OF_YEAR, -i)
                            }
                            val startTs = weekStartCal.timeInMillis
                            val endTs = startTs + 7L * 24 * 60 * 60 * 1000 - 1
                            val kw = weekStartCal.get(Calendar.WEEK_OF_YEAR)
                            
                            val weekLogs = relevantChartLogs.filter { it.timestamp in startTs..endTs }
                            val reached = weekLogs.count { it.outcome.startsWith("erreicht") }.toLong()
                            val notReached = weekLogs.size.toLong() - reached
                            
                            val label = if (i == 0) "Diese Woche" else "KW $kw"
                            weeksList.add(ChartBarData(label, reached, notReached, weekLogs.size.toLong()))
                        }
                        weeksList
                    }
                    "monat" -> {
                        // Monatsansicht: Compare different months (the last 6 months)!
                        val monthsListChart = mutableListOf<ChartBarData>()
                        val sdf = SimpleDateFormat("MMM yy", Locale.GERMANY)
                        for (i in 5 downTo 0) {
                            val mCal = Calendar.getInstance().apply {
                                add(Calendar.MONTH, -i)
                                set(Calendar.DAY_OF_MONTH, 1)
                                set(Calendar.HOUR_OF_DAY, 0)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            val startOfMonth = mCal.timeInMillis
                            mCal.add(Calendar.MONTH, 1)
                            val endOfMonth = mCal.timeInMillis - 1

                            val monthLogs = relevantChartLogs.filter { it.timestamp in startOfMonth..endOfMonth }
                            val reached = monthLogs.count { it.outcome.startsWith("erreicht") }.toLong()
                            val notReached = monthLogs.size.toLong() - reached

                            val monthLabel = sdf.format(Date(startOfMonth))
                            monthsListChart.add(ChartBarData(monthLabel, reached, notReached, monthLogs.size.toLong()))
                        }
                        monthsListChart
                    }
                    else -> {
                        // Gesamt - Compare the last 12 months!
                        val monthsListChart = mutableListOf<ChartBarData>()
                        val sdf = SimpleDateFormat("MMM yy", Locale.GERMANY)
                        for (i in 11 downTo 0) {
                            val mCal = Calendar.getInstance().apply {
                                add(Calendar.MONTH, -i)
                                set(Calendar.DAY_OF_MONTH, 1)
                                set(Calendar.HOUR_OF_DAY, 0)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            val startOfMonth = mCal.timeInMillis
                            mCal.add(Calendar.MONTH, 1)
                            val endOfMonth = mCal.timeInMillis - 1

                            val monthLogs = relevantChartLogs.filter { it.timestamp in startOfMonth..endOfMonth }
                            val reached = monthLogs.count { it.outcome.startsWith("erreicht") }.toLong()
                            val notReached = monthLogs.size.toLong() - reached

                            val monthLabel = sdf.format(Date(startOfMonth))
                            monthsListChart.add(ChartBarData(monthLabel, reached, notReached, monthLogs.size.toLong()))
                        }
                        monthsListChart
                    }
                }
            }
            "monat" -> {
                if (monthsList.isNotEmpty() && selectedMonthIndex in monthsList.indices) {
                    val range = monthsList[selectedMonthIndex].second
                    val startTs = range.first
                    val endTs = range.last

                    val daysList = mutableListOf<ChartBarData>()
                    val sdf = SimpleDateFormat("dd.", Locale.GERMANY)
                    val mCal = Calendar.getInstance().apply { timeInMillis = startTs }
                    val endCal = Calendar.getInstance().apply { timeInMillis = endTs }

                    while (mCal.timeInMillis <= endCal.timeInMillis) {
                        val dayStart = mCal.timeInMillis
                        val dayEnd = dayStart + 24L * 60 * 60 * 1000 - 1

                        val dayLogs = relevantChartLogs.filter { it.timestamp in dayStart..dayEnd }
                        val reached = dayLogs.count { it.outcome.startsWith("erreicht") }.toLong()
                        val notReached = dayLogs.size.toLong() - reached

                        val dayLabel = sdf.format(Date(dayStart))
                        daysList.add(ChartBarData(dayLabel, reached, notReached, dayLogs.size.toLong()))

                        mCal.add(Calendar.DAY_OF_YEAR, 1)
                    }
                    daysList
                } else {
                    emptyList()
                }
            }
            "custom" -> {
                val start = customStartDate ?: 0L
                val end = customEndDate?.let {
                    val endCal = Calendar.getInstance().apply {
                        timeInMillis = it
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                        set(Calendar.MILLISECOND, 999)
                    }
                    endCal.timeInMillis
                } ?: System.currentTimeMillis()

                val diffMs = end - start
                val diffDays = (diffMs / (24L * 60 * 60 * 1000)).toInt()

                if (diffDays <= 10) {
                    // Group day-by-day
                    val daysList = mutableListOf<ChartBarData>()
                    val sdf = SimpleDateFormat("E dd.MM", Locale.GERMANY) // E.g., "Mo. 29.06"
                    val mCal = Calendar.getInstance().apply { timeInMillis = start }
                    for (i in 0..diffDays) {
                        val dayStart = mCal.timeInMillis
                        val dayEnd = dayStart + 24L * 60 * 60 * 1000 - 1
                        if (dayStart > end) break

                        val dayLogs = relevantChartLogs.filter { it.timestamp in dayStart..dayEnd }
                        val reached = dayLogs.count { it.outcome.startsWith("erreicht") }.toLong()
                        val notReached = dayLogs.size.toLong() - reached

                        val dayLabel = sdf.format(Date(dayStart)).replace("..", ".")
                        daysList.add(ChartBarData(dayLabel, reached, notReached, dayLogs.size.toLong()))

                        mCal.add(Calendar.DAY_OF_YEAR, 1)
                    }
                    daysList
                } else if (diffDays <= 45) {
                    // Group week-by-week (7-day chunks)
                    val weeksList = mutableListOf<ChartBarData>()
                    val sdf = SimpleDateFormat("dd.MM", Locale.GERMANY)
                    val mCal = Calendar.getInstance().apply { timeInMillis = start }
                    var weekIndex = 1
                    while (mCal.timeInMillis <= end) {
                        val wStart = mCal.timeInMillis
                        mCal.add(Calendar.DAY_OF_YEAR, 6)
                        val wEnd = minOf(mCal.timeInMillis, end)
                        mCal.add(Calendar.DAY_OF_YEAR, 1) // Set to next week's start

                        val weekLogs = relevantChartLogs.filter { it.timestamp in wStart..wEnd }
                        val reached = weekLogs.count { it.outcome.startsWith("erreicht") }.toLong()
                        val notReached = weekLogs.size.toLong() - reached

                        val label = "W$weekIndex (${sdf.format(Date(wStart))})"
                        weeksList.add(ChartBarData(label, reached, notReached, weekLogs.size.toLong()))
                        weekIndex++
                    }
                    weeksList
                } else {
                    // Group month-by-month
                    val monthsListChart = mutableListOf<ChartBarData>()
                    val sdf = SimpleDateFormat("MMM yy", Locale.GERMANY)
                    val mCal = Calendar.getInstance().apply { timeInMillis = start }
                    while (mCal.timeInMillis <= end) {
                        val startOfMonth = mCal.timeInMillis
                        
                        // Walk to end of this month or 'end'
                        val endOfMonthCal = Calendar.getInstance().apply {
                            timeInMillis = startOfMonth
                            set(Calendar.DAY_OF_MONTH, mCal.getActualMaximum(Calendar.DAY_OF_MONTH))
                            set(Calendar.HOUR_OF_DAY, 23)
                            set(Calendar.MINUTE, 59)
                            set(Calendar.SECOND, 59)
                            set(Calendar.MILLISECOND, 999)
                        }
                        val endOfMonth = minOf(endOfMonthCal.timeInMillis, end)
                        
                        val monthLogs = relevantChartLogs.filter { it.timestamp in startOfMonth..endOfMonth }
                        val reached = monthLogs.count { it.outcome.startsWith("erreicht") }.toLong()
                        val notReached = monthLogs.size.toLong() - reached

                        val monthLabel = sdf.format(Date(startOfMonth))
                        monthsListChart.add(ChartBarData(monthLabel, reached, notReached, monthLogs.size.toLong()))
                        
                        // Move to first day of next month
                        mCal.set(Calendar.DAY_OF_MONTH, 1)
                        mCal.add(Calendar.MONTH, 1)
                        if (mCal.timeInMillis > end) break
                    }
                    monthsListChart
                }
            }
            else -> emptyList()
        }
    }

    val totalDurationSeconds = remember(filteredLogs) {
        filteredLogs.sumOf { it.durationSeconds }
    }

    val formattedTotalDuration = remember(totalDurationSeconds) {
        val h = totalDurationSeconds / 3600
        val m = (totalDurationSeconds % 3600) / 60
        val s = totalDurationSeconds % 60
        if (h > 0) "${h}h ${m}m" else if (m > 0) "${m}m ${s}s" else "${s}s"
    }

    val avgDurationSeconds = remember(filteredLogs) {
        if (filteredLogs.isEmpty()) 0L else filteredLogs.sumOf { it.durationSeconds } / filteredLogs.size
    }

    val formattedAvgDuration = remember(avgDurationSeconds) {
        val m = avgDurationSeconds / 60
        val s = avgDurationSeconds % 60
        if (m > 0) "${m}m ${s}s" else "${s}s"
    }

    // Time of Day distribution calculations
    val timeDistribution = remember(filteredLogs) {
        var morgen = 0
        var vormittag = 0
        var mittag = 0
        var nachmittag = 0
        var feierabend = 0
        var nacht = 0

        filteredLogs.forEach { log ->
            val cal = Calendar.getInstance()
            cal.timeInMillis = log.timestamp
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            when (hour) {
                in 6..8 -> morgen++
                in 9..11 -> vormittag++
                in 12..13 -> mittag++
                in 14..16 -> nachmittag++
                in 17..20 -> feierabend++
                else -> nacht++
            }
        }
        val total = filteredLogs.size.coerceAtLeast(1).toFloat()
        listOf(
            "Frühschicht (06–09 Uhr)" to (morgen to (morgen / total)),
            "Vormittag (09–12 Uhr)" to (vormittag to (vormittag / total)),
            "Mittagspause (12–14 Uhr)" to (mittag to (mittag / total)),
            "Nachmittag (14–17 Uhr)" to (nachmittag to (nachmittag / total)),
            "Feierabend (17–21 Uhr)" to (feierabend to (feierabend / total)),
            "Nachts (21–06 Uhr)" to (nacht to (nacht / total))
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Top row with Title, Eintragen-Button and 3-Dots dropdown menu
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Historie & Abschlüsse",
                style = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                ),
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { showAnnahmenForm = !showAnnahmenForm },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (showAnnahmenForm) Color.White.copy(alpha = 0.1f) else Color(0xFF00FF87).copy(alpha = 0.15f),
                    contentColor = if (showAnnahmenForm) Color.White else Color(0xFF00FF87)
                ),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, if (showAnnahmenForm) Color.White.copy(alpha = 0.2f) else Color(0xFF00FF87).copy(alpha = 0.4f)),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = if (showAnnahmenForm) Icons.Default.Close else Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (showAnnahmenForm) "Schließen" else "Eintragen",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Box {
                IconButton(
                    onClick = { showThreeDotsMenu = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Optionen",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                DropdownMenu(
                    expanded = showThreeDotsMenu,
                    onDismissRequest = { showThreeDotsMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Abschlussstatistik 📊") },
                        onClick = {
                            showThreeDotsMenu = false
                            showHistorieAndAbschlüsse = true
                            showFullscreenStatsDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Annahme 📂") },
                        onClick = {
                            showThreeDotsMenu = false
                            showAnnahmeDokumenteDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Neukunden 👤") },
                        onClick = {
                            showThreeDotsMenu = false
                            showNeukundenDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Heiße Angebote 🔥") },
                        onClick = {
                            showThreeDotsMenu = false
                            showHeisseAngeboteDialog = true
                        }
                    )
                }
            }
        }

        // Animated Input Form for "Annahmen/Abschlüsse"
        AnimatedVisibility(
            visible = showAnnahmenForm,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF00FF87).copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Neuen Abschluss eintragen",
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF00FF87)
                        )
                    )

                    // 1. Spartenauswahl (Strom / Gas)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Sparte", style = MaterialTheme.typography.labelMedium.copy(color = Color.White.copy(alpha = 0.6f)))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Strom", "Gas").forEach { type ->
                                val selected = annahmeType == type
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (selected) Color(0xFF00FF87) else Color.White.copy(alpha = 0.05f))
                                        .clickable { annahmeType = type }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = type,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) Color(0xFF0F172A) else Color.White
                                    )
                                }
                            }
                        }
                    }

                    // 2. Kundentyp (Neukunde / Bestandskunde)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Kundentyp", style = MaterialTheme.typography.labelMedium.copy(color = Color.White.copy(alpha = 0.6f)))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Neukunde", "Bestandskunde").forEach { cType ->
                                val selected = customerType == cType
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (selected) Color(0xFF00FF87) else Color.White.copy(alpha = 0.05f))
                                        .clickable { customerType = cType }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = cType,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) Color(0xFF0F172A) else Color.White
                                    )
                                }
                            }
                        }
                    }

                    // 3. Kundennummer Input field
                    OutlinedTextField(
                        value = customerNumberInput,
                        onValueChange = { customerNumberInput = it },
                        label = { Text("Kundennummer / Vertragsnummer") },
                        placeholder = { Text("z.B. KD-98124 oder Vertrag-223") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00FF87),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedLabelColor = Color(0xFF00FF87),
                            unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                        )
                    )

                    // 4. Verbrauch & Laufzeit Inputs side-by-side
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = consumptionInput,
                            onValueChange = { newValue ->
                                if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                                    consumptionInput = newValue
                                }
                            },
                            label = { Text("Verbrauch (kWh)") },
                            placeholder = { Text("z.B. 4000") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1.2f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00FF87),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedLabelColor = Color(0xFF00FF87),
                                unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                            )
                        )

                        OutlinedTextField(
                            value = termInput,
                            onValueChange = { newValue ->
                                if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                                    termInput = newValue
                                }
                            },
                            label = { Text("Laufzeit (Jahre)") },
                            placeholder = { Text("z.B. 2") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(0.8f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00FF87),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedLabelColor = Color(0xFF00FF87),
                                unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                            )
                        )
                    }

                    Button(
                        onClick = {
                            val consumption = consumptionInput.toLongOrNull() ?: 0L
                            val term = termInput.toIntOrNull() ?: 1
                            if (consumption > 0) {
                                viewModel.saveAnnahme(
                                    type = annahmeType,
                                    customerType = customerType,
                                    consumption = consumption,
                                    termYears = term,
                                    customerNumber = customerNumberInput.trim()
                                )
                                sendAnnahmeNotification(
                                    context = context,
                                    type = annahmeType,
                                    customerType = customerType,
                                    consumption = consumption,
                                    customerNumber = customerNumberInput.trim()
                                )
                                consumptionInput = ""
                                customerNumberInput = ""
                                Toast.makeText(context, "Abschluss erfolgreich eingetragen!", Toast.LENGTH_SHORT).show()
                                showAnnahmenForm = false // Close the form on success
                            } else {
                                Toast.makeText(context, "Bitte geben Sie einen gültigen Verbrauch ein.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF87)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Eintragen", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Full-screen Dialog for Abschluss-Statistik
        if (showFullscreenStatsDialog) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showFullscreenStatsDialog = false },
                properties = androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = false
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F172A)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Close button and Title
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Abschluss-Statistik (Vollbild)",
                                style = TextStyle(
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = Color(0xFF00FF87)
                                )
                            )
                            IconButton(onClick = { showFullscreenStatsDialog = false }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Schließen", tint = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        val totalCount = annahmen.size
                        val totalWeighted = annahmen.sumOf { it.weightedVolume }
                        val stromWeighted = annahmen.filter { it.type == "Strom" }.sumOf { it.weightedVolume }
                        val gasWeighted = annahmen.filter { it.type == "Gas" }.sumOf { it.weightedVolume }

                        val neukundenCount = annahmen.count { it.customerType == "Neukunde" }
                        val bestandskundenCount = annahmen.count { it.customerType == "Bestandskunde" }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFF00FF87), modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Abschlüsse", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                                    Text("$totalCount", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00FF87))
                                }
                            }

                            Card(
                                modifier = Modifier.weight(1.5f),
                                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.TrendingUp, contentDescription = null, tint = Color(0xFF00FF87), modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Volumen gewichtet", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                                    val numberFormat = java.text.NumberFormat.getIntegerInstance(Locale.GERMANY)
                                    Text("${numberFormat.format(totalWeighted)} kWh", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00FF87))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Detailed split inside Fullscreen Stats
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("Detaillierte Übersicht", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                
                                val numberFormat = java.text.NumberFormat.getIntegerInstance(Locale.GERMANY)
                                
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("⚡ Strom verkauft:", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
                                    Text("${numberFormat.format(stromWeighted)} kWh", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Divider(color = Color.White.copy(alpha = 0.08f))
                                
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("🔥 Gas verkauft:", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
                                    Text("${numberFormat.format(gasWeighted)} kWh", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Divider(color = Color.White.copy(alpha = 0.08f))
                                
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("👥 Neukunden / Bestandskunden:", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
                                    Text("$neukundenCount / $bestandskundenCount", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "Eingetragene Abschlüsse:",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (annahmen.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                annahmen.forEach { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = "${item.type} (${item.customerType})",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                                if (item.customerNumber.isNotEmpty()) {
                                                    Box(
                                                        modifier = Modifier
                                                            .background(Color(0xFF00FF87).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = item.customerNumber,
                                                            color = Color(0xFF00FF87),
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            val numberFormat = java.text.NumberFormat.getIntegerInstance(Locale.GERMANY)
                                            Text(
                                                text = "${numberFormat.format(item.consumption)} kWh * ${item.termYears} J = ${numberFormat.format(item.weightedVolume)} kWh",
                                                fontSize = 12.sp,
                                                color = Color.White.copy(alpha = 0.6f)
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.deleteAnnahme(item.id) }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Löschen",
                                                tint = Color.Red.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Noch keine Abschlüsse eingetragen.",
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 13.sp
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }

        // Full-screen Dialog for Annahmen & Dokumente
        if (showAnnahmeDokumenteDialog) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showAnnahmeDokumenteDialog = false },
                properties = androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = false
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F172A)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFF00FF87), modifier = Modifier.size(28.dp))
                                Text(
                                    text = "Annahmen & Dokumente",
                                    style = TextStyle(
                                        fontFamily = FontFamily.SansSerif,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp,
                                        color = Color.White
                                    )
                                )
                            }
                            IconButton(onClick = { showAnnahmeDokumenteDialog = false }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Schließen", tint = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Search Filter field
                            OutlinedTextField(
                                value = docSearchQuery,
                                onValueChange = { docSearchQuery = it },
                                label = { Text("Kundennummer suchen...") },
                                placeholder = { Text("z.B. KD-99231") },
                                singleLine = true,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Suchen",
                                        tint = Color(0xFF00FF87)
                                    )
                                },
                                trailingIcon = {
                                    if (docSearchQuery.isNotEmpty()) {
                                        IconButton(onClick = { docSearchQuery = "" }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Leeren",
                                                tint = Color.White.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF00FF87),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                    focusedLabelColor = Color(0xFF00FF87),
                                    unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                                )
                            )

                            // List of integrated documents
                            val filteredDocs = annahmeDokumente.filter {
                                docSearchQuery.isBlank() || it.customerNumber.contains(docSearchQuery, ignoreCase = true)
                            }

                            if (filteredDocs.isNotEmpty()) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    filteredDocs.forEach { doc ->
                                        val isExpanded = selectedAnnahmeDocId == doc.id
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable {
                                                    selectedAnnahmeDocId = if (isExpanded) null else doc.id
                                                },
                                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
                                            border = BorderStroke(
                                                1.dp,
                                                if (isExpanded) Color(0xFF00FF87).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.05f)
                                            )
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = "Kundennummer: ${doc.customerNumber}",
                                                            fontSize = 15.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF00FF87)
                                                        )
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                            text = "Datei: ${doc.fileName}",
                                                            fontSize = 12.sp,
                                                            color = Color.White.copy(alpha = 0.6f)
                                                        )
                                                    }

                                                    Icon(
                                                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                        contentDescription = if (isExpanded) "Details verbergen" else "Details anzeigen",
                                                        tint = Color.White.copy(alpha = 0.7f)
                                                    )
                                                }

                                                // Expanded details with file content overview and download button
                                                AnimatedVisibility(
                                                    visible = isExpanded,
                                                    enter = expandVertically() + fadeIn(),
                                                    exit = shrinkVertically() + fadeOut()
                                                ) {
                                                    Column(
                                                        modifier = Modifier
                                                            .padding(top = 10.dp)
                                                            .fillMaxWidth()
                                                            .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                                                            .padding(12.dp)
                                                    ) {
                                                        Text(
                                                            text = "Dokument-Inhalt / Kurzinfo:",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.White.copy(alpha = 0.5f)
                                                        )
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(
                                                            text = doc.fileContentString.ifEmpty { "(Kein Text hinterlegt)" },
                                                            fontSize = 13.sp,
                                                            color = Color.White
                                                        )
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        Text(
                                                            text = "Typ: ${doc.fileType} | Importiert: ${java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date(doc.timestamp))}",
                                                            fontSize = 10.sp,
                                                            color = Color.White.copy(alpha = 0.4f)
                                                        )
                                                        Spacer(modifier = Modifier.height(12.dp))

                                                        // Elegant Download button
                                                        Button(
                                                            onClick = {
                                                                viewModel.downloadAnnahmeDokument(doc) { bytes ->
                                                                    if (bytes != null) {
                                                                        saveBytesToDownloads(
                                                                            context = context,
                                                                            fileName = doc.fileName,
                                                                            bytes = bytes
                                                                        )
                                                                    } else {
                                                                        Toast.makeText(
                                                                            context,
                                                                            "PDF konnte nicht aus Supabase geladen werden.",
                                                                            Toast.LENGTH_LONG
                                                                        ).show()
                                                                    }
                                                                }
                                                            },
                                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF87)),
                                                            shape = RoundedCornerShape(8.dp),
                                                            modifier = Modifier.fillMaxWidth(),
                                                            contentPadding = PaddingValues(vertical = 8.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.ArrowDownward,
                                                                contentDescription = null,
                                                                tint = Color(0xFF0F172A),
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text(
                                                                text = "Datei herunterladen 📥",
                                                                color = Color(0xFF0F172A),
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (docSearchQuery.isNotEmpty()) "Keine Dokumente für diese Kundennummer gefunden." else "Keine Annahme-Dokumente vorhanden.",
                                        color = Color.White.copy(alpha = 0.4f),
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Full-screen Dialog for Neukunden
        if (showNeukundenDialog) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showNeukundenDialog = false },
                properties = androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = false
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F172A)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF00FF87), modifier = Modifier.size(28.dp))
                                Text(
                                    text = "Neukunden-Verwaltung 👤",
                                    style = TextStyle(
                                        fontFamily = FontFamily.SansSerif,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp,
                                        color = Color.White
                                    )
                                )
                            }
                            IconButton(onClick = { showNeukundenDialog = false }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Schließen", tint = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Form to Add a Neukunde
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                border = BorderStroke(1.dp, Color(0xFF00FF87).copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        "Neuen Kunden anlegen ➕",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF00FF87)
                                    )

                                    // Customer Number input
                                    OutlinedTextField(
                                        value = neukundeCustomerNumber,
                                        onValueChange = { neukundeCustomerNumber = it },
                                        label = { Text("Kundennummer") },
                                        placeholder = { Text("z.B. KD-55291") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF00FF87),
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                            focusedLabelColor = Color(0xFF00FF87),
                                            unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                                        )
                                    )

                                    // Phone input
                                    OutlinedTextField(
                                        value = neukundePhone,
                                        onValueChange = { neukundePhone = it },
                                        label = { Text("Telefonnummer") },
                                        placeholder = { Text("z.B. +49 176 12345678") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF00FF87),
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                            focusedLabelColor = Color(0xFF00FF87),
                                            unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                                        )
                                    )

                                    // Create button
                                    Button(
                                        onClick = {
                                            if (neukundeCustomerNumber.isBlank() || neukundePhone.isBlank()) {
                                                Toast.makeText(context, "Bitte füllen Sie beide Felder aus!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                viewModel.saveNeukunde(
                                                    customerNumber = neukundeCustomerNumber.trim(),
                                                    phone = neukundePhone.trim()
                                                )
                                                neukundeCustomerNumber = ""
                                                neukundePhone = ""
                                                Toast.makeText(context, "Neukunde erfolgreich angelegt! 🚀", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF87)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Kunden anlegen 🚀", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }

                            // List of Neukunden
                            Text(
                                text = "Aktive Neukunden:",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.7f)
                            )

                            if (neukunden.isNotEmpty()) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    neukunden.forEach { item ->
                                        val isAlert = item.callAttempts >= 5
                                        
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isAlert) Color(0xFF7F1D1D).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.04f)
                                            ),
                                            border = BorderStroke(
                                                width = if (isAlert) 2.dp else 1.dp,
                                                color = if (isAlert) Color.Red else Color.White.copy(alpha = 0.05f)
                                            ),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                // Top row: KD & delete button
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column {
                                                        Text(
                                                            text = "Kundennummer: ${item.customerNumber}",
                                                            fontSize = 15.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isAlert) Color.White else Color(0xFF00FF87)
                                                        )
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        val dateStr = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date(item.dateCreated))
                                                        Text(
                                                            text = "Datum: $dateStr",
                                                            fontSize = 11.sp,
                                                            color = Color.White.copy(alpha = 0.6f)
                                                        )
                                                    }

                                                    IconButton(
                                                        onClick = {
                                                            viewModel.deleteNeukunde(item.id)
                                                            Toast.makeText(context, "Kunde gelöscht.", Toast.LENGTH_SHORT).show()
                                                        }
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Delete,
                                                            contentDescription = "Löschen",
                                                            tint = if (isAlert) Color.White else Color.Red.copy(alpha = 0.7f)
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(8.dp))

                                                // Phone row
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(Icons.Default.Phone, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                                                    Text(
                                                        text = item.phone,
                                                        fontSize = 13.sp,
                                                        color = Color.White
                                                    )
                                                }

                                                Spacer(modifier = Modifier.height(10.dp))

                                                // Status tracker / display
                                                val statusLabel = when (item.status) {
                                                    "Anrufen" -> "1. Anrufen 📞"
                                                    "Datenmail schreiben" -> "2. Datenmail schreiben ✉️"
                                                    "Angebot erstellen" -> "3. Angebot erstellen 📝"
                                                    "Zum Stand fragen" -> "4. Zum Stand fragen ❓"
                                                    else -> item.status
                                                }

                                                Box(
                                                    modifier = Modifier
                                                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                                ) {
                                                    Text(
                                                        text = "Status: $statusLabel",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isAlert) Color.White else Color(0xFF00FF87)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.height(12.dp))

                                                // Action section: Call attempts & Status advancement
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // Call attempts indicator & plus button
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            text = "Anwählversuche: ${item.callAttempts}",
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isAlert) Color.White else Color.White.copy(alpha = 0.8f)
                                                        )
                                                        IconButton(
                                                            onClick = {
                                                                viewModel.incrementNeukundeCallAttempts(item)
                                                            },
                                                            modifier = Modifier
                                                                .size(24.dp)
                                                                .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Add,
                                                                contentDescription = "+1 Anwählversuch",
                                                                tint = Color.White,
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                        }
                                                    }

                                                    // Next status button
                                                    val nextActionText = when (item.status) {
                                                        "Anrufen" -> "Datenmail ➡️"
                                                        "Datenmail schreiben" -> "Angebot ➡️"
                                                        "Angebot erstellen" -> "Abschließen ➡️"
                                                        else -> "Fertig ➡️"
                                                    }

                                                    Button(
                                                        onClick = {
                                                            viewModel.advanceNeukundeStatus(item) {
                                                                Toast.makeText(context, "Kunde hat 'Zum Stand fragen' erreicht und wurde erfolgreich entfernt! 🎉", Toast.LENGTH_LONG).show()
                                                            }
                                                        },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = if (isAlert) Color.White else Color(0xFF00FF87)
                                                        ),
                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Text(
                                                            text = nextActionText,
                                                            color = Color(0xFF0F172A),
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Keine aktiven Neukunden vorhanden.",
                                        color = Color.White.copy(alpha = 0.4f),
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Full-screen Dialog for Heiße Angebote
        if (showHeisseAngeboteDialog) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showHeisseAngeboteDialog = false },
                properties = androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = false
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F172A)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFF00FF87), modifier = Modifier.size(28.dp))
                                Text(
                                    text = "Heiße Angebote 🔥",
                                    style = TextStyle(
                                        fontFamily = FontFamily.SansSerif,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp,
                                        color = Color.White
                                    )
                                )
                            }
                            IconButton(onClick = { showHeisseAngeboteDialog = false }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Schließen", tint = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Form to Add a Heißes Angebot
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                border = BorderStroke(1.dp, Color(0xFF00FF87).copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        "Heißes Angebot anlegen ➕",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF00FF87)
                                    )

                                    // Customer Number input
                                    OutlinedTextField(
                                        value = heissAngebotCustomerNumber,
                                        onValueChange = { heissAngebotCustomerNumber = it },
                                        label = { Text("Kundennummer") },
                                        placeholder = { Text("z.B. KD-99231") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF00FF87),
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                            focusedLabelColor = Color(0xFF00FF87),
                                            unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                                        )
                                    )

                                    // Phone input
                                    OutlinedTextField(
                                        value = heissAngebotPhone,
                                        onValueChange = { heissAngebotPhone = it },
                                        label = { Text("Telefonnummer") },
                                        placeholder = { Text("z.B. +49 176 87654321") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF00FF87),
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                            focusedLabelColor = Color(0xFF00FF87),
                                            unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                                        )
                                    )

                                    // Notes input
                                    OutlinedTextField(
                                        value = heissAngebotNotes,
                                        onValueChange = { heissAngebotNotes = it },
                                        label = { Text("Details / Notizen (optional)") },
                                        placeholder = { Text("z.B. Will unbedingt heute unterschreiben") },
                                        singleLine = false,
                                        maxLines = 3,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF00FF87),
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                            focusedLabelColor = Color(0xFF00FF87),
                                            unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                                        )
                                    )

                                    // Create button
                                    Button(
                                        onClick = {
                                            if (heissAngebotCustomerNumber.isBlank() || heissAngebotPhone.isBlank()) {
                                                Toast.makeText(context, "Bitte füllen Sie mindestens Kundennummer und Telefonnummer aus!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                viewModel.saveHeissAngebot(
                                                    customerNumber = heissAngebotCustomerNumber.trim(),
                                                    phone = heissAngebotPhone.trim(),
                                                    notes = heissAngebotNotes.trim()
                                                )
                                                heissAngebotCustomerNumber = ""
                                                heissAngebotPhone = ""
                                                heissAngebotNotes = ""
                                                Toast.makeText(context, "Heißes Angebot erfolgreich angelegt! 🔥", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF87)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Angebot anlegen 🔥", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }

                            // List of Heiße Angebote
                            Text(
                                text = "Aktive heiße Angebote:",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.7f)
                            )

                            if (heisseAngebote.isNotEmpty()) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    heisseAngebote.forEach { item ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(
                                                containerColor = Color.White.copy(alpha = 0.04f)
                                            ),
                                            border = BorderStroke(
                                                width = 1.dp,
                                                color = Color(0xFF00FF87).copy(alpha = 0.2f)
                                            ),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                // Top row: KD & delete button
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column {
                                                        Text(
                                                            text = "Kundennummer: ${item.customerNumber}",
                                                            fontSize = 15.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF00FF87)
                                                        )
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        val dateStr = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date(item.dateCreated))
                                                        Text(
                                                            text = "Erstellt: $dateStr",
                                                            fontSize = 11.sp,
                                                            color = Color.White.copy(alpha = 0.6f)
                                                        )
                                                    }

                                                    IconButton(
                                                        onClick = {
                                                            viewModel.deleteHeissAngebot(item.id)
                                                            Toast.makeText(context, "Angebot gelöscht.", Toast.LENGTH_SHORT).show()
                                                        }
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Delete,
                                                            contentDescription = "Löschen",
                                                            tint = Color.Red.copy(alpha = 0.7f)
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(8.dp))

                                                // Phone row
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(Icons.Default.Phone, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                                                    Text(
                                                        text = item.phone,
                                                        fontSize = 13.sp,
                                                        color = Color.White
                                                    )
                                                }

                                                if (item.notes.isNotBlank()) {
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Text(
                                                        text = "Notiz: ${item.notes}",
                                                        fontSize = 12.sp,
                                                        color = Color.White.copy(alpha = 0.8f),
                                                        style = TextStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.height(12.dp))

                                                // Action section: Call attempts & Direct Call
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // Call attempts indicator & plus button
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            text = "Anwählversuche: ${item.callAttempts}",
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.White.copy(alpha = 0.8f)
                                                        )
                                                        IconButton(
                                                            onClick = {
                                                                viewModel.incrementHeissAngebotCallAttempts(item)
                                                            },
                                                            modifier = Modifier
                                                                .size(24.dp)
                                                                .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Add,
                                                                contentDescription = "+1 Anwählversuch",
                                                                tint = Color.White,
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                        }
                                                    }

                                                    // Call button
                                                    Button(
                                                        onClick = {
                                                            viewModel.initiateCall(item.phone, name = "Kunde (${item.customerNumber})")
                                                            showHeisseAngeboteDialog = false
                                                            Toast.makeText(context, "Anruf wird gestartet... 📞", Toast.LENGTH_SHORT).show()
                                                        },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = Color(0xFF00FF87)
                                                        ),
                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Phone,
                                                            contentDescription = "Anrufen",
                                                            tint = Color(0xFF0F172A),
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = "Anrufen",
                                                            color = Color(0xFF0F172A),
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(10.dp))
                                                androidx.compose.material3.HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                                                Spacer(modifier = Modifier.height(10.dp))

                                                Button(
                                                    onClick = {
                                                        convertingHeissAngebot = item
                                                    },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = Color(0xFF10B981)
                                                    ),
                                                    modifier = Modifier.fillMaxWidth().testTag("convert_heiss_angebot_btn"),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = "Als Abschluss buchen",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "In Abschlüsse aufnehmen ✔️",
                                                        color = Color.White,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Keine aktiven heißen Angebote vorhanden.",
                                        color = Color.White.copy(alpha = 0.4f),
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Dialog to convert a Heißes Angebot directly into an Abschluss (Annahme)
        if (convertingHeissAngebot != null) {
            val item = convertingHeissAngebot!!
            var selectedType by remember { mutableStateOf("Strom") }
            var selectedCustomerType by remember { mutableStateOf("Neukunde") }
            var consumption by remember { mutableStateOf("3500") }
            var termYears by remember { mutableStateOf("2") }
            var customerNumber by remember { mutableStateOf(item.customerNumber) }

            androidx.compose.ui.window.Dialog(
                onDismissRequest = { convertingHeissAngebot = null }
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1E293B),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF00FF87).copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Abschluss eintragen 📝",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )

                        Text(
                            text = "Kunde: ${item.customerNumber} (${item.phone})",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )

                        androidx.compose.material3.HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                        // Kundennummer
                        OutlinedTextField(
                            value = customerNumber,
                            onValueChange = { customerNumber = it },
                            label = { Text("Kundennummer", color = Color.White.copy(alpha = 0.6f)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF00FF87),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Type (Strom / Gas)
                        Column {
                            Text("Spartentyp", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { selectedType = "Strom" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedType == "Strom") Color(0xFF00FF87) else Color.White.copy(alpha = 0.1f)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Strom ⚡", color = if (selectedType == "Strom") Color(0xFF0F172A) else Color.White)
                                }
                                Button(
                                    onClick = { selectedType = "Gas" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedType == "Gas") Color(0xFF00FF87) else Color.White.copy(alpha = 0.1f)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Gas 🔥", color = if (selectedType == "Gas") Color(0xFF0F172A) else Color.White)
                                }
                            }
                        }

                        // Customer Type (Neukunde / Bestandskunde)
                        Column {
                            Text("Kundentyp", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { selectedCustomerType = "Neukunde" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedCustomerType == "Neukunde") Color(0xFF00FF87) else Color.White.copy(alpha = 0.1f)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Neukunde 🆕", color = if (selectedCustomerType == "Neukunde") Color(0xFF0F172A) else Color.White, fontSize = 11.sp)
                                }
                                Button(
                                    onClick = { selectedCustomerType = "Bestandskunde" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedCustomerType == "Bestandskunde") Color(0xFF00FF87) else Color.White.copy(alpha = 0.1f)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Bestand 👥", color = if (selectedCustomerType == "Bestandskunde") Color(0xFF0F172A) else Color.White, fontSize = 11.sp)
                                }
                            }
                        }

                        // Consumption
                        OutlinedTextField(
                            value = consumption,
                            onValueChange = { consumption = it },
                            label = { Text("Jahresverbrauch (kWh)", color = Color.White.copy(alpha = 0.6f)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF00FF87),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        
                    }
                }
            }
        }
    }
}



@Composable
fun AddNeukundeDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String?, String?, String?, String?, String?, Long?, String?, String) -> Unit
) {
    var customerNumber by remember { mutableStateOf("KD-${(10000..99999).random()}") }
    var phone by remember { mutableStateOf("") }
    var customerName by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var deliveryAddress by remember { mutableStateOf("") }
    var meterNumber by remember { mutableStateOf("") }
    var consumption by remember { mutableStateOf("") }
    var energyType by remember { mutableStateOf("Strom") }
    var routine by remember { mutableStateOf("Gewerbe") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            border = BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Neukunde anlegen 👤",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = customerNumber,
                    onValueChange = { customerNumber = it },
                    label = { Text("Kundennummer") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00FF87),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Telefonnummer *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00FF87),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = customerName,
                    onValueChange = { customerName = it },
                    label = { Text("Name / Ansprechpartner") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00FF87),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = company,
                    onValueChange = { company = it },
                    label = { Text("Firma (Optional)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00FF87),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("E-Mail") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00FF87),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Abbrechen", color = Color.White.copy(alpha = 0.6f))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (phone.isNotBlank()) {
                                onConfirm(
                                    customerNumber.trim(),
                                    phone.trim(),
                                    customerName.ifBlank { null },
                                    company.ifBlank { null },
                                    email.ifBlank { null },
                                    deliveryAddress.ifBlank { null },
                                    meterNumber.ifBlank { null },
                                    consumption.toLongOrNull(),
                                    energyType,
                                    routine
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF87)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Speichern 🚀", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun IncomingCallBottomOverlay(
    contactName: String,
    contactPhone: String,
    contactCompany: String,
    contactReason: String,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        border = BorderStroke(2.dp, Color(0xFF00FF87))
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00FF87).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneCallback,
                        contentDescription = "Eingehender Anruf",
                        tint = Color(0xFF00FF87),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column {
                    Text(
                        text = contactName.ifBlank { "Eingehender Anruf" },
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = contactPhone.ifBlank { "Unbekannte Nummer" },
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = onDecline,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444))
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = "Ablehnen", tint = Color.White, modifier = Modifier.size(20.dp))
                }

                IconButton(
                    onClick = onAnswer,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00FF87))
                ) {
                    Icon(Icons.Default.Call, contentDescription = "Annehmen", tint = Color(0xFF0F172A), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun IncomingCallScreen(
    contactName: String,
    contactPhone: String,
    contactCompany: String,
    contactReason: String,
    contactNotes: String,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onMinimize: () -> Unit
) {
    Dialog(
        onDismissRequest = onMinimize,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val transition = rememberInfiniteTransition(label = "pulse_ring")
        val ringScale by transition.animateFloat(
            initialValue = 0.95f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "ring_scale"
        )
        val ringAlpha by transition.animateFloat(
            initialValue = 0.4f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "ring_alpha"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF070D18))
                .padding(24.dp)
        ) {
            // Top Minimize Button
            IconButton(
                onClick = onMinimize,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 16.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E293B))
            ) {
                Icon(Icons.Default.CloseFullscreen, contentDescription = "Minimieren", tint = Color.White)
            }

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Animated Glowing Avatar Ring
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(140.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(130.dp)
                            .graphicsLayer {
                                scaleX = ringScale
                                scaleY = ringScale
                                alpha = ringAlpha
                            }
                            .clip(CircleShape)
                            .background(Color(0xFF00FF87).copy(alpha = 0.2f))
                    )

                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B))
                            .border(2.dp, Color(0xFF00FF87), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        val initial = contactName.firstOrNull()?.toString()?.uppercase() ?: "?"
                        Text(
                            text = initial,
                            color = Color(0xFF00FF87),
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = "EINGEHENDER ANRUF ⚡",
                    color = Color(0xFF00FF87),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )

                Text(
                    text = contactName.ifBlank { "Unbekannter Anrufer" },
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                if (contactCompany.isNotBlank()) {
                    Text(
                        text = contactCompany,
                        color = Color(0xFF94A3B8),
                        fontSize = 16.sp
                    )
                }

                Text(
                    text = contactPhone,
                    color = Color(0xFF00F0FF),
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Monospace
                )

                if (contactReason.isNotBlank() || contactNotes.isNotBlank()) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (contactReason.isNotBlank()) {
                                Text("Grund: $contactReason", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                            if (contactNotes.isNotBlank()) {
                                Text("Notiz: $contactNotes", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Bottom Action Buttons
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Decline
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = onDecline,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444))
                    ) {
                        Icon(Icons.Default.CallEnd, contentDescription = "Ablehnen", tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                    Text("Ablehnen", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                }

                // Answer
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = onAnswer,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00FF87))
                    ) {
                        Icon(Icons.Default.Call, contentDescription = "Annehmen", tint = Color(0xFF0F172A), modifier = Modifier.size(34.dp))
                    }
                    Text("Annehmen", color = Color(0xFF00FF87), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun OngoingCallDialog(
    contactName: String,
    contactPhone: String,
    onHangUp: (Long) -> Unit,
    isAutoCallActive: Boolean,
    onHangUpAndPause: (Long) -> Unit,
    wrapUpData: com.example.viewmodel.WrapUpData,
    onNameChange: (String) -> Unit,
    onCustomerNumberChange: (String) -> Unit,
    onCompanyChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onCallReasonChange: (String) -> Unit,
    onToggleOffset: (String) -> Unit,
    onOutcomeChange: (String) -> Unit,
    contact: com.example.database.ContactEntity?,
    recentCallLogs: List<com.example.database.CallLogEntity>,
    onForceClose: () -> Unit,
    onMinimize: () -> Unit,
    onAddToHotbox: (String, String) -> Unit,
    onSaveInCallData: () -> Unit = {}
) {
    val context = LocalContext.current
    var localElapsedSeconds by remember { mutableStateOf(0L) }
    var showCustomerTimeline by remember { mutableStateOf(false) }

    val powerManager = remember {
        context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
    }
    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as? android.hardware.SensorManager
    }
    var isNearCheek by remember { mutableStateOf(false) }

    DisposableEffect(isSpeakerOn) {
        var proximityWakeLock: android.os.PowerManager.WakeLock? = null
        try {
            if (!isSpeakerOn && powerManager?.isWakeLockLevelSupported(android.os.PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK) == true) {
                proximityWakeLock = powerManager.newWakeLock(
                    android.os.PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                    "Stromruf:OngoingCallProximity"
                )
                proximityWakeLock.acquire()
            }
        } catch (e: Exception) {
            android.util.Log.e("OngoingCallDialog", "Failed to acquire proximity wakelock: ${e.localizedMessage}")
        }

        val proximitySensor = sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_PROXIMITY)
        val sensorListener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(event: android.hardware.SensorEvent?) {
                val distance = event?.values?.getOrNull(0) ?: Float.MAX_VALUE
                val maxRange = proximitySensor?.maximumRange ?: 5f
                isNearCheek = distance < maxRange
            }
            override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
        }
        if (proximitySensor != null) {
            sensorManager?.registerListener(sensorListener, proximitySensor, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)
        }

        onDispose {
            try {
                if (proximityWakeLock?.isHeld == true) {
                    proximityWakeLock?.release()
                }
            } catch (e: Exception) {}
            try {
                sensorManager?.unregisterListener(sensorListener)
            } catch (e: Exception) {}
        }
    }

    val clipboardManager = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
    }
    var lastCopiedCustomerNumber by remember { mutableStateOf("") }

    val checkClipboard = {
        try {
            if (clipboardManager != null && clipboardManager.hasPrimaryClip()) {
                val clipData = clipboardManager.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    val text = clipData.getItemAt(0)?.text?.toString()?.trim() ?: ""
                    val custNoMatch = Regex("""(?<!\d)([79]\d{5})(?!\d)""").find(text)?.value
                    if (custNoMatch != null && custNoMatch != wrapUpData.customerNumber && custNoMatch != lastCopiedCustomerNumber) {
                        lastCopiedCustomerNumber = custNoMatch
                        onCustomerNumberChange(custNoMatch)
                        Toast.makeText(context, "Kundennummer $custNoMatch automatisch übernommen 📋", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    DisposableEffect(clipboardManager) {
        val listener = android.content.ClipboardManager.OnPrimaryClipChangedListener {
            checkClipboard()
        }
        clipboardManager?.addPrimaryClipChangedListener(listener)
        onDispose {
            clipboardManager?.removePrimaryClipChangedListener(listener)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            checkClipboard()
            kotlinx.coroutines.delay(800)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            localElapsedSeconds += 1
        }
    }

    val liveServiceDuration = com.example.service.DialerInCallService.callDurationSeconds.value
    val currentDuration = if (liveServiceDuration > 0) liveServiceDuration else localElapsedSeconds

    val minutes = currentDuration / 60
    val seconds = currentDuration % 60
    val formattedDuration = String.format("%02d:%02d", minutes, seconds)

    var isMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(false) }
    var showDtmfPad by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }

    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
    }

    val callState = com.example.service.DialerInCallService.activeCallState.value
    val isRingingOrDialing = callState == android.telecom.Call.STATE_DIALING || 
                            callState == android.telecom.Call.STATE_CONNECTING || 
                            callState == android.telecom.Call.STATE_RINGING

    val statusTitle = when {
        isRingingOrDialing -> "WÄHLT / KLINGELT..."
        callState == android.telecom.Call.STATE_ACTIVE -> "IM GESPRÄCH"
        else -> "AKTIVER ANRUF"
    }

    Dialog(
        onDismissRequest = onMinimize,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF070D18))
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // FIXED STICKY TOP BAR - ALWAYS VISIBLE AT THE VERY TOP
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF0D1527),
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Top Row: Minimize, Status Badge, Live Duration, Hotbox Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onMinimize,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E293B))
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloseFullscreen,
                                contentDescription = "Minimieren (Bubble)",
                                tint = Color.White
                            )
                        }

                        // Status Badge
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isRingingOrDialing) Color(0xFFF59E0B).copy(alpha = 0.15f) else Color(0xFF00FF87).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, if (isRingingOrDialing) Color(0xFFF59E0B) else Color(0xFF00FF87))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isRingingOrDialing) Color(0xFFF59E0B) else Color(0xFF00FF87))
                                )
                                Text(
                                    text = statusTitle,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isRingingOrDialing) Color(0xFFF59E0B) else Color(0xFF00FF87)
                                )
                            }
                        }

                        // Duration Badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1E293B),
                            border = BorderStroke(1.dp, Color(0xFF334155))
                        ) {
                            Text(
                                text = formattedDuration,
                                color = Color(0xFF00FF87),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        // Hotbox Toggle
                        IconButton(
                            onClick = { onAddToHotbox(contactName, contactPhone) },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (contact?.isHotBox == true) Color(0xFFEF4444).copy(alpha = 0.2f) else Color(0xFF1E293B))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Whatshot,
                                contentDescription = "Hotbox",
                                tint = if (contact?.isHotBox == true) Color(0xFFEF4444) else Color.White
                            )
                        }
                    }

                    // PRIMARY HANGUP BUTTON - MOVED TO THE VERY TOP AS REQUESTED
                    Button(
                        onClick = {
                            if (isNearCheek && !isSpeakerOn) {
                                android.util.Log.w("OngoingCallDialog", "Cheek touch ignored on hangup button")
                                return@Button
                            }
                            onHangUp(currentDuration)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "Anruf beenden",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "🔴 ANRUF BEENDEN / AUFLEGEN",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Auto-Call Pause Button (if in auto mode)
                    if (isAutoCallActive) {
                        Button(
                            onClick = {
                                if (isNearCheek && !isSpeakerOn) {
                                    android.util.Log.w("OngoingCallDialog", "Cheek touch ignored on pause button")
                                    return@Button
                                }
                                onHangUpAndPause(currentDuration)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "⏸ Auflegen & Auto-Call Pause",
                                color = Color.Black,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Scrollable Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Caller Info Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131E30)),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00FF87).copy(alpha = 0.15f))
                                .border(2.dp, Color(0xFF00FF87), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            val displayName = wrapUpData.name.ifBlank { contactName.ifBlank { contact?.name ?: "" } }
                            val initial = displayName.firstOrNull()?.toString()?.uppercase() ?: "☎"
                            Text(
                                text = initial,
                                color = Color(0xFF00FF87),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            val currentDisplayName = wrapUpData.name.ifBlank { contactName.ifBlank { contact?.name ?: "Neuer Gesprächspartner" } }
                            Text(
                                text = currentDisplayName,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )

                            val displayCompany = wrapUpData.company.ifBlank { contact?.company ?: "" }
                            if (displayCompany.isNotBlank()) {
                                Text(
                                    text = displayCompany,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 13.sp,
                                    maxLines = 1
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = contactPhone.ifBlank { contact?.phone ?: "" },
                                    color = Color(0xFF00F0FF),
                                    fontSize = 15.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold
                                )

                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("Telefonnummer", contactPhone)
                                        clipboard?.setPrimaryClip(clip)
                                        Toast.makeText(context, "Nummer kopiert 📋", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Nummer kopieren",
                                        tint = Color(0xFF00F0FF),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // EDITABLE CUSTOMER DATA CARD (NAME & KUNDENNUMMER EINTRAGEN WÄHREND DES ANRUFS)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141F32)),
                    border = BorderStroke(1.5.dp, Color(0xFF00F0FF).copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EditNote,
                                contentDescription = null,
                                tint = Color(0xFF00F0FF),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Kundendaten während des Anrufs erfassen",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Wird direkt zur Rufnummer gespeichert und übernommen.",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )

                        // Name Field
                        OutlinedTextField(
                            value = wrapUpData.name,
                            onValueChange = onNameChange,
                            label = { Text("Name / Ansprechpartner") },
                            placeholder = { Text("z.B. Max Mustermann") },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF00FF87), modifier = Modifier.size(20.dp))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF00FF87),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedContainerColor = Color(0xFF0F172A),
                                unfocusedContainerColor = Color(0xFF0F172A),
                                focusedLabelColor = Color(0xFF00FF87),
                                unfocusedLabelColor = Color(0xFF94A3B8)
                            )
                        )

                        // Kundennummer Field
                        OutlinedTextField(
                            value = wrapUpData.customerNumber,
                            onValueChange = onCustomerNumberChange,
                            label = { Text("Kundennummer") },
                            placeholder = { Text("z.B. 912345") },
                            leadingIcon = {
                                Icon(Icons.Default.AccountBox, contentDescription = null, tint = Color(0xFF00F0FF), modifier = Modifier.size(20.dp))
                            },
                            trailingIcon = {
                                if (wrapUpData.customerNumber.isNotBlank()) {
                                    IconButton(onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("Kundennummer", wrapUpData.customerNumber)
                                        clipboard?.setPrimaryClip(clip)
                                        Toast.makeText(context, "Kundennummer kopiert: ${wrapUpData.customerNumber} 📋", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Kundennummer kopieren",
                                            tint = Color(0xFF00F0FF),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF00F0FF),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedContainerColor = Color(0xFF0F172A),
                                unfocusedContainerColor = Color(0xFF0F172A),
                                focusedLabelColor = Color(0xFF00F0FF),
                                unfocusedLabelColor = Color(0xFF94A3B8)
                            )
                        )

                        // Firma / Zusatz Field
                        OutlinedTextField(
                            value = wrapUpData.company,
                            onValueChange = onCompanyChange,
                            label = { Text("Firma / Zusatz (optional)") },
                            placeholder = { Text("z.B. Stadtwerke / Energie AG") },
                            leadingIcon = {
                                Icon(Icons.Default.Business, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(20.dp))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF94A3B8),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedContainerColor = Color(0xFF0F172A),
                                unfocusedContainerColor = Color(0xFF0F172A),
                                focusedLabelColor = Color(0xFF94A3B8),
                                unfocusedLabelColor = Color(0xFF64748B)
                            )
                        )

                        // Quick Save Button
                        Button(
                            onClick = onSaveInCallData,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F0FF).copy(alpha = 0.15f)),
                            border = BorderStroke(1.dp, Color(0xFF00F0FF).copy(alpha = 0.6f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Zwischenspeichern",
                                tint = Color(0xFF00F0FF),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "💾 Kundendaten jetzt zwischenspeichern",
                                color = Color(0xFF00F0FF),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Quick In-Call Action Bar (Mute, Speaker, DTMF, Minimize)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mute
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = {
                                isMuted = !isMuted
                                audioManager?.isMicrophoneMute = isMuted
                            },
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(if (isMuted) Color(0xFFF59E0B) else Color(0xFF1E293B))
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Stumm",
                                tint = if (isMuted) Color.Black else Color.White
                            )
                        }
                        Text(if (isMuted) "Stumm" else "Mikro", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }

                    // Speaker
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = {
                                isSpeakerOn = !isSpeakerOn
                                try {
                                    com.example.service.DialerInCallService.instance?.setAudioRouteCompat(
                                        if (isSpeakerOn) android.telecom.CallAudioState.ROUTE_SPEAKER
                                        else android.telecom.CallAudioState.ROUTE_EARPIECE
                                    )
                                } catch (e: Exception) {
                                    audioManager?.isSpeakerphoneOn = isSpeakerOn
                                }
                            },
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(if (isSpeakerOn) Color(0xFF00F0FF) else Color(0xFF1E293B))
                        ) {
                            Icon(
                                imageVector = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                                contentDescription = "Lautsprecher",
                                tint = if (isSpeakerOn) Color.Black else Color.White
                            )
                        }
                        Text("Lautsprecher", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }

                    // Dialpad DTMF
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = { showDtmfPad = !showDtmfPad },
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(if (showDtmfPad) Color(0xFF00FF87) else Color(0xFF1E293B))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Dialpad,
                                contentDescription = "Ziffern",
                                tint = if (showDtmfPad) Color(0xFF0F172A) else Color.White
                            )
                        }
                        Text("Tasten", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }

                    // Minimize
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = onMinimize,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E293B))
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureInPicture,
                                contentDescription = "Bubble",
                                tint = Color.White
                            )
                        }
                        Text("Minimieren", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }
                }

                // DTMF Dialpad when active
                AnimatedVisibility(visible = showDtmfPad) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        border = BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val rows = listOf(
                                listOf('1', '2', '3'),
                                listOf('4', '5', '6'),
                                listOf('7', '8', '9'),
                                listOf('*', '0', '#')
                            )
                            for (row in rows) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    for (key in row) {
                                        Button(
                                            onClick = { com.example.service.DialerInCallService.playDtmf(key) },
                                            modifier = Modifier.weight(1f).height(46.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A))
                                        ) {
                                            Text(key.toString(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Live Wrap-Up & Notes Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Gesprächsnotiz & Ergebnis ✍️",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // Outcome Chips
                        Text("Ergebnis:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        val outcomes = listOf("Erreicht", "Nicht erreicht", "Rückruf", "Termin", "Kein Interesse", "Annahme")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(outcomes) { item ->
                                val isSelected = wrapUpData.outcome == item
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onOutcomeChange(item) },
                                    color = if (isSelected) Color(0xFF00FF87).copy(alpha = 0.2f) else Color(0xFF0F172A),
                                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF00FF87) else Color(0xFF334155))
                                ) {
                                    Text(
                                        text = item,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color(0xFF00FF87) else Color.White,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        // Follow-up Offset Shortcuts
                        Text("Wiedervorlage:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        val offsets = listOf("+1 Tag", "+3 Tage", "+1 Woche", "+2 Wochen")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(offsets) { offset ->
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onToggleOffset(offset) },
                                    color = Color(0xFF0F172A),
                                    border = BorderStroke(1.dp, Color(0xFF334155))
                                ) {
                                    Text(
                                        text = offset,
                                        fontSize = 11.sp,
                                        color = Color(0xFF00F0FF),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        // Live Note Field
                        OutlinedTextField(
                            value = wrapUpData.note,
                            onValueChange = onNoteChange,
                            placeholder = { Text("Notiz während des Gesprächs eingeben...", color = Color(0xFF64748B), fontSize = 13.sp) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF00FF87),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedContainerColor = Color(0xFF0F172A),
                                unfocusedContainerColor = Color(0xFF0F172A)
                            )
                        )

                        OutlinedButton(
                            onClick = { showCustomerTimeline = true },
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, Color(0xFF00F0FF))
                        ) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                tint = Color(0xFF00F0FF),
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                "Kundenverlauf ansehen / weitere Notiz",
                                color = Color(0xFF00F0FF),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Recent History Toggle
                val matchingLogs = remember(recentCallLogs, contactPhone) {
                    recentCallLogs.filter { ContactsUtil.arePhoneNumbersMatching(it.phone, contactPhone) }.take(3)
                }

                if (matchingLogs.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showHistory = !showHistory },
                        color = Color(0xFF1E293B),
                        border = BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Bisherige Anrufe (${matchingLogs.size})",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Icon(
                                    imageVector = if (showHistory) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8)
                                )
                            }

                            if (showHistory) {
                                matchingLogs.forEach { log ->
                                    val dateStr = java.text.SimpleDateFormat("dd.MM.yy HH:mm", java.util.Locale.GERMANY).format(java.util.Date(log.timestamp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("$dateStr · ${log.outcome ?: "Anruf"}", fontSize = 12.sp, color = Color(0xFF94A3B8))
                                        Text("${log.durationSeconds}s", fontSize = 12.sp, color = Color(0xFF00FF87))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCustomerTimeline) {
        com.example.ui.screens.CustomerTimelineDialog(
            contactId = contact?.id,
            contactName = contact?.name ?: contactName,
            phone = contact?.phone?.takeIf { it.isNotBlank() } ?: contactPhone,
            callLogs = recentCallLogs,
            source = "call_mask",
            onDismiss = { showCustomerTimeline = false }
        )
    }
}

@Composable
fun AddFollowUpDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Long, String) -> Unit,
    contacts: List<com.example.database.ContactEntity> = emptyList(),
    initialDueAt: Long? = null,
    initialPhone: String = "",
    initialName: String = "",
    contactPhone: String = ""
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initialName) }
    var phone by remember { mutableStateOf(initialPhone.ifBlank { contactPhone }) }
    var note by remember { mutableStateOf("") }
    var callReason by remember { mutableStateOf("Nachfassen") }

    val cal = remember {
        java.util.Calendar.getInstance().apply {
            if (initialDueAt != null && initialDueAt > 0) {
                timeInMillis = initialDueAt
            } else {
                add(java.util.Calendar.DAY_OF_YEAR, 1)
                set(java.util.Calendar.HOUR_OF_DAY, 10)
                set(java.util.Calendar.MINUTE, 0)
            }
        }
    }
    var dueTimestamp by remember { mutableStateOf(cal.timeInMillis) }
    val sdf = remember { java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.GERMANY) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            border = BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Wiedervorlage planen ⏰",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name / Kontakt") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00FF87),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Telefonnummer *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00FF87),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Date Picker Button
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            val now = java.util.Calendar.getInstance().apply { timeInMillis = dueTimestamp }
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    val timeCal = java.util.Calendar.getInstance().apply { timeInMillis = dueTimestamp }
                                    TimePickerDialog(
                                        context,
                                        { _, hour, min ->
                                            val newCal = java.util.Calendar.getInstance().apply {
                                                set(y, m, d, hour, min, 0)
                                            }
                                            dueTimestamp = newCal.timeInMillis
                                        },
                                        timeCal.get(java.util.Calendar.HOUR_OF_DAY),
                                        timeCal.get(java.util.Calendar.MINUTE),
                                        true
                                    ).show()
                                },
                                now.get(java.util.Calendar.YEAR),
                                now.get(java.util.Calendar.MONTH),
                                now.get(java.util.Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                    color = Color(0xFF0F172A),
                    border = BorderStroke(1.dp, Color(0xFF00FF87))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Fälligkeitsdatum & Uhrzeit", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            Text(sdf.format(java.util.Date(dueTimestamp)), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00FF87))
                        }
                        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Color(0xFF00FF87))
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Notiz / Aufgabe") },
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00FF87),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Abbrechen", color = Color.White.copy(alpha = 0.6f))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (phone.isNotBlank()) {
                                onConfirm(name.trim(), phone.trim(), note.trim(), dueTimestamp, callReason)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF87)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Planen ⏰", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun RingtonePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (android.net.Uri, String) -> Unit
) {
    val context = LocalContext.current
    val ringtones = remember {
        val manager = RingtoneManager(context).apply {
            setType(RingtoneManager.TYPE_NOTIFICATION or RingtoneManager.TYPE_RINGTONE or RingtoneManager.TYPE_ALARM)
        }
        val cursor = manager.cursor
        val list = mutableListOf<Pair<android.net.Uri, String>>()
        while (cursor.moveToNext()) {
            val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
            val uri = manager.getRingtoneUri(cursor.position)
            if (uri != null && title != null) {
                list.add(uri to title)
            }
        }
        list
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            border = BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp).padding(8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Klingelton auswählen 🔔", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(ringtones) { (uri, title) ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onConfirm(uri, title) },
                            color = Color(0xFF0F172A)
                        ) {
                            Text(title, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Schließen", color = Color.White.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    appTheme: String = "",
    onThemeChange: (String) -> Unit = {},
    bgStyle: String = "",
    onBgStyleChange: (String) -> Unit = {},
    screenBrightness: Float = 1f,
    onBrightnessChange: (Float) -> Unit = {},
    alarmEnabled: Boolean = false,
    onAlarmToggle: (Boolean) -> Unit = {},
    currentRingDuration: Int = 0,
    onRingDurationChange: (Int) -> Unit = {},
    currentRingtone: String = "",
    selectedRingtoneTitle: String = "",
    onSelectRingtoneClick: () -> Unit = {},
    autoCallDelaySeconds: Int = 0,
    onAutoCallDelaySecondsChange: (Int) -> Unit = {},
    preferredAudioDevice: String = "",
    onPreferredAudioDeviceChange: (String) -> Unit = {},
    clipboardBubblePosition: String = "",
    onClipboardBubblePositionChange: (String) -> Unit = {},
    clipboardBubbleOnLocalCopy: Boolean = false,
    onClipboardBubbleOnLocalCopyChange: (Boolean) -> Unit = {},
    onSignOut: (() -> Unit)? = null,
    isSimulationModeEnabled: Boolean = false,
    onSimulationModeToggle: (Boolean) -> Unit = {},
    isDefaultDialer: Boolean = false,
    isCallPermissionGranted: Boolean = false,
    onRequestDefaultDialer: () -> Unit = {},
    onRequestCallPermission: () -> Unit = {},
    onNotificationToggle: (Boolean) -> Unit = {},
    viewModel: StromrufViewModel? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("stromruf_prefs", Context.MODE_PRIVATE) }
    val secureSettings = remember { SecureIntegrationSettings(context) }
    val mailManager = remember { MailAccountManager(context) }
    val telegram = remember { TelegramClient(context) }
    val cfg = LocalThemeConfig.current
    val accent = cfg.primaryColor

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf(
        "⚡ Supabase",
        "🔑 Tokens & Mail",
        "✈️ Telegram",
        "📱 Telefonie",
        "🎨 Design",
        "🔌 MCP Server"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cfg.cardBackground),
            border = BorderStroke(1.dp, cfg.cardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Einstellungen", color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Schließen", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Horizontal Tab Bar
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = cfg.baseBackground,
                    contentColor = accent,
                    edgePadding = 4.dp,
                    indicator = { tabPositions ->
                        if (selectedTab < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = accent
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == index) accent else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    }
                }

                // Tab Content Area (Scrollable)
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        when (selectedTab) {
                            0 -> {
                                // ⚡ SUPABASE CLOUD TAB
                                val supabaseEmail = remember { SupabaseAuthClient.getSessionEmail(context) ?: "angemeldet" }
                                var isSyncing by remember { mutableStateOf(false) }

                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = cfg.baseBackground),
                                    border = BorderStroke(1.dp, cfg.cardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CloudDone, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Supabase Cloud-Anbindung", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        }
                                        Text("Angemeldet als: $supabaseEmail", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                        Text("Endpoint: https://yepluyipizbbrgoffqdq.supabase.co", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 11.sp)
                                        
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(8.dp).background(Color(0xFF10B981), CircleShape))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Status: Verbunden & Synchronisiert 🟢", color = Color(0xFF10B981), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }

                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = cfg.baseBackground),
                                    border = BorderStroke(1.dp, cfg.cardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("Live-Synchronisation", color = accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("Gleicht Kontakte, Hotbox-Listen, Wiedervorlagen, Anrufnotizen und PDF-Dokumente mit der Supabase-Datenbank ab.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)

                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    isSyncing = true
                                                    val localDao = AppDatabase.getDatabase(context).stromrufDao()
                                                    val ok = SupabaseDbClient.syncAllDown(context, localDao)
                                                    SupabaseDbClient.refreshLocalCache(context, localDao)
                                                    if (ok) {
                                                        Toast.makeText(context, "✅ Supabase erfolgreich synchronisiert!", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "⚠️ Sync abgeschlossen", Toast.LENGTH_SHORT).show()
                                                    }
                                                    isSyncing = false
                                                }
                                            },
                                            enabled = !isSyncing,
                                            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = cfg.onAccent),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            if (isSyncing) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = cfg.onAccent, strokeWidth = 2.dp)
                                                Spacer(Modifier.width(8.dp))
                                                Text("Synchronisiere...", color = cfg.onAccent, fontWeight = FontWeight.Bold)
                                            } else {
                                                Icon(Icons.Default.Sync, contentDescription = null, tint = cfg.onAccent)
                                                Spacer(Modifier.width(8.dp))
                                                Text("Jetzt mit Supabase synchronisieren", color = cfg.onAccent, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        Text("ℹ️ Automatische Hintergrund-Synchronisation alle 15 Sekunden aktiv.", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 10.sp)
                                    }
                                }

                                if (onSignOut != null) {
                                    OutlinedButton(
                                        onClick = {
                                            onSignOut.invoke()
                                            onDismiss()
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                                        border = BorderStroke(1.dp, Color(0xFFEF4444)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.ExitToApp, contentDescription = null, tint = Color(0xFFEF4444))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Aus Supabase abmelden 🚪", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            1 -> {
                                // 🔑 TOKENS & E-MAIL TAB
                                var openAiKey by remember { mutableStateOf(secureSettings.getOpenAiKey() ?: "") }
                                var openAiStatus by remember { mutableStateOf("") }
                                var openAiTesting by remember { mutableStateOf(false) }

                                var googleClientId by remember { mutableStateOf(secureSettings.getGoogleClientId() ?: "") }
                                var microsoftClientId by remember { mutableStateOf(secureSettings.getMicrosoftClientId() ?: "") }
                                var defaultMailProvider by remember { mutableStateOf(secureSettings.getDefaultMailProvider()) }
                                var mailStatus by remember { mutableStateOf("") }

                                // OpenAI Card
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = cfg.baseBackground),
                                    border = BorderStroke(1.dp, cfg.cardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("OpenAI API-Schlüssel", color = accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("Wird für KI-Mailgenerierung, Whisper-Transkription und Agenten genutzt.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)

                                        OutlinedTextField(
                                            value = openAiKey,
                                            onValueChange = { openAiKey = it },
                                            label = { Text("OpenAI API Key (sk-...)") },
                                            visualTransformation = PasswordVisualTransformation(),
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )

                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                            Button(
                                                onClick = {
                                                    secureSettings.saveOpenAiKey(openAiKey)
                                                    openAiStatus = "API-Key gespeichert! 💾"
                                                },
                                                enabled = openAiKey.isNotBlank(),
                                                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = cfg.onAccent),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Speichern", color = cfg.onAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    scope.launch {
                                                        openAiTesting = true
                                                        openAiStatus = "Teste Verbindung..."
                                                        val ok = OpenAiClient(context).testApiKey()
                                                        openAiStatus = if (ok) "Verbindung erfolgreich! ✅" else "Fehlgeschlagen ❌ (Key prüfen)"
                                                        openAiTesting = false
                                                    }
                                                },
                                                enabled = openAiKey.isNotBlank() && !openAiTesting,
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(if (openAiTesting) "Testet..." else "Testen ⚡", fontSize = 12.sp)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    secureSettings.clearOpenAiKey()
                                                    openAiKey = ""
                                                    openAiStatus = "Key gelöscht."
                                                },
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Löschen", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                            }
                                        }

                                        if (openAiStatus.isNotBlank()) {
                                            Text(openAiStatus, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp)
                                        }
                                    }
                                }

                                // Google / Gmail Card
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = cfg.baseBackground),
                                    border = BorderStroke(1.dp, cfg.cardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("Google / Gmail (OAuth)", color = Color(0xFF0284C7), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        OutlinedTextField(
                                            value = googleClientId,
                                            onValueChange = { googleClientId = it },
                                            label = { Text("Eigene Google Client-ID (optional)") },
                                            placeholder = { Text("Standard nutzen oder eigene ID") },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                            Button(
                                                onClick = {
                                                    secureSettings.saveGoogleClientId(googleClientId)
                                                    mailStatus = "Google Client-ID gespeichert."
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = cfg.onAccent),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Speichern", fontSize = 12.sp)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    var act: android.app.Activity? = null
                                                    var curr = context
                                                    while (curr is android.content.ContextWrapper) {
                                                        if (curr is android.app.Activity) {
                                                            act = curr
                                                            break
                                                        }
                                                        curr = curr.baseContext
                                                    }
                                                    secureSettings.saveDefaultMailProvider("gmail")
                                                    act?.startActivity(mailManager.buildGmailLoginIntent())
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1.2f)
                                            ) {
                                                Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Gmail Login", fontSize = 12.sp)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    mailManager.disconnectGmail()
                                                    mailStatus = "Gmail getrennt."
                                                },
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Trennen", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }

                                // Microsoft / Outlook Card
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = cfg.baseBackground),
                                    border = BorderStroke(1.dp, cfg.cardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("Microsoft / Outlook (OAuth)", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        OutlinedTextField(
                                            value = microsoftClientId,
                                            onValueChange = { microsoftClientId = it },
                                            label = { Text("Eigene Microsoft Client-ID (optional)") },
                                            placeholder = { Text("Standard nutzen oder eigene ID") },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                            Button(
                                                onClick = {
                                                    secureSettings.saveMicrosoftClientId(microsoftClientId)
                                                    mailStatus = "Microsoft Client-ID gespeichert."
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = cfg.onAccent),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Speichern", fontSize = 12.sp)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    var act: android.app.Activity? = null
                                                    var curr = context
                                                    while (curr is android.content.ContextWrapper) {
                                                        if (curr is android.app.Activity) {
                                                            act = curr
                                                            break
                                                        }
                                                        curr = curr.baseContext
                                                    }
                                                    secureSettings.saveDefaultMailProvider("outlook")
                                                    act?.startActivity(mailManager.buildOutlookLoginIntent())
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1.2f)
                                            ) {
                                                Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Outlook Login", fontSize = 12.sp)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    mailManager.disconnectOutlook()
                                                    mailStatus = "Outlook getrennt."
                                                },
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Trennen", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }

                                // Standard E-Mail Provider Selector
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = cfg.baseBackground),
                                    border = BorderStroke(1.dp, cfg.cardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Standard-Versandanbieter für E-Mails", color = accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                            listOf("ask" to "Immer fragen", "gmail" to "Gmail", "outlook" to "Outlook").forEach { (key, label) ->
                                                val isSel = defaultMailProvider == key
                                                Surface(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable {
                                                            defaultMailProvider = key
                                                            secureSettings.saveDefaultMailProvider(key)
                                                        },
                                                    color = if (isSel) accent.copy(alpha = 0.2f) else cfg.cardBackground,
                                                    border = BorderStroke(1.dp, if (isSel) accent else cfg.cardBorder)
                                                ) {
                                                    Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                                        Text(label, fontSize = 11.sp, color = if (isSel) accent else MaterialTheme.colorScheme.onSurface)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                if (mailStatus.isNotBlank()) {
                                    Text(mailStatus, color = accent, fontSize = 12.sp)
                                }
                            }

                            2 -> {
                                // ✈️ TELEGRAM TAB
                                var botToken by remember { mutableStateOf(secureSettings.getTelegramBotToken() ?: "") }
                                var chatId by remember { mutableStateOf(secureSettings.getTelegramChatId() ?: "") }
                                var autoForward by remember { mutableStateOf(secureSettings.isTelegramAutoForward()) }
                                var tgStatus by remember { mutableStateOf("") }
                                var tgBusy by remember { mutableStateOf(false) }
                                var tgWaitingForConnect by remember { mutableStateOf(false) }

                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = cfg.baseBackground),
                                    border = BorderStroke(1.dp, cfg.cardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Send, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(20.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Telegram-Verknüpfung", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        }
                                        Text(
                                            "1. Bot-Token von @BotFather eingeben und speichern.\n" +
                                            "2. 'Mit Telegram verbinden' tippen und im Bot-Chat auf 'Start' tippen.\n" +
                                            "3. Deine Chat-ID wird automatisch erkannt und hinterlegt.",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )

                                        OutlinedTextField(
                                            value = botToken,
                                            onValueChange = { botToken = it },
                                            label = { Text("Telegram Bot-Token") },
                                            visualTransformation = PasswordVisualTransformation(),
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )

                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                            Button(
                                                enabled = !tgBusy && botToken.isNotBlank(),
                                                onClick = {
                                                    secureSettings.saveTelegramBotToken(botToken)
                                                    tgStatus = "Bot-Token gespeichert."
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = cfg.onAccent),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Speichern", color = cfg.onAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }

                                            Button(
                                                enabled = !tgBusy && botToken.isNotBlank(),
                                                onClick = {
                                                    secureSettings.saveTelegramBotToken(botToken)
                                                    scope.launch {
                                                        tgBusy = true
                                                        tgWaitingForConnect = true
                                                        tgStatus = "Prüfe Bot..."
                                                        val botInfo = telegram.getBotUsernameDetailed()
                                                        if (!botInfo.ok || botInfo.detail.isBlank()) {
                                                            tgStatus = "Bot nicht erreichbar: ${botInfo.detail}"
                                                            tgBusy = false
                                                            tgWaitingForConnect = false
                                                            return@launch
                                                        }
                                                        val link = telegram.buildBotLink(botInfo.detail)
                                                        try {
                                                            context.startActivity(
                                                                Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                            )
                                                        } catch (e: Exception) {
                                                            tgStatus = "Telegram konnte nicht geöffnet werden."
                                                            tgBusy = false
                                                            tgWaitingForConnect = false
                                                            return@launch
                                                        }
                                                        tgStatus = "Warte auf deine 'Start'-Nachricht an @${botInfo.detail}..."
                                                        val result = telegram.waitForPrivateChatDetailed(timeoutMs = 90_000)
                                                        if (result.ok) {
                                                            chatId = result.detail
                                                            tgStatus = "✓ Verbunden! Chat-ID: ${result.detail}"
                                                            telegram.sendMessageDetailed("✅ STROMRUF ist jetzt mit deinem Telegram verbunden.")
                                                        } else {
                                                            tgStatus = result.detail
                                                        }
                                                        tgBusy = false
                                                        tgWaitingForConnect = false
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1.4f)
                                            ) {
                                                Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Verbinden", fontSize = 12.sp)
                                            }
                                        }

                                        if (tgWaitingForConnect) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = accent, strokeWidth = 2.dp)
                                                Text("Tippe in Telegram auf 'Start' – ich warte...", color = accent, fontSize = 11.sp)
                                            }
                                        }

                                        OutlinedTextField(
                                            value = chatId,
                                            onValueChange = {
                                                chatId = it
                                                if (it.isNotBlank()) secureSettings.saveTelegramChatId(it)
                                            },
                                            label = { Text("Chat-ID (automatisch oder manuell)") },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Notizen automatisch weiterleiten", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                                                Text("Jede Gesprächs- und Sprachnotiz im Hintergrund senden", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                            }
                                            Switch(
                                                checked = autoForward,
                                                onCheckedChange = {
                                                    autoForward = it
                                                    secureSettings.setTelegramAutoForward(it)
                                                }
                                            )
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                            OutlinedButton(
                                                enabled = !tgBusy && botToken.isNotBlank() && chatId.isNotBlank(),
                                                onClick = {
                                                    secureSettings.saveTelegramBotToken(botToken)
                                                    secureSettings.saveTelegramChatId(chatId)
                                                    scope.launch {
                                                        tgBusy = true
                                                        tgStatus = "Sende Testnachricht..."
                                                        val res = telegram.sendMessageDetailed("✅ Testnachricht aus STROMRUF.")
                                                        tgStatus = if (res.ok) "Testnachricht gesendet – schau in Telegram! ✉️" else "Fehler: ${res.detail}"
                                                        tgBusy = false
                                                    }
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Testen ✉️", fontSize = 12.sp)
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    secureSettings.clearTelegram()
                                                    botToken = ""
                                                    chatId = ""
                                                    tgStatus = "Telegram-Verknüpfung gelöscht."
                                                },
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Trennen", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                            }
                                        }

                                        if (tgStatus.isNotBlank()) {
                                            Text(tgStatus, color = accent, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }

                            3 -> {
                                // 📱 TELEFONIE & AUDIO TAB
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = cfg.baseBackground),
                                    border = BorderStroke(1.dp, cfg.cardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("Standard-Audioausgabe bei Anrufen", color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            val devices = listOf("earpiece" to "Hörer", "speaker" to "Lautsprecher", "bluetooth" to "Bluetooth")
                                            devices.forEach { (key, label) ->
                                                val isSelected = preferredAudioDevice == key
                                                Surface(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable {
                                                            onPreferredAudioDeviceChange(key)
                                                            prefs.edit().putString("preferred_audio_device", key).apply()
                                                        },
                                                    color = if (isSelected) accent.copy(alpha = 0.2f) else cfg.cardBackground,
                                                    border = BorderStroke(1.dp, if (isSelected) accent else cfg.cardBorder)
                                                ) {
                                                    Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                                        Text(label, fontSize = 11.sp, color = if (isSelected) accent else MaterialTheme.colorScheme.onSurface)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = cfg.baseBackground),
                                    border = BorderStroke(1.dp, cfg.cardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("Auto-Call Verzögerung (Sekunden)", color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf(3, 5, 10, 15).forEach { sec ->
                                                val isSelected = autoCallDelaySeconds == sec
                                                Surface(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable {
                                                            onAutoCallDelaySecondsChange(sec)
                                                            prefs.edit().putInt("hotbox_delay_seconds", sec).apply()
                                                        },
                                                    color = if (isSelected) accent.copy(alpha = 0.2f) else cfg.cardBackground,
                                                    border = BorderStroke(1.dp, if (isSelected) accent else cfg.cardBorder)
                                                ) {
                                                    Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                                        Text("${sec}s", fontSize = 12.sp, color = if (isSelected) accent else MaterialTheme.colorScheme.onSurface)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = cfg.baseBackground),
                                    border = BorderStroke(1.dp, cfg.cardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("Klingelton & Anruf-Alarm", color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Alarm & Ton aktivieren", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                                            Switch(checked = alarmEnabled, onCheckedChange = onAlarmToggle)
                                        }

                                        OutlinedButton(
                                            onClick = onSelectRingtoneClick,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.Notifications, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                "Ton: ${selectedRingtoneTitle.ifBlank { "Standard-Klingelton" }}",
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = cfg.baseBackground),
                                    border = BorderStroke(1.dp, cfg.cardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("System-Integration", color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)

                                        if (!isDefaultDialer) {
                                            Button(
                                                onClick = onRequestDefaultDialer,
                                                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = cfg.onAccent),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("Als Standard-Telefon-App festlegen", color = cfg.onAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Simulationsmodus", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                                Text("Test-Anrufe ohne echtes Mobilfunknetz", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                            }
                                            Switch(checked = isSimulationModeEnabled, onCheckedChange = onSimulationModeToggle)
                                        }
                                    }
                                }
                            }

                            4 -> {
                                // 🎨 DESIGN & SYSTEM TAB
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = cfg.baseBackground),
                                    border = BorderStroke(1.dp, cfg.cardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("Erscheinungsbild & Theme", color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            val themes = listOf("dark" to "Dunkel 🌙", "light" to "Hell ☀️", "system" to "System ⚙️")
                                            themes.forEach { (key, label) ->
                                                val isSelected = appTheme == key
                                                Surface(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickable { onThemeChange(key) },
                                                    color = if (isSelected) accent.copy(alpha = 0.2f) else cfg.cardBackground,
                                                    border = BorderStroke(1.dp, if (isSelected) accent else cfg.cardBorder)
                                                ) {
                                                    Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                                        Text(label, fontSize = 11.sp, color = if (isSelected) accent else MaterialTheme.colorScheme.onSurface)
                                                    }
                                                }
                                            }
                                        }

                                        Text("Farbschema & Stil", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        val styles = listOf(
                                            "platinum_metal" to "Platin 🪙",
                                            "gold_luxury" to "Gold 👑",
                                            "cyber_voltage" to "Cyber ⚡",
                                            "rose_metal" to "Rosé 🌸",
                                            "industrial_steel" to "Stahl ⚓"
                                        )
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                styles.take(3).forEach { (key, label) ->
                                                    val isSelected = bgStyle.lowercase() == key || (key == "platinum_metal" && bgStyle.isBlank())
                                                    Surface(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .clickable { onBgStyleChange(key) },
                                                        color = if (isSelected) accent.copy(alpha = 0.25f) else cfg.cardBackground,
                                                        border = BorderStroke(1.dp, if (isSelected) accent else cfg.cardBorder)
                                                    ) {
                                                        Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                                            Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) accent else MaterialTheme.colorScheme.onSurface)
                                                        }
                                                    }
                                                }
                                            }
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                styles.drop(3).forEach { (key, label) ->
                                                    val isSelected = bgStyle.lowercase() == key
                                                    Surface(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .clickable { onBgStyleChange(key) },
                                                        color = if (isSelected) accent.copy(alpha = 0.25f) else cfg.cardBackground,
                                                        border = BorderStroke(1.dp, if (isSelected) accent else cfg.cardBorder)
                                                    ) {
                                                        Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                                            Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) accent else MaterialTheme.colorScheme.onSurface)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = cfg.baseBackground),
                                    border = BorderStroke(1.dp, cfg.cardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("Display-Helligkeit", color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Slider(
                                            value = if (screenBrightness < 0f) 0.8f else screenBrightness,
                                            onValueChange = { onBrightnessChange(it) },
                                            valueRange = 0.1f..1f,
                                            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
                                        )
                                        TextButton(
                                            onClick = { onBrightnessChange(-1f) },
                                            modifier = Modifier.align(Alignment.End)
                                        ) {
                                            Text("System-Standard verwenden", color = accent, fontSize = 11.sp)
                                        }
                                    }
                                }

                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = cfg.baseBackground),
                                    border = BorderStroke(1.dp, cfg.cardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("Clipboard-Bubble (Zwischenablage)", color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        
                                        Text("Bubble-Position:", color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp)
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            listOf("top_left" to "Oben L", "top_right" to "Oben R", "bottom_left" to "Unten L", "bottom_right" to "Unten R").forEach { (pos, lbl) ->
                                                val isSel = clipboardBubblePosition == pos
                                                Surface(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .clickable { onClipboardBubblePositionChange(pos) },
                                                    color = if (isSel) accent.copy(alpha = 0.2f) else cfg.cardBackground,
                                                    border = BorderStroke(1.dp, if (isSel) accent else cfg.cardBorder)
                                                ) {
                                                    Box(modifier = Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                                        Text(lbl, fontSize = 10.sp, color = if (isSel) accent else MaterialTheme.colorScheme.onSurface)
                                                    }
                                                }
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Bei lokalem Kopieren einblenden", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                                            Switch(checked = clipboardBubbleOnLocalCopy, onCheckedChange = onClipboardBubbleOnLocalCopyChange)
                                        }
                                    }
                                }
                            }

                            5 -> {
                                // 🔌 MCP SERVER TAB
                                var edgeFunctionName by remember { mutableStateOf("stromruf-mcp") }
                                val baseUrl = "https://yepluyipizbbrgoffqdq.supabase.co"
                                val functionsUrl = "$baseUrl/functions/v1"
                                val authUrl = "$functionsUrl/$edgeFunctionName/auth"
                                val tokenUrl = "$functionsUrl/$edgeFunctionName/token"
                                val regUrl = "$functionsUrl/$edgeFunctionName/register"
                                val resource = "urn:stromruf:mcp"

                                fun copyUrl(lbl: String, text: String) {
                                    val clipManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText(lbl, text)
                                    clipManager.setPrimaryClip(clip)
                                    Toast.makeText(context, "$lbl kopiert 📋", Toast.LENGTH_SHORT).show()
                                }

                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = cfg.baseBackground),
                                    border = BorderStroke(1.dp, cfg.cardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("ChatGPT MCP Server (OAuth)", color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Text("Kopiere diese URLs in die ChatGPT Plugin/MCP Einstellungen, um deinen KI-Assistenten anzubinden.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)

                                        OutlinedTextField(
                                            value = edgeFunctionName,
                                            onValueChange = { edgeFunctionName = it },
                                            label = { Text("Edge Function Name") },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )

                                        McpUrlRow("Auth-URL", authUrl) { copyUrl("Auth-URL", authUrl) }
                                        McpUrlRow("Token-URL", tokenUrl) { copyUrl("Token-URL", tokenUrl) }
                                        McpUrlRow("Registrierungs-URL", regUrl) { copyUrl("Registrierungs-URL", regUrl) }
                                        McpUrlRow("Basis-URL", baseUrl) { copyUrl("Basis-URL", baseUrl) }
                                        McpUrlRow("Ressource", resource) { copyUrl("Ressource", resource) }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Done Button
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = cfg.onAccent),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Schließen", color = cfg.onAccent, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ProximityScreenShield(isCallActive: Boolean) { }

fun sendAnnahmeNotification(
    context: android.content.Context,
    type: String = "",
    customerType: String = "",
    consumption: Long = 0L,
    customerNumber: String = "",
    name: String = "",
    phone: String = "",
    reason: String = ""
) { }
