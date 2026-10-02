from pathlib import Path
root=Path('/tmp/messenger-build/messenger_live')

p=root/'app/build.gradle.kts'
s=p.read_text().replace('versionCode = 30','versionCode = 31').replace('versionName = "2.19"','versionName = "2.20"')
p.write_text(s)

p=root/'app/src/main/java/com/example/messengerui/CallModels.kt'
s=p.read_text()
s=s.replace('    val iceGatheringComplete: Boolean = false\n)', '    val iceGatheringComplete: Boolean = false,\n    val inboundAudioKbps: Int? = null,\n    val outboundAudioKbps: Int? = null,\n    val localCandidateType: String? = null,\n    val remoteCandidateType: String? = null,\n    val selectedProtocol: String? = null,\n    val mediaStalled: Boolean = false\n)', 1)
s=s.replace('    val reconnectCount: Int = 0\n)', '    val reconnectCount: Int = 0,\n    val inboundAudioKbps: Int? = null,\n    val outboundAudioKbps: Int? = null,\n    val localCandidateType: String? = null,\n    val remoteCandidateType: String? = null,\n    val selectedProtocol: String? = null,\n    val mediaStalled: Boolean = false,\n    val lastRecoveryReason: String? = null\n)', 1)
p.write_text(s)

p=root/'app/src/main/java/com/example/messengerui/WebRtcVoiceEngine.kt'
s=p.read_text()
s=s.replace('    private var lastStatsAtMs: Long = 0L', '    private var lastStatsAtMs: Long = 0L\n    private var lastInboundBytes: Long = 0L\n    private var lastOutboundBytes: Long = 0L\n    private var lastInboundAdvanceAtMs: Long = 0L\n    private var lastOutboundAdvanceAtMs: Long = 0L\n    private var connectedSinceMs: Long = 0L')
s=s.replace('AudioManager.AUDIOFOCUS_GAIN -> onAudioInterruption(false)', 'AudioManager.AUDIOFOCUS_GAIN -> { onAudioInterruption(false); reapplySelectedRoute() }')
s=s.replace('''        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshRoutes()
            if (selectedRoute !in availableRouteLabels()) selectBestDefaultRoute()
        }''','''        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshRoutes()
            val preferredAdded=addedDevices.orEmpty().any { routeLabel(it)=="Bluetooth" || routeLabel(it)=="Headset" }
            if(selectedRoute !in availableRouteLabels() || (selectedRoute=="Phone" && preferredAdded)) selectBestDefaultRoute() else reapplySelectedRoute()
        }''')
s=s.replace('''        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refreshRoutes()
            if (selectedRoute !in availableRouteLabels()) selectBestDefaultRoute()
        }''','''        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refreshRoutes()
            if(selectedRoute !in availableRouteLabels()) selectBestDefaultRoute() else reapplySelectedRoute()
        }''')

