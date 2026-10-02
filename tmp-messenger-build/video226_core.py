from pathlib import Path
root=Path('/tmp/messenger-build/messenger_live')

# Version
p=root/'app/build.gradle.kts'
s=p.read_text().replace('versionCode = 36','versionCode = 37').replace('versionName = "2.25"','versionName = "2.26"')
p.write_text(s)

# Manifest video permissions / call foreground type / PiP
p=root/'app/src/main/AndroidManifest.xml'
s=p.read_text()
if '<uses-permission android:name="android.permission.CAMERA" />' not in s:
    s=s.replace('<uses-permission android:name="android.permission.RECORD_AUDIO" />','<uses-permission android:name="android.permission.RECORD_AUDIO" />\n    <uses-permission android:name="android.permission.CAMERA" />')
if '<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CAMERA" />' not in s:
    s=s.replace('<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />','<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />\n    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CAMERA" />')
s=s.replace('android:foregroundServiceType="microphone"','android:foregroundServiceType="microphone|camera"')
s=s.replace('android:name=".MainActivity"\n            android:exported="true"', 'android:name=".MainActivity"\n            android:exported="true"\n            android:supportsPictureInPicture="true"')
p.write_text(s)

# Call UI model
p=root/'app/src/main/java/com/example/messengerui/CallModels.kt'
s=p.read_text()
old='''data class VoiceCallUi(
    val callId: String,
    val peerId: String,
    val peerName: String,
    val incoming: Boolean,
    val phase: VoiceCallPhase,
    val muted: Boolean = false,
    val speaker: Boolean = false,
    val routeLabel: String = "Phone",
    val connectedAtMs: Long? = null,
    val error: String? = null,
    val audioInterrupted: Boolean = false
)'''
new='''data class VoiceCallUi(
    val callId: String,
    val peerId: String,
    val peerName: String,
    val incoming: Boolean,
    val phase: VoiceCallPhase,
    val callType: CallType = CallType.AUDIO,
    val muted: Boolean = false,
    val speaker: Boolean = false,
    val routeLabel: String = "Phone",
    val connectedAtMs: Long? = null,
    val error: String? = null,
    val audioInterrupted: Boolean = false,
    val cameraEnabled: Boolean = true,
    val frontCamera: Boolean = true,
    val remoteVideoAvailable: Boolean = false
)'''
if old not in s: raise SystemExit('VoiceCallUi anchor missing')
s=s.replace(old,new,1)
p.write_text(s)

# API supports AUDIO or VIDEO
p=root/'app/src/main/java/com/example/messengerui/ApiClient.kt'
s=p.read_text()
old='''    fun startVoiceCall(token:String,userId:String,clientUuid:String):RemoteVoiceCall =
        request("POST","calls/start.php",JSONObject().put("user_id",userId).put("client_uuid",clientUuid).put("type","AUDIO"),token).getJSONObject("call").toRemoteVoiceCall()'''
new='''    fun startVoiceCall(token:String,userId:String,clientUuid:String,type:CallType=CallType.AUDIO):RemoteVoiceCall =
        request("POST","calls/start.php",JSONObject().put("user_id",userId).put("client_uuid",clientUuid).put("type",type.name),token).getJSONObject("call").toRemoteVoiceCall()'''
if old not in s: raise SystemExit('startVoiceCall API anchor missing')
s=s.replace(old,new,1)
p.write_text(s)

# Extend existing WebRTC engine with optional video.
p=root/'app/src/main/java/com/example/messengerui/WebRtcVoiceEngine.kt'
s=p.read_text()
imports='''import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.EglBase
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSink
import org.webrtc.VideoSource
import org.webrtc.VideoTrack'''
s=s.replace('import org.webrtc.AudioTrack',imports,1)

s=s.replace('''    private val forceRelayOnly: Boolean = false,
    private val onLocalSignal:''','''    private val forceRelayOnly: Boolean = false,
    private val videoEnabled: Boolean = false,
    private val onRemoteVideoAvailable: (Boolean) -> Unit = {},
    private val onLocalSignal:''',1)

