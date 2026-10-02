from pathlib import Path
root=Path('/tmp/messenger-build/messenger_live')

# Incoming video-call notification semantics.
p=root/'app/src/main/java/com/example/messengerui/NotificationHelper.kt'
s=p.read_text()
s=s.replace('fun showIncomingCall(context:Context,callId:String,callerName:String,callerPhone:String=""):Boolean {',
            'fun showIncomingCall(context:Context,callId:String,callerName:String,callerPhone:String="",callType:CallType=CallType.AUDIO):Boolean {',1)
s=s.replace('putExtra(IncomingCallActivity.EXTRA_CALLER_PHONE,callerPhone)',
            'putExtra(IncomingCallActivity.EXTRA_CALLER_PHONE,callerPhone)\n            putExtra(IncomingCallActivity.EXTRA_CALL_TYPE,callType.name)',1)
# Only first incoming full call content.
s=s.replace('.setSmallIcon(R.drawable.ic_notification).setContentTitle(callerName).setContentText("Incoming voice call")',
            '.setSmallIcon(R.drawable.ic_notification).setContentTitle(callerName).setContentText(if(callType==CallType.VIDEO)"Incoming video call" else "Incoming voice call")',1)

# Silent incoming helper supports type too.
s=s.replace('fun showSilentIncomingCall(context:Context,callId:String,callerName:String,callerPhone:String=""):Boolean {',
            'fun showSilentIncomingCall(context:Context,callId:String,callerName:String,callerPhone:String="",callType:CallType=CallType.AUDIO):Boolean {',1)
# There is another IncomingCallActivity phone extra in silent helper.
silent_start=s.find('fun showSilentIncomingCall')
if silent_start>=0:
    idx=s.find('putExtra(IncomingCallActivity.EXTRA_CALLER_PHONE,callerPhone)',silent_start)
    if idx>=0:
        end=idx+len('putExtra(IncomingCallActivity.EXTRA_CALLER_PHONE,callerPhone)')
        s=s[:end]+'\n            putExtra(IncomingCallActivity.EXTRA_CALL_TYPE,callType.name)'+s[end:]
p.write_text(s)

# FCM propagates call_type.
p=root/'app/src/main/java/com/example/messengerui/MessengerFirebaseService.kt'
s=p.read_text()
old='''                    val phone=data["caller_phone"].orEmpty()
                    val silenceUnknown=db.getBooleanSetting("silence_unknown_callers",false) && !db.hasContactId(callerId)
                    if(silenceUnknown) NotificationHelper.showSilentIncomingCall(this,callId,name,phone)
                    else NotificationHelper.showIncomingCall(this,callId,name,phone)'''
new='''                    val phone=data["caller_phone"].orEmpty()
                    val callType=runCatching { CallType.valueOf(data["call_type"].orEmpty().uppercase()) }.getOrDefault(CallType.AUDIO)
                    val silenceUnknown=db.getBooleanSetting("silence_unknown_callers",false) && !db.hasContactId(callerId)
                    if(silenceUnknown) NotificationHelper.showSilentIncomingCall(this,callId,name,phone,callType)
                    else NotificationHelper.showIncomingCall(this,callId,name,phone,callType)'''
if old not in s: raise SystemExit('FCM incoming call block missing')
s=s.replace(old,new,1)
p.write_text(s)

# Realtime incoming helper calls carry type.
p=root/'app/src/main/java/com/example/messengerui/AppViewModel.kt'
s=p.read_text()
s=s.replace('NotificationHelper.showIncomingCall(getApplication(),remote.id,remote.peerName,remote.peerPhone)',
            'NotificationHelper.showIncomingCall(getApplication(),remote.id,remote.peerName,remote.peerPhone,remote.type)')
s=s.replace('NotificationHelper.showSilentIncomingCall(getApplication(),remote.id,remote.peerName,remote.peerPhone)',
            'NotificationHelper.showSilentIncomingCall(getApplication(),remote.id,remote.peerName,remote.peerPhone,remote.type)')

# Video-aware redial.
old='''        if(chat!=null) startVoiceCall(chat.id) else lastToast="Conversation for this caller is not available"'''
if old in s:
    s=s.replace(old,'''        if(chat!=null) {
            if(call.type==CallType.VIDEO) startVideoCall(chat.id) else startVoiceCall(chat.id)
        } else lastToast="Conversation for this caller is not available"''',1)
p.write_text(s)

# Dedicated incoming call activity displays video type.
p=root/'app/src/main/java/com/example/messengerui/IncomingCallActivity.kt'
s=p.read_text()
if 'import androidx.compose.material.icons.outlined.Videocam' not in s:
    s=s.replace('import androidx.compose.material.icons.outlined.CallEnd','import androidx.compose.material.icons.outlined.CallEnd\nimport androidx.compose.material.icons.outlined.Videocam')