start=s.index('                var received = 0L\n')
end=s.index('                val quality = when {', start)
new_stats='''                var received=0L
                var lost=0L
                var inboundBytes=0L
                var outboundBytes=0L
                values.forEach { stat ->
                    val mediaKind=(stat.members["kind"] ?: stat.members["mediaType"])?.toString()
                    if(mediaKind=="audio" && stat.type=="inbound-rtp"){
                        received += (stat.members["packetsReceived"] as? Number)?.toLong() ?: 0L
                        lost += ((stat.members["packetsLost"] as? Number)?.toLong() ?: 0L).coerceAtLeast(0L)
                        inboundBytes += (stat.members["bytesReceived"] as? Number)?.toLong() ?: 0L
                    }
                    if(mediaKind=="audio" && stat.type=="outbound-rtp") outboundBytes += (stat.members["bytesSent"] as? Number)?.toLong() ?: 0L
                }
                val totalBytes=inboundBytes+outboundBytes
                val lossPct=if(received+lost>0) lost.toDouble()*100.0/(received+lost).toDouble() else null
                val now=System.currentTimeMillis()
                val deltaMs=if(lastStatsAtMs>0L)(now-lastStatsAtMs).coerceAtLeast(1L) else 0L
                val deltaKbps=if(lastStatsAtMs>0L && totalBytes>=lastStatsBytes)(((totalBytes-lastStatsBytes).toDouble()*8.0)/deltaMs).toInt().coerceAtLeast(0) else null
                val inboundKbps=if(lastStatsAtMs>0L && inboundBytes>=lastInboundBytes)(((inboundBytes-lastInboundBytes).toDouble()*8.0)/deltaMs).toInt().coerceAtLeast(0) else null
                val outboundKbps=if(lastStatsAtMs>0L && outboundBytes>=lastOutboundBytes)(((outboundBytes-lastOutboundBytes).toDouble()*8.0)/deltaMs).toInt().coerceAtLeast(0) else null
                if(inboundBytes>lastInboundBytes) lastInboundAdvanceAtMs=now
                if(outboundBytes>lastOutboundBytes) lastOutboundAdvanceAtMs=now
                if(lastInboundAdvanceAtMs==0L && inboundBytes>0L) lastInboundAdvanceAtMs=now
                if(lastOutboundAdvanceAtMs==0L && outboundBytes>0L) lastOutboundAdvanceAtMs=now
                lastStatsAtMs=now; lastStatsBytes=totalBytes; lastInboundBytes=inboundBytes; lastOutboundBytes=outboundBytes
                val mediaStalled=connectedSinceMs>0L && now-connectedSinceMs>=25_000L && lastOutboundAdvanceAtMs>0L && now-lastOutboundAdvanceAtMs<=8_000L && (lastInboundAdvanceAtMs==0L || now-lastInboundAdvanceAtMs>=25_000L)
                val bitrateKbps=availableKbps?.takeIf { it>0 } ?: deltaKbps
'''
s=s[:start]+new_stats+s[end:]
old='                onQuality(CallQualityStats(quality, rttMs, lossPct, bitrateKbps, relayInUse, relayCandidateAvailable, iceGatheringComplete))'
new='''                val localType=localCandidate?.members?.get("candidateType")?.toString()
                val remoteType=remoteCandidate?.members?.get("candidateType")?.toString()
                val protocol=(localCandidate?.members?.get("protocol") ?: remoteCandidate?.members?.get("protocol"))?.toString()
                onQuality(CallQualityStats(quality,rttMs,lossPct,bitrateKbps,relayInUse,relayCandidateAvailable,iceGatheringComplete,inboundKbps,outboundKbps,localType,remoteType,protocol,mediaStalled))'''
if old not in s: raise SystemExit('quality callback missing')
s=s.replace(old,new,1)
s=s.replace('    private fun selectBestDefaultRoute() { setRoute(preferredNonSpeakerRoute()) }','''    private fun selectBestDefaultRoute() { setRoute(preferredNonSpeakerRoute()) }
    private fun reapplySelectedRoute() {
        if(closed)return
        if(selectedRoute !in availableRouteLabels()){ selectBestDefaultRoute(); return }
        if(Build.VERSION.SDK_INT>=31){
            availableCommunicationDevices().firstOrNull { routeLabel(it)==selectedRoute }?.let { target ->
                if(audioManager.communicationDevice?.id!=target.id) runCatching { audioManager.setCommunicationDevice(target) }
            }
        } else setRoute(selectedRoute)
        updateProximity(); onRoute(selectedRoute)
    }''')
s=s.replace('PeerConnection.IceConnectionState.CONNECTED,PeerConnection.IceConnectionState.COMPLETED->onState(VoiceCallPhase.CONNECTED)', 'PeerConnection.IceConnectionState.CONNECTED,PeerConnection.IceConnectionState.COMPLETED->{ if(connectedSinceMs==0L)connectedSinceMs=System.currentTimeMillis(); onState(VoiceCallPhase.CONNECTED) }')
s=s.replace('PeerConnection.PeerConnectionState.CONNECTED->onState(VoiceCallPhase.CONNECTED)', 'PeerConnection.PeerConnectionState.CONNECTED->{ if(connectedSinceMs==0L)connectedSinceMs=System.currentTimeMillis(); onState(VoiceCallPhase.CONNECTED) }')
p.write_text(s)

