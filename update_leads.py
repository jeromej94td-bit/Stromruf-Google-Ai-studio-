import sys

with open('./app/src/main/java/com/example/ui/screens/LeadsScreen.kt', 'r') as f:
    content = f.read()

# Add callLogs collection
content = content.replace('val contacts by viewModel.contacts.collectAsState()', 'val contacts by viewModel.contacts.collectAsState()\n    val callLogs by viewModel.callLogs.collectAsState()')

# Focus List PersonRow update
target_focus = '''                        badge = {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                when {
                                    done -> StatusBadge("Zyklus erledigt", BadgeTone.Neutral)
                                    c.isReachableNow() -> StatusBadge("Jetzt erreichbar", BadgeTone.Success)
                                    else -> StatusBadge("Außerhalb Zeitfenster", BadgeTone.Neutral)
                                }
                                val last = c.lastCallAt
                                if (last == null) StatusBadge("Nie kontaktiert", BadgeTone.Warn)
                                else StatusBadge("Vor ${daysAgo(last)} T.", BadgeTone.Info)
                            }
                        },'''

replacement_focus = '''                        badge = {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                when {
                                    done -> StatusBadge("Zyklus erledigt", BadgeTone.Neutral)
                                    c.isReachableNow() -> StatusBadge("Jetzt erreichbar", BadgeTone.Success)
                                    else -> StatusBadge("Außerhalb Zeitfenster", BadgeTone.Neutral)
                                }
                                val last = c.lastCallAt
                                if (last == null) StatusBadge("Nie kontaktiert", BadgeTone.Warn)
                                else StatusBadge("Vor ${daysAgo(last)} T.", BadgeTone.Info)
                                
                                val logs = callLogs.filter { it.phone == c.phone }
                                val reached = logs.count { it.outcome.equals("Erreicht", ignoreCase = true) }
                                val notReached = logs.size - reached
                                if (logs.isNotEmpty()) {
                                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.background(Color.White.copy(alpha=0.1f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                                        Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(10.dp), tint = Color.White)
                                        Text("$reached/$notReached", fontSize = 10.sp, color = Color.White)
                                    }
                                }
                            }
                        },'''
content = content.replace(target_focus, replacement_focus)

# Neukunden List PersonRow update
target_neu = '''                        badge = {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                StatusBadge(n.status, statusTone)
                                StatusBadge("${n.callAttempts} Versuche", if (n.callAttempts > 3) BadgeTone.Error else BadgeTone.Neutral)
                                StatusBadge("Seit ${daysAgo(n.dateCreated)} T.", BadgeTone.Info)
                            }
                        },'''

replacement_neu = '''                        badge = {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                StatusBadge(n.status, statusTone)
                                StatusBadge("Seit ${daysAgo(n.dateCreated)} T.", BadgeTone.Info)
                                
                                val logs = callLogs.filter { it.phone == n.phone }
                                val reached = logs.count { it.outcome.equals("Erreicht", ignoreCase = true) }
                                val notReached = logs.size - reached
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.background(Color.White.copy(alpha=0.1f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(10.dp), tint = Color.White)
                                    Text("$reached/$notReached", fontSize = 10.sp, color = Color.White)
                                }
                            }
                        },'''
content = content.replace(target_neu, replacement_neu)

with open('./app/src/main/java/com/example/ui/screens/LeadsScreen.kt', 'w') as f:
    f.write(content)
