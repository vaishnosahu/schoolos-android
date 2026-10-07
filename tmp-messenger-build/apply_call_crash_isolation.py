from pathlib import Path

root = Path('/tmp/messenger-build/app/src/main/java/com/example/messengerui')


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 match, found {count}')
    return text.replace(old, new, 1)

# ProcessExitDiagnostics.kt
p = root / 'ProcessExitDiagnostics.kt'
s = p.read_text()
s = replace_once(s,
    '    private const val KEY_CRASH = "last_java_crash"\n',
    '    private const val KEY_CRASH = "last_java_crash"\n    private const val KEY_CALL_STAGE = "last_call_stage"\n',
    'add call stage key')
s = replace_once(s,
'''        val crash=p.getString(KEY_CRASH,"").orEmpty()\n        return when {\n            crash.isNotBlank() -> crash.take(180)\n            exit.isNotBlank() -> exit.take(180)\n            else -> "No previous crash recorded"\n        }\n''',
'''        val crash=p.getString(KEY_CRASH,"").orEmpty()\n        val stage=p.getString(KEY_CALL_STAGE,"").orEmpty()\n        val base=when {\n            crash.isNotBlank() -> crash.take(180)\n            exit.isNotBlank() -> exit.take(180)\n            else -> "No previous crash recorded"\n        }\n        return if(stage.isBlank()) base else "$base · stage ${stage.take(80)}"\n''',
    'include crash stage in summary')
s = replace_once(s,
    '    private fun installJavaCrashRecorder(context: Context) {',
'''    fun markCallStage(context: Context, stage: String) {\n        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()\n            .putString(KEY_CALL_STAGE,stage.take(80))\n            .commit()\n    }\n\n    fun clearCallStage(context: Context) {\n        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()\n            .remove(KEY_CALL_STAGE)\n            .apply()\n    }\n\n    private fun installJavaCrashRecorder(context: Context) {''',
    'add call stage helpers')
s = replace_once(s,
'''        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()\n            .putString(KEY_EXIT,"$reason · status ${info.status}")\n            .remove(KEY_CRASH)\n            .apply()\n''',
'''        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()\n            .putString(KEY_EXIT,"$reason · status ${info.status}")\n            .apply()\n''',
    'preserve java crash detail')
p.write_text(s)

# AppViewModel.kt
p = root / 'AppViewModel.kt'
s = p.read_text()
s = replace_once(s,
'''        callActionBusy=true\n        acceptedLocallyCallId=current.callId\n        val token=db.serverToken()\n''',
'''        callActionBusy=true\n        acceptedLocallyCallId=current.callId\n        ProcessExitDiagnostics.markCallStage(getApplication(),"answer_begin")\n        val token=db.serverToken()\n''',
    'answer begin breadcrumb')
s = replace_once(s,
'''                val config=voiceCallConfig ?: withContext(Dispatchers.IO){ api.voiceCallConfig(token) }\n                val call=withContext(Dispatchers.IO){ api.acceptVoiceCall(token,current.callId) }\n                config to call\n''',
'''                val config=voiceCallConfig ?: withContext(Dispatchers.IO){ api.voiceCallConfig(token) }\n                ProcessExitDiagnostics.markCallStage(getApplication(),"answer_config_ready")\n                val call=withContext(Dispatchers.IO){ api.acceptVoiceCall(token,current.callId) }\n                ProcessExitDiagnostics.markCallStage(getApplication(),"answer_server_accepted")\n                config to call\n''',
    'answer network breadcrumbs')
s = replace_once(s,
'''                activeVoiceCall=current.copy(phase=VoiceCallPhase.CONNECTING)\n                ensureVoiceEngine(config)\n                NotificationHelper.cancelIncomingCall(getApplication(),current.callId)\n                CallForegroundService.start(getApplication(),current.callId,current.peerName,current.callType==CallType.VIDEO && current.cameraEnabled)\n''',
'''                activeVoiceCall=current.copy(phase=VoiceCallPhase.CONNECTING)\n                ProcessExitDiagnostics.markCallStage(getApplication(),"answer_before_engine")\n                ensureVoiceEngine(config)\n                ProcessExitDiagnostics.markCallStage(getApplication(),"answer_after_engine")\n                NotificationHelper.cancelIncomingCall(getApplication(),current.callId)\n                ProcessExitDiagnostics.markCallStage(getApplication(),"answer_before_foreground_service")\n                CallForegroundService.start(getApplication(),current.callId,current.peerName,current.callType==CallType.VIDEO && current.cameraEnabled)\n                ProcessExitDiagnostics.markCallStage(getApplication(),"answer_after_foreground_service")\n''',
    'answer engine breadcrumbs')
