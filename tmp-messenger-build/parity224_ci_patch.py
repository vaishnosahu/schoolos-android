from pathlib import Path
root=Path('/tmp/messenger-build/messenger_live')

p=root/'app/build.gradle.kts'
s=p.read_text().replace('versionCode = 34','versionCode = 35').replace('versionName = "2.23"','versionName = "2.24"')
p.write_text(s)

p=root/'app/src/main/java/com/example/messengerui/WebRtcVoiceEngine.kt'
s=p.read_text()
s=s.replace('''class WebRtcVoiceEngine(
    context: Context,
    iceServers: List<IceServerConfig>,''','''class WebRtcVoiceEngine(
    context: Context,
    iceServers: List<IceServerConfig>,
    private val forceRelayOnly: Boolean = false,''',1)
s=s.replace('''            val config = PeerConnection.RTCConfiguration(rtcIce).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            }''','''            val config = PeerConnection.RTCConfiguration(rtcIce).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
                if (forceRelayOnly) iceTransportsType = PeerConnection.IceTransportsType.RELAY
            }''',1)
p.write_text(s)

p=root/'app/src/main/java/com/example/messengerui/AppViewModel.kt'
s=p.read_text()
s=s.replace('''    var silenceUnknownCallers by mutableStateOf(db.getBooleanSetting("silence_unknown_callers", false))
        private set''','''    var silenceUnknownCallers by mutableStateOf(db.getBooleanSetting("silence_unknown_callers", false))
        private set
    var relayValidationMode by mutableStateOf(db.getBooleanSetting("relay_validation_mode", false))
        private set
    var waitingCallActionBusy by mutableStateOf(false)
        private set''',1)
anchor='''    fun updateSilenceUnknownCallers(value:Boolean) {
        silenceUnknownCallers=value
        db.setBooleanSetting("silence_unknown_callers",value)
    }
'''
if anchor not in s: raise SystemExit('silence setter anchor missing')
s=s.replace(anchor,anchor+'''    fun updateRelayValidationMode(value:Boolean) {
        relayValidationMode=value
        db.setBooleanSetting("relay_validation_mode",value)
    }

''',1)

old='''            if(switchFromCurrent && activeVoiceCall?.callId!=null && activeVoiceCall?.callId!=callId){
                closeVoiceEngine()
                CallForegroundService.stop(getApplication())
                activeVoiceCall=null
                waitingVoiceCall=null
            }
            runCatching { withContext(Dispatchers.IO){ api.voiceCallState(token,callId,0L) } }'''
new='''            if(switchFromCurrent && activeVoiceCall?.callId!=null && activeVoiceCall?.callId!=callId){
                val current=activeVoiceCall
                if(current!=null) {
                    runCatching { withContext(Dispatchers.IO){ api.endVoiceCall(token,current.callId,"switched_call") } }
                        .onSuccess { upsertServerCallLog(it) }
                }
                closeVoiceEngine()
                CallForegroundService.stop(getApplication())
                current?.let { NotificationHelper.markCallTerminal(getApplication(),it.callId) }
                activeVoiceCall=null
                waitingVoiceCall=null
            }
            runCatching { withContext(Dispatchers.IO){ api.voiceCallState(token,callId,0L) } }'''
if old not in s: raise SystemExit('notification switch anchor missing')
s=s.replace(old,new,1)

marker='''    fun declineVoiceCall() {
'''
waiting='''    fun declineWaitingVoiceCall() {
        val waiting=waitingVoiceCall ?: return
        if(waitingCallActionBusy) return
        waitingCallActionBusy=true
        val token=db.serverToken()
        viewModelScope.launch {
            try {
                val remote=withContext(Dispatchers.IO){ api.declineVoiceCall(token,waiting.id) }
                upsertServerCallLog(remote)
                NotificationHelper.markCallTerminal(getApplication(),waiting.id)
                waitingVoiceCall=null
            } catch(t:Throwable) {
                lastToast=t.userMessage("Unable to decline waiting call")
            } finally {
                waitingCallActionBusy=false
            }
        }
    }

    fun answerWaitingVoiceCall() {
        val waiting=waitingVoiceCall ?: return
        val current=activeVoiceCall ?: return
        if(waitingCallActionBusy || callActionBusy) return
        waitingCallActionBusy=true
        acceptedLocallyCallId=waiting.id
        val token=db.serverToken()
        viewModelScope.launch {
            try {
                val ended=withContext(Dispatchers.IO){ api.endVoiceCall(token,current.callId,"switched_call") }
                val config=withContext(Dispatchers.IO){ api.voiceCallConfig(token) }
                val sync=withContext(Dispatchers.IO){ api.voiceCallState(token,waiting.id,0L) }
                val accepted=withContext(Dispatchers.IO){ api.acceptVoiceCall(token,waiting.id) }

                closeVoiceEngine()
                CallForegroundService.stop(getApplication())
                NotificationHelper.markCallTerminal(getApplication(),current.callId)
                upsertServerCallLog(ended)

                voiceCallConfig=config
                callSignalCursor=sync.signalCursor
                callConnectedReported=false
                callInitialOfferSent=false
                callReconnectCount=0
                lastIceRestartAtMs=0L
                callNetworkHandle=connectivityManager.activeNetwork?.networkHandle
                callDiagnostics=CallDiagnostics(networkLabel=currentCallNetworkLabel(),turnConfigured=config.turnConfigured)
                applyRemoteCall(accepted)
                activeVoiceCall=activeVoiceCall?.copy(phase=VoiceCallPhase.CONNECTING)
                waitingVoiceCall=null
                ensureVoiceEngine(config)
                NotificationHelper.cancelIncomingCall(getApplication(),accepted.id)
                CallForegroundService.start(getApplication(),accepted.id,accepted.peerName)
                sync.signals.forEach { processVoiceSignal(it) }
                upsertServerCallLog(accepted)
                navigate(AppScreen.VoiceCall(accepted.id))
                startCallStateLoop()
            } catch(t:Throwable) {
                acceptedLocallyCallId=null
                lastToast=t.userMessage("Unable to switch calls")
            } finally {
                waitingCallActionBusy=false
            }
        }
    }

'''
if marker not in s: raise SystemExit('decline marker missing')
s=s.replace(marker,waiting+marker,1)

