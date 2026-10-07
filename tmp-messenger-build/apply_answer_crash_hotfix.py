from pathlib import Path

p=Path("/tmp/messenger-build/app/src/main/java/com/example/messengerui/AppViewModel.kt")
s=p.read_text()

def replace_once(text, old, new, label):
    count=text.count(old)
    if count!=1:
        raise SystemExit(f"{label}: expected 1 match, found {count}")
    return text.replace(old,new,1)

s=replace_once(s,'import kotlinx.coroutines.suspendCancellableCoroutine\n','',"remove suspend bridge import")
s=replace_once(s,'import kotlin.coroutines.resume\n','',"remove resume import")
s=replace_once(
    s,
    '    private var callSignalCursor: Long = 0L\n    private val processedCallSignalIds = LinkedHashSet<Long>()\n',
    '    private var callSignalCursor: Long = 0L\n    private val processedCallSignalIds = LinkedHashSet<Long>()\n    private var applyingRemoteSignalId: Long? = null\n',
    "add in-flight signal guard"
)

start=s.index('    private suspend fun processVoiceSignals(signals:List<RemoteCallSignal>) {')
end=s.index('    private fun onVoiceEngineState(',start)
block='''    private suspend fun processVoiceSignals(signals:List<RemoteCallSignal>) {
        val current=activeVoiceCall ?: return
        if(current.incoming && current.phase==VoiceCallPhase.INCOMING_RINGING){
            CallLifecycleLog.info("signal_deferred_until_answer",current.callId,"count=${signals.size}")
            return
        }
        if(applyingRemoteSignalId!=null) return
        for(signal in signals.sortedBy { it.id }) {
            if(signal.id>0L && (signal.id<=callSignalCursor || processedCallSignalIds.contains(signal.id))) {
                CallLifecycleLog.info("signal_duplicate_ignored", current.callId, "id=${signal.id} type=${signal.type}")
                continue
            }
            val config=voiceCallConfig ?: runCatching { withContext(Dispatchers.IO){ api.voiceCallConfig(db.serverToken()) } }.getOrNull()
            if(config==null){
                CallLifecycleLog.info("signal_deferred_no_config",current.callId,"id=${signal.id} type=${signal.type}")
                return
            }
            ensureVoiceEngine(config)
            val engine=voiceEngine ?: return
            when(signal.type.uppercase()){
                "OFFER", "ANSWER" -> {
                    val generation=voiceEngineGeneration
                    applyingRemoteSignalId=signal.id
                    val callback:(WebRtcVoiceEngine.SignalApplyResult)->Unit = { result ->
                        viewModelScope.launch {
                            if(activeVoiceCall?.callId!=current.callId || voiceEngineGeneration!=generation || applyingRemoteSignalId!=signal.id) return@launch
                            if(result==WebRtcVoiceEngine.SignalApplyResult.APPLIED || result==WebRtcVoiceEngine.SignalApplyResult.IGNORED){
                                markRemoteSignalConsumed(signal,result)
                            } else {
                                CallLifecycleLog.info("signal_not_consumed",current.callId,"id=${signal.id} type=${signal.type} result=${result.name}")
                            }
                            applyingRemoteSignalId=null
                            callStateWake.trySend(Unit)
                        }
                    }
                    if(signal.type.equals("OFFER",true)) engine.acceptOffer(signal.payload.optString("sdp"),callback)
                    else engine.acceptAnswer(signal.payload.optString("sdp"),callback)
                    return
                }
                "ICE" -> {
                    val result=engine.addRemoteIce(signal.payload)
                    if(result==WebRtcVoiceEngine.SignalApplyResult.APPLIED || result==WebRtcVoiceEngine.SignalApplyResult.IGNORED){
                        markRemoteSignalConsumed(signal,result)
                    } else {
                        CallLifecycleLog.info("signal_not_consumed",current.callId,"id=${signal.id} type=${signal.type} result=${result.name}")
                        return
                    }
                }
                else -> markRemoteSignalConsumed(signal,WebRtcVoiceEngine.SignalApplyResult.IGNORED)
            }
        }
    }

    private fun markRemoteSignalConsumed(signal:RemoteCallSignal,result:WebRtcVoiceEngine.SignalApplyResult) {
        if(signal.id>0L){
            processedCallSignalIds.add(signal.id)
            callSignalCursor=maxOf(callSignalCursor,signal.id)
        }
        while(processedCallSignalIds.size>512) processedCallSignalIds.remove(processedCallSignalIds.first())
        CallLifecycleLog.info("signal_consumed",activeVoiceCall?.callId,"id=${signal.id} type=${signal.type} result=${result.name}")
    }

'''
s=s[:start]+block+s[end:]

s=replace_once(
    s,
    'callStateJob?.cancel(); callStateJob=null; callSignalCursor=0L; processedCallSignalIds.clear(); callConnectedReported=false',
    'callStateJob?.cancel(); callStateJob=null; callSignalCursor=0L; processedCallSignalIds.clear(); applyingRemoteSignalId=null; callConnectedReported=false',
    "clear in-flight signal on call finish"
)
s=replace_once(
    s,
    'clearVoiceSignalQueue("engine_closed")\n        voiceEngineGeneration += 1',
    'clearVoiceSignalQueue("engine_closed")\n        applyingRemoteSignalId=null\n        voiceEngineGeneration += 1',
    "clear in-flight signal on engine close"
)

p.write_text(s)
print("ANSWER_CRASH_HOTFIX_TRANSFORM_APPLIED")