s = replace_once(s,
'''                upsertServerCallLog(call)\n                startCallStateLoop()\n            }.onFailure { acceptedLocallyCallId=null; lastToast=it.userMessage("Unable to accept call") }\n''',
'''                upsertServerCallLog(call)\n                startCallStateLoop()\n                ProcessExitDiagnostics.markCallStage(getApplication(),"answer_state_loop_started")\n            }.onFailure {\n                ProcessExitDiagnostics.markCallStage(getApplication(),"answer_java_failure:${it.javaClass.simpleName}")\n                acceptedLocallyCallId=null\n                lastToast=it.userMessage("Unable to accept call")\n            }\n''',
    'answer completion breadcrumbs')
s = replace_once(s,
'''        callDiagnostics=CallDiagnostics()\n        voiceCallRoutes.clear(); callActionBusy=false\n        activeVoiceCall=null\n''',
'''        callDiagnostics=CallDiagnostics()\n        voiceCallRoutes.clear(); callActionBusy=false\n        ProcessExitDiagnostics.clearCallStage(getApplication())\n        activeVoiceCall=null\n''',
    'clear call breadcrumb on finish')
p.write_text(s)

# WebRtcVoiceEngine.kt: rollback only process-wide initialization change, retain senior ACK/ICE fixes.
p = root / 'WebRtcVoiceEngine.kt'
s = p.read_text()
start = s.index('private object MessengerWebRtcRuntime {')
end = s.index('\n\nclass WebRtcVoiceEngine(', start)
s = s[:start] + s[end+2:]
s = replace_once(s,
'''            MessengerWebRtcRuntime.ensureInitialized(appContext)\n            val factoryBuilder = PeerConnectionFactory.builder()\n''',
'''            ProcessExitDiagnostics.markCallStage(appContext,"webrtc_before_global_init")\n            PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(appContext).createInitializationOptions())\n            ProcessExitDiagnostics.markCallStage(appContext,"webrtc_after_global_init")\n            val factoryBuilder = PeerConnectionFactory.builder()\n''',
    'rollback global init singleton')
s = replace_once(s,
'''            factory = factoryBuilder.createPeerConnectionFactory()\n''',
'''            ProcessExitDiagnostics.markCallStage(appContext,"webrtc_before_factory_create")\n            factory = factoryBuilder.createPeerConnectionFactory()\n            ProcessExitDiagnostics.markCallStage(appContext,"webrtc_after_factory_create")\n''',
    'factory breadcrumbs')
s = replace_once(s,
'''            peer = factory!!.createPeerConnection(config, observer()) ?: error("Unable to create peer connection")\n''',
'''            ProcessExitDiagnostics.markCallStage(appContext,"webrtc_before_peer_create")\n            peer = factory!!.createPeerConnection(config, observer()) ?: error("Unable to create peer connection")\n            ProcessExitDiagnostics.markCallStage(appContext,"webrtc_after_peer_create")\n''',
    'peer breadcrumbs')
s = replace_once(s,
'''            audioManager.registerAudioDeviceCallback(deviceCallback, null)\n            enterCommunicationMode()\n''',
'''            ProcessExitDiagnostics.markCallStage(appContext,"webrtc_before_audio_route")\n            audioManager.registerAudioDeviceCallback(deviceCallback, null)\n            enterCommunicationMode()\n''',
    'audio breadcrumbs')
s = replace_once(s,
'''            refreshRoutes()\n            selectBestDefaultRoute()\n        }.onFailure { onError(it.message ?: "Unable to initialize call audio") }\n''',
'''            refreshRoutes()\n            selectBestDefaultRoute()\n            ProcessExitDiagnostics.markCallStage(appContext,"webrtc_engine_ready")\n        }.onFailure {\n            ProcessExitDiagnostics.markCallStage(appContext,"webrtc_java_failure:${it.javaClass.simpleName}")\n            onError(it.message ?: "Unable to initialize call audio")\n        }\n''',
    'engine result breadcrumbs')
p.write_text(s)

print('CALL_CRASH_ISOLATION_TRANSFORM_APPLIED')