s=s.replace('''    private var audioTrack: AudioTrack? = null
    private var peer: PeerConnection? = null''','''    private var audioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var eglBase: EglBase? = null
    private var localVideoSink: VideoSink? = null
    private var remoteVideoSink: VideoSink? = null
    private var usingFrontCamera = true
    private var peer: PeerConnection? = null''',1)

anchor='''            factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
            val audioConstraints = MediaConstraints().apply {'''
video_init='''            factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
            if(videoEnabled && ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED){
                eglBase=EglBase.create()
                val enumerator=Camera2Enumerator(appContext)
                val names=enumerator.deviceNames.toList()
                val cameraName=names.firstOrNull { enumerator.isFrontFacing(it) } ?: names.firstOrNull()
                if(cameraName!=null){
                    usingFrontCamera=enumerator.isFrontFacing(cameraName)
                    videoCapturer=enumerator.createCapturer(cameraName,null) as? CameraVideoCapturer
                    if(videoCapturer!=null){
                        surfaceTextureHelper=SurfaceTextureHelper.create("MessengerVideoCapture",eglBase!!.eglBaseContext)
                        videoSource=factory!!.createVideoSource(false)
                        videoCapturer!!.initialize(surfaceTextureHelper,appContext,videoSource!!.capturerObserver)
                        videoCapturer!!.startCapture(720,1280,24)
                        videoTrack=factory!!.createVideoTrack("messenger-video",videoSource).apply { setEnabled(true) }
                    }
                }
            }
            val audioConstraints = MediaConstraints().apply {'''
if anchor not in s: raise SystemExit('factory anchor missing')
s=s.replace(anchor,video_init,1)

s=s.replace('''            peer!!.addTrack(audioTrack, listOf("messenger-stream"))''','''            peer!!.addTrack(audioTrack, listOf("messenger-stream"))
            videoTrack?.let { peer!!.addTrack(it, listOf("messenger-stream")) }''',1)

insert_after='''    fun setMuted(muted:Boolean){ audioTrack?.setEnabled(!muted) }
'''
video_methods='''    fun setMuted(muted:Boolean){ audioTrack?.setEnabled(!muted) }

    fun setCameraEnabled(enabled:Boolean){ videoTrack?.setEnabled(enabled) }

    fun switchCamera(onDone:(Boolean)->Unit = {}) {
        val capturer=videoCapturer ?: return
        capturer.switchCamera(object: CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                usingFrontCamera=isFrontCamera
                onDone(isFrontCamera)
            }
            override fun onCameraSwitchError(errorDescription: String?) { }
        })
    }

    fun attachLocalVideoSink(sink:VideoSink?) {
        localVideoSink?.let { videoTrack?.removeSink(it) }
        localVideoSink=sink
        sink?.let { videoTrack?.addSink(it) }
    }

    fun attachRemoteVideoSink(sink:VideoSink?) {
        remoteVideoSink?.let { remoteVideoTrack?.removeSink(it) }
        remoteVideoSink=sink
        sink?.let { remoteVideoTrack?.addSink(it) }
    }

    fun videoEglContext(): EglBase.Context? = eglBase?.eglBaseContext
    fun hasLocalVideo(): Boolean = videoTrack != null
'''
if insert_after not in s: raise SystemExit('setMuted anchor missing')
s=s.replace(insert_after,video_methods,1)

s=s.replace('''        runCatching{audioTrack?.dispose()}; audioTrack=null
        runCatching{audioSource?.dispose()}; audioSource=null
        runCatching{factory?.dispose()}; factory=null''','''        localVideoSink?.let { runCatching { videoTrack?.removeSink(it) } }; localVideoSink=null
        remoteVideoSink?.let { runCatching { remoteVideoTrack?.removeSink(it) } }; remoteVideoSink=null
        remoteVideoTrack=null
        onRemoteVideoAvailable(false)
        runCatching { videoCapturer?.stopCapture() }; runCatching { videoCapturer?.dispose() }; videoCapturer=null
        runCatching { surfaceTextureHelper?.dispose() }; surfaceTextureHelper=null
        runCatching { videoTrack?.dispose() }; videoTrack=null
        runCatching { videoSource?.dispose() }; videoSource=null
        runCatching{audioTrack?.dispose()}; audioTrack=null
        runCatching{audioSource?.dispose()}; audioSource=null
        runCatching{factory?.dispose()}; factory=null
        runCatching { eglBase?.release() }; eglBase=null''',1)

