from pathlib import Path

root=Path("/tmp/messenger-build/messenger_live")
pkg=root/"app/src/main/java/com/example/messengerui"

# Version
p=root/"app/build.gradle.kts"
s=p.read_text().replace("versionCode = 22","versionCode = 23").replace('versionName = "2.11"','versionName = "2.12"')
p.write_text(s)

# Permissions
p=root/"app/src/main/AndroidManifest.xml"
s=p.read_text()
if 'android.permission.WAKE_LOCK' not in s:
    s=s.replace(
        '    <uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />',
        '    <uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />\n'
        '    <uses-permission android:name="android.permission.WAKE_LOCK" />\n'
        '    <uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />'
    )
p.write_text(s)

# Voice call model
p=pkg/"CallModels.kt"
s=p.read_text()
s=s.replace(
    '    val error: String? = null\n)',
    '    val error: String? = null,\n    val audioInterrupted: Boolean = false\n)'
)
p.write_text(s)

# Audio engine
engine=Path(__file__).parent/"voice212"/"WebRtcVoiceEngine.kt"
(pkg/"WebRtcVoiceEngine.kt").write_text(engine.read_text())

# ViewModel
p=pkg/"AppViewModel.kt"
s=p.read_text()
s=s.replace(
'''    var activeVoiceCall by mutableStateOf<VoiceCallUi?>(null)
        private set
''',
'''    var activeVoiceCall by mutableStateOf<VoiceCallUi?>(null)
        private set
    val voiceCallRoutes = mutableStateListOf<String>()
    var callActionBusy by mutableStateOf(false)
        private set
''',1)

s=s.replace(
'''    fun startVoiceCall(chatId: String) {
        if (androidx.core.content.ContextCompat.checkSelfPermission''',
'''    fun startVoiceCall(chatId: String) {
        if (callActionBusy) return
        if (androidx.core.content.ContextCompat.checkSelfPermission''',1)

s=s.replace(
'''        val token=db.serverToken()
        viewModelScope.launch {
            runCatching {
                val config=withContext(Dispatchers.IO){ api.voiceCallConfig(token) }''',
'''        val token=db.serverToken()
        callActionBusy=true
        viewModelScope.launch {
            runCatching {
                val config=withContext(Dispatchers.IO){ api.voiceCallConfig(token) }''',1)

s=s.replace(
'''.onFailure { lastToast=it.userMessage("Unable to start voice call") }
        }
    }
''',
'''.onFailure { lastToast=it.userMessage("Unable to start voice call") }
            callActionBusy=false
        }
    }
''',1)

s=s.replace(
'''    fun acceptVoiceCall() {
        val current=activeVoiceCall ?: return
        if(current.phase!=VoiceCallPhase.INCOMING_RINGING) return
        val token=db.serverToken()
        viewModelScope.launch {''',
'''    fun acceptVoiceCall() {
        val current=activeVoiceCall ?: return
        if(current.phase!=VoiceCallPhase.INCOMING_RINGING || callActionBusy) return
        callActionBusy=true
        val token=db.serverToken()
        viewModelScope.launch {''',1)

s=s.replace(
'''.onFailure { lastToast=it.userMessage("Unable to accept call") }
        }
    }

    fun declineVoiceCall() {''',
'''.onFailure { lastToast=it.userMessage("Unable to accept call") }
            callActionBusy=false
        }
    }

    fun declineVoiceCall() {''',1)

s=s.replace(
'''    fun declineVoiceCall() {
        val current=activeVoiceCall ?: return
        val token=db.serverToken()
        viewModelScope.launch {''',
'''    fun declineVoiceCall() {
        val current=activeVoiceCall ?: return
        if(callActionBusy) return
        callActionBusy=true
        val token=db.serverToken()
        viewModelScope.launch {''',1)

s=s.replace(
'''.onFailure { finishVoiceCall(null,false); lastToast=it.userMessage("Unable to decline call") }
        }
    }

    fun endVoiceCall''',
'''.onFailure { finishVoiceCall(null,false); lastToast=it.userMessage("Unable to decline call") }
            callActionBusy=false
        }
    }

    fun endVoiceCall''',1)

s=s.replace(
'''    fun endVoiceCall(reason:String="ended") {
        val current=activeVoiceCall ?: return
        val token=db.serverToken()''',
'''    fun endVoiceCall(reason:String="ended") {
        val current=activeVoiceCall ?: return
        if(callActionBusy) return
        callActionBusy=true
        val token=db.serverToken()''',1)