p=root/'app/src/main/java/com/example/messengerui/CallAlertStore.kt'
s=p.read_text().replace('    private fun cleanup(context: Context) {','    fun cleanupExpired(context: Context) {').replace('cleanup(context)','cleanupExpired(context)')
p.write_text(s)
p=root/'app/src/main/java/com/example/messengerui/MessengerApplication.kt'
s=p.read_text().replace('        PushRegistration.initialize(this)','        CallAlertStore.cleanupExpired(this)\n        PushRegistration.initialize(this)')
p.write_text(s)

p=root/'app/src/main/java/com/example/messengerui/AppViewModel.kt'
s=p.read_text()
s=s.replace('    private var callReconnectCount = 0','    private var callReconnectCount = 0\n    private var lastMediaStallRecoveryAtMs = 0L\n    private var acceptedLocallyCallId: String? = null',1)
accept='''    fun acceptVoiceCall() {
        val current=activeVoiceCall ?: return
        if(current.phase!=VoiceCallPhase.INCOMING_RINGING || callActionBusy) return
        callActionBusy=true
        val token=db.serverToken()'''
if accept not in s: raise SystemExit('accept anchor missing')
s=s.replace(accept,'''    fun acceptVoiceCall() {
        val current=activeVoiceCall ?: return
        if(current.phase!=VoiceCallPhase.INCOMING_RINGING || callActionBusy) return
        callActionBusy=true
        acceptedLocallyCallId=current.callId
        val token=db.serverToken()''',1)
s=s.replace('.onFailure { lastToast=it.userMessage("Unable to accept call") }\n            callActionBusy=false','.onFailure { acceptedLocallyCallId=null; lastToast=it.userMessage("Unable to accept call") }\n            callActionBusy=false',1)
s=s.replace('''        if(!networkAvailable || current.phase == VoiceCallPhase.ENDED) return
        val now=System.currentTimeMillis()''','''        if(!networkAvailable || current.phase == VoiceCallPhase.ENDED) return
        if(callReconnectCount>=6 && reason!="network_change") return
        val now=System.currentTimeMillis()
        callDiagnostics=callDiagnostics.copy(lastRecoveryReason=reason)''',1)
s=s.replace('''            reconnectCount=callReconnectCount
        )
    }''','''            reconnectCount=callReconnectCount,
            inboundAudioKbps=quality.inboundAudioKbps,
            outboundAudioKbps=quality.outboundAudioKbps,
            localCandidateType=quality.localCandidateType,
            remoteCandidateType=quality.remoteCandidateType,
            selectedProtocol=quality.selectedProtocol,
            mediaStalled=quality.mediaStalled
        )
        val current=activeVoiceCall
        if(quality.mediaStalled && current?.phase==VoiceCallPhase.CONNECTED && networkAvailable){
            val now=System.currentTimeMillis()
            if(now-lastMediaStallRecoveryAtMs>=30_000L){
                lastMediaStallRecoveryAtMs=now
                requestCallRecovery("audio_receive_stall", forceRebuild=callReconnectCount>=1)
            }
        }
    }''',1)
s=s.replace('''        callInitialOfferSent=false; voiceMediaConnected=false; callNetworkHandle=null; lastIceRestartAtMs=0L; lastIncomingRecoveryAtMs=0L; callReconnectCount=0
        callDiagnostics=CallDiagnostics()''','''        callInitialOfferSent=false; voiceMediaConnected=false; callNetworkHandle=null; lastIceRestartAtMs=0L; lastIncomingRecoveryAtMs=0L; callReconnectCount=0
        lastMediaStallRecoveryAtMs=0L; acceptedLocallyCallId=null
        callDiagnostics=CallDiagnostics()''')
old='''                NotificationHelper.cancelIncomingCall(getApplication(),remote.id)
                applyRemoteCall(remote)
                if(!remote.incoming){'''
new='''                NotificationHelper.cancelIncomingCall(getApplication(),remote.id)
                val current=activeVoiceCall
                val acceptedHere=acceptedLocallyCallId==remote.id
                if(remote.incoming && current?.callId==remote.id && !acceptedHere){ finishVoiceCall(remote,false); return }
                applyRemoteCall(remote)
                if(!remote.incoming){'''
if old not in s: raise SystemExit('accepted event anchor missing')
s=s.replace(old,new,1)
p.write_text(s)

print('Messenger 2.20 hardening applied')