s=s.replace('''        override fun onAddTrack(receiver:RtpReceiver?,mediaStreams:Array<out MediaStream>?){}''','''        override fun onAddTrack(receiver:RtpReceiver?,mediaStreams:Array<out MediaStream>?){
            val track=receiver?.track()
            if(track is VideoTrack){
                remoteVideoTrack?.let { old -> remoteVideoSink?.let { old.removeSink(it) } }
                remoteVideoTrack=track
                remoteVideoSink?.let { track.addSink(it) }
                onRemoteVideoAvailable(true)
            }
        }''',1)
p.write_text(s)

# ViewModel: generic start, video controls, engine video mode.
p=root/'app/src/main/java/com/example/messengerui/AppViewModel.kt'
s=p.read_text()
if 'import android.Manifest' not in s:
    s=s.replace('package com.example.messengerui\n','package com.example.messengerui\n\nimport android.Manifest\nimport android.content.pm.PackageManager\nimport androidx.core.content.ContextCompat\n',1)

start=s.index('    fun startVoiceCall(chatId: String) {')
end=s.index('\n    fun openVoiceCallFromNotification',start)
newblock='''    fun startVoiceCall(chatId: String) = startCall(chatId,CallType.AUDIO)
    fun startVideoCall(chatId: String) = startCall(chatId,CallType.VIDEO)

    private fun startCall(chatId:String,type:CallType) {
        if(callActionBusy) return
        if(ContextCompat.checkSelfPermission(getApplication(),Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            lastToast="Microphone permission is required for calls"; return
        }
        if(type==CallType.VIDEO && ContextCompat.checkSelfPermission(getApplication(),Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            lastToast="Camera permission is required for video calls"; return
        }
        val chat=chats.firstOrNull { it.id==chatId } ?: return
        if(chat.isGroup){ lastToast="1:1 calls are available for direct chats"; return }
        if(!serverMode){ lastToast="Connect to the server before starting a call"; return }
        if(activeVoiceCall!=null){ lastToast="Another call is already active"; return }
        val peerId=chat.memberIds.firstOrNull()
        if(peerId.isNullOrBlank()){ lastToast="Call recipient is unavailable"; return }
        val token=db.serverToken()
        callActionBusy=true
        viewModelScope.launch {
            runCatching {
                val config=withContext(Dispatchers.IO){ api.voiceCallConfig(token) }
                val call=withContext(Dispatchers.IO){ api.startVoiceCall(token,peerId,UUID.randomUUID().toString(),type) }
                config to call
            }.onSuccess { (config,call) ->
                voiceCallConfig=config
                callSignalCursor=0L
                callConnectedReported=false
                callInitialOfferSent=false
                callReconnectCount=0
                lastIceRestartAtMs=0L
                callNetworkHandle=connectivityManager.activeNetwork?.networkHandle
                callDiagnostics=CallDiagnostics(networkLabel=currentCallNetworkLabel(),turnConfigured=config.turnConfigured)
                activeVoiceCall=VoiceCallUi(call.id,call.peerId,call.peerName,false,VoiceCallPhase.OUTGOING_RINGING,callType=call.type,cameraEnabled=call.type==CallType.VIDEO)
                upsertServerCallLog(call)
                navigate(AppScreen.VoiceCall(call.id))
                startCallStateLoop()
            }.onFailure { lastToast=it.userMessage(if(type==CallType.VIDEO)"Unable to start video call" else "Unable to start voice call") }
            callActionBusy=false
        }
    }
'''
s=s[:start]+newblock+s[end:]

# Accept path camera permission guard.
s=s.replace('''        if(current.phase!=VoiceCallPhase.INCOMING_RINGING || callActionBusy) return
        callActionBusy=true''','''        if(current.phase!=VoiceCallPhase.INCOMING_RINGING || callActionBusy) return
        if(current.callType==CallType.VIDEO && ContextCompat.checkSelfPermission(getApplication(),Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){ lastToast="Camera permission is required for video calls"; return }
        callActionBusy=true''',1)

