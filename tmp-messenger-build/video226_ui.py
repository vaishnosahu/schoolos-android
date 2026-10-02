from pathlib import Path
root=Path('/tmp/messenger-build/messenger_live')

# Foreground service supports camera for active video calls.
p=root/'app/src/main/java/com/example/messengerui/CallForegroundService.kt'
s=p.read_text()
s=s.replace('''        val peer = intent?.getStringExtra(EXTRA_PEER_NAME).orEmpty().ifBlank { "Messenger call" }''','''        val peer = intent?.getStringExtra(EXTRA_PEER_NAME).orEmpty().ifBlank { "Messenger call" }
        val video = intent?.getBooleanExtra(EXTRA_VIDEO,false) == true''',1)
s=s.replace('''            foregroundServiceType()
        )''','''            foregroundServiceType(video)
        )''',1)
s=s.replace('''            foregroundServiceType()
        )''','''            foregroundServiceType(false)
        )''',1)
s=s.replace('''    private fun foregroundServiceType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0''','''    private fun foregroundServiceType(video:Boolean): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or (if(video) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0)
        } else 0''',1)
s=s.replace('''        private const val EXTRA_PEER_NAME = "peer_name"

        fun start(context: Context, callId: String, peerName: String) {''','''        private const val EXTRA_PEER_NAME = "peer_name"
        private const val EXTRA_VIDEO = "video"

        fun start(context: Context, callId: String, peerName: String, video:Boolean=false) {''',1)
s=s.replace('''.putExtra(EXTRA_PEER_NAME, peerName)
            ContextCompat.startForegroundService(context, intent)''','''.putExtra(EXTRA_PEER_NAME, peerName)
                .putExtra(EXTRA_VIDEO, video)
            ContextCompat.startForegroundService(context, intent)''',1)
p.write_text(s)

# ViewModel remembers renderer sinks across engine creation/rebuild and starts camera FGS for video.
p=root/'app/src/main/java/com/example/messengerui/AppViewModel.kt'
s=p.read_text()
s=s.replace('''    private var voiceEngine: WebRtcVoiceEngine? = null''','''    private var voiceEngine: WebRtcVoiceEngine? = null
    private var pendingLocalVideoSink: org.webrtc.VideoSink? = null
    private var pendingRemoteVideoSink: org.webrtc.VideoSink? = null''',1)
s=s.replace('''    fun attachLocalVideoSink(sink:org.webrtc.VideoSink?) { voiceEngine?.attachLocalVideoSink(sink) }
    fun attachRemoteVideoSink(sink:org.webrtc.VideoSink?) { voiceEngine?.attachRemoteVideoSink(sink) }''','''    fun attachLocalVideoSink(sink:org.webrtc.VideoSink?) { pendingLocalVideoSink=sink; voiceEngine?.attachLocalVideoSink(sink) }
    fun attachRemoteVideoSink(sink:org.webrtc.VideoSink?) { pendingRemoteVideoSink=sink; voiceEngine?.attachRemoteVideoSink(sink) }''',1)
# after engine created, attach pending sinks and prefer speaker for video.
needle='''        activeVoiceCall=activeVoiceCall?.copy(phase=VoiceCallPhase.CONNECTING)
        startCallQualityLoop()'''
replacement='''        pendingLocalVideoSink?.let { voiceEngine?.attachLocalVideoSink(it) }
        pendingRemoteVideoSink?.let { voiceEngine?.attachRemoteVideoSink(it) }
        if(activeVoiceCall?.callType==CallType.VIDEO) voiceEngine?.setSpeaker(true)
        activeVoiceCall=activeVoiceCall?.copy(phase=VoiceCallPhase.CONNECTING)
        startCallQualityLoop()'''
if needle not in s: raise SystemExit('engine post-create anchor missing')
s=s.replace(needle,replacement,1)
# foreground service starts video-aware
s=s.replace('''CallForegroundService.start(getApplication(),current.callId,current.peerName)''','''CallForegroundService.start(getApplication(),current.callId,current.peerName,current.callType==CallType.VIDEO)''')
s=s.replace('''CallForegroundService.start(getApplication(),accepted.id,accepted.peerName)''','''CallForegroundService.start(getApplication(),accepted.id,accepted.peerName,accepted.type==CallType.VIDEO)''')
p.write_text(s)

# UI imports
p=root/'app/src/main/java/com/example/messengerui/MessengerApp.kt'
s=p.read_text()
if 'import androidx.compose.ui.viewinterop.AndroidView' not in s:
    s=s.replace('import androidx.compose.ui.platform.LocalFocusManager','import androidx.compose.ui.platform.LocalFocusManager\nimport androidx.compose.ui.viewinterop.AndroidView')

