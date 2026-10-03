from pathlib import Path
import sys

root=Path(sys.argv[1])

def rep(rel, old, new, n=1):
    p=root/rel
    s=p.read_text()
    if old not in s:
        raise SystemExit(f"missing {rel}: {old[:100]!r}")
    s=s.replace(old,new,n) if n!=-1 else s.replace(old,new)
    p.write_text(s)

rep('app/build.gradle.kts','versionCode = 46','versionCode = 47')
rep('app/build.gradle.kts','versionName = "2.26.9"','versionName = "2.26.10"')

p=root/'app/src/main/java/com/example/messengerui/NotificationHelper.kt'
s=p.read_text()
old='''        val openIntent=Intent(context,MainActivity::class.java).apply {
            action = "com.example.messengerui.INCOMING_CALL"
            data = android.net.Uri.parse("messenger://call/$callId")
            flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("call_id",callId)
            putExtra("incoming_call",true)
            putExtra("call_action","open")
        }'''
new='''        val openIntent=Intent(context,IncomingCallActivity::class.java).apply {
            action = "com.example.messengerui.INCOMING_CALL"
            data = android.net.Uri.parse("messenger://call/$callId")
            flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(IncomingCallActivity.EXTRA_CALL_ID,callId)
            putExtra(IncomingCallActivity.EXTRA_CALLER_NAME,callerName)
            putExtra(IncomingCallActivity.EXTRA_CALLER_PHONE,callerPhone)
            putExtra(IncomingCallActivity.EXTRA_CALL_TYPE,callType.name)
        }'''
if old not in s:
    raise SystemExit('incoming openIntent not found')
s=s.replace(old,new,1)
p.write_text(s)

p=root/'app/src/main/java/com/example/messengerui/MessengerFirebaseService.kt'
s=p.read_text()
old='''            val pm=getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Messenger:IncomingCallPush").apply{setReferenceCounted(false)}
            runCatching{wakeLock.acquire(15_000L)}'''
new='''            val pm=getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val wakeLock=pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "Messenger:IncomingCallPush"
            ).apply{setReferenceCounted(false)}
            runCatching{wakeLock.acquire(12_000L)}'''
if old not in s:
    raise SystemExit('incoming wake lock block not found')
s=s.replace(old,new,1)
p.write_text(s)

p=root/'app/src/main/java/com/example/messengerui/CallForegroundService.kt'
s=p.read_text()
s=s.replace('import android.os.IBinder\n','import android.os.IBinder\nimport android.os.PowerManager\n',1)
s=s.replace('''class CallForegroundService : Service() {
    override fun onCreate() {''','''class CallForegroundService : Service() {
    private var videoScreenWakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {''',1)
s=s.replace('''            CallLifecycleLog.info("foreground_service_started", callId, if(video) "video" else "audio")
            START_NOT_STICKY''','''            if(video) acquireVideoScreenWakeLock() else releaseVideoScreenWakeLock()
            CallLifecycleLog.info("foreground_service_started", callId, if(video) "video" else "audio")
            START_NOT_STICKY''',1)
marker='''    private fun foregroundServiceType(video:Boolean): Int ='''
insert='''    @Suppress("DEPRECATION")
    private fun acquireVideoScreenWakeLock() {
        if(videoScreenWakeLock?.isHeld == true) return
        val pm=getSystemService(Context.POWER_SERVICE) as PowerManager
        videoScreenWakeLock=pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK,"Messenger:ActiveVideoCall").apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }
    }

    private fun releaseVideoScreenWakeLock() {
        videoScreenWakeLock?.let { lock -> if(lock.isHeld) runCatching { lock.release() } }
        videoScreenWakeLock=null
    }

    override fun onDestroy() {
        releaseVideoScreenWakeLock()
        super.onDestroy()
    }

'''
if marker not in s:
    raise SystemExit('FGS marker missing')
s=s.replace(marker,insert+marker,1)
p.write_text(s)