old='''    fun toggleVoiceCallSpeaker() {
        val current=activeVoiceCall ?: return
        val speaker=!current.speaker
        voiceEngine?.setSpeaker(speaker)
        activeVoiceCall=current.copy(speaker=speaker,routeLabel=if(speaker)"Speaker" else current.routeLabel)
    }
'''
new='''    fun toggleVoiceCallSpeaker() {
        val current=activeVoiceCall ?: return
        voiceEngine?.setSpeaker(!current.speaker)
    }

    fun setVoiceCallRoute(route:String) {
        if(route.isBlank()) return
        voiceEngine?.setRoute(route)
    }
'''
if old not in s: raise SystemExit("speaker block missing")
s=s.replace(old,new,1)

old='''        voiceEngine=WebRtcVoiceEngine(getApplication(),config.iceServers,
            onLocalSignal={ type,payload -> sendVoiceSignal(type,payload) },
            onState={ phase -> viewModelScope.launch { onVoiceEngineState(phase) } },
            onRoute={ route -> activeVoiceCall=activeVoiceCall?.copy(routeLabel=route,speaker=route=="Speaker") },
            onError={ error -> viewModelScope.launch { onVoiceEngineError(error) } }
        )'''
new='''        voiceEngine=WebRtcVoiceEngine(getApplication(),config.iceServers,
            onLocalSignal={ type,payload -> sendVoiceSignal(type,payload) },
            onState={ phase -> viewModelScope.launch { onVoiceEngineState(phase) } },
            onRoute={ route -> activeVoiceCall=activeVoiceCall?.copy(routeLabel=route,speaker=route=="Speaker") },
            onRoutes={ routes -> viewModelScope.launch { voiceCallRoutes.clear(); voiceCallRoutes.addAll(routes) } },
            onAudioInterruption={ interrupted -> viewModelScope.launch { activeVoiceCall=activeVoiceCall?.copy(audioInterrupted=interrupted) } },
            onError={ error -> viewModelScope.launch { onVoiceEngineError(error) } }
        )'''
if old not in s: raise SystemExit("engine callback anchor missing")
s=s.replace(old,new,1)

s=s.replace(
'activeVoiceCall=current.copy(phase=VoiceCallPhase.CONNECTED,connectedAtMs=current.connectedAtMs ?: System.currentTimeMillis())',
'activeVoiceCall=current.copy(phase=VoiceCallPhase.CONNECTED,connectedAtMs=current.connectedAtMs ?: System.currentTimeMillis(),audioInterrupted=false)',
1)

s=s.replace(
'''        callStateJob?.cancel(); callStateJob=null; callSignalCursor=0L; callConnectedReported=false
        activeVoiceCall=null''',
'''        callStateJob?.cancel(); callStateJob=null; callSignalCursor=0L; callConnectedReported=false
        voiceCallRoutes.clear(); callActionBusy=false
        activeVoiceCall=null''',1)
p.write_text(s)

