from pathlib import Path
import sys

root = Path(sys.argv[1])

def rep(rel, old, new, count=-1):
    p = root / rel
    s = p.read_text()
    if old not in s:
        raise SystemExit(f"missing pattern in {rel}: {old[:80]!r}")
    if count == -1:
        s = s.replace(old, new)
    else:
        s = s.replace(old, new, count)
    p.write_text(s)

rep("app/build.gradle.kts", 'versionCode = 45', 'versionCode = 46')
rep("app/build.gradle.kts", 'versionName = "2.26.8"', 'versionName = "2.26.9"')

rep("app/src/main/AndroidManifest.xml",
'''        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:supportsPictureInPicture="true"
            android:launchMode="singleTop"
            android:showWhenLocked="true"
            android:turnScreenOn="true"
            android:windowSoftInputMode="adjustResize">''',
'''        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:supportsPictureInPicture="true"
            android:launchMode="singleTask"
            android:windowSoftInputMode="adjustResize">''')

rep("app/src/main/java/com/example/messengerui/MainActivity.kt",
'import android.os.Bundle\n',
'import android.os.Bundle\nimport android.provider.Settings\nimport android.net.Uri\nimport android.app.NotificationManager\n', 1)

rep("app/src/main/java/com/example/messengerui/MainActivity.kt",
'        PushRegistration.initialize(this)\n',
'        PushRegistration.initialize(this)\n        maybePromptFullScreenCallAccess()\n', 1)

rep("app/src/main/java/com/example/messengerui/MainActivity.kt",
'''    private fun applyIncomingCallWindowPolicy(intent: Intent?) {''',
'''    private fun maybePromptFullScreenCallAccess() {
        if (android.os.Build.VERSION.SDK_INT < 34) return
        val manager = getSystemService(NotificationManager::class.java)
        if (runCatching { manager.canUseFullScreenIntent() }.getOrDefault(false)) return
        val prefs = getSharedPreferences("messenger_call_setup", MODE_PRIVATE)
        if (prefs.getBoolean("full_screen_prompted_v2", false)) return
        prefs.edit().putBoolean("full_screen_prompted_v2", true).apply()
        window.decorView.postDelayed({
            runCatching {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                    data = Uri.parse("package:$packageName")
                })
            }.onFailure { CallLifecycleLog.error("full_screen_permission_prompt_failed", error=it) }
        }, 650L)
    }

    private fun applyIncomingCallWindowPolicy(intent: Intent?) {''', 1)

rep("app/src/main/java/com/example/messengerui/IncomingCallActivity.kt",
'flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP',
'flags=Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP', 1)

nh = root / "app/src/main/java/com/example/messengerui/NotificationHelper.kt"
s = nh.read_text()
old = '''        val openIntent=Intent(context,IncomingCallActivity::class.java).apply {
            action = "com.example.messengerui.INCOMING_CALL"
            data = android.net.Uri.parse("messenger://call/$callId")
            flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(IncomingCallActivity.EXTRA_CALL_ID,callId)
            putExtra(IncomingCallActivity.EXTRA_CALLER_NAME,callerName)
            putExtra(IncomingCallActivity.EXTRA_CALLER_PHONE,callerPhone)
            putExtra(IncomingCallActivity.EXTRA_CALL_TYPE,callType.name)
        }'''
new = '''        val openIntent=Intent(context,MainActivity::class.java).apply {
            action = "com.example.messengerui.INCOMING_CALL"
            data = android.net.Uri.parse("messenger://call/$callId")
            flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("call_id",callId)
            putExtra("incoming_call",true)
            putExtra("call_action","open")
        }'''
if old not in s: raise SystemExit("main incoming intent block missing")
s = s.replace(old,new,1)
old = '''        val openIntent=Intent(context,IncomingCallActivity::class.java).apply {
            action="com.example.messengerui.INCOMING_CALL"
            data=android.net.Uri.parse("messenger://call/$callId")
            flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(IncomingCallActivity.EXTRA_CALL_ID,callId)
            putExtra(IncomingCallActivity.EXTRA_CALLER_NAME,callerName)
            putExtra(IncomingCallActivity.EXTRA_CALLER_PHONE,callerPhone)
            putExtra(IncomingCallActivity.EXTRA_CALL_TYPE,callType.name)
        }'''