# Generic video call permission helper.
voice_helper='''@Composable
private fun rememberVoiceCallPermissionAction(vm: AppViewModel, onGranted: () -> Unit): () -> Unit {'''
idx=s.index(voice_helper)
end=s.index('\n@Composable\nprivate fun CallsTab',idx)
helper=s[idx:end]
video_helper='''

@Composable
private fun rememberVideoCallPermissionAction(vm: AppViewModel, onGranted: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val mic=result[Manifest.permission.RECORD_AUDIO] == true || ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED
        val camera=result[Manifest.permission.CAMERA] == true || ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED
        if(mic && camera) onGranted() else vm.lastToast = "Camera and microphone permissions are required for video calls"
    }
    return {
        val permissions=buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.CAMERA)
            if(Build.VERSION.SDK_INT>=31) add(Manifest.permission.BLUETOOTH_CONNECT)
        }.toTypedArray()
        val mic=ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED
        val camera=ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED
        if(mic && camera) onGranted() else launcher.launch(permissions)
    }
}
'''
s=s[:end]+video_helper+s[end:]

# ChatScreen video action.
anchor='''    val requestVoiceCall: () -> Unit = {
        val permissions = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
        }.toTypedArray()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.startVoiceCall(chatId) else callPermission.launch(permissions)
    }'''
if anchor not in s: raise SystemExit('ChatScreen voice request anchor missing')
new=anchor+'''
    val requestVideoCall = rememberVideoCallPermissionAction(vm) { vm.startVideoCall(chatId) }'''
s=s.replace(anchor,new,1)
s=s.replace('''IconButton(enabled = false, onClick = {}) { Icon(Icons.Outlined.Videocam, "Video call unavailable") }''','''IconButton(enabled = !chat.isGroup, onClick = requestVideoCall) { Icon(Icons.Outlined.Videocam, "Video call") }''',1)

# Chat info video action.
s=s.replace('''    val requestAudioCall = rememberVoiceCallPermissionAction(vm) { vm.startVoiceCall(chatId) }''','''    val requestAudioCall = rememberVoiceCallPermissionAction(vm) { vm.startVoiceCall(chatId) }
    val requestVideoCall = rememberVideoCallPermissionAction(vm) { vm.startVideoCall(chatId) }''',1)
s=s.replace('''                        ActionCard(Icons.Outlined.Call, "Audio") { requestAudioCall() }
                        ActionCard(Icons.Outlined.Search, "Search")''','''                        ActionCard(Icons.Outlined.Call, "Audio") { requestAudioCall() }
                        if(!chat.isGroup) ActionCard(Icons.Outlined.Videocam, "Video") { requestVideoCall() }
                        ActionCard(Icons.Outlined.Search, "Search")''',1)

# Incoming/active call permission launcher accepts video too.
old='''    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) vm.acceptVoiceCall() else vm.lastToast = "Microphone permission is required for voice calls"
    }'''
new='''    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val mic=result[Manifest.permission.RECORD_AUDIO] == true || ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED
        val camera=call?.callType!=CallType.VIDEO || result[Manifest.permission.CAMERA] == true || ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED
        if(mic && camera) vm.acceptVoiceCall() else vm.lastToast = if(call?.callType==CallType.VIDEO) "Camera and microphone permissions are required for video calls" else "Microphone permission is required for voice calls"
    }'''
if old not in s: raise SystemExit('call permission launcher anchor missing')
s=s.replace(old,new,1)

# Phase copy says video call.
s=s.replace('''call?.phase == VoiceCallPhase.INCOMING_RINGING -> "Incoming voice call"''','''call?.phase == VoiceCallPhase.INCOMING_RINGING -> if(call.callType==CallType.VIDEO) "Incoming video call" else "Incoming voice call"''',1)
s=s.replace('''else "Voice call",''','''else if(call?.callType==CallType.VIDEO) "Video call" else "Voice call",''',1)

# Insert video surface replacing avatar area when video call.
avatar='''        Spacer(Modifier.height(54.dp))
        Avatar(call?.peerName ?: "Messenger", 124.dp)
        Spacer(Modifier.height(22.dp))
        Text(call?.peerName ?: "Messenger call", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)'''
