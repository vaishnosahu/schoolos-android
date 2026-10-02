package com.example.messengerui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

class WebRtcVoiceEngine(
    context: Context,
    iceServers: List<IceServerConfig>,
    private val onLocalSignal: (String, JSONObject) -> Unit,
    private val onState: (VoiceCallPhase) -> Unit,
    private val onRoute: (String) -> Unit,
    private val onRoutes: (List<String>) -> Unit,
    private val onAudioInterruption: (Boolean) -> Unit,
    private val onQuality: (CallQualityStats) -> Unit,
    private val onError: (String) -> Unit
) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var factory: PeerConnectionFactory? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var peer: PeerConnection? = null
    private var closed = false
    private var selectedRoute = "Phone"
    private var offerInFlight = false
    private var relayCandidateAvailable = false
    private var lastStatsBytes: Long = 0L
    private var lastStatsAtMs: Long = 0L
    private val proximityWakeLock: PowerManager.WakeLock? = runCatching {
        if (powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "Messenger:VoiceCallProximity").apply { setReferenceCounted(false) }
        } else null
    }.getOrNull()

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> onAudioInterruption(false)
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> onAudioInterruption(true)
            else -> Unit
        }
    }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshRoutes()
            if (selectedRoute !in availableRouteLabels()) selectBestDefaultRoute()
        }
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refreshRoutes()
            if (selectedRoute !in availableRouteLabels()) selectBestDefaultRoute()
        }
    }

    init {
        runCatching {
            PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(appContext).createInitializationOptions())
            factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
            val audioConstraints = MediaConstraints().apply {
                optional.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                optional.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
                optional.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
                optional.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            }
            audioSource = factory!!.createAudioSource(audioConstraints)
            audioTrack = factory!!.createAudioTrack("messenger-audio", audioSource).apply { setEnabled(true) }
            val rtcIce = iceServers.flatMap { server -> server.urls.map { url ->
                PeerConnection.IceServer.builder(url).setUsername(server.username).setPassword(server.credential).createIceServer()
            } }
            val config = PeerConnection.RTCConfiguration(rtcIce).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            }
            peer = factory!!.createPeerConnection(config, observer()) ?: error("Unable to create peer connection")
            peer!!.addTrack(audioTrack, listOf("messenger-stream"))
            audioManager.registerAudioDeviceCallback(deviceCallback, null)
            enterCommunicationMode()
            refreshRoutes()
            selectBestDefaultRoute()
        }.onFailure { onError(it.message ?: "Unable to initialize call audio") }
    }

    fun createOffer() = createOfferInternal()

    fun restartIceAndCreateOffer(): Boolean {
        val pc = peer ?: return false
        if (closed || offerInFlight) return false
        runCatching { pc.restartIce() }.onFailure { return false }
        createOfferInternal()
        return true
    }

    private fun createOfferInternal() {
        val pc = peer ?: return onError("Call engine unavailable")
        if (closed || offerInFlight) return
        offerInFlight = true
        pc.createOffer(object:SdpAdapter(){
            override fun onCreateSuccess(desc: SessionDescription?) {
                if(desc==null){ offerInFlight=false; return }
                pc.setLocalDescription(object:SdpAdapter(){
                    override fun onSetSuccess() {
                        offerInFlight=false
                        onLocalSignal("OFFER", JSONObject().put("sdp",desc.description))
                    }
                    override fun onSetFailure(error:String?) {
                        offerInFlight=false
                        onError(error ?: "Unable to apply local call offer")
                    }
                },desc)
            }
            override fun onCreateFailure(error:String?) {
                offerInFlight=false
                onError(error ?: "Unable to create call offer")
            }
        }, MediaConstraints())
    }

    fun acceptOffer(sdp:String) {
        val pc=peer ?: return onError("Call engine unavailable")
        val remote=SessionDescription(SessionDescription.Type.OFFER,sdp)
        pc.setRemoteDescription(object:SdpAdapter(){
            override fun onSetSuccess() {
                pc.createAnswer(object:SdpAdapter(){
                    override fun onCreateSuccess(desc: SessionDescription?) { if(desc==null)return; pc.setLocalDescription(SdpAdapter(),desc); onLocalSignal("ANSWER",JSONObject().put("sdp",desc.description)) }
                    override fun onCreateFailure(error:String?) { onError(error ?: "Unable to create call answer") }
                },MediaConstraints())
            }
            override fun onSetFailure(error:String?) { onError(error ?: "Unable to apply call offer") }
        },remote)
    }

    fun acceptAnswer(sdp:String) { peer?.setRemoteDescription(SdpAdapter(),SessionDescription(SessionDescription.Type.ANSWER,sdp)) }
    fun addRemoteIce(payload:JSONObject) {
        val mid=payload.optString("sdp_mid").takeIf{it.isNotBlank()}
        val index=payload.optInt("sdp_mline_index",0)
        val candidate=payload.optString("candidate")
        if(candidate.isNotBlank()) peer?.addIceCandidate(IceCandidate(mid,index,candidate))
    }

    fun setMuted(muted:Boolean){ audioTrack?.setEnabled(!muted) }

    fun setSpeaker(speaker:Boolean) = setRoute(if (speaker) "Speaker" else preferredNonSpeakerRoute())

    fun setRoute(route: String) {
        val available = availableRouteLabels()
        val targetLabel = route.takeIf { it in available } ?: preferredNonSpeakerRoute()
        selectedRoute = targetLabel
        if (Build.VERSION.SDK_INT >= 31) {
            val target = availableCommunicationDevices().firstOrNull { routeLabel(it) == targetLabel }
            if (target != null) runCatching { audioManager.setCommunicationDevice(target) }
            selectedRoute = routeLabel(audioManager.communicationDevice).takeIf { it in available } ?: targetLabel
        } else {
            @Suppress("DEPRECATION")
            when (targetLabel) {
                "Speaker" -> {
                    audioManager.stopBluetoothSco()
                    audioManager.isBluetoothScoOn = false
                    audioManager.isSpeakerphoneOn = true
                }
                "Bluetooth" -> {
                    audioManager.isSpeakerphoneOn = false
                    if (hasBluetoothPermission()) {
                        audioManager.startBluetoothSco()
                        audioManager.isBluetoothScoOn = true
                    }
                }
                else -> {
                    audioManager.stopBluetoothSco()
                    audioManager.isBluetoothScoOn = false
                    audioManager.isSpeakerphoneOn = false
                }
            }
        }
        updateProximity()
        onRoute(selectedRoute)
        refreshRoutes()
    }

    fun requestQualitySnapshot() {
        val pc = peer ?: return
        if (closed) return
        runCatching {
            pc.getStats { report ->
                val stats = report.statsMap
                val values = stats.values
                val pair = values.firstOrNull { stat ->
                    stat.type == "candidate-pair" &&
                        stat.members["state"]?.toString() == "succeeded" &&
                        ((stat.members["nominated"] as? Boolean) == true || (stat.members["selected"] as? Boolean) == true)
                } ?: values.firstOrNull { stat -> stat.type == "candidate-pair" && stat.members["state"]?.toString() == "succeeded" }

                val localId = pair?.members?.get("localCandidateId")?.toString()
                val remoteId = pair?.members?.get("remoteCandidateId")?.toString()
                val localCandidate = localId?.let(stats::get)
                val remoteCandidate = remoteId?.let(stats::get)
                val relayInUse = localCandidate?.members?.get("candidateType")?.toString() == "relay" ||
                    remoteCandidate?.members?.get("candidateType")?.toString() == "relay"

                val rttMs = (pair?.members?.get("currentRoundTripTime") as? Number)?.toDouble()?.let { (it * 1000.0).toInt().coerceAtLeast(0) }
                val availableKbps = (pair?.members?.get("availableOutgoingBitrate") as? Number)?.toDouble()?.let { (it / 1000.0).toInt().coerceAtLeast(0) }

                var received = 0L
                var lost = 0L
                var totalBytes = 0L
                values.forEach { stat ->
                    val mediaKind = (stat.members["kind"] ?: stat.members["mediaType"])?.toString()
                    if (mediaKind == "audio" && stat.type == "inbound-rtp") {
                        received += (stat.members["packetsReceived"] as? Number)?.toLong() ?: 0L
                        lost += ((stat.members["packetsLost"] as? Number)?.toLong() ?: 0L).coerceAtLeast(0L)
                        totalBytes += (stat.members["bytesReceived"] as? Number)?.toLong() ?: 0L
                    }
                    if (mediaKind == "audio" && stat.type == "outbound-rtp") {
                        totalBytes += (stat.members["bytesSent"] as? Number)?.toLong() ?: 0L
                    }
                }
                val lossPct = if (received + lost > 0) (lost.toDouble() * 100.0 / (received + lost).toDouble()) else null
                val now = System.currentTimeMillis()
                val deltaKbps = if (lastStatsAtMs > 0L && now > lastStatsAtMs && totalBytes >= lastStatsBytes) {
                    (((totalBytes - lastStatsBytes).toDouble() * 8.0) / (now - lastStatsAtMs).toDouble()).toInt().coerceAtLeast(0)
                } else null
                lastStatsAtMs = now
                lastStatsBytes = totalBytes
                val bitrateKbps = availableKbps?.takeIf { it > 0 } ?: deltaKbps
                val quality = when {
                    rttMs != null && rttMs >= 600 -> "Poor"
                    lossPct != null && lossPct >= 8.0 -> "Poor"
                    bitrateKbps != null && bitrateKbps in 1..23 -> "Poor"
                    rttMs != null && rttMs >= 300 -> "Fair"
                    lossPct != null && lossPct >= 3.0 -> "Fair"
                    bitrateKbps != null && bitrateKbps in 1..39 -> "Fair"
                    rttMs != null || lossPct != null || bitrateKbps != null -> "Good"
                    else -> "Checking…"
                }
                onQuality(CallQualityStats(quality, rttMs, lossPct, bitrateKbps, relayInUse, relayCandidateAvailable))
            }
        }
    }

    fun close() {
        if(closed)return
        closed=true
        releaseProximity()
        runCatching { audioManager.unregisterAudioDeviceCallback(deviceCallback) }
        runCatching{peer?.close()}; peer=null
        runCatching{audioTrack?.dispose()}; audioTrack=null
        runCatching{audioSource?.dispose()}; audioSource=null
        runCatching{factory?.dispose()}; factory=null
        if(Build.VERSION.SDK_INT>=31) runCatching{audioManager.clearCommunicationDevice()}
        else @Suppress("DEPRECATION") {
            runCatching { audioManager.stopBluetoothSco() }
            audioManager.isBluetoothScoOn=false
            audioManager.isSpeakerphoneOn=false
        }
        audioManager.mode=AudioManager.MODE_NORMAL
        audioFocusRequest?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }
        onAudioInterruption(false)
    }

    private fun enterCommunicationMode(){
        audioManager.mode=AudioManager.MODE_IN_COMMUNICATION
        val attrs=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        if(Build.VERSION.SDK_INT>=26){
            val req=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(audioFocusListener)
                .setAcceptsDelayedFocusGain(false)
                .build()
            audioFocusRequest=req
            audioManager.requestAudioFocus(req)
        } else @Suppress("DEPRECATION") {
            audioManager.requestAudioFocus(audioFocusListener,AudioManager.STREAM_VOICE_CALL,AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
    }

    private fun availableCommunicationDevices(): List<AudioDeviceInfo> = if (Build.VERSION.SDK_INT >= 31) {
        runCatching { audioManager.availableCommunicationDevices }.getOrDefault(emptyList())
    } else {
        runCatching { audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList() }.getOrDefault(emptyList())
    }

    private fun availableRouteLabels(): List<String> {
        val labels = availableCommunicationDevices().map(::routeLabel).filter { it != "Other" }.distinct().toMutableList()
        if ("Phone" !in labels) labels.add("Phone")
        if ("Speaker" !in labels) labels.add("Speaker")
        return listOf("Phone","Bluetooth","Headset","Speaker").filter { it in labels }
    }

    private fun refreshRoutes() { onRoutes(availableRouteLabels()) }

    private fun preferredNonSpeakerRoute(): String {
        val routes=availableRouteLabels()
        return when {
            "Headset" in routes -> "Headset"
            "Bluetooth" in routes -> "Bluetooth"
            else -> "Phone"
        }
    }

    private fun selectBestDefaultRoute() { setRoute(preferredNonSpeakerRoute()) }

    private fun hasBluetoothPermission(): Boolean = Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun updateProximity() {
        if (selectedRoute == "Phone" && !closed) {
            proximityWakeLock?.let { if (!it.isHeld) runCatching { it.acquire() } }
        } else releaseProximity()
    }

    private fun releaseProximity() { proximityWakeLock?.let { if (it.isHeld) runCatching { it.release() } } }

    private fun routeLabel(d:AudioDeviceInfo?):String = when(d?.type){
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,AudioDeviceInfo.TYPE_BLE_HEADSET,AudioDeviceInfo.TYPE_BLE_SPEAKER -> "Bluetooth"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Speaker"
        AudioDeviceInfo.TYPE_WIRED_HEADSET,AudioDeviceInfo.TYPE_WIRED_HEADPHONES,AudioDeviceInfo.TYPE_USB_HEADSET -> "Headset"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Phone"
        else -> "Other"
    }

    private fun observer()=object:PeerConnection.Observer{
        override fun onSignalingChange(newState:PeerConnection.SignalingState?){}
        override fun onIceConnectionChange(newState:PeerConnection.IceConnectionState?){ when(newState){ PeerConnection.IceConnectionState.CONNECTED,PeerConnection.IceConnectionState.COMPLETED->onState(VoiceCallPhase.CONNECTED); PeerConnection.IceConnectionState.DISCONNECTED->onState(VoiceCallPhase.RECONNECTING); PeerConnection.IceConnectionState.FAILED->onError("Call connection failed"); else->Unit } }
        override fun onConnectionChange(newState:PeerConnection.PeerConnectionState?){ when(newState){ PeerConnection.PeerConnectionState.CONNECTED->onState(VoiceCallPhase.CONNECTED); PeerConnection.PeerConnectionState.DISCONNECTED->onState(VoiceCallPhase.RECONNECTING); PeerConnection.PeerConnectionState.FAILED->onError("Call connection failed"); PeerConnection.PeerConnectionState.CLOSED->onState(VoiceCallPhase.ENDED); else->Unit } }
        override fun onIceConnectionReceivingChange(receiving:Boolean){}
        override fun onIceGatheringChange(newState:PeerConnection.IceGatheringState?){}
        override fun onIceCandidate(candidate:IceCandidate?){
            if(candidate!=null) {
                if(candidate.sdp.contains(" typ relay ", ignoreCase=true)) relayCandidateAvailable=true
                onLocalSignal("ICE",JSONObject().put("sdp_mid",candidate.sdpMid).put("sdp_mline_index",candidate.sdpMLineIndex).put("candidate",candidate.sdp))
            }
        }
        override fun onIceCandidatesRemoved(candidates:Array<out IceCandidate>?){}
        override fun onAddStream(stream:MediaStream?){}
        override fun onRemoveStream(stream:MediaStream?){}
        override fun onDataChannel(channel:DataChannel?){}
        override fun onRenegotiationNeeded(){}
        override fun onAddTrack(receiver:RtpReceiver?,mediaStreams:Array<out MediaStream>?){}
    }

    private open class SdpAdapter:SdpObserver{
        override fun onCreateSuccess(desc:SessionDescription?){}
        override fun onSetSuccess(){}
        override fun onCreateFailure(error:String?){}
        override fun onSetFailure(error:String?){}
    }
}