p=root/'app/src/main/java/com/example/messengerui/WebRtcVoiceEngine.kt'
s=p.read_text()
s=s.replace('''    private val forceRelayOnly: Boolean = false,
    private val videoEnabled: Boolean = false,''','''    private val forceRelayOnly: Boolean = false,
    private val videoEnabled: Boolean = false,
    private val incomingCall: Boolean = false,''',1)
s=s.replace('''            if (videoEnabled) {
                val streamIds=listOf("messenger-stream")
                val localVideo=videoTrack
                videoTransceiver = if(localVideo!=null) {
                    peer!!.addTransceiver(
                        localVideo,
                        RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV,streamIds)
                    )
                } else {
                    peer!!.addTransceiver(
                        MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                        RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY,streamIds)
                    )
                }
            }''','''            if (videoEnabled && !incomingCall) {
                val streamIds=listOf("messenger-stream")
                val localVideo=videoTrack
                videoTransceiver = if(localVideo!=null) {
                    peer!!.addTransceiver(
                        localVideo,
                        RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV,streamIds)
                    )
                } else {
                    peer!!.addTransceiver(
                        MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                        RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY,streamIds)
                    )
                }
            }''',1)
s=s.replace('''    private var lastInboundBytes: Long = 0L
    private var lastOutboundBytes: Long = 0L''','''    private var lastInboundBytes: Long = 0L
    private var lastOutboundBytes: Long = 0L
    private var lastInboundVideoBytes: Long = 0L
    private var lastOutboundVideoBytes: Long = 0L''',1)
s=s.replace('''                var inboundBytes=0L
                var outboundBytes=0L
                values.forEach { stat ->''','''                var inboundBytes=0L
                var outboundBytes=0L
                var inboundVideoBytes=0L
                var outboundVideoBytes=0L
                var inboundVideoFrames=0L
                var outboundVideoFrames=0L
                values.forEach { stat ->''',1)
s=s.replace('''                    if(mediaKind=="audio" && stat.type=="outbound-rtp") outboundBytes += (stat.members["bytesSent"] as? Number)?.toLong() ?: 0L
                }''','''                    if(mediaKind=="audio" && stat.type=="outbound-rtp") outboundBytes += (stat.members["bytesSent"] as? Number)?.toLong() ?: 0L
                    if(mediaKind=="video" && stat.type=="inbound-rtp") {
                        inboundVideoBytes += (stat.members["bytesReceived"] as? Number)?.toLong() ?: 0L
                        inboundVideoFrames += ((stat.members["framesDecoded"] ?: stat.members["framesReceived"]) as? Number)?.toLong() ?: 0L
                    }
                    if(mediaKind=="video" && stat.type=="outbound-rtp") {
                        outboundVideoBytes += (stat.members["bytesSent"] as? Number)?.toLong() ?: 0L
                        outboundVideoFrames += ((stat.members["framesEncoded"] ?: stat.members["framesSent"]) as? Number)?.toLong() ?: 0L
                    }
                }''',1)
s=s.replace('''                val outboundKbps=if(lastStatsAtMs>0L && outboundBytes>=lastOutboundBytes)(((outboundBytes-lastOutboundBytes).toDouble()*8.0)/deltaMs).toInt().coerceAtLeast(0) else null
''','''                val outboundKbps=if(lastStatsAtMs>0L && outboundBytes>=lastOutboundBytes)(((outboundBytes-lastOutboundBytes).toDouble()*8.0)/deltaMs).toInt().coerceAtLeast(0) else null
                val inboundVideoKbps=if(lastStatsAtMs>0L && inboundVideoBytes>=lastInboundVideoBytes)(((inboundVideoBytes-lastInboundVideoBytes).toDouble()*8.0)/deltaMs).toInt().coerceAtLeast(0) else null
                val outboundVideoKbps=if(lastStatsAtMs>0L && outboundVideoBytes>=lastOutboundVideoBytes)(((outboundVideoBytes-lastOutboundVideoBytes).toDouble()*8.0)/deltaMs).toInt().coerceAtLeast(0) else null
''',1)
s=s.replace('''                lastStatsAtMs=now; lastStatsBytes=totalBytes; lastInboundBytes=inboundBytes; lastOutboundBytes=outboundBytes
''','''                lastStatsAtMs=now; lastStatsBytes=totalBytes; lastInboundBytes=inboundBytes; lastOutboundBytes=outboundBytes
                lastInboundVideoBytes=inboundVideoBytes; lastOutboundVideoBytes=outboundVideoBytes
''',1)
s=s.replace('''                onQuality(CallQualityStats(quality,rttMs,lossPct,bitrateKbps,relayInUse,relayCandidateAvailable,iceGatheringComplete,inboundKbps,outboundKbps,localType,remoteType,protocol,mediaStalled))''','''                val direction=runCatching { videoTransceiver?.currentDirection?.name ?: videoTransceiver?.direction?.name }.getOrNull()
                onQuality(CallQualityStats(quality,rttMs,lossPct,bitrateKbps,relayInUse,relayCandidateAvailable,iceGatheringComplete,inboundKbps,outboundKbps,localType,remoteType,protocol,mediaStalled,inboundVideoKbps,outboundVideoKbps,inboundVideoFrames,outboundVideoFrames,direction))''',1)
