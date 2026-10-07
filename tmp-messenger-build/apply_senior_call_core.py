from pathlib import Path

root = Path("/tmp/messenger-build/app/src/main/java/com/example/messengerui")
vm_path = root / "AppViewModel.kt"
we_path = root / "WebRtcVoiceEngine.kt"

def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)

vm = vm_path.read_text()
vm = replace_once(vm, "import kotlinx.coroutines.launch\n", "import kotlinx.coroutines.launch\nimport kotlinx.coroutines.suspendCancellableCoroutine\n", "vm suspend import")
vm = replace_once(vm, "import org.json.JSONObject\n", "import org.json.JSONObject\nimport kotlin.coroutines.resume\n", "vm resume import")

for old, new, label in [
    ('sync.signals.sortedBy { it.id }.forEach { processVoiceSignal(it) }\n                    callSignalCursor=maxOf(callSignalCursor,sync.signalCursor)',
     'processVoiceSignals(sync.signals)', 'notification cursor'),
    ('sync.signals.sortedBy { it.id }.forEach { processVoiceSignal(it) }\n                callSignalCursor=maxOf(callSignalCursor,sync.signalCursor)',
     'processVoiceSignals(sync.signals)', 'accept cursor'),
    ('sync.signals.sortedBy { it.id }.forEach { processVoiceSignal(it) }\n                            callSignalCursor=maxOf(callSignalCursor,sync.signalCursor)',
     'processVoiceSignals(sync.signals)', 'state cursor'),
    ('for(signal in packet.signals.sortedBy { it.id }) processVoiceSignal(signal)\n            callSignalCursor=maxOf(callSignalCursor,packet.signalCursor)',
     'processVoiceSignals(packet.signals)', 'recovery cursor'),
]:
    vm = replace_once(vm, old, new, label)

start = vm.index("    private suspend fun processVoiceSignal(signal:RemoteCallSignal) {")
end = vm.index("    private fun onVoiceEngineState(", start)
new_vm_block = '''    private suspend fun processVoiceSignals(signals:List<RemoteCallSignal>) {
        for(signal in signals.sortedBy { it.id }) {
            if(!processVoiceSignal(signal)) break
        }
    }

    private suspend fun processVoiceSignal(signal:RemoteCallSignal):Boolean {
        activeVoiceCall ?: return false
        if(signal.id>0L && (signal.id<=callSignalCursor || processedCallSignalIds.contains(signal.id))) {
            CallLifecycleLog.info("signal_duplicate_ignored", activeVoiceCall?.callId, "id=${signal.id} type=${signal.type}")
            return true
        }
        val config=voiceCallConfig ?: runCatching { withContext(Dispatchers.IO){ api.voiceCallConfig(db.serverToken()) } }.getOrNull()
        if(config==null){
            CallLifecycleLog.info("signal_deferred_no_config",activeVoiceCall?.callId,"id=${signal.id} type=${signal.type}")
            return false
        }
        ensureVoiceEngine(config)
        val engine=voiceEngine ?: return false
        val result=when(signal.type.uppercase()){
            "OFFER" -> suspendCancellableCoroutine { cont ->
                engine.acceptOffer(signal.payload.optString("sdp")) { if(cont.isActive) cont.resume(it) }
            }
            "ANSWER" -> suspendCancellableCoroutine { cont ->
                engine.acceptAnswer(signal.payload.optString("sdp")) { if(cont.isActive) cont.resume(it) }
            }
            "ICE" -> engine.addRemoteIce(signal.payload)
            else -> WebRtcVoiceEngine.SignalApplyResult.IGNORED
        }
        val consumed=result==WebRtcVoiceEngine.SignalApplyResult.APPLIED || result==WebRtcVoiceEngine.SignalApplyResult.IGNORED
        if(!consumed){
            CallLifecycleLog.info("signal_not_consumed",activeVoiceCall?.callId,"id=${signal.id} type=${signal.type} result=${result.name}")
            return false
        }
        if(signal.id>0L){
            processedCallSignalIds.add(signal.id)
            callSignalCursor=maxOf(callSignalCursor,signal.id)
        }
        while(processedCallSignalIds.size>512) processedCallSignalIds.remove(processedCallSignalIds.first())
        CallLifecycleLog.info("signal_consumed", activeVoiceCall?.callId, "id=${signal.id} type=${signal.type} result=${result.name}")
        return true
    }

'''
vm = vm[:start] + new_vm_block + vm[end:]
vm_path.write_text(vm)