# Video controls before toggle speaker
anchor='''    fun toggleVoiceCallSpeaker() {
'''
controls='''    fun toggleCallCamera() {
        val current=activeVoiceCall ?: return
        if(current.callType!=CallType.VIDEO) return
        val enabled=!current.cameraEnabled
        voiceEngine?.setCameraEnabled(enabled)
        activeVoiceCall=current.copy(cameraEnabled=enabled)
    }

    fun switchCallCamera() {
        val current=activeVoiceCall ?: return
        if(current.callType!=CallType.VIDEO || !current.cameraEnabled) return
        voiceEngine?.switchCamera { front -> viewModelScope.launch { activeVoiceCall=activeVoiceCall?.copy(frontCamera=front) } }
    }

    fun attachLocalVideoSink(sink:org.webrtc.VideoSink?) { voiceEngine?.attachLocalVideoSink(sink) }
    fun attachRemoteVideoSink(sink:org.webrtc.VideoSink?) { voiceEngine?.attachRemoteVideoSink(sink) }
    fun callVideoEglContext():org.webrtc.EglBase.Context? = voiceEngine?.videoEglContext()

'''
if anchor not in s: raise SystemExit('speaker anchor missing')
s=s.replace(anchor,controls+anchor,1)

# engine constructor adds video mode
s=s.replace('''voiceEngine=WebRtcVoiceEngine(getApplication(),config.iceServers,relayValidationMode,
            onLocalSignal=''', '''voiceEngine=WebRtcVoiceEngine(getApplication(),config.iceServers,relayValidationMode,activeVoiceCall?.callType==CallType.VIDEO,
            onRemoteVideoAvailable={ available -> viewModelScope.launch { activeVoiceCall=activeVoiceCall?.copy(remoteVideoAvailable=available) } },
            onLocalSignal=''')
# fallback old form
s=s.replace('''voiceEngine=WebRtcVoiceEngine(getApplication(),config.iceServers,
            onLocalSignal=''', '''voiceEngine=WebRtcVoiceEngine(getApplication(),config.iceServers,false,activeVoiceCall?.callType==CallType.VIDEO,
            onRemoteVideoAvailable={ available -> viewModelScope.launch { activeVoiceCall=activeVoiceCall?.copy(remoteVideoAvailable=available) } },
            onLocalSignal=''')

# applyRemoteCall constructor include type.
old='''        activeVoiceCall=VoiceCallUi(
            remote.id,remote.peerId,remote.peerName,remote.incoming,phase,
            muted=current?.muted ?: false,
            speaker=current?.speaker ?: false,
            routeLabel=current?.routeLabel ?: "Phone",
            connectedAtMs=current?.connectedAtMs ?: remote.connectedAtIso?.let(::parseEpochMs),
            error=null,
            audioInterrupted=current?.audioInterrupted ?: false
        )'''
if old in s:
    new='''        activeVoiceCall=VoiceCallUi(
            remote.id,remote.peerId,remote.peerName,remote.incoming,phase,
            callType=remote.type,
            muted=current?.muted ?: false,
            speaker=current?.speaker ?: false,
            routeLabel=current?.routeLabel ?: "Phone",
            connectedAtMs=current?.connectedAtMs ?: remote.connectedAtIso?.let(::parseEpochMs),
            error=null,
            audioInterrupted=current?.audioInterrupted ?: false,
            cameraEnabled=current?.cameraEnabled ?: (remote.type==CallType.VIDEO),
            frontCamera=current?.frontCamera ?: true,
            remoteVideoAvailable=current?.remoteVideoAvailable ?: false
        )'''
    s=s.replace(old,new,1)
else:
    # semantic fallback: first VoiceCallUi in applyRemoteCall positional first line.
    s=s.replace('''        activeVoiceCall=VoiceCallUi(
            remote.id,remote.peerId,remote.peerName,remote.incoming,phase,''','''        activeVoiceCall=VoiceCallUi(
            remote.id,remote.peerId,remote.peerName,remote.incoming,phase,callType=remote.type,''',1)

p.write_text(s)

print('Messenger 2.26 video core data/media applied')