s=s.replace('''        val callerPhone=intent.getStringExtra(EXTRA_CALLER_PHONE).orEmpty()
        setContent {''','''        val callerPhone=intent.getStringExtra(EXTRA_CALLER_PHONE).orEmpty()
        val callType=runCatching { CallType.valueOf(intent.getStringExtra(EXTRA_CALL_TYPE).orEmpty()) }.getOrDefault(CallType.AUDIO)
        setContent {''',1)
s=s.replace('IncomingCallSurface(callerName,callerPhone,onAnswer={',
            'IncomingCallSurface(callerName,callerPhone,callType,onAnswer={',1)
s=s.replace('''    private fun IncomingCallSurface(callerName:String,callerPhone:String,onAnswer:()->Unit,onDecline:()->Unit){''',
            '''    private fun IncomingCallSurface(callerName:String,callerPhone:String,callType:CallType,onAnswer:()->Unit,onDecline:()->Unit){''',1)
s=s.replace('''            Text("Incoming voice call",style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)''',
            '''            Text(if(callType==CallType.VIDEO)"Incoming video call" else "Incoming voice call",style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)''',1)
s=s.replace('''                    Icon(Icons.Outlined.Call,null); Spacer(Modifier.padding(4.dp)); Text("Answer")''',
            '''                    Icon(if(callType==CallType.VIDEO) Icons.Outlined.Videocam else Icons.Outlined.Call,null); Spacer(Modifier.padding(4.dp)); Text(if(callType==CallType.VIDEO)"Video" else "Answer")''',1)
s=s.replace('''        const val EXTRA_CALLER_PHONE="caller_phone"''','''        const val EXTRA_CALLER_PHONE="caller_phone"
        const val EXTRA_CALL_TYPE="call_type"''',1)
p.write_text(s)

# Main UI answer permission and PiP + video redial.
p=root/'app/src/main/java/com/example/messengerui/MessengerApp.kt'
s=p.read_text()

old='''    val notificationAnswerPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(granted) vm.acceptVoiceCall() else vm.lastToast = "Microphone permission is required to answer calls"
        onInitialDestinationConsumed()
    }'''
new='''    val notificationAnswerPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val call=vm.activeVoiceCall
        val mic=result[Manifest.permission.RECORD_AUDIO]==true || ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED
        val camera=call?.callType!=CallType.VIDEO || result[Manifest.permission.CAMERA]==true || ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED
        if(mic && camera) vm.acceptVoiceCall() else vm.lastToast = if(call?.callType==CallType.VIDEO)"Camera and microphone permissions are required to answer video calls" else "Microphone permission is required to answer calls"
        onInitialDestinationConsumed()
    }'''
if old not in s: raise SystemExit('top answer permission launcher anchor missing')
s=s.replace(old,new,1)

old='''            if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) {
                vm.acceptVoiceCall()
                onInitialDestinationConsumed()
            } else notificationAnswerPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)'''
new='''            val mic=ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED
            val camera=call.callType!=CallType.VIDEO || ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED
            if(mic && camera) {
                vm.acceptVoiceCall()
                onInitialDestinationConsumed()
            } else {
                val needed=buildList {
                    add(Manifest.permission.RECORD_AUDIO)
                    if(call.callType==CallType.VIDEO) add(Manifest.permission.CAMERA)
                }.toTypedArray()
                notificationAnswerPermissionLauncher.launch(needed)
            }'''
if old not in s: raise SystemExit('initial answer permission path missing')
s=s.replace(old,new,1)

# PiP on explicit minimize for video.
old='''FilledTonalIconButton(onClick = { vm.back() }) { Icon(Icons.Filled.KeyboardArrowDown, "Minimize call") }'''
new='''FilledTonalIconButton(onClick = {
                    if(call.callType==CallType.VIDEO && Build.VERSION.SDK_INT>=26) {
                        val activity=context as? android.app.Activity
                        runCatching {
                            activity?.enterPictureInPictureMode(
                                android.app.PictureInPictureParams.Builder()
                                    .setAspectRatio(android.util.Rational(9,16))
                                    .build()
                            )
                        }.onFailure { vm.back() }
                    } else vm.back()
                }) { Icon(Icons.Filled.KeyboardArrowDown, "Minimize call") }'''
if old not in s: raise SystemExit('minimize call anchor missing')
s=s.replace(old,new,1)