new = '''        val openIntent=Intent(context,MainActivity::class.java).apply {
            action="com.example.messengerui.INCOMING_CALL"
            data=android.net.Uri.parse("messenger://call/$callId")
            flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("call_id",callId)
            putExtra("incoming_call",true)
            putExtra("call_action","open")
        }'''
if old not in s: raise SystemExit("silent incoming intent block missing")
s = s.replace(old,new,1)
s = s.replace('        if(fullScreenAllowed) builder.setFullScreenIntent(fullScreenPi,true)\n',
              '        builder.setFullScreenIntent(fullScreenPi,true)\n',1)
nh.write_text(s)

rep("app/src/main/java/com/example/messengerui/MessengerApp.kt",
'import androidx.compose.ui.platform.LocalContext\n',
'import androidx.compose.ui.platform.LocalContext\nimport androidx.compose.ui.platform.LocalView\n',1)

rep("app/src/main/java/com/example/messengerui/MessengerApp.kt",
'''private fun VoiceCallScreen(vm: AppViewModel, callId: String) {
    val context = LocalContext.current
    val call = vm.activeVoiceCall
''',
'''private fun VoiceCallScreen(vm: AppViewModel, callId: String) {
    val context = LocalContext.current
    val rootView = LocalView.current
    val call = vm.activeVoiceCall
    DisposableEffect(callId) {
        val activity=context as? android.app.Activity
        rootView.keepScreenOn = true
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if(Build.VERSION.SDK_INT>=27) activity?.setShowWhenLocked(true)
        else @Suppress("DEPRECATION") activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        onDispose {
            rootView.keepScreenOn = false
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if(Build.VERSION.SDK_INT>=27) activity?.setShowWhenLocked(false)
            else @Suppress("DEPRECATION") activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
    }
''',1)

wv = root / "app/src/main/java/com/example/messengerui/WebRtcVoiceEngine.kt"
s = wv.read_text()
s = s.replace('    private var peer: PeerConnection? = null\n',
'''    private var peer: PeerConnection? = null
    private var videoTransceiver: RtpTransceiver? = null
''',1)
old = '''                if(localVideo!=null) {
                    peer!!.addTransceiver(
                        localVideo,
                        RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV,streamIds)
                    )
                } else {
                    peer!!.addTransceiver(
                        MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                        RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY,streamIds)
                    )
                }'''
new = '''                videoTransceiver = if(localVideo!=null) {
                    peer!!.addTransceiver(
                        localVideo,
                        RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV,streamIds)
                    )
                } else {
                    peer!!.addTransceiver(
                        MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                        RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY,streamIds)
                    )
                }'''
if old not in s: raise SystemExit("video transceiver init missing")
s=s.replace(old,new,1)
s=s.replace('''        offerInFlight = true
        pc.createOffer''',
'''        offerInFlight = true
        normalizeVideoTransceiverForTwoWayMedia()
        pc.createOffer''',1)
s=s.replace('''            override fun onSetSuccess() {
                syncRemoteVideoTrackFromTransceivers()
                pc.createAnswer''',
'''            override fun onSetSuccess() {
                normalizeVideoTransceiverForTwoWayMedia()
                syncRemoteVideoTrackFromTransceivers()
                pc.createAnswer''',1)
s=s.replace('''        pc.setRemoteDescription(object:SdpAdapter(){
            override fun onSetSuccess() { syncRemoteVideoTrackFromTransceivers() }''',
'''        pc.setRemoteDescription(object:SdpAdapter(){
            override fun onSetSuccess() {
                normalizeVideoTransceiverForTwoWayMedia()
                syncRemoteVideoTrackFromTransceivers()
            }''',1)
s=s.replace('''    fun requestQualitySnapshot() {
        if(videoEnabled){
            val now=System.currentTimeMillis()''',
'''    fun requestQualitySnapshot() {
        if(videoEnabled){
            normalizeVideoTransceiverForTwoWayMedia()
            val now=System.currentTimeMillis()''',1)