old='''        voiceEngine=WebRtcVoiceEngine(getApplication(),config.iceServers,
            onLocalSignal={ type,payload -> sendVoiceSignal(type,payload) },'''
new='''        voiceEngine=WebRtcVoiceEngine(getApplication(),config.iceServers,relayValidationMode,
            onLocalSignal={ type,payload -> sendVoiceSignal(type,payload) },'''
if old not in s: raise SystemExit('engine constructor anchor missing')
s=s.replace(old,new,1)
p.write_text(s)

p=root/'app/src/main/java/com/example/messengerui/MessengerApp.kt'
s=p.read_text()
anchor='''        Spacer(Modifier.height(54.dp))
        Avatar(call?.peerName ?: "Messenger", 124.dp)'''
insert='''        vm.waitingVoiceCall?.let { waiting ->
            Surface(
                modifier=Modifier.fillMaxWidth().padding(top=14.dp),
                shape=RoundedCornerShape(18.dp),
                color=MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("Incoming call waiting",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                    Text(waiting.peerName.ifBlank { waiting.peerPhone.ifBlank { "Unknown caller" } },fontWeight=FontWeight.SemiBold)
                    if(waiting.peerPhone.isNotBlank()) Text(waiting.peerPhone,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick={vm.declineWaitingVoiceCall()},enabled=!vm.waitingCallActionBusy,modifier=Modifier.weight(1f)){Text("Decline")}
                        Button(onClick={vm.answerWaitingVoiceCall()},enabled=!vm.waitingCallActionBusy,modifier=Modifier.weight(1f)){Text("End & answer")}
                    }
                }
            }
        }

        Spacer(Modifier.height(54.dp))
        Avatar(call?.peerName ?: "Messenger", 124.dp)'''
if anchor not in s: raise SystemExit('voice screen anchor missing')
s=s.replace(anchor,insert,1)

diag='''                    Text("Reconnects: \${d.reconnectCount}")
                    d.lastRecoveryReason?.let { Text("Last recovery: \${it.replace('_',' ')}") }'''
diag_new='''                    Text("Reconnects: \${d.reconnectCount}")
                    d.lastRecoveryReason?.let { Text("Last recovery: \${it.replace('_',' ')}") }
                    HorizontalDivider()
                    Text("Relay validation: \${if(vm.relayValidationMode) "Relay only for calls" else "Normal routing"}")
                    Text(
                        if(vm.relayValidationMode) "TURN-only validation is enabled. Disable after testing." else "Normal calls prefer direct media and use TURN when needed.",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick={vm.updateRelayValidationMode(!vm.relayValidationMode)}) {
                        Text(if(vm.relayValidationMode) "Use normal routing" else "Validate TURN on next call")
                    }'''
if diag not in s:
    diag='''                    Text("Reconnects: \${d.reconnectCount}")'''
    diag_new='''                    Text("Reconnects: \${d.reconnectCount}")
                    HorizontalDivider()
                    Text("Relay validation: \${if(vm.relayValidationMode) "Relay only for calls" else "Normal routing"}")
                    TextButton(onClick={vm.updateRelayValidationMode(!vm.relayValidationMode)}) { Text(if(vm.relayValidationMode) "Use normal routing" else "Validate TURN on next call") }'''
if diag not in s: raise SystemExit('diagnostics anchor missing')
s=s.replace(diag,diag_new,1)
p.write_text(s)

print('2.24 call waiting and relay validation applied')