video_ui='''        Spacer(Modifier.height(if(call?.callType==CallType.VIDEO) 14.dp else 54.dp))
        if(call?.callType==CallType.VIDEO) {
            Box(
                Modifier.fillMaxWidth().weight(0.72f).clip(RoundedCornerShape(24.dp)).background(Color.Black)
            ) {
                var remoteRenderer by remember(call.callId) { mutableStateOf<org.webrtc.SurfaceViewRenderer?>(null) }
                var localRenderer by remember(call.callId) { mutableStateOf<org.webrtc.SurfaceViewRenderer?>(null) }

                AndroidView(
                    modifier=Modifier.fillMaxSize(),
                    factory={ ctx ->
                        org.webrtc.SurfaceViewRenderer(ctx).apply {
                            vm.callVideoEglContext()?.let { init(it,null) }
                            setEnableHardwareScaler(true)
                            setMirror(false)
                            remoteRenderer=this
                            vm.attachRemoteVideoSink(this)
                        }
                    },
                    update={ renderer ->
                        vm.callVideoEglContext()?.let { egl ->
                            if(remoteRenderer!==renderer) {
                                runCatching { renderer.init(egl,null) }
                                remoteRenderer=renderer
                                vm.attachRemoteVideoSink(renderer)
                            }
                        }
                    }
                )
                if(!call.remoteVideoAvailable && call.phase==VoiceCallPhase.CONNECTED) {
                    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                        Column(horizontalAlignment=Alignment.CenterHorizontally) {
                            Avatar(call.peerName,82.dp)
                            Spacer(Modifier.height(10.dp))
                            Text("Waiting for video",color=Color.White)
                        }
                    }
                }
                if(call.cameraEnabled) {
                    AndroidView(
                        modifier=Modifier.align(Alignment.TopEnd).padding(12.dp).size(width=112.dp,height=158.dp).clip(RoundedCornerShape(18.dp)),
                        factory={ ctx ->
                            org.webrtc.SurfaceViewRenderer(ctx).apply {
                                vm.callVideoEglContext()?.let { init(it,null) }
                                setEnableHardwareScaler(true)
                                setMirror(call.frontCamera)
                                setZOrderMediaOverlay(true)
                                localRenderer=this
                                vm.attachLocalVideoSink(this)
                            }
                        },
                        update={ it.setMirror(call.frontCamera) }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        } else {
            Avatar(call?.peerName ?: "Messenger", 124.dp)
            Spacer(Modifier.height(22.dp))
        }
        Text(call?.peerName ?: "Messenger call", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)'''
if avatar not in s: raise SystemExit('call avatar anchor missing')
s=s.replace(avatar,video_ui,1)

# Incoming accept permission list includes camera.
s=s.replace('''val permissions=buildList { add(Manifest.permission.RECORD_AUDIO); if(Build.VERSION.SDK_INT>=31) add(Manifest.permission.BLUETOOTH_CONNECT) }.toTypedArray()
                            if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) vm.acceptVoiceCall() else permissionLauncher.launch(permissions)''','''val permissions=buildList { add(Manifest.permission.RECORD_AUDIO); if(call.callType==CallType.VIDEO) add(Manifest.permission.CAMERA); if(Build.VERSION.SDK_INT>=31) add(Manifest.permission.BLUETOOTH_CONNECT) }.toTypedArray()
                            val mic=ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED
                            val camera=call.callType!=CallType.VIDEO || ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED
                            if(mic && camera) vm.acceptVoiceCall() else permissionLauncher.launch(permissions)''',1)

# Active call controls: add camera toggle + switch for video.
controls='''                CallControl(if(call.muted) Icons.Filled.MicOff else Icons.Filled.Mic, if(call.muted) "Unmute" else "Mute", call.muted) { vm.toggleVoiceCallMute() }
                CallControl(
                    when(call.routeLabel) { "Bluetooth" -> Icons.Filled.BluetoothAudio; "Headset" -> Icons.Filled.Headset; "Speaker" -> Icons.Filled.VolumeUp; else -> Icons.Filled.PhoneInTalk },
                    call.routeLabel,
                    call.speaker
                ) { routeDialog = true }'''
controls_new='''                CallControl(if(call.muted) Icons.Filled.MicOff else Icons.Filled.Mic, if(call.muted) "Unmute" else "Mute", call.muted) { vm.toggleVoiceCallMute() }
                if(call.callType==CallType.VIDEO) {
                    CallControl(if(call.cameraEnabled) Icons.Filled.Videocam else Icons.Filled.VideocamOff, if(call.cameraEnabled) "Camera" else "Camera off", !call.cameraEnabled) { vm.toggleCallCamera() }
                    CallControl(Icons.Filled.Cameraswitch, "Flip", false) { vm.switchCallCamera() }
                }
                CallControl(
                    when(call.routeLabel) { "Bluetooth" -> Icons.Filled.BluetoothAudio; "Headset" -> Icons.Filled.Headset; "Speaker" -> Icons.Filled.VolumeUp; else -> Icons.Filled.PhoneInTalk },
                    call.routeLabel,
                    call.speaker
                ) { routeDialog = true }'''
if controls not in s: raise SystemExit('active control anchor missing')
s=s.replace(controls,controls_new,1)

p.write_text(s)

print('Messenger 2.26 video UI and foreground integration applied')