we = we_path.read_text()
we = replace_once(
    we,
    '''private object MessengerWebRtcEgl {
    val base: EglBase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { EglBase.create() }
    val context: EglBase.Context get() = base.eglBaseContext
}

class WebRtcVoiceEngine(''',
    '''private object MessengerWebRtcEgl {
    val base: EglBase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { EglBase.create() }
    val context: EglBase.Context get() = base.eglBaseContext
}

private object MessengerWebRtcRuntime {
    @Volatile private var initialized = false
    fun ensureInitialized(context: Context) {
        if(initialized) return
        synchronized(this) {
            if(initialized) return
            PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context.applicationContext).createInitializationOptions())
            initialized = true
        }
    }
}

class WebRtcVoiceEngine(''',
    "webrtc runtime"
)
we = replace_once(we, ') {\n    private val appContext', ') {\n    enum class SignalApplyResult { APPLIED, IGNORED, DEFERRED, FAILED }\n\n    private val appContext', "result enum")
we = replace_once(
    we,
    'PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(appContext).createInitializationOptions())',
    'MessengerWebRtcRuntime.ensureInitialized(appContext)',
    "one time init"
)

start = we.index("    private fun applyRemoteOffer(")
end = we.index("    private fun prepareRemoteDescriptionUpdate()", start)
new_negotiation = '''    private fun applyRemoteOffer(pc:PeerConnection,remote:SessionDescription,onResult:(SignalApplyResult)->Unit) {
        prepareRemoteDescriptionUpdate()
        ignoreRemoteOfferIce=false
        pc.setRemoteDescription(object:SdpAdapter(){
            override fun onSetSuccess() {
                completeRemoteDescriptionUpdate(pc)
                normalizeVideoTransceiverForTwoWayMedia()
                syncRemoteVideoTrackFromTransceivers()
                scheduleRemoteVideoSyncBurst()
                pc.createAnswer(object:SdpAdapter(){
                    override fun onCreateSuccess(desc: SessionDescription?) {
                        if(desc==null){ onResult(SignalApplyResult.FAILED); return }
                        if(videoEnabled && !containsVideoMedia(desc.description)){
                            onError("Video media could not be negotiated")
                            onResult(SignalApplyResult.FAILED)
                            return
                        }
                        pc.setLocalDescription(object:SdpAdapter(){
                            override fun onSetSuccess(){
                                onLocalSignal("ANSWER",JSONObject().put("sdp",desc.description))
                                onResult(SignalApplyResult.APPLIED)
                            }
                            override fun onSetFailure(error:String?){
                                onError(error ?: "Unable to apply local call answer")
                                onResult(SignalApplyResult.FAILED)
                            }
                        },desc)
                    }
                    override fun onCreateFailure(error:String?) {
                        onError(error ?: "Unable to create call answer")
                        onResult(SignalApplyResult.FAILED)
                    }
                },MediaConstraints())
            }
            override fun onSetFailure(error:String?) {
                failRemoteDescriptionUpdate()
                onError(error ?: "Unable to apply call offer")
                onResult(SignalApplyResult.FAILED)
            }
        },remote)
    }

    fun acceptOffer(sdp:String,onResult:(SignalApplyResult)->Unit) {
        val pc=peer ?: run { onError("Call engine unavailable"); onResult(SignalApplyResult.FAILED); return }
        if(videoEnabled && !containsVideoMedia(sdp)){
            onError("Remote video media is unavailable")
            onResult(SignalApplyResult.FAILED)
            return
        }
        val remote=SessionDescription(SessionDescription.Type.OFFER,sdp)
        val state=runCatching { pc.signalingState() }.getOrNull()
        val readyForOffer=!offerInFlight && (state==PeerConnection.SignalingState.STABLE || settingRemoteAnswerPending)
        val collision=!readyForOffer
        if(collision){
            CallLifecycleLog.info("offer_collision_detected",detail="role=${if(incomingCall) "polite" else "impolite"} state=${state?.name ?: "unknown"}")
            if(!incomingCall){
                ignoreRemoteOfferIce=true
                CallLifecycleLog.info("offer_collision_ignored",detail="state=${state?.name ?: "unknown"}")
                onResult(SignalApplyResult.IGNORED)
                return
            }
            ++localOfferEpoch
            offerInFlight=false
            if(state==PeerConnection.SignalingState.HAVE_LOCAL_OFFER){
                val rollback=SessionDescription(SessionDescription.Type.ROLLBACK,"")
                pc.setLocalDescription(object:SdpAdapter(){
                    override fun onSetSuccess(){
                        CallLifecycleLog.info("offer_collision_rollback",detail="applied")
                        applyRemoteOffer(pc,remote,onResult)
                    }
                    override fun onSetFailure(error:String?){
                        CallLifecycleLog.info("offer_collision_rollback_failed",detail=error ?: "unknown")
                        onError(error ?: "Unable to resolve call negotiation collision")
                        onResult(SignalApplyResult.FAILED)
                    }
                },rollback)
                return
            }
            if(state!=PeerConnection.SignalingState.STABLE){
                CallLifecycleLog.info("offer_collision_deferred",detail="state=${state?.name ?: "unknown"}")
                onResult(SignalApplyResult.DEFERRED)
                return
            }
        }
        applyRemoteOffer(pc,remote,onResult)
    }

    fun acceptAnswer(sdp:String,onResult:(SignalApplyResult)->Unit) {
        val pc=peer ?: run { onError("Call engine unavailable"); onResult(SignalApplyResult.FAILED); return }
        if(videoEnabled && !containsVideoMedia(sdp)){
            onError("Remote video media is unavailable")
            onResult(SignalApplyResult.FAILED)
            return
        }
        val state=runCatching { pc.signalingState() }.getOrNull()
        if(state!=PeerConnection.SignalingState.HAVE_LOCAL_OFFER){
            val alreadyApplied=runCatching { pc.remoteDescription?.type==SessionDescription.Type.ANSWER }.getOrDefault(false)
            CallLifecycleLog.info(if(alreadyApplied) "stale_answer_ignored" else "answer_deferred",detail="state=${state?.name ?: "unknown"}")
            onResult(if(alreadyApplied) SignalApplyResult.IGNORED else SignalApplyResult.DEFERRED)
            return
        }
        ignoreRemoteOfferIce=false
        settingRemoteAnswerPending=true
        prepareRemoteDescriptionUpdate()
        pc.setRemoteDescription(object:SdpAdapter(){
            override fun onSetSuccess() {
                settingRemoteAnswerPending=false
                completeRemoteDescriptionUpdate(pc)
                normalizeVideoTransceiverForTwoWayMedia()
                syncRemoteVideoTrackFromTransceivers()
                scheduleRemoteVideoSyncBurst()
                onResult(SignalApplyResult.APPLIED)
            }
            override fun onSetFailure(error:String?) {
                settingRemoteAnswerPending=false
                failRemoteDescriptionUpdate()
                onError(error ?: "Unable to apply call answer")
                onResult(SignalApplyResult.FAILED)
            }
        },SessionDescription(SessionDescription.Type.ANSWER,sdp))
    }

'''
we = we[:start] + new_negotiation + we[end:]