p.write_text(s)

p=root/'app/src/main/java/com/example/messengerui/CallModels.kt'
s=p.read_text()
s=s.replace('''    val selectedProtocol: String? = null,
    val mediaStalled: Boolean = false
)''','''    val selectedProtocol: String? = null,
    val mediaStalled: Boolean = false,
    val inboundVideoKbps: Int? = null,
    val outboundVideoKbps: Int? = null,
    val inboundVideoFrames: Long = 0L,
    val outboundVideoFrames: Long = 0L,
    val videoDirection: String? = null
)''',1)
s=s.replace('''    val mediaStalled: Boolean = false,
    val lastRecoveryReason: String? = null
)''','''    val mediaStalled: Boolean = false,
    val lastRecoveryReason: String? = null,
    val inboundVideoKbps: Int? = null,
    val outboundVideoKbps: Int? = null,
    val inboundVideoFrames: Long = 0L,
    val outboundVideoFrames: Long = 0L,
    val videoDirection: String? = null
)''',1)
p.write_text(s)

p=root/'app/src/main/java/com/example/messengerui/AppViewModel.kt'
s=p.read_text()
s=s.replace('''        voiceEngine=WebRtcVoiceEngine(getApplication(),config.iceServers,relayValidationMode,activeVoiceCall?.callType==CallType.VIDEO,
''','''        voiceEngine=WebRtcVoiceEngine(getApplication(),config.iceServers,relayValidationMode,activeVoiceCall?.callType==CallType.VIDEO,activeVoiceCall?.incoming==true,
''',1)
s=s.replace('''            selectedProtocol=quality.selectedProtocol,
            mediaStalled=quality.mediaStalled
''','''            selectedProtocol=quality.selectedProtocol,
            mediaStalled=quality.mediaStalled,
            inboundVideoKbps=quality.inboundVideoKbps,
            outboundVideoKbps=quality.outboundVideoKbps,
            inboundVideoFrames=quality.inboundVideoFrames,
            outboundVideoFrames=quality.outboundVideoFrames,
            videoDirection=quality.videoDirection
''',1)
p.write_text(s)

p=root/'app/src/main/java/com/example/messengerui/MessengerApp.kt'
s=p.read_text()
s=s.replace('''                    Text("Media receive: ${if(d.mediaStalled) "Recovering" else "Healthy"}")
                    Text("Reconnects: ${d.reconnectCount}")''','''                    Text("Media receive: ${if(d.mediaStalled) "Recovering" else "Healthy"}")
                    if(call.callType==CallType.VIDEO) {
                        Text("Video TX: ${d.outboundVideoKbps?.let { "$it kbps" } ?: "checking"} · frames ${d.outboundVideoFrames}")
                        Text("Video RX: ${d.inboundVideoKbps?.let { "$it kbps" } ?: "checking"} · frames ${d.inboundVideoFrames}")
                        d.videoDirection?.let { Text("Video transceiver: $it") }
                    }
                    Text("Incoming window: ${vm.callNotificationReadinessText()}")
                    Text("Reconnects: ${d.reconnectCount}")''',1)
p.write_text(s)

print("v2.26.10 transform applied")