s=s.replace('runCatching{peer?.close()}; runCatching{peer?.dispose()}; peer=null\n',
            'runCatching{peer?.close()}; runCatching{peer?.dispose()}; peer=null; videoTransceiver=null\n',1)
s=s.replace('''    private fun isRemoteVideoTrackUsable(track:VideoTrack?):Boolean =
        track!=null && !closed && runCatching { track.id(); true }.getOrDefault(false)
''',
'''    private fun isRemoteVideoTrackUsable(track:VideoTrack?):Boolean =
        track!=null && !closed && runCatching {
            track.id()
            track.state() == MediaStreamTrack.State.LIVE
        }.getOrDefault(false)
''',1)
marker='''    @Synchronized
    private fun syncRemoteVideoTrackFromTransceivers() {'''
helper='''    @Synchronized
    private fun normalizeVideoTransceiverForTwoWayMedia() {
        if(!videoEnabled || closed) return
        val pc=peer ?: return
        val local=videoTrack
        val candidates=buildList {
            videoTransceiver?.let { add(it) }
            pc.transceivers.forEach { transceiver ->
                if(transceiver===videoTransceiver) return@forEach
                val isVideo=runCatching {
                    transceiver.receiver.track() is VideoTrack || transceiver.sender.track() is VideoTrack
                }.getOrDefault(false)
                if(isVideo) add(transceiver)
            }
        }
        val target=candidates.firstOrNull { transceiver ->
            runCatching { transceiver.receiver.track() is VideoTrack }.getOrDefault(false)
        } ?: candidates.firstOrNull() ?: return
        runCatching {
            if(local!=null) {
                if(target.sender.track() !== local) target.sender.setTrack(local,false)
                target.direction=RtpTransceiver.RtpTransceiverDirection.SEND_RECV
            } else {
                target.direction=RtpTransceiver.RtpTransceiverDirection.RECV_ONLY
            }
            videoTransceiver=target
        }.onFailure { CallLifecycleLog.info("video_transceiver_normalize_failed", detail=it.javaClass.simpleName) }
    }

'''
if marker not in s: raise SystemExit("sync marker missing")
s=s.replace(marker,helper+marker,1)
old='''        val current=remoteVideoTrack
        if(isRemoteVideoTrackUsable(current)) {
            bindRemoteVideoTrack(current)
            return
        }
        if(current!=null) clearRemoteVideoTrack(current)
        val track=runCatching {
            peer?.transceivers
                ?.asSequence()
                ?.mapNotNull { transceiver -> runCatching { transceiver.receiver.track() as? VideoTrack }.getOrNull() }
                ?.firstOrNull { candidate -> isRemoteVideoTrackUsable(candidate) }
        }.getOrNull()
        if(track!=null) bindRemoteVideoTrack(track)
'''
new='''        val current=remoteVideoTrack
        val candidates=runCatching {
            peer?.transceivers
                ?.asSequence()
                ?.mapNotNull { transceiver -> runCatching { transceiver.receiver.track() as? VideoTrack }.getOrNull() }
                ?.filter { candidate -> isRemoteVideoTrackUsable(candidate) }
                ?.toList()
                .orEmpty()
        }.getOrDefault(emptyList())
        val now=System.currentTimeMillis()
        val currentFresh=current!=null && isRemoteVideoTrackUsable(current) && lastRemoteVideoFrameAtMs>0L && now-lastRemoteVideoFrameAtMs<2_500L
        val replacement=candidates.firstOrNull { it !== current }
        when {
            currentFresh -> bindRemoteVideoTrack(current)
            replacement!=null -> bindRemoteVideoTrack(replacement)
            isRemoteVideoTrackUsable(current) -> bindRemoteVideoTrack(current)
            else -> {
                if(current!=null) clearRemoteVideoTrack(current)
                candidates.firstOrNull()?.let(::bindRemoteVideoTrack)
            }
        }
'''
if old not in s: raise SystemExit("sync body missing")
s=s.replace(old,new,1)
wv.write_text(s)

print("v2.26.9 transform applied")