# Calls list uses correct permission for video redial.
old='''            val requestRedial = rememberVoiceCallPermissionAction(vm) { vm.redialVoiceCall(call) }'''
new='''            val requestAudioRedial = rememberVoiceCallPermissionAction(vm) { vm.redialVoiceCall(call) }
            val requestVideoRedial = rememberVideoCallPermissionAction(vm) { vm.redialVoiceCall(call) }
            val requestRedial = if(call.type==CallType.VIDEO) requestVideoRedial else requestAudioRedial'''
s=s.replace(old,new,1)

# Video history trailing icon becomes actionable.
old='''                    if (call.type == CallType.AUDIO) IconButton(onClick = { requestRedial() }) { Icon(Icons.Outlined.Call, "Call") }
                    else Icon(Icons.Outlined.Videocam, "Video call history", tint = MaterialTheme.colorScheme.onSurfaceVariant)'''
new='''                    IconButton(onClick = { requestRedial() }) {
                        Icon(if(call.type==CallType.VIDEO) Icons.Outlined.Videocam else Icons.Outlined.Call, if(call.type==CallType.VIDEO)"Video call" else "Call")
                    }'''
if old in s: s=s.replace(old,new,1)

# Call details permission-safe redial.
old='''    val requestRedial=rememberVoiceCallPermissionAction(vm){ vm.redialVoiceCall(call) }'''
new='''    val requestAudioRedial=rememberVoiceCallPermissionAction(vm){ vm.redialVoiceCall(call) }
    val requestVideoRedial=rememberVideoCallPermissionAction(vm){ vm.redialVoiceCall(call) }
    val requestRedial=if(call.type==CallType.VIDEO) requestVideoRedial else requestAudioRedial'''
s=s.replace(old,new,1)

# Renderer late EGL initialization safety.
s=s.replace('''                var remoteRenderer by remember(call.callId) { mutableStateOf<org.webrtc.SurfaceViewRenderer?>(null) }
                var localRenderer by remember(call.callId) { mutableStateOf<org.webrtc.SurfaceViewRenderer?>(null) }''',
'''                var remoteRenderer by remember(call.callId) { mutableStateOf<org.webrtc.SurfaceViewRenderer?>(null) }
                var localRenderer by remember(call.callId) { mutableStateOf<org.webrtc.SurfaceViewRenderer?>(null) }
                var remoteRendererInitialized by remember(call.callId) { mutableStateOf(false) }
                var localRendererInitialized by remember(call.callId) { mutableStateOf(false) }''',1)

s=s.replace('''                            vm.callVideoEglContext()?.let { init(it,null) }
                            setEnableHardwareScaler(true)
                            setMirror(false)
                            remoteRenderer=this
                            vm.attachRemoteVideoSink(this)''',
'''                            vm.callVideoEglContext()?.let { init(it,null); remoteRendererInitialized=true }
                            setEnableHardwareScaler(true)
                            setMirror(false)
                            remoteRenderer=this
                            if(remoteRendererInitialized) vm.attachRemoteVideoSink(this)''',1)

s=s.replace('''                    update={ renderer ->
                        vm.callVideoEglContext()?.let { egl ->
                            if(remoteRenderer!==renderer) {
                                runCatching { renderer.init(egl,null) }
                                remoteRenderer=renderer
                                vm.attachRemoteVideoSink(renderer)
                            }
                        }
                    }''',
'''                    update={ renderer ->
                        if(!remoteRendererInitialized) {
                            vm.callVideoEglContext()?.let { egl ->
                                runCatching { renderer.init(egl,null) }.onSuccess {
                                    remoteRendererInitialized=true
                                    remoteRenderer=renderer
                                    vm.attachRemoteVideoSink(renderer)
                                }
                            }
                        }
                    }''',1)

s=s.replace('''                                vm.callVideoEglContext()?.let { init(it,null) }
                                setEnableHardwareScaler(true)
                                setMirror(call.frontCamera)
                                setZOrderMediaOverlay(true)
                                localRenderer=this
                                vm.attachLocalVideoSink(this)''',
'''                                vm.callVideoEglContext()?.let { init(it,null); localRendererInitialized=true }
                                setEnableHardwareScaler(true)
                                setMirror(call.frontCamera)
                                setZOrderMediaOverlay(true)
                                localRenderer=this
                                if(localRendererInitialized) vm.attachLocalVideoSink(this)''',1)

s=s.replace('''                        update={ it.setMirror(call.frontCamera) }''',
'''                        update={ renderer ->
                            if(!localRendererInitialized) {
                                vm.callVideoEglContext()?.let { egl ->
                                    runCatching { renderer.init(egl,null) }.onSuccess {
                                        localRendererInitialized=true
                                        localRenderer=renderer
                                        vm.attachLocalVideoSink(renderer)
                                    }
                                }
                            }
                            renderer.setMirror(call.frontCamera)
                        }''',1)

p.write_text(s)

print('Messenger 2.26 video runtime hardening applied')
