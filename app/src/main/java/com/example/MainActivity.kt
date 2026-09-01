package com.example

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
        // Hotbox-Namen zus√§tzlich direkt aus den Kontakten rekonstruieren.
        // Dadurch erscheinen auch Hotboxen, die √ºber MCP / ChatGPT /
        // Backend erstellt wurden, selbst wenn hot_box_lists im Android
        // Client wegen Cache/RLS/Sync noch nicht verf√ºgbar ist.
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
                val name = intent.getStringExtra("CONTACT_NAME") ?: "R√ºckruf"
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

        // ---- TELEFONIE: Kern-Integration ‚Äî echten Anruf √ºber das Android-System starten ----
        // Ohne diesen Block zeigt die App nur die Anruf-Maske, w√§hlt aber NICHT.
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
                            // Als Standard-Dialer: Anruf direkt √ºber TelecomManager platzieren (verhindert Auswahldialog)
                            telecomManager.placeCall(uri, null)
                        } else {
                            // Wenn nicht Standard-Dialer: ACTION_CALL verwenden (System √ºbernimmt)
                            val callIntent = android.content.Intent(android.content.Intent.ACTION_CALL, uri).apply {
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(callIntent)
                        }
                    } else {
                        // Ohne Berechtigung: ACTION_DIAL verwenden (√∂ffnet W√§hlpad)
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
                        "Kundennummer $digitsOnly kopiert! üìã",
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
                                            contentDescription = "Schlie√üen",
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
                                            contentDescription = "Schlie√üen",
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
                                                    text = "Kunde speichern üíæ",
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
                                                        Toast.makeText(this@MainActivity, "Kunde $localName ($localCustomerNo) erfolgreich gespeichert! üíæ", Toast.LENGTH_LONG).show()
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

    private fun checkAndAutoRecordCall() {
        val activeCall = viewModel.activeCall.value ?: return
        
        // If simulation mode is active, the call is simulated entirely in the app UI, so do not auto-record upon app resume.
        if (viewModel.isSimulationModeEnabled.value) {
            return
        }

        // If DialerInCallService has an active call, the call is still ongoing! Do not record it yet.
        if (com.example.service.DialerInCallService.activeCall.value != null) {
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
        Toast.makeText(this, "Anruf automatisch erfasst! üéØ ($durationLabel)", Toast.LENGTH_LONG).show()
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
                            // Check if this single copy contains BOTH a 5-12 digit customer number and name!
                            val matchCustNo = Regex("\\b\\d{5,12}\\b").find(cleaned)
                            if (matchCustNo != null && cleaned.any { it.isLetter() }) {
                                val extractedCustNo = matchCustNo.value
                                val extractedName = cleaned.replace(extractedCustNo, "")
                                    .replace(Regex("[(),\\-_|]"), " ")
                                    .replace(Regex("\\s+"), " ")
                                    .trim()
                                if (extractedName.isNotBlank() && extractedName.length >= 2) {
                                    viewModel.updateQuickSaveDialog(customerNo = extractedCustNo, name = extractedName)
                                }
                            } else {
                                // If it is all digits (and not "+..."), treat it as customer number
                                val isDigits = cleaned.all { it.isDigit() } && cleaned.length in 5..12
                                if (isDigits) {
                                    viewModel.updateQuickSaveDialog(customerNo = cleaned)
                                } else if (cleaned.any { it.isLetter() }) {
                                    viewModel.updateQuickSaveDialog(name = cleaned)
                                }
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
                                    Toast.makeText(this, "Kunde $savedName ($savedCustNo) erfolgreich gespeichert! üíæ", Toast.LENGTH_LONG).show()
                                }
                            }
                            return // Done processing for QuickSave dialog!
                        }

                        // Normal clipboard bubble trigger
                        val isCustomerNo = cleaned.all { it.isDigit() } && (cleaned.length == 6 || (cleaned.length in 5..10 && !cleaned.startsWith("0")))
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
            val name = intent.getStringExtra("CONTACT_NAME") ?: "R√ºckruf"
            val phone = intent.getStringExtra("CONTACT_PHONE") ?: ""
            if (!phone.isNullOrBlank()) {
                intent.removeExtra("SHOW_INCOMING_ALERT")
                viewModel.activeIncomingAlert.value = com.example.viewmodel.IncomingAlert(id, name, phone)
            }
        } else {
            val phone = intent.getStringExtra("CALL_IMMEDIATELY")
            val name = intent.getStringExtra("CALL_IMMEDIATELY_NAME") ?: "R√ºckruf"
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
        "erreicht_interesse" -> OutcomeMeta("Erreicht ‚Äì Interesse", Color(0xFF10B981))
        "erreicht_abschluss" -> OutcomeMeta("Erreicht ‚Äì Abschluss", Color(0xFF0D9488))
        "erreicht_kein_interesse" -> OutcomeMeta("Erreicht ‚Äì kein Interesse", Color(0xFFEF4444))
        "nicht_erreicht" -> OutcomeMeta("Nicht erreicht", Color(0xFFF59E0B))
        "falsche_nummer" -> OutcomeMeta("Falsche Nummer", Color(0xFF64748B))
        else -> OutcomeMeta("Unbekannt / ‚Äì", Color(0xFF94A3B8))
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
                    com.example.service.DialerInCallService.hangUp()
                    viewModel.startWrapUpForDirectCall(contactPhone, contactName, finalDuration)
                },
                isAutoCallActive = isAutoCallActive,
                onHangUpAndPause = { finalDuration ->
                    viewModel.pauseAutoCall()
                    com.example.service.DialerInCallService.hangUp()
                    viewModel.startWrapUpForDirectCall(contactPhone, contactName, finalDuration)
                },
                wrapUpData = wrapUpData,
                onNoteChange = { viewModel.setWrapUpNote(it) },
                onCallReasonChange = { viewModel.setWrapUpCallReason(it) },
                onToggleOffset = { viewModel.toggleWrapUpOffset(it) },
                onOutcomeChange = { viewModel.setWrapUpOutcome(it) },
                contact = matchingContact,
                recentCallLogs = callLogs,
                onForceClose = {
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
                }
            )
        }

        // Full Screen Conversation Wrap-up Dialog & Follow-up Scheduler
        if (showWrapUpDialog) {
            WrapUpDialog(
                viewModel = viewModel,
                data = wrapUpData,
                onValueChange = { viewModel.updateWrapUpFields(it.name, it.company, it.email) },
                onOutcomeChange = { viewModel.setWrapUpOutcome(it) },
                onSaveContactChange = { viewModel.setWrapUpSaveContact(it) },
                onNoteChange = { viewModel.setWrapUpNote(it) },
                onCallReasonChange = { viewModel.setWrapUpCallReason(it) },
                onToggleOffset = { viewModel.toggleWrapUpOffset(it) },
                onAddCustomDate = { viewModel.addCustomFollowUpDate(it) },
                onRemoveCustomDate = { viewModel.removeCustomFollowUpDate(it) },
                onCancel = { viewModel.cancelWrapUp() },
                onSave = {
                    if (wrapUpData.saveContact && wrapUpData.name.isNotBlank()) {
                        if (com.example.util.ContactsUtil.hasWriteContactsPermission(context)) {
                            val success = com.example.util.ContactsUtil.saveContactToSystemDirectly(context, wrapUpData.name, wrapUpData.phone)
                            if (success) {
                                android.widget.Toast.makeText(context, "Kontakt im Telefonbuch gespeichert! üíæ", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                com.example.util.ContactsUtil.saveContactViaIntent(context, wrapUpData.name, wrapUpData.phone)
                            }
                        } else {
                            com.example.util.ContactsUtil.saveContactViaIntent(context, wrapUpData.name, wrapUpData.phone)
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
                                text = "Wiedervorlage f√§llig! üéØ",
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
                                            Toast.makeText(context, "Um 10 Minuten verschoben ‚è∞", Toast.LENGTH_SHORT).show()
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
                label = "F√ÑLLIG JETZT",
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
            icon = { Icon(Icons.Default.Phone, contentDescription = "Direktwahl & F√§llig") },
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
            icon = { Icon(Icons.Default.List, contentDescription = "Aktivit√§t & Historie") },
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
                    Toast.makeText(context, "Kontakt \"$quickName\" erfolgreich direkt im Telefonbuch gespeichert! üíæ", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Fehler beim direkten Speichern. √ñffne Kontaktemanager...", Toast.LENGTH_SHORT).show()
                    com.example.util.ContactsUtil.saveContactViaIntent(context, quickName, quickPhone)
                }
            }
        } else {
            Toast.makeText(context, "Berechtigung verweigert. √ñffne vorausgef√ºllte System-Kontakte...", Toast.LENGTH_SHORT).show()
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
                                        text = "Neukunde anlegen üë§",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                text = {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            text = "Gib die Kundennummer f√ºr den Neukunden ein:",
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
                                                Toast.makeText(context, "Bitte f√ºllen Sie beide Felder aus!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                viewModel.saveNeukunde(
                                                    customerNumber = dialogCustomerNumber.trim(),
                                                    phone = dialogPhone.trim()
                                                )
                                                showAddNeukundeDialog = false
                                                Toast.makeText(context, "Neukunde erfolgreich angelegt! üöÄ", Toast.LENGTH_SHORT).show()
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
                                    Toast.makeText(context, "Bitte einen Namen eingeben, um zu speichern ‚úçÔ∏è", Toast.LENGTH_LONG).show()
                                } else {
                                    if (com.example.util.ContactsUtil.hasWriteContactsPermission(context)) {
                                        val success = com.example.util.ContactsUtil.saveContactToSystemDirectly(context, quickName, quickPhone)
                                        if (success) {
                                            Toast.makeText(context, "Kontakt \"$quickName\" erfolgreich direkt im Telefonbuch gespeichert! üíæ", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "Fehler beim direkten Speichern. √ñffne Kontaktemanager...", Toast.LENGTH_SHORT).show()
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
                                        Toast.makeText(context, "Bitte einen Namen eingeben, um zu speichern ‚úçÔ∏è", Toast.LENGTH_LONG).show()
                                    } else {
                                        viewModel.addManualContact(
                                            name = quickName,
                                            phone = quickPhone,
                                            company = "",
                                            email = ""
                                        )
                                        Toast.makeText(context, "Kontakt \"$quickName\" in Stromruf gespeichert! ‚ö°", Toast.LENGTH_LONG).show()
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
                                contentDescription = "L√∂schen",
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
                                    "K√ºrzliche Anrufe",
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
                                    "Berechtigung f√ºr Kontakte wird ben√∂tigt, um Ihr Adressbuch zu durchsuchen.",
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
                                                    Toast.makeText(context, "${contact.name} f√ºr Direktwahl √ºbernommen! üéØ", Toast.LENGTH_SHORT).show()
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
                                                contentDescription = "√úbernehmen",
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
                                    "Berechtigung f√ºr die Anrufliste wird ben√∂tigt, um k√ºrzliche Telefonanrufe einzusehen.",
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
                                    "Keine k√ºrzlichen Anrufe in der Liste.",
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
                                                    Toast.makeText(context, "${call.name ?: call.number} f√ºr Direktwahl √ºbernommen! üéØ", Toast.LENGTH_SHORT).show()
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
                                                contentDescription = "√úbernehmen",
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

        // F√§llig heute Section
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
                    text = "Heute f√§llige R√ºckrufe (${todayPending.size})",
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
                        text = "Keine f√§lligen R√ºckrufe. Saubere Liste.",
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
                        text = "Kontakte & Leads importieren üì•",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00FF87)
                        )
                    )
                }

                Text(
                    text = "Importieren Sie neue Kontakte aus Ihren Telefonkontakten oder f√ºgen Sie eine Bulk-Liste mit Telefonnummern hinzu.",
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
                            "$created neue Leads erstellt ($skipped bereits vorhandene √ºbersprungen). üìÇ"
                        } else {
                            "$created neue Leads erstellt! üöÄ"
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
            "√úberf√§llig" to overdueList,
            "Heute f√§llig" to todayList,
            "Diese Woche" to weekList,
            "Sp√§ter" to laterList
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
                Icon(Icons.Default.Add, contentDescription = "Wiedervorlage hinzuf√ºgen")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Hinzuf√ºgen", fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
                    contentDescription = "Weckton ausw√§hlen",
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
                        text = "Keine f√§lligen Wiedervorlagen mehr offen.",
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
                                "√úberf√§llig" -> Color(0xFFEF4444)
                                "Heute f√§llig" -> Color(0xFF00FF87)
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
                                overdue = header == "√úberf√§llig",
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
                    text = "Excel / Nummernliste importieren üìÇ",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                
                Text(
                    text = "F√ºgen Sie eine Liste von Rufnummern ein oder laden Sie eine Datei (CSV/TXT). Wir suchen automatisch nach allen Nummern und aktivieren sie in der Hot Box.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                OutlinedTextField(
                    value = pastedText,
                    onValueChange = { pastedText = it },
                    placeholder = { Text("Nummern hier einf√ºgen (z.B. 01721234567, +491519876543, ...)") },
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
                    Text("Aus Datei importieren (CSV/TXT) üìÑ")
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
                    Icon(Icons.Default.ArrowBack, contentDescription = "Zur√ºck", tint = Color.White)
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
                        text = "Neuen Kunden eintragen üìù",
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
                                text = "Autopilot: Durchrufen ü§ñ",
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
                                Icon(Icons.Default.Refresh, contentDescription = "Zur√ºcksetzen", tint = Color(0xFF00FF87))
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
                                        if (secs != null) "STOP (${secs}s) üõë" else "STOPPEN üõë"
                                    } else "STARTEN üöÄ",
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
                                            contentDescription = "L√∂schen",
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
                                text = "Vollbildmodus aktiv üî• (Zur√ºck-Taste f√ºr Normal)",
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
                                            val numberRegex = Regex("\\d+")
                                            val targetContact = contacts.find { it.id == nextHotBoxContactId } ?: contacts.firstOrNull { it.isHotBox }
                                            if (targetContact != null) {
                                                val digitsOnly = numberRegex.find(targetContact.name)?.value 
                                                    ?: numberRegex.find(targetContact.company ?: "")?.value 
                                                    ?: ""
                                                if (digitsOnly.isNotEmpty()) {
                                                    try {
                                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                        val clip = android.content.ClipData.newPlainText("Kundennummer", digitsOnly)
                                                        clipboard.setPrimaryClip(clip)
                                                        Toast.makeText(context, "Kundennummer $digitsOnly kopiert! üìã", Toast.LENGTH_SHORT).show()
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Fehler beim Kopieren: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                    }
                                                } else {
                                                    Toast.makeText(context, "Keine Kundennummer im Namen gefunden! ‚ö†Ô∏è", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                Toast.makeText(context, "Kein Hotbox-Kontakt vorhanden! ‚ö†Ô∏è", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isAutoCallActive) "STOP üõë" else "START ü§ñ",
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
                                    text = "Ausklappen ‚ÜóÔ∏è",
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
                                text = { Text("Importieren üìÇ") },
                                onClick = {
                                    showMenu = false
                                    showHotBoxImportDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Zyklus resetten üîÑ") },
                                onClick = {
                                    showMenu = false
                                    viewModel.resetCurrentHotBoxCycle()
                                    Toast.makeText(context, "Kampagnen-Zyklus manuell zur√ºckgesetzt! üîÑ", Toast.LENGTH_SHORT).show()
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
                    Icon(Icons.Default.Add, contentDescription = "Plusschaltfl√§che", modifier = Modifier.size(18.dp))
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
                            text = "Alle Kontakte üë•",
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
                            text = "$listName üî•",
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
                        text = "Alle Leads ($totalCount) üë•",
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
                            text = "Nur Offene ($uncalledCount) üéØ",
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
                                text = "Hot Box: $selectedListsStr üî•",
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
                                        contentDescription = "Liste l√∂schen",
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
                                            "AUTOPILOT AKTIV üöÄ (${secs}s)"
                                        } else {
                                            "AUTOPILOT AKTIV üöÄ"
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
                                            val numberRegex = Regex("\\d+")
                                            val targetContact = contacts.find { it.id == nextHotBoxContactId } ?: contacts.firstOrNull { it.isHotBox }
                                            if (targetContact != null) {
                                                val digitsOnly = numberRegex.find(targetContact.name)?.value 
                                                    ?: numberRegex.find(targetContact.company ?: "")?.value 
                                                    ?: ""
                                                if (digitsOnly.isNotEmpty()) {
                                                    try {
                                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                        val clip = android.content.ClipData.newPlainText("Kundennummer", digitsOnly)
                                                        clipboard.setPrimaryClip(clip)
                                                        Toast.makeText(context, "Kundennummer $digitsOnly kopiert! üìã", Toast.LENGTH_SHORT).show()
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Fehler beim Kopieren: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                    }
                                                } else {
                                                    Toast.makeText(context, "Keine Kundennummer im Namen gefunden! ‚ö†Ô∏è", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                Toast.makeText(context, "Kein Hotbox-Kontakt vorhanden! ‚ö†Ô∏è", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                    .padding(horizontal = 10.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isAutoCallActive) "STOP üõë" else "START ü§ñ",
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
                                    text = "N√ÑCHSTER HOTBOX-ANRUF üî•: ${nextHotBoxContact.name}",
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
                                            text = "N√ÑCHSTER HOTBOX-ANRUF üî•",
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
                                                    val numberRegex = Regex("\\d+")
                                                    val targetContact = contacts.find { it.id == nextHotBoxContactId } ?: contacts.firstOrNull { it.isHotBox }
                                                    if (targetContact != null) {
                                                        val digitsOnly = numberRegex.find(targetContact.name)?.value 
                                                            ?: numberRegex.find(targetContact.company ?: "")?.value 
                                                            ?: ""
                                                        if (digitsOnly.isNotEmpty()) {
                                                            try {
                                                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                                val clip = android.content.ClipData.newPlainText("Kundennummer", digitsOnly)
                                                                clipboard.setPrimaryClip(clip)
                                                                Toast.makeText(context, "Kundennummer $digitsOnly kopiert! üìã", Toast.LENGTH_SHORT).show()
                                                            } catch (e: Exception) {
                                                                Toast.makeText(context, "Fehler beim Kopieren: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                            }
                                                        } else {
                                                            Toast.makeText(context, "Keine Kundennummer im Namen gefunden! ‚ö†Ô∏è", Toast.LENGTH_SHORT).show()
                                                        }
                                                    } else {
                                                        Toast.makeText(context, "Kein Hotbox-Kontakt vorhanden! ‚ö†Ô∏è", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            )
                                            .padding(horizontal = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (isAutoCallActive) "STOP üõë" else "START ü§ñ",
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
                                        Text("L√∂schen?", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
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
                                                contentDescription = "Kunde l√∂schen",
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
                            "$created neue Leads erstellt ($skipped bereits vorhandene √ºbersprungen). üìÇ"
                        } else {
                            "$created neue Leads erstellt! üöÄ"
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
                        "Keine Hei√ü-Kontakte in der Hot Box üî• vorhanden."
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
                                    text = "Diese Kontakte wurden aus der Hotbox entfernt, bleiben aber f√ºr diese Sitzung hier sichtbar.",
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
                        Text("Gib einen Namen f√ºr deine neue Kampagnenliste ein:")
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
                title = { Text("Liste l√∂schen?") },
                text = {
                    Text("M√∂chtest du die Liste '$listToDelete' wirklich l√∂schen? Alle zugeordneten Kontakte werden aus der Hotbox entfernt.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.removeHotBoxList(listToDelete)
                            showDeleteListConfirmation = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        Text("L√∂schen", color = Color.White)
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
                        text = "Anruf l√§uft: ${call.name ?: "Unbekannt"}",
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
                    Icon(Icons.Default.Delete, contentDescription = "Wiedervorlage l√∂schen", tint = Color(0xFFEF4444))
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
        contact.lastCallAt?.let { "Letzter Anruf: ${fmtDateTime(it)} ¬∑ ${meta.label}" }
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
                        val numberRegex = Regex("\\d+")
                        val digitsOnly = numberRegex.find(contact.name)?.value 
                            ?: numberRegex.find(contact.company ?: "")?.value 
                            ?: ""
                        if (digitsOnly.isNotEmpty()) {
                            try {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Kundennummer", digitsOnly)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Kundennummer $digitsOnly kopiert! üìã", Toast.LENGTH_SHORT).show()
                            } catch (e: java.lang.Exception) {
                                Toast.makeText(context, "Fehler beim Kopieren: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "Keine Kundennummer im Namen gefunden! ‚ö†Ô∏è", Toast.LENGTH_SHORT).show()
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
                                        text = "ANGERUFEN ‚úì",
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
                                        text = "OFFEN ‚≠ï",
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
                                    text = "N√ÑCHSTER üéØ",
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
                                    text = "ZULETZT üîÑ",
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
                                text = "¬∑ ${contact.company}",
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
                        Text("L√∂schen?", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
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
                                    text = { Text("In Hotbox-Liste aufnehmen üî•", fontWeight = FontWeight.Bold, color = Color(0xFFEF4444), fontSize = 11.sp) },
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
                            Icon(Icons.Default.Delete, contentDescription = "Kunde l√∂schen", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
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
                        Icon(Icons.Default.Close, contentDescription = "Schlie√üen", tint = Color.White)
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
                                    text = "Erreicht ‚úîÔ∏è",
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
                                    text = "Nicht erreicht ‚ùå",
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
                                text = "Genaueres Ergebnis ausw√§hlen:",
                                style = TextStyle(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            val subOutcomes = if (selectedCategory == "erreicht") {
                                listOf(
                                    "erreicht_interesse" to "Interesse üëç",
                                    "erreicht_abschluss" to "Abschluss üèÜ",
                                    "erreicht_kein_interesse" to "Kein Interesse üëé"
                                )
                            } else {
                                listOf(
                                    "nicht_erreicht" to "Nicht erreicht ‚è≥",
                                    "falsche_nummer" to "Falsche Nummer üö´"
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
                                    text = "${data.existingContact.name} ist bereits im Telefonbuch gespeichert.",
                                    fontSize = 13.sp,
                                    color = Color(0xFF065F46),
                                    fontWeight = FontWeight.Medium
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
                                Text("In Hot Box üî• behalten / hinzuf√ºgen (hohe Abschluss-Chance)", fontSize = 13.sp, fontWeight = FontWeight.Medium)
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
                                        text = "Aktivit√§ts-Zeitraum einschr√§nken (nur in dieser Zeit w√§hlen):",
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

                        // Gespr√§chsdauer (Minutes/Seconds)
                        Text(
                            text = "Gespr√§chsdauer (Minuten / Sekunden)",
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
                                    text = "Gespr√§chsnotiz:",
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
                                        text = if (isDictating) "Stop Diktat" else "Diktieren üé§",
                                        color = if (isDictating) Color(0xFFEF4444) else Color(0xFF0F766E),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = data.note,
                                onValueChange = { onNoteChange(it) },
                                placeholder = { Text("Notiz zum Gespr√§ch hinzuf√ºgen...") },
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

                        Text("Zeitperiode w√§hlen (Mehrfachauswahl erlaubt):", fontSize = 12.sp, color = Color(0xFF64748B))
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
                                Text("Termin hinzuf√ºgen", fontSize = 11.sp)
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
                                Toast.makeText(context, "Bitte ein Anrufergebnis ausw√§hlen.", Toast.LENGTH_SHORT).show()
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
                Text("Erreichbarkeit einstellen ‚è∞", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Legen Sie fest, wann ${contact.name} erreichbar ist. Au√üerhalb dieser Zeiten wird der Kunde in der Hot Box automatisch ausgeblendet.",
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
                    text = "Aktivit√§ts-Zeitraum (Uhrzeit):",
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
                            text = "In die Hotbox aufnehmen üî•",
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
                                        text = "$selectedListName üî•",
                                        color = Color.Black,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Ausw√§hlen",
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
                                            text = { Text("$name üî•", fontSize = 13.sp) },
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
                            text = "Aktivit√§ts-Zeitraum einschr√§nken (nur in dieser Zeit w√§hlen):",
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
                        Toast.makeText(context, "Bitte Name und Telefonnummer ausf√ºllen.", Toast.LENGTH_SHORT).show()
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
                            text = "Berechtigung f√ºr Kontakte wird ben√∂tigt, um Ihr System-Adressbuch zu durchsuchen.",
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
            Toast.makeText(context, "Datei erfolgreich unter Downloads gespeichert! üì•\n($fileName)", Toast.LENGTH_LONG).show()
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
    var showHistorieAndAbschl√ºsse by remember { mutableStateOf(false) }
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
            "Fr√ºhschicht (06‚Äì09 Uhr)" to (morgen to (morgen / total)),
            "Vormittag (09‚Äì12 Uhr)" to (vormittag to (vormittag / total)),
            "Mittagspause (12‚Äì14 Uhr)" to (mittag to (mittag / total)),
            "Nachmittag (14‚Äì17 Uhr)" to (nachmittag to (nachmittag / total)),
            "Feierabend (17‚Äì21 Uhr)" to (feierabend to (feierabend / total)),
            "Nachts (21‚Äì06 Uhr)" to (nacht to (nacht / total))
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
                text = "Historie & Abschl√ºsse",
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
                    text = if (showAnnahmenForm) "Schlie√üen" else "Eintragen",
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
                        text = { Text("Abschlussstatistik üìä") },
                        onClick = {
                            showThreeDotsMenu = false
                            showHistorieAndAbschl√ºsse = true
                            showFullscreenStatsDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Annahme üìÇ") },
                        onClick = {
                            showThreeDotsMenu = false
                            showAnnahmeDokumenteDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Neukunden üë§") },
                        onClick = {
                            showThreeDotsMenu = false
                            showNeukundenDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Hei√üe Angebote üî•") },
                        onClick = {
                            showThreeDotsMenu = false
                            showHeisseAngeboteDialog = true
                        }
                    )
                }
            }
        }

        // Animated Input Form for "Annahmen/Abschl√ºsse"
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
                                Toast.makeText(context, "Bitte geben Sie einen g√ºltigen Verbrauch ein.", Toast.LENGTH_SHORT).show()
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
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Schlie√üen", tint = Color.White)
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
                                    Text("Abschl√ºsse", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
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
                                Text("Detaillierte √úbersicht", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                
                                val numberFormat = java.text.NumberFormat.getIntegerInstance(Locale.GERMANY)
                                
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("‚ö° Strom verkauft:", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
                                    Text("${numberFormat.format(stromWeighted)} kWh", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Divider(color = Color.White.copy(alpha = 0.08f))
                                
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("üî• Gas verkauft:", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
                                    Text("${numberFormat.format(gasWeighted)} kWh", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Divider(color = Color.White.copy(alpha = 0.08f))
                                
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("üë• Neukunden / Bestandskunden:", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
                                    Text("$neukundenCount / $bestandskundenCount", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "Eingetragene Abschl√ºsse:",
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
                                                contentDescription = "L√∂schen",
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
                                    text = "Noch keine Abschl√ºsse eingetragen.",
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
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Schlie√üen", tint = Color.White)
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
                                                                text = "Datei herunterladen üì•",
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
                                        text = if (docSearchQuery.isNotEmpty()) "Keine Dokumente f√ºr diese Kundennummer gefunden." else "Keine Annahme-Dokumente vorhanden.",
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
                                    text = "Neukunden-Verwaltung üë§",
                                    style = TextStyle(
                                        fontFamily = FontFamily.SansSerif,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp,
                                        color = Color.White
                                    )
                                )
                            }
                            IconButton(onClick = { showNeukundenDialog = false }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Schlie√üen", tint = Color.White)
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
                                        "Neuen Kunden anlegen ‚ûï",
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
                                                Toast.makeText(context, "Bitte f√ºllen Sie beide Felder aus!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                viewModel.saveNeukunde(
                                                    customerNumber = neukundeCustomerNumber.trim(),
                                                    phone = neukundePhone.trim()
                                                )
                                                neukundeCustomerNumber = ""
                                                neukundePhone = ""
                                                Toast.makeText(context, "Neukunde erfolgreich angelegt! üöÄ", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF87)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Kunden anlegen üöÄ", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                                                            Toast.makeText(context, "Kunde gel√∂scht.", Toast.LENGTH_SHORT).show()
                                                        }
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Delete,
                                                            contentDescription = "L√∂schen",
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
                                                    "Anrufen" -> "1. Anrufen üìû"
                                                    "Datenmail schreiben" -> "2. Datenmail schreiben ‚úâÔ∏è"
                                                    "Angebot erstellen" -> "3. Angebot erstellen üìù"
                                                    "Zum Stand fragen" -> "4. Zum Stand fragen ‚ùì"
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
                                                            text = "Anw√§hlversuche: ${item.callAttempts}",
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
                                                                contentDescription = "+1 Anw√§hlversuch",
                                                                tint = Color.White,
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                        }
                                                    }

                                                    // Next status button
                                                    val nextActionText = when (item.status) {
                                                        "Anrufen" -> "Datenmail ‚û°Ô∏è"
                                                        "Datenmail schreiben" -> "Angebot ‚û°Ô∏è"
                                                        "Angebot erstellen" -> "Abschlie√üen ‚û°Ô∏è"
                                                        else -> "Fertig ‚û°Ô∏è"
                                                    }

                                                    Button(
                                                        onClick = {
                                                            viewModel.advanceNeukundeStatus(item) {
                                                                Toast.makeText(context, "Kunde hat 'Zum Stand fragen' erreicht und wurde erfolgreich entfernt! üéâ", Toast.LENGTH_LONG).show()
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

        // Full-screen Dialog for Hei√üe Angebote
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
                                    text = "Hei√üe Angebote üî•",
                                    style = TextStyle(
                                        fontFamily = FontFamily.SansSerif,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp,
                                        color = Color.White
                                    )
                                )
                            }
                            IconButton(onClick = { showHeisseAngeboteDialog = false }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Schlie√üen", tint = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Form to Add a Hei√ües Angebot
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
                                        "Hei√ües Angebot anlegen ‚ûï",
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
                                                Toast.makeText(context, "Bitte f√ºllen Sie mindestens Kundennummer und Telefonnummer aus!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                viewModel.saveHeissAngebot(
                                                    customerNumber = heissAngebotCustomerNumber.trim(),
                                                    phone = heissAngebotPhone.trim(),
                                                    notes = heissAngebotNotes.trim()
                                                )
                                                heissAngebotCustomerNumber = ""
                                                heissAngebotPhone = ""
                                                heissAngebotNotes = ""
                                                Toast.makeText(context, "Hei√ües Angebot erfolgreich angelegt! üî•", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF87)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Angebot anlegen üî•", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }

                            // List of Hei√üe Angebote
                            Text(
                                text = "Aktive hei√üe Angebote:",
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
                                                            Toast.makeText(context, "Angebot gel√∂scht.", Toast.LENGTH_SHORT).show()
                                                        }
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Delete,
                                                            contentDescription = "L√∂schen",
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
                                                            text = "Anw√§hlversuche: ${item.callAttempts}",
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
                                                                contentDescription = "+1 Anw√§hlversuch",
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
                                                            Toast.makeText(context, "Anruf wird gestartet... üìû", Toast.LENGTH_SHORT).show()
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
                                                        text = "In Abschl√ºsse aufnehmen ‚úîÔ∏è",
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
                                        text = "Keine aktiven hei√üen Angebote vorhanden.",
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

        // Dialog to convert a Hei√ües Angebot directly into an Abschluss (Annahme)
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
                            text = "Abschluss eintragen üìù",
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
                                    Text("Strom ‚ö°", color = if (selectedType == "Strom") Color(0xFF0F172A) else Color.White)
                                }
                                Button(
                                    onClick = { selectedType = "Gas" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedType == "Gas") Color(0xFF00FF87) else Color.White.copy(alpha = 0.1f)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Gas üî•", color = if (selectedType == "Gas") Color(0xFF0F172A) else Color.White)
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
                                    Text("Neukunde üÜï", color = if (selectedCustomerType == "Neukunde") Color(0xFF0F172A) else Color.White, fontSize = 11.sp)
                                }
                                Button(
                                    onClick = { selectedCustomerType = "Bestandskunde" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedCustomerType == "Bestandskunde") Color(0xFF00FF87) else Color.White.copy(alpha = 0.1f)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Bestand üë•", color = if (selectedCustomerType == "Bestandskunde") Color(0xFF0F172A) else Color.White, fontSize = 11.sp)
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

                        // Laufzeit in Jahren
                        OutlinedTextField(
               xúÏ}€éIñÿ˚|E4°ÈaÌPTUÈ⁄¬Hr]XRm◊E.ñ∫›≥^YdêLW2ìõï(çÄ}0¸b¿ÿ˚`ã]ÿø⁄À¬Û¶?È~Ç„DddFfFDF$ì•“tÃ¥äd∆…∏ú8qŒâsAHÄwéó`Ù≈8úˇÑù0Í˝
i Ä{3«üB≥èyCÚ…ç—'}{œπ¿mwéﬂ«›ŒëìL>`“Æ˚óŒ,ƒù^íGˆ‡ﬂ˛è37∆˝Q∞Xvo1s»õ˝Gìçç∫7]‚ÂE‡Ñ„”EÏ>ÙÓ˚‚7]˛ƒ˘rÅÖü·cˇ$ô_‡pCˇ
⁄S¿|öƒûÎ„1åÈ¿≈ﬁxOúƒã£>{§´E0	FIƒÏU«ØÔ@‚Øä!møÑc0t7ﬂln<y\3!ÖûH0…Ws{≤°≈ZÛ“y0v'.Üóßˆ'ÆÁ;Ôt«Ò¨´∆æÒ+ÂOg¡ï~’Í_´Ôˆ,›Å;ﬁN¬nöc?&»ÑO˝h·å»D.ª[€˝ÒB3ÙQ˚.NüªI~=5˛ûÁé.È>˛;∆Æ?}Ö›(⁄!}ª†£~‚yuõ ö9tùâ?∆„Ω Ùq8Ñ/ªO`TıdS}Ö›È,ÓnM⁄g˚îç?€úAaZˆÿV%„ç…‹KË∂ÜJk÷ ÄÒºùããèfÿÔË1~RS'@É≈¨}Äú∞ËaÙ§¸ïÃ)«Ï«¡Q‡OO√≤Ú›Ù‚)⁄<2F	ÁCôù·°ã¯∂å–ôΩ”≈WÑZ∞◊èúwx«˜ùŸ◊œáò
ˆ(∆ÙP®'4Ñˇ≈¡áÁE${¬◊»ÚH◊ÉL£ysÒ|N◊¿~ Ï8Ñ◊æ0¬£'rF›ªávº(@!ûÔ0ägò∞—ìâa_r¢ ˇ«1YZólÒyﬂ∑ÿﬂÛ¿â‚˛‹πƒtÁ€,yLòØ·)—hÊ%QÑp8	ºià›—˛v¢(&gÚÕ∞˚˘üpÑ8übÔÛ?ìFÒ7Ë_˛È?˝Ôé˘Àÿ®é'/œ_Ω=:=yiF˝hFŒ‘gW{2’b¯™OÆãÚâÂêsL"µmmÓ~˜dk£≠ìk∏ ≤¬°è~˛áøˇˇ˜Ô‰“z›©f˜Kı€‚7˘'·º<r>,IèíyÈ|¥YèTxÌå«ÑƒHãÙ/™ E]Jz#z†m=™RˇŸD∏{TÓƒÂ o∏å_B{ë∂éÇ)ZN®NÉ«'´é‹	ÚaC‘‰ê…⁄¬°;s"hH⁄ΩŒõ¡!2Ô„˜Œ|··~ª^HhGo‡É¨Q7eIy7…[ªﬂ»-ì∞U	ÖÌUKŒ3¢áGrMä|P¸±CN6◊ÒŒgdjôŒ5—øq·æ¢y<$ä§|J(EI∏¶ÍFˇÇ™D›-≤ﬁΩ∫Nó˚˙Ä–ßå1’†˙R’Æ÷*6≤˝±`dﬂ›z†Áy≈{Ó‘ÁÙŒˇÓÔëˇ‡áÙo©∆”ñb§cká£:…Ÿù;S¸€(Å¡ÛQ?•≈˛èNËì©3Pˆ±è£QËrÈ≠MZs⁄í3-Æ'kçZ´˙%Ââz6Xwò…2¿-`ƒå≈D‹ö˛›·“Õ¬¿w#ÚŒ˙â  Õ‚iÁ∫ÉÏC7∆f≠áÓ8∆	G√3W;˚Å?(∞˝È⁄‚\æƒWüˇ8±èÜ.Fª‘»ÿùíyƒ~%sÙGãÛG≥åj‰9Áç	˚›‹Íìˇmon?Bÿı?$é∞ﬂ7ú|>}€≠Mﬂ0!ÇÈˇ‡ê¸∏·Ù©	ÕÿF+Êár¢»Ò«a‡é˚Œb—ﬂ≈Ó;7^nºËá¯oàTÁ«†Å—¿!\my:Èr¥«éÔN¢~~Œ˜œ;˚o˜vééà¥˝íú[õ˜kq◊aÜÜM+±≥Ü]’àª´	€5¬Z~¶0BÜÛW‡≠d]c≤Ò å±Îaü»ƒÂMcK√çD^"*‚sÉ1RsÉp>H$0vH¶fHú	Ì\R¢ˇ¸«}ãÜ±ªQÏ^*xI/=\°çxπà∏∫ò-˚±{¯è›dŒ$ªóH9SëS.zH¯)"¸4õ'ºÑÀ–ów¸0ôÃrxÖËÛü¿˙AÜËÀ.cü4qÜàÿDÇn:Ùã`ºŒ	
6p{∆i.∫fBQê„`Œ7Ål>+_¬:p=ò∞úD®K÷‹i›#ﬂ¯NL˛˝=ôµ–IÊUÑJqs=∂3∫úÜ¿A∫Íiå
ìXñ≥Ô[ ŸŸª˘kƒd+—ˆÅ|ÅÃ¥Ω	]&∂JœêGˆ%9XîÓDÈ:vP†Œ+úƒX#tÊ∞÷ÏY∫Ï∫gôôê=Ãi£#ó“•ﬂ
#ÈOÇp‡åfË#ÇªªB‹@wü´52n4L≠Æd*:fç}7Éj—!xo}ÌS{≤u˝≥#œ]tUá†A{aÄäûœ√ÜÜ≥,B¢ÔÑKÑΩß¶úsBíÑâ∞õıztÈ\Ü˜±0€0ŸI-kœ7èhUŸ4>ˆu*Áj«∫©hNâ“¿¢Xw&P<‚qh$DÆ¶"√«údˇµH4-â˚ 5÷ Ñ© WC`∫°T/û•{wìÌøJ”´$∫9Õ´»J`∑wükHØ…≈s˝˛2<@≥Á[8H*RSFfpûrh·\ÂP«‡lYP9[8c;±3-ú¢®sê9bﬂ=FÁŒî~)°˜7≥oß8rÊÈ◊/Ÿﬂ˙ÈIﬂﬂ‡dGQ8!˘e™B‘úíjOKMhñÉÕÈôµQù¢èÃà/√”Ë4ç09ó∆´úß‚8≤sµºFÜgkÜKr∆^c¨r‘r0πi∞ª-µ;Ç9¨È(Ê∞˙ëÃ°…—<,R`ãá33ÆJOø´_pæ´=waz…sÒ,:"LΩÔF'A<ò/‚e∑÷ ^{d4eÅ∂G7mìà√Qx7/æ'ã?_&g·iuÎ		!Ä7{,:∏´∫¡Í‘}¢=uLòÉ∞@È·wH8¸{rÑ}D.¸’C]∆–[£É@y√€Ë‡@§¯çV¨£çèè‰/„ﬁ∆Ç◊ËÕ·3FÀ  ´»! Jπb{”N∞†»·¢H.ñxdBÇHªåt-DäïãE€˘É≤Ì‹BÑ hCå 0% Ïƒ	Äf"Ä˝ÖYπuªRC∂≥[>ŸÎâ©Ò…ŒxIõw–ﬂc≤µò±ÌN>ˇiz·Ñé˜ü
	àdµ"ú—xB:?t¡7hü· ÁN‹Ìå«˝„„˛í QYè≤˜qˇÂ‡ÏxÁ‰'uoñΩß≤ÛailDX´Î6Y8í©ˇ˚≥?≈Ò°ˆß6î ΩY0åÅ
^Ù=C0 «ªs|Ëì•ry‡è^Òˆø&√√·>Ÿ'¡tç˛±—€Zb'Ï1≤á∆pÎNIS1ëLrà¡ÔM9≈0<oiqBqàp‹Ut≥á6ŸˇÏÑé5ÎËÒ·——·p∞wz≤oçÃNÇ(—ô-6m‚1∆h‡ØõΩXXî|‡?vŒl)í„”ìÛW+cŸﬂ˘ÈÌÈ¡[ÜÃL‹1ıû6ò&#Ôcõê°à^Wyb‚Õﬂ(DÁ¥yÎ∞™7∑©wgu$âÇ”òÀØ√Y›¬·yìé±†¥£‡ôR ÏM˛f÷í`3f Íê™Û9z#Î…=K!Ê°'•–ààØ©èπØQ˙©Ä9@˝pzRÙ}zÚ≤^•i -ÀbÅ◊Ø	*‰!"-É!
$d¯∫Î∆òëlùù$gMÍvC4»ß˛Y…|+"·VD6Ñ›
»∑Ú≠Ä|+ k·V@˛j‰›√·üªÄ\íÖn≈„û®èÅ˙◊%"W-wF!ıy”\¢i+ø±˚ƒä˛‘áë7dM®±È.dÏËÅﬁK¿Ñs÷«πr–≤Vö+√J˛(ÖΩ˛>	?ˇitI§Ï∆~≈ X7 DÒ'sÍ+!Ìíy&3ì=†–ÍZ§`-!
õ£€∑6"¥U‘´N†Í±ΩÄYµu—>jJm3IA⁄˚0vM8w1jﬂ,ÑqÁ‰ÏÕ¡ Ωwéœâ®¬]$·◊!¸MÉÛúπK4wv|≤˝°„GC2∫Iù8#úﬁﬂQÈßê?ÑÊ,yÙ‡ÒÉ'ª5Ï≤ﬁcˇA=Ég√æÛëyÔ„ÒQ0ç(ˇ˝d5˙„¿(Ω∆∑ΩŸäxóÜÊhFb„pªΩJpç€kpv68‹{u˜_ø9=¸Ç∂Wàù—Ãπp=7^ûëU˙ıuo.!ãÂ`Á·‡;›‡õ˚ ∑«Ï-0‚‰|}}ˆ˘ﬂÔΩ˛~p¯À9gô! r)Ñ§˜ìêzü1V¿S£›û´_˚~˙¸_—˛Œõ¡Ÿ/o'Ìºõ~Ò}4ÿŸΩø˘§˝#U•IÔcBüìÒ:qÑ.àtq9Æ|t±ÑÀ$bpµäµtßööóL¢qÆc?Ø}À∂û…æ}§€∑⁄Àã/í?Nù˘g}È„˝I– wΩ¿–6K-fñI€kÆDîm’ø‘_"dπÑíËä,i‚OëëÃ4ÈNº\‘òÕ≤
	πuÏí
ÈG(gg˙1ÛÒ¶yÜbåæ
Ω$@Æ!◊á£òL<˘\L·Ü&üˇ¢1ˆ≥;®{G(ö&$°òúÄa(Üõﬁ”±PxÇ$Ö7kîFÀC≥4Zﬁ¨]ÿô‘5§&–*ç∑_Ò™¶‘M()˙ˆ[YàñÎã°çÆ?vG8⁄æ˚´j£ø&gQ≈l,ij¿4Mt§æ√F1!"‘∆áXÑÜT6s≤Û'Ñßõ≠9º7∏©$Oöí„ù›EwûÆ'ßåBÅE:3OèSsç±°KÛ¯≈rî¨íß@QΩ√:¡≠i¿QKÒŒ:N
¸∞eπ´MÜbö[úûèp Ω
‚ã‡=˙ó˙˚ˇYsÊR+Hß	t˙ëµØyË˚º˝`“jú*6ÁÏËdõìk;C≤qó?¬–’˘BQÿfpÄ@£1◊æZ◊Où&–œˇÌø7Z0@·`@aªh¶--.GŸpÒR#ŒW±xgpNƒ[≤Ÿ˛À?6Zª≥S∂+g÷Œb›8¬Ü´v˜…ˆ¡£’ÂnCÉÅê¢X}ó(íûC÷b◊	—ﬁåTën≈nP:On≠∫îï#ò˚s™≠%ìâ…
lÙó’ı+}≈VO±÷Oåıµ¿gîÏ†5-≈&mãZè—À∑&9ËËî[üä◊XYj[IZØr¥>•®^Rœv∂‰çûëE~BÕK˛¢:!¬~ôe	£Óù2«Ÿêø#C6L.b	æqpôÄƒOç6>:gı¶¸î¡hyíË~ßÅÆÚã1àÊÆ€∑VQÿãÖ˛yZA€Ík∂Mht|˘v'“(¿é˚cê±yÍy»¢~Bˇ≈Èˆ√ŒaDR¥JÑ∂∏œ VûUrÂ∂5ÄkL2kî”¨±G<ccõµÊYÍBøÁÜ#¬M@±˜öØ∫]hQ¥Y3áÔ/ÀdO[F°%´ÂÎ∫•rnw–W∑Éæ{∞s˜…5Ó†‚±tªè™ﬂ™{^âÖ¡=Ïô©	ÍZ†.K˚A¢‘Öπ∏}Ö‹¿÷ áv¥6˘e%∑^¶çZ»%Ø*~hƒïıy[/ÔX1ßf‚J¶Söñhê•á◊oâ£®4!ΩÒO''@÷‘Í∂°√Iñµ-ªhK˘<5Ls_…Íõa4…nøﬁÛ√2£}ŸÏøL]ﬁ˚,=≠∞ÈÎe©Îk‘´Êõ5.él4˚º≤e˜S„F7øLmG⁄›T˙ÊŒ{z2”DúUÏ;±”ü;H-˜Ö[µO‰€˜ß·I‚y›çdl8·ù¯;Ÿ”G` ›:RøÕÅ™ëòﬂƒ∞Kãl_ho;¯Qp˜y’ú•n≈èÉB´∫kÔ¬iPhYwÁ*úÖvu∑~‹Ëk62≈jj˘ˆZE#^'Ë°Z-–≠-l¥úÏ‹»∞Ù@”säXK]í]Ä…F”ºŒ`>ˆSGI
€fæíÜëÊÕRJ¨ûô„±¡˘ﬂòö‹ò7∫pBÓ,ê”(∏zs>!◊¨ÂÔû°'≠ÊÆiÅ`LDzï±ã„+\Á◊¬AoŸ•öQ˝≤öf‚»ñ$óÇ…º€$d3qp)É0söˇ*{™ŒÚSÜ˙¥lÌ∞Í›tåQŸî ∞œô¿yÿùèê,0/>YÊ√h$$ñ¥Sw @ãÉCœ—ÊFA ≤¨"+2g›?£+	ãîX‹Œ˚#r(Åvøe’X„™&É&ª`’
(ä≤Õ2’óõL } `=¨¿û	⁄-xÉ‰ó™eè	ÄV˛°˛∏tBgîﬁ™ó^B˛<'Ón†{Ee)ˇe#UÑ}Bõ'=d√sEhJ⁄⁄"qM*p’·b3ﬁïÃ˛ä¯U¶¢8XPﬂ°î≥ı»¬/˛ò3∫fomJx úî˝ >Ω]W†gV%°T–6qhìö2ú©î#ôOaÉ∂Û*Ìù\zºù%ΩÏÎ›L°yﬂÌÚÒä¿	4º•Œq
‘^;iráFö, v~çTiﬂ ÿoV+Íç>Ñ≈|Î9n∑õ™Ó˜¶èq{∫≤jQì…¥∆â2ÆOK÷’† Äh\‹ºA πÅŒ}≠⁄ØÈìf§∑rÚ]S∆πäM”xÃE„ßy≥J‡¶•~§#ª5®r¯32®“¯A}6{nM™∑&’[ìj‹öToM™∑&’∆∏nM™∑&’[ì™1‹öTKµRÁ≠Iï¬≠I’nM™∑&’[ìÍ≠Iı∫L™ _€MΩG‘FYEO•xÜ≥T6Í™"7M ëµë${íS6∑/ΩÉ8∏<èelÕRãHÍ»lHLOÌÊm®‚◊Ÿbàä"M\∞!õL◊
CR∫•K¶@y‹Ÿe¶«î»‹µÑÿ4Ò~ùaLSaTì‡ @3™,|Qc=k‚ùØíπÕ3œ˚îÚ]ü9ﬁœÛ$≈bzú6êjÁﬂﬁ#ø&s,ùGƒ≤Í”øÎ¢T	A ﬂ(ôôLæ≤^§õPáå¿¸p:Ÿwñ¥6¸,çãTlOÄ:À–µÑ3ÆDcâ{®h.ñœ¶.Ö™iöù)é…ƒΩÑô=9DòÕ∫}^G¥TÆ9r¬)6†ZÄu«øßqúı®< .≤
«õ˝áD¿“6kêHâØ{T0üeﬂ4√Rn7ﬁwﬂπêtqEv°?’¶˛úπ£K"∫QŸçå…rH2H∫µ£n∫˘{4¶ˆ∞/òˆ–[‡à¸Ÿw«Ë”Fˆ˝‹âG3≤ ¥ZÈ(V∆R”ÿH;l∑ú&Ò(ò„cÚ†Å:›YﬁyÎÇ8Ìqïnn~EÂtlwa-/∞µ‰i?.ˇmõ™˜Nç)Ühg¡⁄zäN"˝ã“åÅOò›Ñ¨˚9(‰h
!p]-:#èU≤åsh3ÒÄm0úûÁ∑È”ˆHMG[ÃyhÜWb÷C∞»‰ßâCD8ÉÿNC+¢ùù%oJ<˛EﬂÚÒî#ˆÂI˙UÁçÅ/f}l–¬c¬HÑ?∆c7ô7RgU FÃ[Ñ⁄&aL§?M?ˆDõ_DndÑ»Ã¥BÏñW¡çˇ!z1$!â√ƒ®:{{W◊“øî¡\33Fk6m´⁄ô åÌÏ+yÂµêÿË°"±ëÂrvÙÊ|/ï£{H»ô¢7BpX+[Í‹˘ç'pÊRSÎ—Êd‡ü–õYÿ625#î°m≥B⁄3FóØôÔÃfl∑%fYg76ëÔ∂ü¢◊≥¿«»O¿¡U€ƒ*˝PÊ0◊O—Z®∞æà£öñs‰‘e 4Y∂˚O—Æ3ûrÍÓ•≈{(U4{àWü–ø…H–¸‚tQÉ©«YØ6¨,îß˜#0œË&æ~‚»…—%4å©ë¨á‡O™Ãn ûºàJî)V„:qBV¢Bí:Hgû¶®Ä≤î≤®ú¿Æàπ.çQs!•Q)π]m]ñ#≤4˜«éü`œ+°JÀ ~ç‚PF$eygÎó&Ô‰;ß=aÜöóõJ3+9∂∑'™Û(£îñµìmc¬-”≥»êY^˚FÎ,;≈Ò;çNÖ•G…/j´Â¥rC˜˜¿r‚¯PèÔ•ÓçµÌ¡⁄ÇØQ6ƒ£¿G6Ó• Ídµ’≥Œ»K°I^e6YÕ¥ÊüˇÓˇ†;ïæ›Ÿ>·∑kPM8¥Gø˙_ÎTùì ∆4	8°R˛∆Ô›(÷Å†ø2ÉPû+Òº”p◊s¸À˙ºê ˆii”óô-ﬂ⁄(jMÙ–‹¬o~X6Ù®ΩâñD≥ œ∫Ùî⁄ü-ÔÎtﬁeq§«â˙9Ø∑#ôz ‘[MvX@ŸE«Å!'¶ó©Ôƒ∫ø¥6êÙ{Ã¿7π	≤≥È¥a;:Ô0∫ác7FÈZ∫FvwDÂ	…≈\A)≥çRi¥ùÒòô	ä_CåﬁÅªã∫óvvº}ﬂ^∆(Z@˝NZ[ûˇpÅùª1ˆÎ+ú”óª~Ã˝¥Ø7L@ØŸ¶T#÷ˆ´ˆòË]∫§f'V‡ÔA~{Íuìíƒy@ÈÑ®Ë”5;¥Õ˝˙RÖF¢πÜ/TEâ§ßäU·Saªæd†≈ÚÅ∞èÃeLi›¿tcòcIÀfSeqc“∞î†m©X{éáa£&[gúnx^pu7Yò≤≤∂àÒ§Õà|ÑÀ⁄∑7ãCﬂç…ŒßñæÃüa≈≥ª%·6»\ÒöWÑãs|˚Y0›]æbØAÃ¿Ïb¸+‡$µú¸+·0ç
îä e:ù]Lƒ‹wAËëó°—Â∞çüÚ†¸|9‹0ﬁ‰)]BÔ\|Â6ºæK7xå·Ì›¸≤◊ò«Ùø˙ÇŒ.ä6æäÌZπ˚*∑',›™;Û˜IòﬁÍ5Ÿëïyºy;∞ŸØ´áÊâˇ•ˇ–J'A˘Æ‰ÑCK•%!Tô:
 °–‚õo
VÙ
ƒÛúÒE=€·e2D>X=2˘i√è¸ÏK˙≈	u/9ÄR?å|¸Õ¨∫=+$Ÿ≈◊¶#®}µÀ^	†¬ıL6∏?¸9°à<:Nß2{Éd‚¥K Kîæ˝ Ê©®√$ü‚é˜ÿ⁄fo(nj$-¨ki•ãèœ®cÂV…£¬ß‚cÅøÔFsó⁄c™*bôu~˙„G‰Séœg&ò/ŸCxÓ∏^QﬂŒç=•“ß$™"?wúÒÆ˝!ÌÅú˙L‡•Øï>∞HIE„¢îvìn"÷aÈct‰!6È#πÛj:VÈS˘Ãß‚:üê ”UÚï-áÇﬁ6 L≈«W;æÔÃÊx«√a,c+Ù!fDqÜˇ&¡Q,’UÚ≈≥áO≤7Ó#ÔH_P≤¸ñ®âF˜JÒSÉ}Á'YI—BQ–)ûÇËÈwÙXô	_ÅT∫Nù}óOr—œ˚èw>ñÊÔò¸0¨€ßüˇˆP˝VéÂ*	«˘8Å8–a≤p.ú#Ë≤áß1"bÇ`Ïá„±KB}œπVÙÛ¯œËÁˇ¯øËø|*¿S˚Ûü¶ê7†™ÕiÁÜa¬s&+*'I'Jöh∞©C™+Ú&,Ä3j≠”ÛóGKT∆q\)ﬁZ=…µq¡æüˇy2Ò+4Y≈XZâtfnˆJÿŒ…pÒ˘èÑxÎÁBÕ∫ÙFÇ+?’-ì:ï:râ©t˙πCä⁄ "m≈ÂççDw÷÷öBh‹LıÙ›c˚∑z ”ÀJ4NNÃ‘∞3ÏDÑ∞Ã^ﬁèÆ	ŒÍ‚öç¶Ü¸´=r*H˝’$Ò§'Ú«S8À˚¯Ω3_x∏?vb u©&Dƒ®ÅOôÙò/	QOQ˙{Ê{Hñû¢!é7åC“Ê9˚5[≈ß®K#JﬂêÁ?Aw…˜0á§)mñŒe˛1ï;Ú/®åë‰r≈S¥v¸^Ià‡èfoˇﬂÔúê	IÀLÍ'd2ObòPö·¯t“-MÇﬁ`∫{Üõ…Wv»Yõ{™l‰π8fáì∑™têâmv»Xõ
™Lº≥√ñ5#YHSéíß^…§A;‘%Qíº ∏•@≠‡ƒYVÒ™wzewXÃ≈∏0\ÿ¿˚a∞Wææ_,ûè3Ït+JD∆Ï+ëcW#°ML>uÆy∞≠$zQóàDÚ¯:¢òKöúáﬂqo™ K¸«Óò}=û.∫!îbUË≥©QÕƒa#œ¿¢ÆÏ⁄ÁW—ñy÷-¢†%Ê ôX¢˜S6PE±ŒÓÅ+w¿Ë[Î”›£æñkÇfôéVåµpí®ïØNìÿ#§>Üâ<p±ß»b.-≠÷Ãü¶Û›õ¡Zf≤¨@¨≤SoS˙h™
BÉ1ŒÆÊ#µYy·ëÒÕ»,Q¢Á>Ùw˚dbﬁ£„$"≥3w|_ç""·aÓJµE'±€·p§ê‡9P˚ÆŒl˚áD∞Í…≤@è=„Ö Øßö;juÁ¨N]´Nïº∏)»úæŒ	_&;ŒOÊs–_napÛ⁄ı“EH˙Í{KÌ ø"‚—Z˚™‰ìõ	Õ	ËÄ®eÍû“’qºçõCCªIQ™—-{ë ßç©∑J‹>lNÉª«–‰&í«ÄYÏI¥°Ù¸\K2FUﬂ'zw‰6<°ÔOt/Å˘î;B¶¬}∆7ŸﬂÍÀP£Gm€ïZí´%*õ•„è√¿øßÖ ¬˝y:Õ˜˚√+7Õ‘wÚdÚGóxÃo»d©Øπè=-pÒ˙(VÃ≥"õ”ÛÙﬁ[…
ÿ9‰⁄ríâègd≤ëA˙îÊ·ZbÃÉ⁄‡ôU¥Í›a>M;ó±˚≤{¢h4Û±Á-Öy794)®ØÀ˙Yv}ÃPã1!´$€#∫3'bµ•§j°ŸS≈æ‚eî{`ò–oîÜø_Ìn˘EP˛ ±πı'iù˜í©√ôËlV04í£¶Ÿ¿M,≥œ±óg∆3Ê'ó⁄Ëj±0L∂Ü<´å6©8Uè‹>◊àAˆïF≤ï◊êcïrë÷!8 ∂iˇÃ∞∂y`^æRËUNP3ßπï“;[æã+a;awÇWN(ﬂ°?Ôìv¿äûãT[1Fq≥Ω¥?jÑWæ«ÿOÍóø_8¿%»<àÎX?ë2G°2ÉOØ±WfÖ€¨,Î∑ŒΩLÜsv„πu\z™ÈÛ.;=4â
®\)jÃ¥x$ãfå¿¨d∆MÀ]…`t‰4(l¨≠rÉVùÿïS∂A’î®W#’ÌÍ$πB['ÿwv..BL‰"ô7Ä|≠ÕgDøΩ®Á$Ÿ;Ï&›"Ω s˝Ë÷{ƒÇù†≤kÎ«ïø¥ø¨ÿs–8ΩÃ+Ã√µ”¬0ãΩ5WSÂü∏œΩƒ√H·C∆∆û"X§ﬂ¸ÑûSkÒ"^¬O|^RØ∞}ï"ç˙¢ êæf‹Åî‘N·◊◊¢´ê≥÷È(ı´Û;_Æ¸”8ÔßËŸ≈x!s.Úò–Eª£`ƒºµ…«~Í≥û>f‚É$ÃÜ•Éë8SbSöID€≤Ëﬂ√˜‡^Ó)ßkù:ÅΩxﬁeŒEœôî<Ü…tJƒ rX¯–@∏W0ø ÷é<òWƒ}ÿ–oQ¥å†`IÊÀeÔ»{@1}˘Ô(AãsIÏz˝t—¯0§”Øû?Ô
‘ûı˙»I|àöL&d")Ìâ[:c•ˆßÒ=Ü∂—∑ﬂ"˝ãgNƒ?ø∆!P<∏¬§dW·ƒóà ÃÖ¡Â„®Â∆≥î,ªíü…6Z@ÿ£˛·©å≈·R¡òÙ=è∞éfÖâãx«{tGH\b…Œ#ùA]≤1ÔGò™1*æ(¨Aﬂ#sK∞TÈlœ*ñ)+Ω#√ ÎW!∆)'Ôî∫´DÌ˙q Pvî1^qãœ(¬ôÿaÀqHiœçg8o•Æ∂›êL©SÔ…~jKä“NÂ?ÒÏ`ûŸ¸ê5¶ØÿZ˙(Ñ ΩÎ_Ys“Ä—†ëÁDQ∆Ç*º,´h|ñx∆~)û7ÖüJ>Ø/äø∫€6ô«+˝5ΩµKõ”Æ	/#Ñ2ëÙ™tZß£%ÒûqŒ‘K£rû§]~YÆåÊ^ ﬁ’]ı¥¶¥”CyxYæ ΩlV∏:∂Qﬁ^ ïÅ
∫1˘≠¨”ºp–3ŒŸèŸõ¡Èç<û~¯$cAlDÆÍ∞Q\2ß≠Na\îLÎCË‡’"—¬¶ﬁë{4√O‰–ü‚¯–èb«·ÓFﬂY,ºeâ-àbö,(ä„Á‚¡>ãÜÆ}	-ßxË—•|U|Oë=G8Óf»~Ïúı oÉW)ÕR≈ÒÈ…˘´Ùí˝ùüﬁûº5¡%>ZB	yŸ¬@ˆﬁT,$:qﬁõª~É@≥Î@§-˛Î
∂!⁄⁄|∫πâvé·∑I'!Üü*„”rÚ≈®–z˝º≤`–+Ÿ/ﬂ~[=AÊEçV|@∂≈
ÚÍÙÕ<Mı™®Ñ_7`N%ÖÂ?<ys>ê‡a?î˜†™™≥∫É[õÜ=ÿTnwŸK)øëøP ≈7{ß'˚Ö7ñztttXz(óÍ©~§‚s‘7ˇFŒ¶òr„cä<ºvGóëôf¿1Nπ¯â»ñ˙fõÂ&«ÑﬂŒ,€Ï;5Åõ"Œ3pí_i·(·L¶ÛW>u£ÒÑ<3tAÑÜF¨ËEg<Ó˜ó–oí˘o–´WOÁsÙõ7≥7‰¿†™&ÓøúÔú¸$¨ÊxR(õ¡^πQ>)®i3Ìa∫œJ˝JYQñ»ñŸèK\˛S·ÚpEx?{∑*N¥Ñ¬ó’´åR/%,À‚y∆Ω-∏~÷,ˇ+/D`•£•	E∫7dIﬂˆ–íl≥ö√Œ°%DO'lïÑ(q§Ã*S:y≤-˚å¢¨¸∆∞>co™¸
ªô˙‚ô-·'•hÿO≤%Åv›¬÷∏‹˘%Ó‹àË›_LÖ]?tG≥?Ø0ç«ûô√ç°+3@},@≈£Y˘$@´£}Ja≠	÷◊‹a®#ÄÄ!hÇw Zà¢É2Û≈¶›5Ù«0Û∑0™≈±ÇüÑ©◊∑ë˙nK3ÎqË∏ûÈ\/Ì≤◊˙K;!göÌ˝{∫#:f©y0í·„ çdv-/5ﬂ»Äa){òD™h£Œ∆°Úb∂–ÌJıR÷øÜÎ£ú¬o‰ø¨uRáÙj@5´i8#FQ¢π˜.ÙE?ΩM6\s¡Ü~Çä‹ﬁ;æ9„‹»Ω ,ÄlÂw'=Úã .ï bcn†/Œ≠bÌ5Æê†˙ÅëZF%ôC"Z8H∞ıH!‹â∞ökË§lE‰ w'∫j„ ¨∏9¯”?Üq\‚⁄ZÁ¶“◊6/¶}‰|Xöî∏oZlÍÎ,VJ•.¡ë›KY÷”6KØîãµT π^¢o%]¨¥@uÛ*Tàj”ø†Z<¬@É)lI5á6äÌQO®∞Á∫ƒK*òwÓ|Lo¬>Ω•RiÈSáπ¬•∞©ãß±W9¿*	gö9°mÖË;´Ü © öOùAk4\2ÿeÙÊ∞¢Ã*Çô;)”-gˆ¿°eß~ õÍÊ∆µÛ8¨J» Ã˘}€¨ÿM•±¨j›Äﬁ◊Âd»Øâ7™˘ÑÎ
`XsrÑÙŒ÷Ì-Ë ’t$fåœÜ∏ ÏD ©(P‚:bVﬂÓ∆ã~07¯;Y,p8r"‹›†é+/,d&÷∏◊M◊ºΩ“Ç[£¢„ ÊõŒúE“p¶P,Lœ¨öÙ20Å˜õ˝ö’f31Ò‹µ;9¨±¨Äªnk9ò[VúO¯>ua™ñÈÎ†üˇˆ†;´œ~‚È¥∞Û⁄{◊±neA˙Òƒnq⁄fıO}Å∞2X∫ TbÎ∏£a/â‚`^ÍåÏ¶æ0Ê·ı*H†3⁄[g—[GMÉ)¬cÊ(UÉ2u‹—›î≥ÕVÔç•)fãHƒt≥‡«’C©#Wç6FóÃ∆9Oo9~ø^øUd~rŸºeÛ¢á\zSoâ£‡¬”i;Ñ‘ãä≠ÅecâCî·K´éR*–Û∂üa\¶jØ§k…öd~ÛïØæ §töÀŒÕ≠«õ[€˜<|ÙxÂåSóxy8·òÂ∞ŒÚ}Òõ."-ÙΩëe≤Sp¨Ø(Oò∫(∞$…e[&πb˜∫ávà^&˛‘(aòf˘wHW.HO-x`ÂäT%◊Íﬁ=¥ãù$v'D≤\¶˛∏‘Ñv»ZÅ\£ôª®2ïñì—Ë•hn4BÕpZÿÅT@ëŒVn[Dz=•%}D~(e8∂<¿7<q°ûæŒ…˜hFq6†~r˜{tÉÓÅ∑cå≤∫ß%FÛÙádéÜD§£IËLUµ`-¶iìj‚´¶W±äy◊_}‘ô±R
Í¢¿›Ì!⁄%4πH£çR/~–f´Q†œû°z≥õ±}v•ävy¿∂yõÜπ+xs.ÀÙ≈gMbê-Îº€ìZmÒÓ◊¶_Ù™◊Œ|A-]àU∑5—ÊõµS¿∆ÀÍü≤'Ù õ›€≈—À¿é^]B…vNeöh1:3ÊTon€î_˝<Y·ÊßãºU˛&„±¶ÚÑE•eªúÇÂñ´õƒ-∑éπ$bqCÔºié-ã\<P\À£af∞FßÈ«˛Ä®™ã®F[Âp›Ÿn ~Q¿8∑Ä¨Õ≠PÖ[	@	∑Ä∑ oy+|]Ä—≈õŒ_u-©ÔMΩÇÍRzo·á∫§f ç9´fpV)Î#y?V≥?B◊'M¸iÙªÒ"Ò/„_¨R+Ø"∑óX∏ø√V≤ßWÇ˚kvW[>6˛5-πrÎ3$6` Ñ8&mèÚ ßjŒÎ±‘û>f’E¯Tf¸Sè4P°•˜W”¯VèïÑ©<Éçÿ0\≤sπË|dVA †6t“¬cQù¶∫^%–D_6"æH“‹ÅØê<W‰ò∫„µ|(Mç kŸ4ëÌ*ÓökMÇQv,Yù}j‹‘áHSZ∫™JÆ!%Æ&µ≠|tÎó4*_Qâ°´hLMSÍÚÙ
kÃ©{FÑb2â#ùa"€$tü¢7°ï«bO[Ò⁄()mˆlòˆLÃhíµ„Üø„πsI«ûã)Ö|t;ûCùÂ¯;>9ñ®Ô[∫‘§q∑¸Î˘OØowévŒés§∞H…Í≤º|zÌÑ„ª?‚—%AﬂÅl"
Y ¨ÏèjﬁS÷úı∞:¢,'´“ë/¬1∏˝hYﬂ§øûûº<?=®rÖe+üÑ›i7˚Ïã¬s0èÈsä∏öπD~MÏœÉw¯<8Å£dq~JúÃSöëEN*£:<?º›;=zs|Úˆdo‰ªê⁄FDprd@9Èõ»fsiÆX)2`I«ÃçıT32rb-L+≥
ì<∑T÷Ù…±Ù<t
˘¸tπ ÛTŒdÇÙ˘Àƒæ‰p∆.ë˙tkÅëøˆú%õrn´*L}ñÑ∏öD˙úN÷zﬁMqﬁN_ôåÀM0_Áî≤Z®qQIjk“ë(Á8 àOï‹˜f§ë*≈¡?x(/xõnäÆ¡Æﬂ,WTz®’rªi	¢Ã›!˘ú]D)BU§™í’œﬂπªX`]·ü@û-ÚÈJé's‰Œ|Ù:.0˙ê†ŸÁ’A˝´F€ÿ[˝ÆeÎí9X«ÃHFz±ÖÌXû0Du“≥∞¸LŒÉÒ›òs≤‡{&Ù®†ª\W^™ìsSàÖãı§çcèÖzkîmNño“u7Ëµ∑=~|îo:LTWÕf#≤∫ÎE+∫xV≠ŒS˘òíëQK3·dA˛·Ñ~óYPJ™"‘_.∑w©‹RÅ:+Àô3v”ZúQæKsz4)ıfôÄÎF8˙ˆmDaõy§«=c‹ıƒ`{mc¥y™Ã¯JÚce¬ó'pÒ‰’æ«¿ùCÂ~PÀzç›l˜V˛+˘¨≈‘Ø!‹Y*‰∞¯É€[ÇïÌÓ++ÊÚóÔKsTã†àñ¸3æ‚X”TÛã·Îœ¥FŸP◊Uj1◊§[º+
•ƒ˚Nzp∞º¯Àìd˛r≈éöô!ªÙ§N/}ÂF{Ã®ÃûÁÂÇ6™Wﬂ”w„‹πÿcW∫i\|EË{ÙN!òá…‰˛{ë3ä	9öÄº  ˇúV ‰WBı@JyOQJÍ≠P=KÀï…ë+Uÿ+¸êRc÷,»‘Z’√Qá¶´kfT™x¥Vs#Añ∞Åèß)NæwQ≥*x©‰-yuÁÙ1L¶zL3@ê¡œd!é»$CY≈M(4µfÇâÿò«”ãv]pH;§5ÓJ˚Z.H∂¡>&:ù7úa\S[§R 4ÙëK!+ÃvZ8S◊gVøàNH˜·&È.d•X∏ÔÇ∏8v⁄—ﬂD¨˘ΩeñDÅ÷ÔÇ6Ø9ÕΩŒæÃπ./¡Ë–(gxcŒª …·S¨ Ú÷&táMÉPœãæ≤y+Æ·d—°ùøt¸ƒ	;=Ëº”?è?ˇ1¸ ÏŸÕ£ﬂ8.¸ÛóâÔñ,X‰;è˛∂ìLìJQuÜx”ó√á”À8Hˇ<!íˇz`ÁU— NK≈‘?g5çÚÈEP∆q5çËÙ"~sSòı”…Ñl2˘pÆ@'˘.ù‰úAdâAÑÈÎ €çÔ∆ô8$Y7∂7∑m¿pﬂ·> '"oü@NË¶„¨ñªê%‡xH±ê¬@®åêZ ˛§≠Úy™ﬁDî&Ü◊◊»ÁIV5§‹*≠ÿ°jDKìdKæõ∏Dw·µ‡Ç3‡n≤à(Käg∏¿ñ≤W	~µl∫ú¿F¬ì>U≥‚!¬kÍ üàç˘º)õ+⁄•3ßl&L]∆Ê£ÏÄi∏tˆˆ$É	∫Ù∏ºL£i&öJ∑∏/À4“Œ)-§'¨E©ÜX•qµ[Ñ∫vYë1qÚ}—èÇ0ª03Èä„HŸVŸ÷]gOÂƒî‰`EŒ‹ÈO\≤
%=˝`Rò
á›+Ï@Æ∏∏õ¡’¢V—¥ø“¸r€ﬂ›ﬂ-'¨∂ºS‘Â≥f÷ﬁg.˜¢Ex≥ê¨∑,¯Kıµ‹ı),¢´;GØ†ºÀ‘†:\-≤(§˜=ZπFÎNháÂπéB¬ºŒ8©89¸%’˝8òövT⁄†ﬁ∑VÁSª7√Ô¬¿?¬M8é‹õˆá ú·–qàllGó›#ı£Õ˜ŸÊÊ¡¡ì«™öÂo$≠˙õ“;s˘ÙØDë‚Ø?°;Ç∞PsA
/¬ﬂÍ˘CÈÅCTñezÒÃ>Ùáé…M‘”,BKå{¢çâé±]ÂõòÏ@ÿ√¨l˝fˇ°*˜©—eÏ◊µA•Â79|ÒzdaªCO>ˇq4ÉIÎﬂ†ÚO¬∆5ê∂´√è_Çò…2lÂñÛ ih+$üf;Ù‚Wy,ß:nÁ8†J&ULèÈ˜È7T˘:ÙøAGt'„:¢y√$WÚµÏç7nË›Aß”ËF®áíù≤∏ÛH·Vaëx,w–ê∑Åπ¢F «Èﬂ™_ïó™?©ípDÍu·•L”A—ïª¿<›ÀÉ:›1c\Y€ºµﬁó(J<93Î’Ìá àk˝ÒRÓôZ^K:xñ,®dS$_P‘—"⁄=ƒá®ç©j¸ê±–…GU¶è˙á3˚ÜÙQôQ#(48®ÿ¢8©÷ùNÜL„ñµ‚:æ¥Y™ﬂÇ}kÈπdƒHçﬁ(ÒòÕÒ dàﬂπA°oë,Üt¿û!^ª…ıëC8!tBXqπæDz-˙ØÖ÷ˆ'Zö5Äl©ïæ˚¬5ƒs	±JçÃ≠Ë"Hhá˜ﬁú~ÚQ÷7)&•ï»!UIÉô`†Tèœ–∆∑3ä∂¥;OÊuÂrEL‘[lä’¬âÀ—U0¸8|/G ‰Ûö)ê‹nˆeº¬rﬂú–≤Áèÿ=}È…ªhª~â	Qñ”.7ß”~TáU˙˘mÍb‡y∫äVW2e∞&®J#3¢ê1®ª
—V.÷∆}0Cê8çI≠Ç]ÇXjßT!·‹Sâ£ BEd√ò[Kæ[•h¢ HÅ©∏Îs‹D‰9∑∫å"5ƒk÷¨_øEn/ÔZ/sØtâä“T ä˜∫+` ÂÓ*‹ÜTû¢£á;;2˛≠~?cI™1K«K⁄¥ùo…0!°≠dî„é7ß`YNÁéÎì_á^Ûdß4b˝=ñ2óRì÷˝(±Ã«ÑÓäH¥≠ø~∆dÕó≤aì1ãS`≈9†a 9‘8‘ú£DÚÂu¥ﬁÌî˙≥éÙ≤Ó≠aìgqW˘#Öè◊T"¯SW,Ô7Èû⁄ÓÆzK-fdq∑«ŒÌ‰À?¿Jﬁ˛2⁄)…‘˝—,Ò/Ò∏˚X4&\°âJÇ
ˇ˛/ö3•&ºA5p|lE€âQÈ
Ïy+ÒG>I∫–_6._≠Á+óØ ,ÃÀç M»lU˜!£v_ærßâÏv8%Û2ÃÌÓæ”Ê◊3—ÒIã√‡BŒäî[Î~†üŒæác£™µ5∑È9•ñ/≈M–ÒÃewÎ&h≤ÎvæwkH…àñÃ< ÚÒ{_`"sl‰&°ÎÌ§âøä¬BΩ≥ä=)»ë6$92;≤ ®K˘ß˝Ÿ(Âl”t≥∂©f˚N¥ ·,Å›Õ˛ìá¶ÌZHO€®÷Ë’€÷-ÿÈ›ÁÜw\j\å˜Àï≈À-”…‰ ÙW˘ÔQ»»∂Ø°ˆ6ƒ€àÕÿÉ)aXÊnñò_RµCRL≈∞¬R8>XŒ¢¡|/YNù|yˆoHM≈™	µ9µ®ı(sB±®2ﬂFes~fù>Y‚À) Ñ„¨∏ï3Có≥ùó©≠Ã≤(k3(üÉ$ &BÕÎ£‹¶)òÈeDPª©ÎƒSG˚ÛÏ«.Ø’’âí#«]:˛€;s©‰ì"éFˆR'	ùóÍw±:¿/Nº¿±Hù:‰ˇêV(€Ïﬂ◊xï!vB"‘‰mø{h—òıóÙ|HÑ˙“i;√Ï–K8ª<Ì>†cpÏ>ÿ‹Ï!ÏDÃ≥Ë¿â‚”$ÜòJá¨—Ä~oYè7§}ÉÄ
‡Ÿá˛~Gñ’ú[º6#ù)Yryç®
vcR¢˜™`˜=¶Sè—ŒZ»™1Q›,ä⁄zX )FK-SΩáY+%e´æfr¢˜UÎ«¡°$— 0zhœÒﬂ9ë‘GfŸ√^”Ãóƒ¶⁄=åâãyp	√¢L≠öœ∏mœ>#0~¬J àéå¬∏Â8tÆ®®sâ¿Ï$…ä'{*3Z÷≠`N•èmyÕ ÉE2ê„è√¿øßu¡É˜ó®‹¡«·≤ø'<KÂ8BÑØﬂw7zH¯`ŸqAÈz2AëùÀÊ{w-ÑpI€ïCwÑV¢Ù:¢ê6°Ã~ç∂¡‹±πQPƒú4Õ…®9}qB \'”®ç\≥|ÿßxùÖ~ã∫†≤DjN{Î°“#NË"‚-K∆
Õ^;tû‡Ît®^&Võ3"ÉMPX¥`YC”ÖˇjÈÒ£>⁄q* l˝‚áEƒZC|øåŸ÷B§F˝∏à˘ª&àÕe∞’ÊÇ>)é¡∆ UÄ ‰<*"nq÷K˙∞=‘äòç¿h∞¢€mÆhiz¨ØT∑“⁄v“⁄à•ƒØû\”ÇﬁosAÔØkãñ7“∫ˆQã;Ù…óYOn˜]”ΩøÆç‘‚}∏6jyT¬¸¯’¸qÛ'≠T*⁄ıy¡§J.`Z+:Ë~E:8Ïﬁﬂ≠Ë@©j‘ñ"±›Ü"A$¶IlØUï®ßÑ˙'t˘xÀ xìiÇƒÛ»(œ†Ä≠SûVçﬂú$¡Ç÷Zc2ø“rıZ‹æôG@À@ì>˝·¸~≤>ì$ãR¥€(ÕKf¨p/†t78ÿzºΩcœÁ[ª…–∏4∏¿0∏æo{pö@mﬁ=‘ŒÖ%Ä_‰`¡«4≈,U∞JçkMj]gm-3{s∞µ◊/v»Ò\(AŒÚ@%ìJ‰e†W—Ñcí’≠ΩF∂,ÑZ‡!üÿ6•’à*†Ön5‰Fi∑Zz˝™~Q¥Gç¸b*_n∆w=gtYÒôy2ëör?≤&Ío£qå›àÂ÷f5™π7+ﬂ}ö9iü–
pŸ∑PÆ)— Q—ã¢‘≥4‚´©J†sÁ£l ü˛≠/¸ Ø˛dv[Üïà@÷ªf=πr∑n`‰Ó*É6N#MOñ
ï€Î}s∑W)^¡ñ2ìPIÛ∏móÎl;–â–t⁄È/e‡˙LŒù,ıfV‘j  h˙LL¶»tU∏W√Œ’öT:YX¥^ÒÆ~ßJe∂6Ñ¥¸€Õ±¨îÆ_Îñ<ÏûÆ*-ÙÜ˛9⁄n≤°õofûõÌ∑w>J∫sm2( ,Éwuk;∫YÊ§:Pï√®Ëbu!´Lu–p√ åeˆ∫:Û^É#jM˚»ÏIkaPWG!”§Ø«X´)«,˝≈¶∞@ˆg≥Ã±ôEíb§icøE;„qZK"{ñ∆πAÌôò<=,§SŒ‘}6bé%O¿zÔBu	xÚÄbÌvzh<Ó£chI†”c• pˇÂ‡ÏxÁ‰ß‚zL}÷ß. íΩC6U◊ó‰Nö≤µa∫VEù oTLÚ<Õ◊ÓÃWÆr»ò£Y⁄:YÚKÎŒÀàÙT¬¨MZ_
…Zû*gﬁ+|T%íáJ
xH˜∏$Ú®≠:ÓUÃˆµÂ”àõ4ïı•£Q˜˚®îñª¢œ=2 %AÛêåí©Î)Úá≤˝ÖfÆˇ!ô|˛”äµJôào §µïÇ»€±Ú6'8ÈÙ
:˜A*”JW≠§rî•#ÖTtPoÑ—⁄›7i°Ä¬È‘=ÙA9ÍA]Öàû,?æ¢ŸWÚæÅWJ¢ﬂw#≈EÇ‘äbúï•∆&oõµE)'T›–’ë•ç
%mU…úÉ>åÚef°£M&s=c ™2+Ó™µ±{í¿=i MÕÌT[∑Ù∑YÚxœˆ”-√yqØ∑ÃµÏ'™J… bze≈©éxî,˜æ-ÈËUw.W}e•—è.&;Á]zd∫|DÄç]®AÑŒù)ö‚ÖÁê%“hÏ÷u)∂I–L¥M∆_˘4)uôfß´7mõ±ÿb7l).wkDàº†∂ô¥¿ÍKóŒ®BïiwÃJL◊›ò“¨kT$.#Çïg^?wÁ©x¶8vX:ê!«41ª+aII~X•3q∞RODÑ¨ﬂ9f5qY/≤È~p£;ıiY ∏ƒ“`ˆ˜óÃ⁄¨º]üo∆ê‰◊´‹√Ωûæx'øûKø=1è`9_“É®C:4∫ìIG*=@Ü!=êÙnî˛∆';Ô°;ÆAªèUH«∏! 3¬ëgxúxZ_ÌSJ©ÀñΩ>Ã∞»∫–À0⁄hî5Ç	±ìƒÓ$°…ï—òÊ`íÅ=Ú]Ï\¬n'TËÒÊØAB•µ´F!QÍQ)–ƒŒj¬ÅÚˆﬂ'Ï:ò ‘Æ¥È˛¬RX}8i˛ö™P∂`ã0X ´√™ Ø+◊}ëu·uˆt7âkœâAsNèÎS·mÚˇ  ˇˇÏ}_s€HíÁ{äjû∑è⁄ëhIv˜∏€„•$⁄fX"’ﬁôùDI¨HÄÄñÂøÌ€]ÃF‹^ÏElÏEﬂ≈ı≈=›Îƒnƒæ˘õÃÿ˘óYÄ†P®@˘O3lëD˝EUfVVÊ/,b!?ÀΩä4πj‘ÙÀ† › ´å{"(Ö·$#)’¿C&Ú∞sÙ*535Ω∞5πîçûÂwNMÊ1çª£ÛWÌ£¡…Ø£e/_Ú“
≥ ë*åG9HëÅªËcê8^A=fU¡7g}.0›Æﬁpì:¯ﬂ(í¢Å„›ˆ*}ÄÎÙ›•7ì;¬‚8ÂPÈ›ÙSMdôPxäÖ†©*4®*Ÿ≥¬õár«†¬ôîsUÖ©]«¡ÆåìÅ°èÔÍ∏∂´ºs/∫äÉ’Zæ/
õï`üU˝å®zN∞4UL-íŒ’fi„¥åJ9i±et_ÜmÈ=Uj2Ùç‚2∫Cπåå≤l•©ÿ†.£ZúäﬂdÒïb·ZÃ…I8«Ü.£Y
í.7(Jrü&ÒBCªRæbhTçÊ%ävù8oÎªÖ…#Û€ô¸æ◊{kìG&é:‚ñ'è ﬁ˛‰Q]∑By§ÁÊ°ıPQ˛Câ\¿¸ƒ'£™n¥ŸÉ¨T}@ì&9≤Ω·å≤}dÊÜShŒNìÒv0Íé2!„Ãıç—ëd˚Æ?úŒl˙˛G‹oF’eo*á˘Ñ{[UÈC{%!)\›@€Øm-DYŸ‹DÁ7—{Z3∫JCÔ.
îRˇ™¸—ËÜYF⁄æ˚UòLôà1„ì*íÈµ®åt˘éipﬂ∫üëå/ñ>r¨qG+£˜∂2™xó+£\Ü¸x…‹ÖªÓªa›ˇ}qﬁHçÓêe§ˇ*zpd™)"ï>3"’tn‰˝®˜Ïàd†‚"’w¢C ;ë∫âó%úLauHÏ·¸.¡∞m~1Åm≥?B¸Õ{
Â}RöLÉ?¯Ÿ+±˜	∞œNa4a≥ë4}È´jé∫æ¯*/ï…Eù¶º=dT¡DF∫+§>Oïˆ…´¨îGâåÙºLdd‰y"##oy®H{êÚZ˘êËû=f÷)…÷‰r#o™v7y3U\sdTßªéåÍ†˘òC≥¯øÔæ¯‚/èò:|1^:§O8ÏL|—(ˆˇ9 <KÕ%l7æ¨≈Ç)ÏÑcMmáÿóGSîkPàˇñ*z=aAõÈíá¸kuYÓÚqË1|7äc»ˆqÒq5Ï˜tÁgñ7Ôpå–º‹µú®é6˛»q„†x¯c∫°æ{=’êÆ1= ~≠=¬∂ÇdóÅãÃ∂¶u◊ß¿åF0®Æ©-y \◊Im·—1y9j/G∂{L_€√L«Œ%œ®ßÅÆ]{áÀkò¥sóÁ®HW|$Ã®Óû√¢Îé@”ç_M$(r⁄äƒM•ﬂ˜¨âﬁúÒ‡$è≠^«÷”ÑÅ¿ˆ˚ˆ|9c~4»{“´%›•‰”πÀGËÜÌá‹q‘À©ŸˆÒÂü„≠çÔCÌœAe˝=ıRU7ÂÛ>úl!ÛÙë^»í Ü ns¯Tãæ±PJ°3_¿ŒÏmpF‡:c{ip‹”1d0ﬂ¬¯+QÕ*[Ö—IyeÓ„$éë=x_ä§ÍVùﬁµ,<{nyw‹tëµ •™’1∞˘”≈ÜÏîÚü±ô)_kËË¸”z Æõë/ˇÿ0N∂(3˜ß™ÊHØÁüÎ√P÷â™«©äûyá;Ω ëéÌ¿Yl6[:ÂñûWŒ	f™‰î#±Jüï/*·ÆveTìrY…fÒÖ°∆%†ƒ¥,Ô¨N¶˜Ã—òﬁïT∂·ÃŒÛW*÷√ˆbA-3 fπäiÃWﬁ‘+›äÃ6F˛ö2wIí/π*ÀùbsV”M¢Uì+\TŒŸZãØ	bn‡·1ÃvÄ¯◊ˆlT`˘7∂ÙW‘”eH∫˚…|YVqπΩá•»†+ÒEÙÿC	„Lz„f√gv∑ò4∏ÿO^∆ø|í øYﬁˇÊxÈ‹–ô¬(,6%Äo6oË›6Oê∂UµìNñùA7ÍQ˛∏Ú0◊úO9îÀ
aÇ0Æ˘£L€[ﬂµçˆH()m“êÇõdÀ4Û´‡ø¢y%V’cBÁ˙@ˇ*:î5å5ËΩ/S7yÌEP∂óÀã«ãe÷nMÀK˝DÊFôæxFo…≥e∞Ù@¨ÿC≤:Dìá‰—1QØ›Ÿ[Àz∞oH)*êœ¨◊–ã ÔÚ)ë∞é˘¯äº`o«œUó‰»“Ãû—»r[m,–ÊË,ÁWs
ÀéÎbÁ·w‰ø#˙ÒßTLjcq5[æYzwº¸sLÃp¬æÄ¬ˇı˜™¬√ªkÍ]Ωvg,I^¸ø"ﬂÛØ»ˇÈ© {pZ˚~ü„~ˇóUµù—“p\ÅäJ√
∫Ò∑§èﬂB5ˇˇ‰PœÚW&˝“ú}£‚FH…w›NQÉ%°È=˜6Z∫ÒG®¡„"“—V≥PÚ‡QÊ æùÛÉX«êYµÛ¨ﬂ 9*øÕbè2N´z#¸Ï Œ˛%≥7‘q|âÎ“Aö £Ã°Ä_7®åÌ{àƒo‹P·±…4ùßÈ$ûµ"*wÊä(˜ÏïùÊzO]’r˙ä»|çß±ƒÂ¥…y,ÆOW∞À.äQcÂ“*yπ#ô8j«xFTó~-##y§qÑdÍ0Y*˜A]y∏vnæ+‚ÚñÏÔ
—^fOóÉ77à¥6√ÅHıÑõOÚh]ØÚ~|~ëP8	"OFºUw˜÷…}ø]H5òLîáîüç…‰ïÂa™–èﬂbrhœF˛pj{Û)ù¡dﬂP;¯πöK>Ë’õ MP™u≠Ωn\ oí¸ŸÖÉPxΩ7°Ô∫“‡z|„¡Ÿ˙ÍÓV+pªN–‹z˜g‰E≠ãê…W„ñ>f®R+´∑†NDVÜ<Ìl⁄‹IQ‰-]≤Q4äáÏ†«Ë‹
l`çÌx“¨KDVÿvk
vh∞m˚3udˆkúıö≈(?æL”®‡"Ÿ¸Ú˜î˜˙Ï VıÜ¥Î4Ò¿∫«Ê]ÑRºR÷ﬂã∞V,‘ja!ƒ5>+DÓã®ﬁœ”Â¸ZÍ§òxü :/Ó5x†ÌW¨…v$u)∂¶*Ø°.™ÒZ¥ vﬂ®Agn íí#˙ø2ÙäoX^òã˜ˇ∆uvÔˇ‡–ç>$°üÖ>ƒ7æñÑ<±ñ∞∂…+·E¨‡c0'Ã-”@YëΩaıIQœ∫—Ë/l:»åwÛÂÙ¯;ö⁄d¸˛'‘ª‡áhÂka éãO4¥0$ΩP|ıÕè°íÅ‘øµÉ·T=Å√)2Ê&D˘®áÑ!*¨T¨ $¬Ä4∂9ìË¨wF]Ë†·.Vß%·%°%_Ze¶™-	Tõs[ìç∏©0$?ﬂè:ø“µ¬%'Ô*$qgÕºÎ
C-¥ÛWÅ”’ÕÕª
c—àãØ°∂&(…¢Cõñ‹B‚≤Î9Ω}ˇ”tm3˘ØXGh	Ã]ìyøc_>i‘‰z|˚j¿z-c^/ÎháT®†üœ¨;X…Óm^x…˚øsÄ_	±%π|ø˙	s[ﬂIÏÇé=ÍO?˛3ÿ7∏vﬂÏº≤¶3òˇ∑Ôˇ0°ﬁ“ôlŒ`˙Yú¡ÙóNüﬁ†OáC^√˚—9qﬁˇ4ú˙xﬁi;p¶9¯l zSÒ‡¿;STÑ©vèb±–éºæ}•k4ñMc+pÑj{…¨√˘∞h'f~~≤2ÔmìΩ]{1≥´å≈∞xËy≤±'ØÀûº—Zƒ	*“Z‘/P•ª2(¶S
±≥P2=¬j°#◊rùOAˆΩ]N(«êXv⁄Kb}v˙ìÙk=ÿÅWx‘•dÑŸuÀYﬁh˛˛ß˜?⁄¿?ŸîÅ¬˘˛ßÄCí23.ÂÍmA3
#'ÕÔˇ‡Qoã`µT-‰:z€d9'á¿ú◊¶;Ôˇ˘NR÷vyªƒ}:µŸ·™RLk”Íñ|™?é(ÂrYù–›~ƒ ät¬aêŒ-€k6BvÄA Ìõ`âÒ +ùó£ñ∑∞Èêb¡pÈÙ‘∫°ñ√€·—·>kæé÷è°"ö:ˇóp≤Í
±ñ¡Im¬≠?`∏57CÀcHYZû¯πı•BïÛÒƒ6ëÀZ%Dú=Lm¢]™öG±2$YLÚgò¸Bé7öªú™iÓG&îüô‚˛õ[Ù≠c)<aﬁv8!â–7⁄{Z{∑)Èáz˜"ú$Ç7¨0â∑Ùö23`jNôÕ&ˆ‘∏>DÊAˆ»¿]êhÃÚ4}ü∫v∏ãhÂõ©ËP
l¶£üÿŒçO‹k™©mcŸ!„±X˙‘@Ô1+Õú?±ËÈA‘v	∂!éø.≠;nu£xoÔÑ‚≠ƒ€›Ëﬁ∆∫˜ÓF˜ñ}´íq˚àDÃ“L<$¸mÊÚé'’('ÚxŸî‘[:ÅÆ‡
Àß%üyiÈ÷PB¸•¶c#Sœo$‡Fn$†î>	¯Ë -¯àÁ	f˘,%†.£nÑr*∑≤BN] ¡ÿÄCWÂŒí34\Ÿà#GkBá-ód…Uÿ±VÄma«≥Ò˙s‘”4ÇtÃYq56º&\á{qÊ+SH˚Õ˝Ä@ÎªË:cws?¿{,øh_è=D«¸‹l<†C&S '§á„¿◊˙úπ7÷åŒ…KwÒ1™˛Ü∞·¬fõ‹R«!£%¸\˚±ñcÊ‚˝¬rFw‰Ü¡/A˙˘≈¨Ê'C3è`- í∂	kç®zXÎRÚlÙÅÍ‘_ÄyÆ=z√≤´∫>mÕCˆ“≤ôf≥Ò»Îqª^ÊÔÑí;Ì≈Å8–ñ¸≥PsÙ‹.Á =zÉ–«LôÓ—&>iLå£oÒÕ˚?8¸ââ∏Ô2d	ú	Íç-ﬂ•ÜÃËÑÆj≤f>ëæã1ÊSƒ˙`ÉR;t¡à‡ØÈÙ™¯8ÏfÂx‘√ádïaìp¨±‹áÒ4˛eN‚œ"’Q∆IÃ{'MZ,í’:ÆÜvWÄÓ¨©“°é¬GÎ(◊Ïc2Ñ"f6dÂ…Ë#ñö•:å=WÍ˘Óã6&ÍÒÁO•Mß¥eõ[p
œÁ|u∂≤iÌÌ~˚do+£uÈﬂàÂÅyL∑Eç?˛Û?pôÆ∂†ë:äìqoÎPŒ€ëÄGÜ;ò†”cíâD«kdéâv>_ﬁ(~wü={ÚÀ≠˚‡âmAè!>ﬁn¯¢Í˘_îë_å’f‡4[‘˚v˜"
Ùi∏4˜˘“⁄ò$˙p&â–∆.¡z∏ŸÈyßoœó3ÜbJö«tÓÓ@?ñ˛÷œ¡.±π~—∫~â≈uFëOAΩ7ˇ´5Ëœqı% F´_{hb€ ô¢ò’q-Õ¬Ÿ˚ﬁÇ"=ÂÒ˘6'ÆáÜ¨˙ã`ÿ∆y∂oﬂ∏cè⁄‘á&ê;u(Ò(h€ë· ‚q˝õk£U◊‚k#€_≠8X¥4Íi≤ö¸iíÓÂû®X{í£ç•“rx|øu:\Ùi b}‚K 7Î◊'s∫Ò“æÓÉ¶#tf˝ÉGÊª~^≠—\¯·CrztN∫ ∂†¶t BmVQŒE∑-+ñÀY‚›ZéÓ◊πê∑0t3ıÂâfÖ¨OÇ∆Vπ≠3y]ç…SãÙï‚RïS¨5OΩµ‰)©⁄I©Ïi©NMxƒŒKw±ò-ù	ir¥"Ñ¢‰e∑hÇtü«§˙‘FÖäS»vø©®Ùÿ"#j;§=ª»ö{>∑ÏŸ√^{Lqì_s8J€∂…çÂ8~ÄLSÏÖü,$ıÌ‡-ºIg \Õ	=ö¬+\v);∏Ëù^\>ªju˙˝´AÔeÁåÿØâ’Ä9ó°Ãhæm∂»—ÃZé(fEø	‹≈Åáa©L®t
©6ÖWCÊS
SCƒÓ™_v˛âígd_uË„ƒ>∂BŒë[‘0ÁS·Æ·Ò∏$Rπ°{÷Í/÷µÂS\pP%Ú˙	˙î›∂≤Ö‘áQlleó¨•ñÌü3Ìyá3Àπiûc#
º;ÉÙ¶lé#w268>¡8 ñA¨O=D˝iF3t“=?Ïµ/éØ˙ùãÔªGù-ä	L+î	≠ÿCÌ‘r@Èg>ç˙≥Zâ:è≠¿j9Ùˆ|™7˙∆N®Æ¿|Rl“-ˇ◊œmODÀß¡y(è–&è?ËWu¸÷¡4∂ÆÂ qo(Îp8√€LvÑN∞õ‡KËπ¥éìŒŸÛ¡ã´ìﬁŸÛ≠ñ?GØSÔ»–Çi““y3§LXõ$¡’Q‰ƒJ∆t:õÄb0E?iÁÄ<¯Å∂f∏ÅADèNaƒıªÇaˆ_Ù.f„,|JÎR»x‘å˜« Ös:°60°c&yZµé≥ ©πW◊RàT≥[FAkäÉê:ﬂ≥ûvé¨7r¿÷Q…$wPaÏÕZ¢i*úyQ¯†•h‚Ùñ˘íkÛyì	èÃ‡-èäD≠Æòç.ÿ∞Ïl]ôœ)∂Aï-P|ˆΩ†#Y∆îÁ‘ér≥+Ï¨ÃÖ=4©®ø»ò`láè"êÀ¡ÆiF œñÓæ—>Àò†ã/˙«r%T√lßZ‡¢?I∏,ÂÔ¢Ï≤´ÍÏﬁvó45i[lBé
Úﬁ¶Ô‘g¢CÚÕ≠>Òøﬁ}¨Ò/èòπ-Ê_Ä,&Ø¶îŒŒmL/»_gî∑Ü°ƒ†±èœ3⁄ê83˛|˘+˛mM˛Ä4·ƒø óéñªﬁ‹
ÑüÄEd∞?ê∆üÌÓè-˛H”b4˘hiƒ+Ct4âf„ôıˆ˚‘`N·!ft~MΩÒ˚ê-Ú√ì|F&X,pî¯!,≥a<¬∫∞$ﬁ`!nAíﬂÙ∆ÕƒîE˘Ì‡ÑÇF”‹j:±ñŒpJGùÒ
4≈J≈Uîw›Ÿl‡v·‰ë,Ú≈Í› ™OVc˚}VQ◊9˜`πÅæ)6…ŒX&¢âym˚6¨%ÏÉáIT1≥Ó`N1h≤%>á_$*¬÷≈úÁù˘"∏ìÚÿõ‰8 6ÈÌ¬ıÇé3Íç«p~!…æºÆôÎS?¿∆°¬DWÊ∂sx◊Û‹	+vÓ¥e]˚MLå‡ÚJA‡o4¥aı∞ƒ√û…¢æÖvû∂fPVŒ:ÿ—⁄Ú@-é¥¿Võê¥w;\Ö¨Â≤'ó8µbU†¡‰-ª4%ˆvì5ˆ◊BUc"åíÔ»rl¥∂ˆ≈ï-‘lŒÿﬂpºΩMÂÒC~»}X1AE&Z[©àŸ|’xxAıM Ì´ Ôµ‡0ˆ8UR0ö+L/‚ßBØÅƒV∆e€\≠¯ç∞µì∆%b€7Ã&¬ﬂ.{Ïo2%00¬ﬂ%E¢å‘ÉP◊c0VNÚ≥ÉÎ§’Åö¶»ÛÅu ˇ,–…ãW≤-_√"%∑õR„ÉOV}1ı*îm˛|3shsÖ5Ôsé
™9XWˇŒ?	°ÇÛÃö€≥ªP¡·ZßÆ„≤À°|5^UL#:Ïcbwn÷ÿ˚Fâ,ëP∞“’à
∫ ÛÖoœp.4¬Çà‹Òò◊úTYaPÒo8.Ω1LG˙$ı87™H˝çñnx¥ÙwŒ4ƒÅ=ß\KDßpw¬ﬂh(I^∏KO–√oOmgàd| e0•$¢⁄Óåmo?M„⁄»|UE\@–˛ºòa»ı]¨ÃÀõ/€¶†£	]ç≥XÔ¨V¸—-QÍÑs0t£è@‚^Àﬁè+{ﬂd/∏K^l'Ï,…èÒ‚jïxí§™”aˆ°Ï‹"gÙ´km…¿[{mÖ◊´ô9ËD?6G¸Îé47Ùƒï•Y`Zw0ÎµéhäF∂ü„-^R	»R![o\NΩ∑‘ŒÿÕ_Œ=\·!6¿LßØguÆd≥≥[< W¨ÑÙmJÑnD(‰áíº]ŒIßÚË‹£Åi˙Ô-x ^wj∑=…©Q˘Üp‰J\ˇr3±Ü´TÆÛHN#:Œ"º&Ì±ÁJe›¨Ãô∏ÆHR≥ïAÀ¸230Â÷≠x4±êí∞¥àbs;%UÜS}Ω©ÇnıªG˚Ö`π<_ôÅ@"L}†,G¥≤Ú3ˆ1∑ÄÆñVãÀ‰^é+â˛ñ.kö’vùÔº¶N/®Ë5_‡Ag.\ãMˇ˝Ä›jÆ!7˘7™h¥“˛à÷ÉIô¿j,TK≤œUkZLZÍkQ—›h∆FÆ¢ÑYÑ|ó8¢ª¢y°szs∑’⁄¥’
\¥5›A#eÛ∫&GhB,∏(Ø~ıäTAT©“ “`ˇ±˛ë^$R/Ú¡Ï5íÅª»◊ºê‰ﬂﬁ7„Ú√≈m∏òîãÒ5b∆«æ˛∂>⁄hÓÖì’¢áÌÁ,‹èC+ΩØœ«•¿ oú:’]E8<dˇ)«ÀöƒDZõ 5KƒöîxÔ⁄‰≤N˙•ÅO” ú´>iö˙$UYı8á ≠ÈÂÒ$Ãì0Wx}%<@zŒƒ-±D€>õ£apfÕÈAË≥±-˛¿íú'qù¿].§9
ù˝˚˘‰ƒu&)£øÌ∑óÅãÌ∂áË'ä…u‹µ–L;∂f>M÷⁄vFÁ÷“G/ìÇÍ∑û1ÍÊtñKÊ&}êpTG˜ Xft÷z?5yÊ+ß>∆ÏïÙ¸ÇZ~Ñ˜?˘4Û(è-ÂN˘kÖ˛¥Nˇ9‚tú¿Óí#.Äå˙w‚N¸–iG˙Üäj≠^…Ø†äN¢¨vùgÆ7§GËπ ª∏ÅΩlœa•‚k…{Æ”çÓ7∏vﬂ‡É·™!©∆≈ó<tf-|:
ﬂµ˙éf˜$qª3uoè)lıYA1∂‹2%¸ëaAPíÈËÿûÿAAìñÄoÑ1iÆYE§f·ÅWG´Çzà√ÿ√$k∫Ó£¿Ÿ?íÓòSÍ—ˇËã≈†3ÃFhÜ†yÖ∞ÜÈ6ÅçGpLÅ=ß¿…],sk√ó¯0ô+;ˇ9úå´WXÙÍíÅ">£hÒŒv˛$ˇ€>NnÚ÷Îÿ—@¨õÛÉ™˘à^c‰‘ªö¨ﬁÁç‹>LgxÖõlr+3<vâõ\≠´JSŒYÈ∫RÆX˘?3é6EúÀf‡-3?!›∏®˝†ØPËﬁ‡∑ÄÀYàG≥ªõï$…ˇ‚"$û"ç8$sè;‹C∂laõ†]’l˜„s'p#ı∂˝ã˛ÇÇ¢t?˝ÙWMÓ◊-œ,/‡—Q[Ï›wﬂA¸S¸Ï´¡“sÄÒA-8r-P\ﬂÖÕfòHlJ^ªòºû4)<2¥Òåé;ëo-⁄¡õí=µE,èí)≈Ä§!àHûÖÕ≠[Üö^”ãœÌ°Á≤÷æåªìç¬Zò¿<F"ä0ßL–Q¯`˚Ú∏€Cûû∆1s:≤≠V[h —∫ÿÚSt4Ê®Á§gU+TáEQ8Ëï3ºA<*\Ï∏πö+ÖÄÒô¡›Ç945,˚ä·n4»Ô~GÚ∆˝’W⁄Ï/-gHÛb‰±i∫µãt‡2E2ÂÁ°Ê§ö?>áíŸÑeﬁ>y’˛uüx†Hr	êﬁZ$p…di1@W/X}Îz7>Üä¢§°Œk€sô˘
6ÖÌgKÊ=ÁsîX·÷b¡Ñíø•Z⁄úS«Õó≤!>ïÈwk<8†ÈØÂaãÔ2õÛ$Rá’Ï^ˇ-p|r@¬ZpË'—œŸ^πØ©ÁŸ#JP9GHFkt*ÔHs39ïÓpâ≤4Õ≤áÄTátb;LeoV¢SÍbÓsEt‘ÙÊ˛ËÄ<õπV†’‡dú£• ªFÕkˆ∫|P4@‹iıö9Ì™˙[XÅÁ¡ôé‚ø°ë¸∆ù≥bûÆ:©ïÒÃ∆èe≤∏d«"“tÂNÌ7uŸÕ’*Á_¥⁄GÉnÔÏÍ¢s‘{~÷˝MÁ™ﬁÈΩÿj¡NúôÑ‘.ñAÁÏ˝lùø\¥ØN⁄gœ/€œ;WßΩ„Œ…6…<ñ|‡ÍŸEßsı¨wq™Å™€ÖmÆÕPîÉ°ı†πØ⁄&6)H$b“ﬁy˚b–mü¿‰ˆ/O˝m&ÛÍäÈDZq∑ß\LpûÉ¸≈kVV	í-í(Ê#[¢dÜ›˙h/	¬˛≠¥‹Gsú$Í3’ñïz î(v∂eèùß3&|Û—ˆ¬≠&ü!~‹¡ö\ﬂ|⁄€û‡`íOHCûÄêÀƒ®^°Cè/<:∂ﬂÑ'†ÍöS„O?˛˜ˇ˝ÔˇÚ˜§¡èK9éZ_.F˜;O5Ô∏,D%D„ÔËÉ’0£¸ê*˜Ó∑é‰iÖesejÜ21ß~(Ûh#`6f#`FvÊ&
rëúY$>ÈıËC4)˛ã<6Ùô/: $?%ﬁ!$'îftíINÓz∂˜=n„um◊∂•‚©∑YeK	R“æ%7 F2wQh#K÷!Z`Ÿ” a"˚tmV©ô⁄≠F´h—| õU∂Î…Ê.V˚k+˜πıœΩÀ}Çw
5-a¸ÇÇMÊ 6®ï∫YÆWRWôñ∂Ié÷;∫Á c˜Ô†ºÁ‚Lê±ÁŒ… Ω)Í∑`	ƒı-È%4ﬁ>1S˜‘Ú≈jÑ‚·Olë¡∫ˇ∏vªÔKOj·°,q…/
Ôcˇ€~xÛæZvÈ*¥≈\u~QkøÎnúÙ≥ÚÕ≥ñm/ûù‘Ì·*ÈW∏Ã`†ô{÷û¯ôÎµ√g∏Œ∂Úà…V¬OóLA?A˙$JÖ_˚≠0^p’µpD,t€ì°â·€|âÁdIÓD"É˘ «a≥o<ËRt)
Ï	¬wQoÃº†Êº±M4 ª27Œ¢ã	í$fÚ¡´ $≠Û=ÆŒBN«†73õK»ui∑nmg‰ﬁ∂xcÁÒ”IÔ•o∏Á‡]˙9\Ñ7O€Ú«òSo¯®Ç'\T"Z˙Ù|fe™äÃK/zò(√\£„Ò$8nÛÍ2#~ÎÒ7ﬂV≠» –Eà$[Qû'äÂùƒç8¢tA|ºzA#–‹^&¯∏À¸N&€@‡¯Xª™§û^Ÿ–µŒåN`’Ù©ˆòì7ÛL	›Ω0:ùÃ-g…nSmÉ› À·ÕCÿ3
Kãy;qÁÜÀn¢zÑ¸Õss›W1Y/1Ì@Q›\}‡.:Né]IÊIæMxBÖ[9Gˇ}¸8ˇÅå€qPı69≤Ω·å2?∑de2ù»úaNeüïO)íXπÒ=ıÆaß#k
< ã~EDƒ%i]!“≤~hiÍPœ!·"•¬ÑS-($qQEı’ﬂò^Zá,¯≥èbA#1eªwõãì‹‡ƒ∏8_k’±ﬁêÚLDj p’⁄{IÔÃ)K5tÏﬁ*≤∫»◊"IOƒ•'ÜH√‡$>ÊÖÆõáäd˚oíÛJÆèdAˇÇ;˚Ê0Æ®Cx•ii¸cà˘UÃ¶–M8™Kﬁ‚œƒcb~_aﬁ¥`ÈgJÂMt]ìù3[)! A# &í^VÑÂMùìŒ≥ﬁY∑£XÃ%‚fkä Ó√©[ˆ∏dL∞2,*π_´£Õê÷	å§ù DVXÎçÛ£h¬’8…V,[i´’ä.“ûS·Ωˇ	T√Ÿ˚üñ„@wm»π¸/Un¸ÈPäA"…ˆ¸©=¡2å∑}ËIMlgÏ¬∂oø∂@‰ËˇÒ‡ÙËˇ–g+õÂ$ﬂkW$≥@™ãa ôpÈ}u¢J”ê9]Lˆl^Nœº∏ıDay2œŒ˛∑èãÖÙ= 	È”ÍeºFæz’‘_Pè@ÉÁ¨cæ
	-ê[ï8Ñáµ†èÉÃ≠∞0ÛKUÏ˙íÊ!’ê8I'oåyö‰$.ˆE(„Hπ˘YPw∫#âFS<$ï"Ã‡√B–åà‰ ¿B–≈•G¸¢√Xö4giRœú˙∆◊,≥Õ†›¥ó§âíA'ïMIl$C])¢ÚJèXC${ãÅW"J+A{*%(¢ÇEØ¸U°qd±«aˆ]`¶◊÷å‰98lnµ˘⁄m¯x?ËEe⁄	{Ñê°ñ=f7 àç¸k:C4Æ	Ω¶”¬
xåà6ò®”èët’´\Õû—Ví˜’(ã—”ÂïÒ
8B8ÀLvÑ{â˝≠ìähnΩ9A*\ÕŸ%A`çg,PõËÖ[ù®:æ≠à|fÌ ^XTæYIí  "X èﬁ<&œ={ƒ‘
DßÕ◊)—–Å˛¿‡ùm?Ëç’´:|¶±◊ÿ&ç}¸ÁQ£J),Úü˛ˇ˘F∑»/ÒÈ'¯œ∑∫E˛üﬁ≈˛C£‘ÈÙH∫∏"ÉaD„*–$¬óçPa»˚ÅÄ>êÜñQ°ä)Lysíåj“#“ıCÉ˘fkÑLWgæ"*Ñ/IS8ì4q≈ÛÎ˝B≈SZò‡î⁄¬*§«∏GèÔ}˝µ^ä¨GÖhœ*√vÒâ•*“ı@M‚8òèõ¨©øﬁÕASWëûó¶H—	∏‘»ÃìõÍÌ⁄4	Î ∏ºaì¶ÄŸ ¶Wûﬁ…#M° »ç˘Ùäö˝mÕ>]Cµ”ÖH¶â7”§øgÙˆäF≤¥ígMı…‚Cz˘q"¥Ë‰>ùUÓ’ô2D¬%W∆‹!2G8DêÚpXÇ∏Ö®<F(bH:[P[K©3rDFöèZø73íæqIe :¶p‘◊Ä ã®BN‰àÚ3´ìﬁâ§˜«‚9yˇ_Ç£¥üƒ#“⁄CT˘Îøn(cª>§÷2∞«K8:.g>˜¬ÇX_ŒÄÕYÏz%∑¯z–¸¯jŸ-6¥≤ßUwû	Ÿ∫∏ µ/øtH÷Vdﬂ)Ø≠¨#Ézí-ú3¿
¡P5Ez	[j.˘Z√Ùn∏cër(ﬁ\Xµ∂ΩQ@ˆÌçóŒ5≈\‡òTk@Ì√"Û¥≠éFf√ ∑Ú˚≈Üi©Öã!3pM´)ÃÉéÅ‘kÒú˙¡“K{ìÊëÎ 6Y‰^jñÖ⁄Y¢SÀù∞`Ïˇf„∑ø˝Ba∑í’√é>sÕˇN¨¥5∂Å≈âÛ4ƒD2R√ü‰V˙îyÂZŒN.]ø"L8M®!Øl§ ß…,oxD⁄˘√ÛÇî÷úO<›O¢ùW¸%É9áwçåa[XVÊ∆ãöÚäGîüàZË2y lÑ(±8˘”èˇüıúŸuHåb¯[PuX§`´TÜÒ¬±=£SPGÌ9â“åÁÁØkÄ˙gnìƒ‚ÖÉÂ≈ØÜçLÀâ”äI˛¯OˇÛﬂˇÂÔÎm]6É≤˜”kÑÃ6’ÿß±`†éT_∆ñˆ≠ƒÃ~≈ΩP6#M–,¨ºãõ€ÍIüΩá‰õ]e)x<[ÍœäJ10«rÑ£ôD	d¬êËÁùã”ˆŸØ∑yñôñcÜemÛ∑YìÎ‘b„Œ}TÍiM˘n
VoïUöóË>¢BôŸı]Åü!“=9{¡6ãú¸A±≥'Aærüs Gò£˙|Úeuï˝∆0“¨ä%U—:Ùç,n©R¿Øµ.|‡Ÿ≤`1©§^±ÕÙÛ≤´√mL∆åT£çò{´V4Ø‹-Á-ºam'B§Ú∆¢àÚ≠Œz”éd`u÷Y‰çŒé¡§KCë“òÙf%ÛdjM”Xdu/õúÄÍ˚*6µ~—mœêÂWÍ2‚É¿áP·w)0[ï%	Hı8JZ0Âl'tweÆÖà¥$ç÷˚ à#∞Ωìwû=›EÅUÃ÷ J4≠!0±ßı§jl)ônæ<≠cÓC‡∂@orö¬4¨Ï≠—ıO{ÈYM’Ï|«s¶Ñâ∞ã gÁ,,ø˛e∆åTü«@„™íøÎÑŸ\‚ß∑#s9ªÓ≥—ÑQØ&è¸È«ˇˆ¢ËΩ’∑‘v∆ÔˇÕLù˙˘jføhA¢æ˚ô\.BQÎì>F$Õ≥ﬁ+r~“>ÍìÛãﬁi˜¨s68˘5i»‡EázÁ‰∞sœ∫ßùã/≥√˚–—…Èt∆9^u=Ä3µ®·ºìôÖº)ùFHÖ€QΩ∂êºtÔ“yYª-$LŸ;‡Kqπ`([ljîE◊íÄ2"]MI–ÜµŸIz$cü5¡Qu„™ômû}˚ÀG{ﬂlmm„À˙ﬁæF¨'‚≤e•’~§¯	˛%zœtø‘Xø´—3©~ŒôT˛≈r;˙M=?ú˚W]ÿ¢®™ª¥ó„ù0,¨Ø$MŸ»îîGz3éTUb"U.I_ä¨"©Ÿßö3ü‘7†~ÃdΩQóïØ5¿>Ê÷èfÒH4Q|.ÏôÀ¡mˆ¨∫πœB*|ƒ“ <0sipAıtﬂ˙E¿ìœ_†÷Xó¯p\_ª,R("Ù}©„ÇûÀ†ñˆÈX´ê>·>†∫∂˜¨t	7Ù'+]Ã~)tÆK!ƒlúQ‚—Q|J.0√ØM˙î∞ÃkJ©rÁÆãcI£mZ6/ïEKe±rP*˙¢§1RVÑT•Dáôÿ®Ô¢Ô£™≈$∆‡≠U’DÇ∂ßíi DŒ—ìE"‘˙5µú‡÷ı0Œ °ÜÌ⁄§ˇ”˘ß≈ç†°[Nso'∞dûJæ".OêMFÓp9œM0cê©^9ÓTBÓf√±á”‡äz≈?
|ÒZ$Õƒv¨∑Cs©4TÖ≈≠Î˛rÃìo5ÚßNÌèıåÖ>0Z°[b≠∏Ω0oñ¯‹où—œÍ0ıqÀÍπ*>™Œf4XxÆ;F'I‡ñNh.á/»YLYùÏI7> Âsµ~wﬁ–·2@Uf¬Bﬂ0uØ≥≥\<¥‡|=‚é`0™	á‡h¯Ú/ò^3ÕA°1T’>ŸY(è\wFg
Oá
7—∆hÑ—ıtÒ’\|o±RâTª“}Aùw∆5ﬁÎéÎ¿?˝¯?∆|=ÀÙMÂ¢Æ*SüDÃç»\"fø…|õhggá∞E‰¢w9Ëû=ôw⁄=∫ËùøËùu»QÔlp—;ÈìÊY{–˝æC¯◊˝¡ØO:[X8SßÍ2–R\ﬁ≥4¥gIXO	§ßRºLøí@ûÚuT◊µÍ˙.Eı–üŸ“€Ètœ˙ÉŒ……ÂŸÛŒ˘”èøˇøıb@ó€ß‚˛‹S∫h ﬂ∫¸Õ`®G˝c	¬¬8—O∏î)+d[íµ≈Û¡zÂ≠§K?myÏßßq‰^®±3ß–ÚëŒUß}qﬁÌuÙ¨îdU∞bµqb1ªa	Ùñ+'GÆn ÓŸ*Sk∑”◊Ü,<ä4^ºˇÉÆ‡¯f“bˇº”~ŸπH7¯=Úuz	<Øq∫§ø¿$=ı∂|xrŸÙzÉÈ∂Q…\7òB„Òﬂu∂¸™{—9æz—i˜;ÉtÎãÏSˇRB¯Â˝í˚_:ú[ì}≥MlË≈6ôY◊t∂UÑÓ∆=Ç˚8Fûª8±ùø„ç÷‡/TòFD√ÖX˘£^!RùX‰ï5.µòÏùJ£©‹fæ;Ê«”›÷Õ;áR‡p¶πﬁX˙T∂îŸk>¬®˜Ä/ÕãDΩ±D˙H=ë.⁄ÿÉ’mÑg±D°ÚÉ¢.ô^?=%ÌjAòTA•Ìe<P[,oﬂ¬B©nÇ Q∏u˜q≥ ò¶¡ñì¡«-È,}Ì<Õô!‘±Hπ79Ydt?◊†A‡_ÉºìZwñH"íxRÿ’v6NúJ“ΩMùRx_Ö/OÈ»^Œ◊vì°kT@í~ylø∂ëGiƒËÔÂÅÏIø,“,™úÍCP.“kXf4„ìÜv§z£}ÉÍüO¢§ö&0ç◊ÙçîÇﬁy9zZv&Ô”π≠tµ/°b£nÀIB∏hÒD¯ùïïÂSi¿£Ù_†Øã5gwr”Ä+≤ÄóÅ˘b}|mŸ3‘:Omæu≤ô9Œâb·Dq ,ﬂ©–7ûîû˝“ÃAÎygpu‹¡±ˆØ∫gÁóÉæöÖç·Vÿ¨Ú/tP≠˘ì≠ Ãù,ÈÔo◊ª≠¡Øœ·†yŸ=tœÆNªG‰wø[G—Iˆ™‘[Oâ#Îzö∏ÏF‘*Fêr@AòX«b2â:ÔbÁæ_w"V	Õ/wn∂?ú'\ëÛŒ':öÑyV"ÃŸÚ,˙ª’©cÀﬁ&Ë (ùXoÔ¥ÇÕ/å πÛ§”9Ñ¿ú˚…U∏Mn(Ç¨¸ ?∂Ïy∑eƒ£ê∏çÜo≤0HàÎ}—Vt˝÷·“ûçZﬂw.˙›ﬁY´¸òÁÄ¸jµ]”œ\ıé;˝VﬂÂ+¡ŒáÓ|æt@Î¡”Ô‹S ∞àêcÿzÆø∆ÿ[∑S
õM`K¶He∆åxÁWiªòô]≤Óﬁ%yx…ﬁ%*1Í!{_%[≠ƒ xJW^»®.d4$Ì› ◊&B†+MïÂj®î¿Ki`˛Ä¡«rIé≤ÃæXŸ’◊”`f©G∆Ùı4'®.¨1¯\¢°hÉÑ/}·π£eàb∏û¨π%⁄Ætà?ƒ‹Û›V„˛óØ6¶ Reo˛º;xMØ¯D]S™(1%fú¥Æµü≈ª(ï‰•tÊ}ë¥∆Zh≤⁄”5Yâî˜‚å*2ú‹*Ÿs>ÑŒ$ª•˛r§œ∆¿œé≤˙T(4Ã!fë¢A,{÷:q'≠Q≥—s&.î¨xA‰N0≥XübË‹|i•Iv¯Ä<‡¿9GT
\5"ë\Â†^ˆe!%ﬁëÌ˜‘∫°ﬁØ–{hÒg˘TK’nû…¨ÑŸ”9ûÖªÏûH@I0HpÜ¥ÿ§5‰&A2Y)fWMH™Ë8(ñ U%Q	R‚
 TÑò5ïèI£ˇÇêÙü4œZe¨äµw”ó"^ ≠w:ÕlÊ≤“•lËi™À_‡ €háffJ&≤∆ 	‘A=áú¬1’√$•èHÁÕçÑ®¶ „UëVXˆÍ_äs¸Â+¯ Àu√›'πx©
Gyún!zkéíö‹∫aµk~û∏Pû†Íâ:"P·]°u≈¢-Â9ÏNXñ[)Ÿ≥dÌóŒ˜èª∑é%Ö_9î’≤.•Òy2Ç√ØˆenÊ≈4é›õ%˘ä†ûÏÉrÎ¬\òW‚Dÿk¯Ã69s§?Lm'ÒêÃıØ0J!≠…'TKTd&ÚcNh÷Ó<ﬂaå:¢¬⁄ŒœîÆ˜⁄)òpåB–≤üAxﬁjXˆïÖë1¥⁄Ê?‘¯4„ç45•Ú∏Â∆óhÑÒ≤
‰q&ªáﬂ>ŸÀØòh‘®2É¢™Ùq^$äÓLéï˘∑¯@«1¥¥o/¯◊>¢îÙUæÎ≤9ﬂêÃÆ∂_vwéª/ÌiøtøVˇ”?ñä{nvw≠wé©;óé∏Ÿm}]wíF0ˇn˛ èJ?˜‹9hÌ¿ÃéÌa¿LÖd‡N&˘Á§∫¢Ã√É-oŸôËÿK¸¿]ÙîãÆ◊µçÑ,ﬁÄÀ∂ÿ¯8óG”……∏c{k8•√õ>ùçœ©7∑YÙÚ*WSdK<µ{L˝†µàj]téz«WÃ´x3%!ÏW6KŸbﬁ:á-zsdN<Ô\úv˚Ã¨¸¸¢}6ËÎ⁄ü|Ù‘öT£âEö€√’,ùXKCuZ3ˆG≥æπ*}‹Ø!^x¨ê ¡ÀΩ2ºÇ!ñÄgß5¯u·Õ—∞Ùò¬SRô1›g}‚Lø€zR◊bw¿bXìf√Â˜'Wãà_çÿL“´Î¿Q≈ï e˙˙Q~?MçªZÑô¸øôeüB?öπ>ïY&¥Û’”ÀweE4¯*¡5*ïô±5éÌ¯®ÓbAùÿÓ ﬂ2¸Y.ﬂò°·˜?≠ÅAƒP®¶w÷™?Êh W±ˆY %†/ÄÔ⁄Û,Tn0›%∞7ÉZ≠˛Ä¡æÈ€vÑ”2±ÈÉL’@PS¡~ÁÏŸ·9AG.oÑt90L~ÉYõô]ˇµÌ€xs0Z¢£ÀÙÜXjñä  ƒó~1∑‚≥,–+\lÉï;r‹ïÑ›`MÈ@o ô¿f‰sÈOXÍõ€5W⁄:¥Ü§.ÄÜ˚PÍı
p®àjµ]ûwŒ∫gœz‰+Ú¢78Ï˝9n:g‰è˜Ø,Ÿq	[íˆ,‘á™§E°çﬁëJPôÙ√\8ÃÄè\ﬂZ,f∞=AF(Õ˘°làºÈƒna™⁄{n‚ZSVöEæpó^$k0SIÚëé3(lld›Em}ô¨È•7+∆úAM=/˛SVydy£‚µ
ÒÿL0Ñ⁄Ä≥˜“"ÈÂ,Õi„ëV˜„¢Ì ¨ º≥g Œ˛¡ìóÈ!∆~í…ÀÃ\∆2 ©/0Ωß∏∆k‡—W⁄],åôˆb◊’M¿öô˘e˘aLˆÖ≈‰5ï2-{…?A≠√√ÖÃJ`¸Ów1C7ıπç$œ`e2àÎ-„øﬁx0âIBü1wË#.l¸K¸¿Ûƒü⁄¬)\ÃoÜh‰@shÏ˚ÍÏM(kÕ˚“xœÃyˆÍrÍÈG∑ Ö∑
çÁñÛ6†6∞c≥Ú°¯˜√7ŒGI˝¶\0´˘~Fäˆtá#'_[ﬁµLƒÃyw∫˘ Z±:±æiíÔÙàf|∫¢-Íä<*Ô⁄©Râπ+!≥ëOöœlXA€§≥ÉhÆ€‰π˘¸¡Í¿åú’Àπê‘u¥EvÎœCºÙtÓ“äÛ&ùl>1ç,∏Ù·‡˚€
µIíƒ@íyªHÁYc¢’€ƒÄ?Èk ëØ¯vˆ‰¨√π¥¸ïê÷8?©=≥éY2ò°˚Å„*H∂ñ‡%tºt√I*qíŒú…£Oöçp°∫·#JÒ∂_™Œ—3knœÓ¬9‚ZßÆ„≤%˚…≤‹ø‘Ú]g√O*ÒìÅ5˘ƒπ…ﬂ¬åNØãÈÈŒí°¥Üoü¶§àw1˜≈_ÚQùO,?`7Êdj˚ÅÎ›1pu“|¡>1ü*ˆ+&6Ú%iÈ#BEX√â;I‡‚yt˚„,óÌË=1pı¢Õù,◊NñÂQVˇŸª‡üZ¡päv!;h-8®{¢Í©m˘Ø˚yuòWÉ®B£ÜXÛ<X7¥π_Í÷
ôø0w»Ù›@é≠–àÃÓ/Oh6†‰9ıﬁ˚üÜSﬂ·aPE(òHU˘íƒ~õã.[ÂFW|++Ï¯ô;—Å#cô¬x¶±S`&4Ë≠æhB=≠Å‚ÌØˆéT	’€‡
,Q.Ì|íõÁ:bàbﬂéi⁄!©ÙnXtU#ú ÌLá4∏•‘@»≠b¢‚EjD&ó•/†∆Û‡sT‚ﬁãŸ∑!úOMˆhmù	IˇImÏˇà™S!Â˙≠h»â˙n"–õV	Uâ§ÒvwÚ}3¿√˚∑J˜.‚º¬ÈG≠“ÃõW%lÅoç16¢
J˘ßÈ˛Ømêÿ	πJµ”{DÂoÿ√S‚ÉíùyWÀızôK∑Ì/‚∫ô†·˚rx∂‘¶~€‡/	{Ó∑çZ_PÆÜº≠4æˇwÒ–l¬åå'›Ô;;«Ωóóßù≥A{–ÌùëØ»´nÁ∏sÒ}Ô‚§˝ºCö«óòÆÒ®}r≤…ƒ®·æøŒ|å&·ræZE…Ç)¿˜¯ i’πi•_√ÊŸmëé7°◊éÌìıy&Xü4√≥méÔI∏SL=ÍI’!ÈoŸ:
s◊|‹Ÿ:B≠O7£b# ê~≈å“‘˜iÉ.it„èÍÒ≠*∞Æ˝·t∂Ù}^A;˛®[¡µùt7^¬w˙R8ïŒ˜Œ
ü·üæ+,ç8ñ√)ΩrñÛ9ıxÈg¸;r≈Î|KÖÜÉ*âxêjDá,“ô¢’&&aº°w∫…ë2	|ù∞zƒÄZµ™öK≠rP∫X∑πü{∑!¸ÖfBDV¶&îhU≤Ωy˛I¸Îp°KaBõ„AÁè™~ Ë ‡œf }&ÆÆÏ£)ng∂∂‡nc≠,_ˇÒ•3,uchh5…KzßX9˙ïó∂üTã‰ähnΩ9A/>‘TÙKπ∞0∆3˜6<Üˆ¬è≠Œ∏êo˚¸ÍS˛≠Ùk”õ¥<ÌxØ≈/Í™1∑¡¯d8Öy˘ı„tè7 ±æ1M›¯Ï%úâ¸‡Ü«≥Èêá/…≥ÔãzFßEí  $æ˘B˝∫K‰⁄»53á;E%ﬁ.ÁÑÂË"cœöPE⁄é”õX≤˜â®∑’êı¬ı˘ZÆWUµ]qYñÒçrjî	z©K>êäz©J>ú¶zØÚPYMèû≈Q≥{%ñƒ≤€(¨µl÷ÊRz¨!ÿ~Â¿˚V$ëäÑﬂ˛œJ¯ç<w±~9e6¬o#¸b⁄ø4}n¬owc≈I“'e≈ŸoEiÓÀXÛQË˙é•fŒ§•53üÙî3∫é+zñ,§˙¿ŸµK©∑õÚG-o‰∫}ÖÎŒ2ÅT:OAû&¶Å^◊ëÕ†™¿âñ®aˇ  ˇˇÏ=€r€HvÔ˛ä÷‘5ë`]|U≈ÒRds-ëIŸÎΩDëMk‡†dçk™Úíá§*ïJ%y»>ÏV™6ï_»√º˘Oˆ≤üês∫qi ›@§mÕî˙¡ÅÓF_Nü>˜SJΩlk€◊ ÛY#>ΩX¥c’ã•VrÀ€√^,ü/ûΩX™≈∂KÌ,£ü)Ê}j¨˙‡•USØñ‹<¸I÷<º‹ODgcı√ú≠'Rwof›∞¸aï∏Ä∫—–
„vkD”◊˚ä⁄IWœÑøºÜ>É¢åÊçaº	ÈùÂ≠¬À 
Ù>SOñ∑íØƒ≥ÜÑÆªf¿çq˚é1 RI$#Ã2êA°}vÒ‘]á%'‡º;˙S8¿<Ñ?ã[.f@ÛLae‰~‡@÷à2 ]¸”@Ékfá¿≤¬⁄ZÅoFCÀ˙˛Ad}/⁄Ün•¡≤*£DÈ)Îtµ-z8∂=[…»˛vOCAù°Ê¿á∆<Ã<∫g‰ #âp™ëÀƒ-}ﬁÅ$7h˘qY:´ˆ∂ÁQRÛAXµè{<IOÍ£Ï’“Û]ı¿äØŒ‚Ó2¶î$Õ¯Ö=Éº±)ÙtÂz3∏ƒIÛÿùÕ‹Î≠≥Èy‘ß¡m¥IêïSÓ‡˝”∑Y˝“µŸõFv“dèÇq©âÒŒò78uΩÀréù9ØΩÿná“Í7Qıü[Eë,ÙÊ ´©¥¬=˛D÷ ~¯¥;ô∞ØÑq¥˝;ÎcﬁœùéÎßß„‚π9ƒﬂY"áeΩñ»wÜ%oıΩFÛOrè SGÌJ¬$]Ûøæø]ﬂª˜≥C∆ü∞Dì%:2ç0oﬁ%jÜ#è“PB∆-ÍXs∫Ox¥ÎMÒh$}s»#wFÔêºk§*p¥˙=Ú¥æ‰µÎ¥ˇ#>7Òb$géDoéËy,Ÿ´S€±Á `‚ª{—)À±mK€∏∂ù±{mÛÊ^&G>b˚(gÏ”ﬂ-©0>˙˛∑§ç˛Yæ}E…YpÒdÃ+˙~Dπ≤-¬s^8NÛp<‰€˚"áº‹"*Â&≥√Í≈µ”8 ¸r◊9ÄõÈ}Ï]…2'2Æ«≤ÃáÍ˜!Ø
‹Zof<d\ﬂÑI.Yed:pÀg4†‰Õ8}w¬·Rƒk9¬BáÄà(8D9¥xóKOülM/ÅTö⁄#ﬂ8ñ˛‘¨1¨Ó¸êµª∆\ø]. À„ΩÉ›áõ∏dÊå^ZpC\oÜ`>¶tAÄ≤Ù¨Ÿò¯∞ﬁî†ÓA∑ÁÉù›ms#√=Ò‹%Ü1õxpà’™·‚:0utæ=ëVì¯>Áw&∫Tw”bÏÏMcèŒjt\lá•ZCqÊåNÇTuî5´≤('ŸììÛüüß.ŸjXxâ7ì´|Ë.XÊ˘œá˝åÏri>”¢ê¢ƒ°∫°≠X¨e:s˝†¸Â2a¢˚#˜⁄π∂<ÖqàTdﬂ‡+é¬fìòà)ØßNó)_≈Ïò/ÿ7‚√dmº~Äóc1ˆæ!C;»‰mR≈[8√"Ôe\K¶≥^ÏxˇZp˜4ü¨Nj:2ŒÜ˝ÓiˇÏòÕÛ∏€iõäm≠Ü YDââTAxñd©gOnO†É]u†ÉÚ'µ≥ÑÊ`b˙ÂÃ«1Ÿ!â«64Wëâú	ŸÄ·H¯vxå£¿öÌ‹À&c#4	⁄»áIØ]ã°≠ã…'∞‹R«3◊R@ ∂Ä˘u®+AEÉ°Xﬁ%¢z;∆∂¢ˇ$||∞†#dl¬QıÈÇZ≈jå€B;f∏‘‹Ÿﬁﬁﬁ$@ÕÚùGïÅÂôÏg—≠ æ˚ Ñ Ò£OÅÈÙÂµ¢ªÙ>∞µŒ⁄ÚªS~‰ÕvÁÖ˘“Ïô}“Í¿Ÿg™†ÚC/ª±í±›J<¿ÉCÈaÇb˛¥B»ì‚'˘ãÎ‘èaç‚ª+LOÑÎ¬˝ë÷ q˙2˘îYπÆÄ˜≤∆+PÎ˛ªpÅ*úCﬂèÙÆ@üπDA∏$Ô"Dùó»…ê)p\≤µekÄfC∞ëú ‰˝Ù8‚¨Éü>‘EÇ™äk∆Çª),xl˘Áb  Ÿvæ2d€°Ésîo¡û6´\ã–é‰aq*¯òg=±nÄT`ì˝tœ˛ÿ‰ˇΩç~Wç”•uo?(d9“˝FÅºÍÖÚb˙]ÕæcFRæD*—i°£ˆ÷b©õ£[!√HU≠¥A≈<aæoæIª“pÎwûé±&?¬BÓR≠”J;Ü•é∫™¬Œ±ÍEªßπòü>}6
}uL◊◊ò>˚xÁÒnkïÙŸ ÌÏä¢„Î=≠'Öµè’ÈmAÿnÿíü| ç3ÁÇæ≥h/„Ä)å™dfOÎ†∂e$≥˘>,m˛ñ•
’ºGQ}\2v‹√9∞øãîk
`+JÖú2∆›Uƒx¨"´B”∫»AÑt¬@q˙ í≈ÛèTJﬁ5¿L6ÎQ! î'd™k⁄<ZÓëê—˝0…ÒË%·}kKy∞h≈MJΩ 5áVÄ,ıEà(F!X™¢,ïQ	A£sÖ‡íDÓ^¥EΩ-§¶*ìØ+ÿ›T¢gwJùíJ˘å∫Ÿ?‰ó Ùõr…®G‹Ü‚òÍ“óı:02ôﬁã˛YÁ®≤Ôb5{ﬂäÿ"*+PŸn™€ñhÀÒƒ≤J˙“≤n+@
’r^˝Ï[_o◊ãI	±‘∫¢Ú¯·2a3Î∫K
@Ì–Ú
ºZb£¨ñ∏8¿èCÓÊüjY˝o-ZÄ^8Úáö‹ç`xÓ;*èW˚ë6jKˇùÌb+¿∫Ç néºm<yXb˛™∏ñ‘Ã∞∆-TUHíHvvÀm"´_qµ!’ÓæNwÿ˛•Ÿ!˜…‡Ì`hûn[/ÎÒ‡◊rUπxQ?j»-º5í=’π˝q\Ü◊õ[öÜºYﬂ∑üƒuò˛ï˙yˇ>‹
L´K˚Ù∑'·÷bhH◊6†˙≥∫®¥FÃJs0C¨TWRGì⁄∫K>™B¶FvÖñ310€Â?—ù3¸!AJ´iY!0ãB€ßc⁄ÒF[’<°âï]—˝µ≈vj~uL+µQ
õ≈®à∂ã·rîpêË‚Ω∆d∑ü,$ºD˜m¢HPYT"É∏‘˛q	≈Íz∂°y¯”f˚MµJ¥à6(Wê¨¶ë[?∂.ftÍî˙”È⁄@F•ñ“ä≠˝û^d√sLYπ≥WDã™úZeOı“i¨W^ ¬ÌÌ1˚´$Jñ'c˘mÄAWv?m-[Åªe1Ø2ÑwÔH≥è”»w©îÛU≤@∏fœŒvâ	B$ï(´∑bÆ^n	Q)iXë…tù/h§ˇ-TGãwæˆ∞›ú!°¬˜~4•W†(9s^¬”≠Î^ƒR%C⁄∫ÁäF—qªåQ~Ω%∂k=‰[AÙÃd√çì äÛ£ˆ‹P}è6e‚Á4Ã≈¢í5+∞åƒí∂€+©ΩäYÆï!ô¶9mT‘ñdå0.ÊHJtK—>⁄∏)kueÁ£nÓ–{¸æÃﬂõÅ	¢µ◊L¸√¸2°Àè∏p£†,Ì°πá&KùT¯Ö’çJò€»ŸBÌ4"ñµ⁄ñ»tI—j≠br¢∞ KuûVÖ˘9¬üì:ìõ?^YÒ±wqÌNàÊKv˛H u'ÕÌâ ˝ôe&≥ﬁ≥œŸpFú'{3*[å)"7‘ºü∏pÒü∆hÈy∞ç•Í¨íkhå∞#Ñ˚Ï( p{Ôõ™5∫E÷|.ﬂÅH∫ÖÁÄ¡∂Ñ›1ı3åNb±?1á∂'Â–∆ı∞Ã˝
TØe◊¶À“∂ù≈2h¢Î¨é˙òÄ"Ê»≥._ ^zYoUUF⁄òLÇQ-‘§x0˛ÊôwﬂÚx0ÉF>«U¢Oj◊ãW¥V√í†ZQqq˘qÅ|dq¬»¥Ê ÅNxë®÷Aƒ_ŒuL £Ç.©≈Kº’óÏZ…úëDzi1n'ëQûR1*È’KÌ¸ñ–#ﬁ‘—∂»pSÄΩîÔfùPr∑Pj·8t:Ø$µ–7ËTﬁ¨{%π>+Vë@t:ÊÀS≥Ûy2,W≥û,N“'SŒ|B^´\Ç7ÕI©üÃî
¯”±PEÅ,¯81¿¸Ã∫˘rÒ,j¨`‚_i¥ä¥æl;ã¥	
uÚ£¨ïS^¥ùÕ(ÅøöY ¶≤àJ@√˘t·î’¥2He°gê9ÙwÅEKà?Q`êUß–Ωäòÿ‹ÄÃË%ªÏ±)‘NVºr
ß*Å1TZ£¬§˜´`Ø™DäK"?‡±Öi2áFâ÷G™â[5x|•0o*õŸ›¶i¶Ú=¥É NS‰ƒ˘‚†â‡úü˜*.€ÿcØÃm{y4Æ«m˚©¶«‚ŒgqX|rÀ¸ìˇ—:->(¶„T>ã…Ã«≈‰·öº5<UÙx1-^üØ!ˆRâªj(cãÑﬂ%§ñ‹ÆzP,a•î“´K%Éú’ıÇ≈~z©ëj•BíÔXM,⁄Òyu›I∞|˛∞º˙Ÿ®jg†™¬àr1)–†≥aüø»ÌÉg≈ﬁ'_ß*EÅ˚M∏êË4áNÄä£˛IKFˆYò›(4Ú:TÓüıhïôò˝Ìmt∏Èÿ˝¥’í/U“K)ÿj®Bï∫~K¨¨Zfçÿ˝ô_®¢0lX¥Õô÷OuHâÑ»r‰G@Ëò∆T1#™,ˇ)£‚ä•¶C∫60°-fx/X¸ïuÄóƒ|Q»”Û˛ø%êW.Æ.
˛©T6àÚ@ÆLòÖ≥¿4'¿~Åê¢ã«Pbπ∫ÇõE"˘#£•∏sÍ”Oô⁄c¡ÕâO\±bgâ,wT5⁄>§·ªaJÆêòÇÔ¿˘n¸ÄŒ‘ª≤G¥»@Ö„ˆakàyªfˇu˚–‹ ñˇ<Œ¯e-F'ﬂy¸a‘’8t÷F˝Y√‚ã‡úã√Òœ√:úî@%Í‹ıçÉ•=ØÕ>¶3GØŒ€ù!Í”TuŒªGÊ¿Ëä¿+ÈzJá£ﬂ∫G”Ÿ«|†næ!·9YªPP«M={4ÏÀ%‹è˘¯™Ω∫$È§¿ÉuÄãKﬁ´=_∏^§Ë.YK£}⁄Îˆá≠Œ°y~d∑ŒNÜ≤tt»k4„ÕÿócSÕ∂ù›d˛85˘¸ä§*√ ¿]Ò⁄æ8îﬁR¿-…!ëÄ‰sc‰Q¿ﬁcﬂèV`6√àiÿIﬁªSÀvZhäb7˚˚£ôÂ˚∆o≠++?≈…Ã∫Ù„å„ì÷ãÛ÷·∞˝∫=|{>hw^úòÁ√n#&K´ûò≠>÷∏óÃê…Ø(ãÍŸéF(nOO|á0jÇX£ôƒ¯ç,Üˆúû⁄@J˙Õç»¿&©Àó%˘≠˛6õÀYÔ®54œœ˙}ŒÃ¥§A˚ÙÙlÿ:81π‡8Ÿè1¡0;F∑ú4.π¡ë	√N¸ŒªªD	!∑µh≤öÀ¿ûÃ†É/Ã˛i´Ûv#A&	Í„›,©Ob˙
¯Ø)‘F`x’‰¸Û≥I¶∂Û›ÚíN>˛p|ÖY…˛±!~g2PçØEìÊ◊"nﬁ [ÃJN<≤N)dºâo‡(,GSŒVÂ'ÓÕ¥ëåë√ŒÈÃèπ3ÕS?Í	ÿ«g<9sÜ¯h∆.Óhc˚∆ÿ≥ÆÒ¨ˆË|Ãbyü£–;›0ú?.\ì-ü¸=r|¬Z•+ı<ÿ n≠9ı˙ÌnVàÌ§è{Íå•+∂ñÅ{àê5q–=%⁄aoö,⁄É˝<≥Lÿèæﬁ°ŒF›ámó™⁄\gb{sxï  ÈµûúÈE^Qòj&(üãj´îQxCÁ¿èKûèÈÃŒÓ¶Öa‹%5Ê4»3ı≈≈"~”°ﬁÂçH·/ÅlÏdä’‰TëhÖñ^™å!Z2Ã¥EZ„’—÷◊XÏ‘m√xäe√¿PÃÓºπÒ}cÉ§π«◊ªÿ»≠—ÿà;Q©ﬂ¢*MÿﬁUiêŸ‘*MÖ›Æ6´`?+M.ííVË}=«¶»¿ÖøjWI„ÖÂ7‚£dD}`%ΩæÇé6b+¿ÚDí¸Ò#ë.Õ{±ØU…öR®Á¢ÜH¸äºŒ%
€Rxnh9ç¯iòöp8ÖÂÁI#ˆ∑øÙ&÷(≠∆ |*o÷mÜ‹U˘/hª[VÒù,M:∑¥ùÊ‹Bk’á˙Ad_R+ÎAä•<‹2“E9ÅÊ‰>â.1ÚÁ?¸áÇ˝éºâ”	DíÀm7#XL[.{Ø,∑UÊ–Q:¡îÅ‡\’√±≈6Däpí»\hH=Ú‹Ÿå™L0Î¶üÑ\ı"ÁôjTÂ…Ãå6#»2¸◊T9dVqºQÑ≠S˚mmmë÷¡‡eß=íù}ÚÍ¨sdv⁄ù„Ó _J€ÈYÿ%=sÅr≥ˇøÂ!.Öq¶?ÁaÂJÄK˝‹çÂz5–/<‡ë<πM´"ágÖ<¬Q·Û£¨üM¸Å8Ùö[_îg(Ωgq√¬VåìõQÀ@T°g ‘ªW¥Á—â˝û—ÑçÏ≥wc˛å•.n6~ıwø˛ı¯7‡	˙¿¿æGÆ†Q‡PÖÖ)ﬁ˚sÚﬂ†„A„q£P)Å¸y&2™¶∫óÚiùOrB™˛Gü‘˘Ë¡Ù›u…7ïoTÙë≠Kîx˙^pé√#V&äñ'Æ˛Œ80 ÕŒÓﬁÉá≈†A‹ÖHp>#»íVìOÎ¶ç…≤’≤=K≥#ó‚ób≠∑"Ár¡UÕß†Fâüü°ú¥6Kq~œàTI÷Ûüsÿ¨ï ,‰BΩπÂîtsõ?>ÿ,≥ÅëÄe(X®
ë«6Äi∫åy∑fµAÚÄ⁄˛¶G^Ã/^ﬁA‰O"ôÄ¨
<râZUh„DÙäwˆ_=xJv?"Ï‚~¥6X|Go.\ÀwŸYA¿zï~RGQÛê
í…öò2≈F/H„ïP€`~#uòáª„s;éì·V9>\Ë[ı¯ò[ß–l´≈‰ætXù	â~ÜÉ1∆Ù'píLú ›IäÀó=I9y–Ó>9iõ«f04ONL•@(â sd_ŸÖ…Ìãd~ÇÃ¸AYË0ÕÖym¡kEê≈*ËI∑RÎÚ¨YßıÚ‘ˇÂèˇˆœwbÆ\©Å©3 ≥*8;´w´äΩOl:°ËÖÊGìÄ4Ågﬂ$Ωì_níÆ¨@ù[SgÍN¸¿3»ÓìMÚ¯·Œ„]“õ∏ﬁwSjœã˚Ω√}∑Éä¥≥U‡RTÍVÖ…_~¸”tF=NêØÉ†ÿyªı¯Òﬁì›ß;€wL‚mÜœ‹›º∑OÃ√WhL÷Å˚ÁµŸ?Ë∑Œ_ﬁ›—BF€d}~ﬁzŸ7…*¡˝Ow7tÆ îôL7@1?`÷Æ\˜)unâ *8†íÉå</õbIL?ÆIxûôÌ›IÒFFñ.ÅK˛˝˛≥o0=kÒó?˛˚¸U“Ä)d¬o¸Áˇ˝ÔøêÉóØﬁ‘	Aóö"⁄kö÷h
@xq¿Õ¥…Øòù t∂?≠yêÖTHœH‹_a'≈π$¢íxp|H+™‚èËƒC*v—/o_›‰FﬁK∆áEÔã◊±Jd™“s~®¥têïPç∏éÔáD!ó‰G–Pi-ù<jª™ÇhÆbâ≥å‘€©Tnûjµt„ ◊'‹,t/¨´3 B¥-ñ:¡ß£¢cò%m'!3 ¢…äEÁÍ–…ï‹,eEäÙ2YD%$w"◊õñ’sãDΩËyÚfÀheåà•»ˆ¨W/b©åÀF2HaH˝qh’‘@Ö5
å;§o!:kÈUE;ÂjÍ’îÖsU6˙Á÷‘£˛U‰/Blá˘Ü‘e¢<‹^˜¸ÂÑÒ\&q'çèÀ-„¯Ïì~˜lÿÓò[o⁄ÊëŸ›Ìü¥^‹âÂI¬ÚÀ∏˝∏„ˆs%¥ÄÆ``ÃìŒÆ#ºL#ÙŸjΩcÆËÂK∆tNZŒå¢óØµÙ' <Ìî•+≠9¶Ç’zŸ}≠æyKâÏU0&ñ5…Hÿ,Jd˝Cˇ0]1	ñ–ôáâ1¯üÂ¥a£É~ﬁÑze¿π˝Ô!˝Bß´#ÿ_á©Ì°óˇ·ΩƒœäÕ]ã)™h-Dy
ßΩ9aS*K¡íìßd•û=„Ωïˆ§'T¡"
Vrüâ2ÕH”´JW∞¨G¬¬{ZõîÀö%-X÷ÃQ¨G‚¬¶«bËÌËÍ“,+J`∞|)]I}ôQArÉlYE*É•ÆdÜµïPÑ6ãF|J=à¨ ’§)XÍIT∞¨G™ıT=èyT÷#]¡ÚI$,Xj·ƒ≤’î¥∞Òh◊^ó@{›rô¸ì‹£0—Æ*xÆ>œ≈Ù0Œ]ÆÜí‘≠K‚Íê∂¶£8M+
lUQHu,€.Ôi.kŸl¥..<
0™J&ﬂ—ÏfÒL}O‘ÈÎÙ”åñì¸€±fáY¿¥S†aOXDKå€Z‚°÷„ˆB;ufØ.v/ÜÖ’{"#Ã…ç¿≈]ò«lVr∆C Ô…öï£ÌdﬁZI¡€…¨w¥=¡$bA*∫®˙ß¨‘3ä^©áåâﬁJ}	fU+ıìÖçuàï‘
 4ﬂUì-í4◊¶¡µ“
óàV¥ÉNI´‘s/G¥,ûÇäê˛Ú«ﬂˇΩ6¬≠ë~§ÁπÔÌπ‹F≥r0M‘∂èY®1∫æãÆ∫q § æ´lèKœI¢SÖa¶Hòt-ä(&]ãÛGÚ‰lQãl–¿(ﬁYÿ∆É±u®Ufbmb* û+P«á›E9çh>∏√Ö1' \÷0´œjEåF2F4˘ÅŸt˚bxƒx¢SÀ_[5‚GRü_ƒª≈',= FÛºBS—∑1|€3œ{˝Ó/⁄ßÌ·[Åh¬ØDÀÇÙ¡À@øØ§yì;âÍß!#{ˆòÑ3∏≤XÆ 7)6,ÓˆπÏ® ÿ±∂‰+yû|Ûa&˝Ò9≤d°ÿ’QƒYÏπ$y†ÿ˛W€øQ6ô[Ô˚°˛.≥œxgœós˛˛˘>Q‰´ƒ|…ì‰€∂Ofhÿ¿≤ê∞¬hD\·›√—|3⁄h4öπä¨·…xñ|„Øìq√Z	è%C,ùŸ–÷N≤5∫â∂îÉ¢rOüo¬IÁ-ˆ14"Ïçà°‚?qá≥pÓuv;”¿Ô—KD/«f«õŸ›⁄,9s—)=2OZoœ;¸„dC6VF>£N≤p`KG9¥l¨ÀYÒ∆‰» 2sÅ‡ù¿:S
Wu`{4Lú ≥≠åó<|&iSQÅO∫b-Å–≤B>ÄÄuË3<O|DíƒayÏf°‡H.‹k∫
Ïuﬂòr¸Á˙FOË\¯™«ñ.ëw˚™⁄∆õ∞∆sÚ,-⁄äˇºl®Rjb'∂us8a6X.0÷)¿∂Í´1
=ˆM≥sﬁ=>>”zeûüt_IÒO4D"bWΩé>.'QÍçAN,5Äx6Ë{kæò—˝–ùL$	]§±^x˙ËiB±bˆBX2~˝ g2˙›`8-Ú-yÑˇ œu≤ÅpœÊ∂≥( >{NÅ∆Ñ˚ßOÏ¿ôFS∏&ˆâ˘√rcTœÃ¿£µ#à^¥ŸêíBçM“8ÅéQ˚<¡'…˘‹'_†∆	˚;:>ÃªÛ}£ﬁ»É(”jûåæÑq°rÖ™TÏHT∞"êmæ,ÒefuñÏë}È∏U≠øà»∏Ïo$±+ÃõÂ"ËÄ{»≈ìƒO‡ÃnÖ»…ÂÊBÆ¿1]‡r[ó≈2`àÉ\Ü	Z„æsíË*QÚPX)íóçãn‡òe—Ùt”ŒZ◊ñÙxmFÌFÓ¢Híx= $Õ¢=è
≥ø
√gøS")‡§Oö*™Ì I°∫±^68†æH=;{πh<R—ÿ≥±?ö⁄ﬁAeL@öùèöRoÈ\˙¸¢ﬁê(c5Ú¡∞MπÜYs¬TÖ¸}ˇ˝Ωˇ  ˇˇ 9ŸÍ