# Call UI
p=pkg/"MessengerApp.kt"
s=p.read_text()
start=s.index("@Composable\nprivate fun VoiceCallScreen")
end=s.index("\n@Composable\nprivate fun DemoCallScreen",start)
new_screen=r'''@Composable
private fun VoiceCallScreen(vm: AppViewModel, callId: String) {
    val context = LocalContext.current
    val call = vm.activeVoiceCall
    var routeDialog by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) vm.acceptVoiceCall() else vm.lastToast = "Microphone permission is required for voice calls"
    }

    LaunchedEffect(callId) {
        if (call?.callId != callId) vm.openVoiceCallFromNotification(callId)
    }
    BackHandler(enabled = call != null) { vm.back() }

    var elapsed by remember(call?.callId, call?.connectedAtMs) { mutableStateOf(0L) }
    LaunchedEffect(call?.connectedAtMs) {
        while (call?.connectedAtMs != null) {
            elapsed = ((System.currentTimeMillis() - call.connectedAtMs) / 1000L).coerceAtLeast(0L)
            kotlinx.coroutines.delay(1000)
        }
    }

    val phaseText = when {
        call?.audioInterrupted == true -> "Call audio interrupted"
        call?.phase == VoiceCallPhase.OUTGOING_RINGING -> "Ringing…"
        call?.phase == VoiceCallPhase.INCOMING_RINGING -> "Incoming voice call"
        call?.phase == VoiceCallPhase.CONNECTING -> "Connecting…"
        call?.phase == VoiceCallPhase.CONNECTED -> "%d:%02d".format(elapsed / 60L, elapsed % 60L)
        call?.phase == VoiceCallPhase.RECONNECTING -> "Reconnecting…"
        call?.phase == VoiceCallPhase.FAILED -> call.error ?: "Call failed"
        call?.phase == VoiceCallPhase.ENDED -> "Call ended"
        else -> "Loading call…"
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 22.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (call != null && call.phase != VoiceCallPhase.INCOMING_RINGING) {
                FilledTonalIconButton(onClick = { vm.back() }) {
                    Icon(Icons.Filled.KeyboardArrowDown, "Minimize call")
                }
            } else {
                Spacer(Modifier.size(48.dp))
            }

            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (call?.phase in listOf(VoiceCallPhase.CONNECTING, VoiceCallPhase.RECONNECTING)) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(7.dp))
                    }
                    Text(phaseText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.size(48.dp))
        }

        Spacer(Modifier.height(54.dp))
        Avatar(call?.peerName ?: "Messenger", 124.dp)
        Spacer(Modifier.height(22.dp))
        Text(call?.peerName ?: "Messenger call", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        if (call?.phase == VoiceCallPhase.CONNECTED) {
            Text(call.routeLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        } else {
            Text("Voice call", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.weight(1f))

        if (call?.phase == VoiceCallPhase.INCOMING_RINGING) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledIconButton(
                        enabled = !vm.callActionBusy,
                        onClick = { vm.declineVoiceCall() },
                        modifier = Modifier.size(70.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Icon(Icons.Filled.CallEnd, "Decline", modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.height(7.dp))
                    Text("Decline", style = MaterialTheme.typography.labelMedium)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledIconButton(
                        enabled = !vm.callActionBusy,
                        onClick = {
                            val permissions=buildList {
                                add(Manifest.permission.RECORD_AUDIO)
                                if(Build.VERSION.SDK_INT>=31) add(Manifest.permission.BLUETOOTH_CONNECT)
                            }.toTypedArray()
                            if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) {
                                vm.acceptVoiceCall()
                            } else {
                                permissionLauncher.launch(permissions)
                            }
                        },
                        modifier = Modifier.size(70.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Filled.Call, "Accept", modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(if(vm.callActionBusy) "Connecting…" else "Answer", style = MaterialTheme.typography.labelMedium)
                }
            }
        } else if (call != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                CallControl(
                    if(call.muted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    if(call.muted) "Unmute" else "Mute",
                    call.muted
                ) { vm.toggleVoiceCallMute() }

                CallControl(
                    when(call.routeLabel) {
                        "Bluetooth" -> Icons.Filled.BluetoothAudio
                        "Headset" -> Icons.Filled.Headset
                        "Speaker" -> Icons.Filled.VolumeUp
                        else -> Icons.Filled.PhoneInTalk
                    },
                    call.routeLabel,
                    call.speaker
                ) { routeDialog = true }
            }

            Spacer(Modifier.height(30.dp))
            FilledIconButton(
                enabled = !vm.callActionBusy,
                onClick = { vm.endVoiceCall() },
                modifier = Modifier.size(74.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Icon(Icons.Filled.CallEnd, "End call", modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if(vm.callActionBusy) "Ending…" else "End call",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(28.dp))
    }

    if (routeDialog && call != null) {
        AlertDialog(
            onDismissRequest = { routeDialog=false },
            title = { Text("Audio output") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val routes = vm.voiceCallRoutes.ifEmpty { listOf("Phone", "Speaker") }
                    routes.forEach { route ->
                        ListItem(
                            modifier = Modifier.fillMaxWidth().clickable {
                                vm.setVoiceCallRoute(route)
                                routeDialog=false
                            },
                            leadingContent = {
                                Icon(
                                    when(route) {
                                        "Bluetooth" -> Icons.Filled.BluetoothAudio
                                        "Headset" -> Icons.Filled.Headset
                                        "Speaker" -> Icons.Filled.VolumeUp
                                        else -> Icons.Filled.PhoneInTalk
                                    },
                                    null
                                )
                            },
                            headlineContent = { Text(route) },
                            trailingContent = {
                                if(route == call.routeLabel) Icon(Icons.Filled.Check, "Selected")
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { routeDialog=false }) { Text("Close") }
            }
        )
    }
}
'''
s=s[:start]+new_screen+s[end:]
p.write_text(s)

print("voice call UX/audio routing 2.12 applied")