start = we.index("    private fun completeRemoteDescriptionUpdate(")
end = we.index("    fun setMuted(", start)
new_ice = '''    private fun completeRemoteDescriptionUpdate(pc:PeerConnection) {
        synchronized(remoteIceLock) {
            if(closed) {
                pendingRemoteIceCandidates.clear()
                remoteDescriptionReady=false
                return
            }
            remoteDescriptionReady=true
            val pendingCount=pendingRemoteIceCandidates.size
            var flushed=0
            repeat(pendingCount) {
                val candidate=pendingRemoteIceCandidates.pollFirst() ?: return@repeat
                val added=runCatching { pc.addIceCandidate(candidate) }.getOrDefault(false)
                if(added) flushed += 1 else {
                    pendingRemoteIceCandidates.addLast(candidate)
                    CallLifecycleLog.info("remote_ice_apply_deferred",detail="buffered=${pendingRemoteIceCandidates.size}")
                }
            }
            if(flushed>0) CallLifecycleLog.info("remote_ice_flushed",detail="count=\$flushed remaining=${pendingRemoteIceCandidates.size}")
        }
    }

    private fun failRemoteDescriptionUpdate() {
        synchronized(remoteIceLock) {
            val dropped=pendingRemoteIceCandidates.size
            pendingRemoteIceCandidates.clear()
            remoteDescriptionReady=false
            if(dropped>0) CallLifecycleLog.info("remote_ice_discarded",detail="remote_description_failed count=\$dropped")
        }
    }

    fun addRemoteIce(payload:JSONObject):SignalApplyResult {
        val mid=payload.optString("sdp_mid").takeIf{it.isNotBlank()}
        val index=payload.optInt("sdp_mline_index",0)
        val value=payload.optString("candidate")
        if(value.isBlank()) return SignalApplyResult.IGNORED
        if (value.contains(" typ relay ", ignoreCase = true)) relayCandidateAvailable = true
        val candidate=IceCandidate(mid,index,value)
        synchronized(remoteIceLock) {
            val pc=peer ?: return SignalApplyResult.DEFERRED
            if(closed) return SignalApplyResult.FAILED
            if(ignoreRemoteOfferIce) {
                CallLifecycleLog.info("remote_ice_ignored_collision",detail="candidate_ignored")
                return SignalApplyResult.IGNORED
            }
            if(!remoteDescriptionReady) {
                pendingRemoteIceCandidates.addLast(candidate)
                CallLifecycleLog.info("remote_ice_buffered",detail="count=${pendingRemoteIceCandidates.size}")
                return SignalApplyResult.APPLIED
            }
            val added=runCatching { pc.addIceCandidate(candidate) }.getOrDefault(false)
            if(!added){
                CallLifecycleLog.info("remote_ice_apply_deferred",detail="addIceCandidate=false")
                return SignalApplyResult.DEFERRED
            }
            return SignalApplyResult.APPLIED
        }
    }

'''
we = we[:start] + new_ice + we[end:]
we_path.write_text(we)

print("SENIOR_CALL_CORE_TRANSFORM_APPLIED")
