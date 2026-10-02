from pathlib import Path
root=Path("/tmp/messenger-build/messenger_live")
pkg=root/"app/src/main/java/com/example/messengerui"

p=root/"app/build.gradle.kts"
s=p.read_text().replace("versionCode = 24","versionCode = 25").replace('versionName = "2.13"','versionName = "2.14"')
p.write_text(s)

(pkg/"CallAlertStore.kt").write_text(r'''package com.example.messengerui

import android.content.Context

object CallAlertStore {
    private const val PREFS = "messenger_call_alerts"
    private const val TERMINAL_TTL_MS = 10 * 60_000L
    private const val SHOWN_TTL_MS = 60_000L
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun markIncomingShown(context: Context, callId: String) {
        if (callId.isBlank()) return
        cleanup(context)
        prefs(context).edit().putLong("shown:$callId", System.currentTimeMillis()).apply()
    }

    fun wasIncomingRecentlyShown(context: Context, callId: String): Boolean {
        val at = prefs(context).getLong("shown:$callId", 0L)
        return at > 0L && System.currentTimeMillis() - at <= SHOWN_TTL_MS
    }

    fun markTerminal(context: Context, callId: String) {
        if (callId.isBlank()) return
        cleanup(context)
        prefs(context).edit().remove("shown:$callId").putLong("terminal:$callId", System.currentTimeMillis()).apply()
    }

    fun isTerminal(context: Context, callId: String): Boolean {
        val at = prefs(context).getLong("terminal:$callId", 0L)
        return at > 0L && System.currentTimeMillis() - at <= TERMINAL_TTL_MS
    }

    fun clearIncoming(context: Context, callId: String) {
        if (callId.isNotBlank()) prefs(context).edit().remove("shown:$callId").apply()
    }

    private fun cleanup(context: Context) {
        val now=System.currentTimeMillis()
        val edit=prefs(context).edit()
        prefs(context).all.forEach { (key,value) ->
            val at=value as? Long ?: return@forEach
            val ttl=if(key.startsWith("terminal:")) TERMINAL_TTL_MS else SHOWN_TTL_MS
            if(now-at>ttl) edit.remove(key)
        }
        edit.apply()
    }
}
''')

(pkg/"CallNotificationActionReceiver.kt").write_text(r'''package com.example.messengerui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CallNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val callId=intent?.getStringExtra(EXTRA_CALL_ID).orEmpty()
        if(callId.isBlank()) return
        when(intent?.action) {
            ACTION_DECLINE -> decline(context.applicationContext,callId)
            ACTION_DISMISS_MISSED -> NotificationHelper.cancelMissedCall(context,callId)
        }
    }

    private fun decline(context: Context, callId: String) {
        val pending=goAsync()
        NotificationHelper.markCallTerminal(context,callId)
        Thread {
            try {
                val token=LocalMessengerDb(context).serverToken()
                if(token.isNotBlank()) runCatching { ApiClient().declineVoiceCall(token,callId) }
            } finally { pending.finish() }
        }.start()
    }

    companion object {
        const val ACTION_DECLINE="com.example.messengerui.action.DECLINE_CALL"
        const val ACTION_DISMISS_MISSED="com.example.messengerui.action.DISMISS_MISSED_CALL"
        const val EXTRA_CALL_ID="call_id"
    }
}
''')

p=pkg/"NotificationHelper.kt"
s=p.read_text()
s=s.replace('const val CALLS_CHANNEL = "calls"','const val CALLS_CHANNEL = "calls_v2"\n    private const val MISSED_CALLS_CHANNEL = "missed_calls"')
old='manager.createNotificationChannel(NotificationChannel(CALLS_CHANNEL, "Calls", NotificationManager.IMPORTANCE_HIGH).apply { description = "Incoming and missed call alerts" })'
new='''manager.createNotificationChannel(NotificationChannel(CALLS_CHANNEL, "Incoming calls", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Incoming Messenger calls"
            enableVibration(true)
            vibrationPattern = longArrayOf(0,700,450,700,450,700)
            val ringtone=android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
            setSound(ringtone,android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            lockscreenVisibility=android.app.Notification.VISIBILITY_PRIVATE
        })
        manager.createNotificationChannel(NotificationChannel(MISSED_CALLS_CHANNEL, "Missed calls", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Missed Messenger calls"
            lockscreenVisibility=android.app.Notification.VISIBILITY_PRIVATE
        })'''
if old not in s: raise SystemExit("calls channel anchor missing")
s=s.replace(old,new,1)
start=s.index("    fun showIncomingCall")
end=s.index("    fun cancelConversation",start)
block=r'''    fun showIncomingCall(context:Context,callId:String,callerName:String):Boolean {
        if(!canNotify(context) || callId.isBlank() || CallAlertStore.isTerminal(context,callId)) return false
        createChannels(context)
        val openIntent=Intent(context,MainActivity::class.java).apply {
            flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("call_id",callId); putExtra("incoming_call",true)
        }
        val openPi=PendingIntent.getActivity(context,("call:$callId").hashCode(),openIntent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val answerIntent=Intent(context,MainActivity::class.java).apply {
            flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("call_id",callId); putExtra("incoming_call",true); putExtra("call_action","answer")
        }
        val answerPi=PendingIntent.getActivity(context,("answer:$callId").hashCode(),answerIntent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val declineIntent=Intent(context,CallNotificationActionReceiver::class.java).apply {
            action=CallNotificationActionReceiver.ACTION_DECLINE
            putExtra(CallNotificationActionReceiver.EXTRA_CALL_ID,callId)
        }
        val declinePi=PendingIntent.getBroadcast(context,("decline:$callId").hashCode(),declineIntent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alreadyShown=CallAlertStore.wasIncomingRecentlyShown(context,callId)
        val ringtone=android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
        val n=NotificationCompat.Builder(context,CALLS_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(callerName).setContentText("Incoming voice call")
            .setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_CALL).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(openPi).setFullScreenIntent(openPi,true).setOngoing(true).setAutoCancel(false).setOnlyAlertOnce(alreadyShown)
            .setTimeoutAfter(45_000).setSound(ringtone).setVibrate(longArrayOf(0,700,450,700,450,700))
            .addAction(R.drawable.ic_notification,"Decline",declinePi).addAction(R.drawable.ic_notification,"Answer",answerPi).build()
        NotificationManagerCompat.from(context).notify(incomingCallNotificationId(callId),n)
        CallAlertStore.markIncomingShown(context,callId)
        return true
    }

    fun cancelIncomingCall(context:Context,callId:String){
        NotificationManagerCompat.from(context).cancel(incomingCallNotificationId(callId))
        CallAlertStore.clearIncoming(context,callId)
    }

    fun markCallTerminal(context:Context,callId:String){
        cancelIncomingCall(context,callId)
        CallAlertStore.markTerminal(context,callId)
    }

    fun showMissedCall(context:Context,callId:String,callerName:String):Boolean {
        if(!canNotify(context) || callId.isBlank()) return false
        markCallTerminal(context,callId)
        val openIntent=Intent(context,MainActivity::class.java).apply {
            flags=Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_calls",true)
        }
        val openPi=PendingIntent.getActivity(context,("missed:$callId").hashCode(),openIntent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val dismissIntent=Intent(context,CallNotificationActionReceiver::class.java).apply {
            action=CallNotificationActionReceiver.ACTION_DISMISS_MISSED
            putExtra(CallNotificationActionReceiver.EXTRA_CALL_ID,callId)
        }
        val dismissPi=PendingIntent.getBroadcast(context,("dismiss-missed:$callId").hashCode(),dismissIntent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n=NotificationCompat.Builder(context,MISSED_CALLS_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("Missed call").setContentText(callerName)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_MISSED_CALL).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(openPi).setAutoCancel(true).setOnlyAlertOnce(true).addAction(R.drawable.ic_notification,"Dismiss",dismissPi).build()
        NotificationManagerCompat.from(context).notify(missedCallNotificationId(callId),n)
        return true
    }

    fun cancelMissedCall(context:Context,callId:String){ NotificationManagerCompat.from(context).cancel(missedCallNotificationId(callId)) }
    fun incomingCallNotificationId(callId:String):Int=("incoming-call:$callId").hashCode()
    fun missedCallNotificationId(callId:String):Int=("missed-call:$callId").hashCode()
    fun ongoingCallNotificationId(callId:String):Int=("ongoing-call:$callId").hashCode()
    fun buildOngoingCall(context:Context,callId:String,peerName:String):android.app.Notification {
        val intent=Intent(context,MainActivity::class.java).apply{flags=Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP;putExtra("call_id",callId)}
        val pi=PendingIntent.getActivity(context,("ongoing:$callId").hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context,CALLS_CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle(peerName).setContentText("Voice call in progress").setCategory(NotificationCompat.CATEGORY_CALL).setPriority(NotificationCompat.PRIORITY_HIGH).setOngoing(true).setContentIntent(pi).build()
    }

'''
s=s[:start]+block+s[end:]
p.write_text(s)

p=pkg/"MessengerFirebaseService.kt"
s=p.read_text()
old='''        if (eventType == "call.incoming") {
            val callId=data["call_id"].orEmpty()
            if(callId.isNotBlank()) NotificationHelper.showIncomingCall(this,callId,data["caller_name"].orEmpty().ifBlank { "Messenger User" })
            return
        }
'''
new='''        if (eventType == "call.incoming") {
            val callId=data["call_id"].orEmpty()
            if(callId.isNotBlank() && !CallAlertStore.isTerminal(this,callId)) NotificationHelper.showIncomingCall(this,callId,data["caller_name"].orEmpty().ifBlank { "Messenger User" })
            return
        }
        if (eventType == "call.accepted") {
            val callId=data["call_id"].orEmpty()
            if(callId.isNotBlank()) NotificationHelper.cancelIncomingCall(this,callId)
            return
        }
        if (eventType == "call.ended") {
            val callId=data["call_id"].orEmpty()
            if(callId.isBlank()) return
            val incoming=data["incoming"]=="1"
            val state=data["state"].orEmpty().uppercase()
            val peer=data["peer_name"].orEmpty().ifBlank { data["caller_name"].orEmpty().ifBlank { "Messenger User" } }
            NotificationHelper.markCallTerminal(this,callId)
            if(incoming && state=="MISSED") NotificationHelper.showMissedCall(this,callId,peer)
            return
        }
'''
if old not in s: raise SystemExit("firebase call block missing")
p.write_text(s.replace(old,new,1))

p=pkg/"MainActivity.kt"
s=p.read_text()
s=s.replace('    private var notificationCallId by mutableStateOf<String?>(null)\n','    private var notificationCallId by mutableStateOf<String?>(null)\n    private var notificationCallAction by mutableStateOf<String?>(null)\n    private var openCallsFromNotification by mutableStateOf(false)\n',1)
s=s.replace('        consumeNotificationIntent(intent)\n        enableEdgeToEdge()','        consumeNotificationIntent(intent)\n        applyIncomingCallWindowPolicy(intent)\n        enableEdgeToEdge()',1)
s=s.replace('''                    initialCallId = notificationCallId,
                    onInitialDestinationConsumed = {''','''                    initialCallId = notificationCallId,
                    initialCallAction = notificationCallAction,
                    initialOpenCalls = openCallsFromNotification,
                    onInitialDestinationConsumed = {''',1)
s=s.replace('''                        notificationCallId = null
''','''                        notificationCallId = null
                        notificationCallAction = null
                        openCallsFromNotification = false
''',1)
s=s.replace('''        consumeNotificationIntent(intent)
    }

    private fun consumeNotificationIntent(intent: Intent?) {''','''        consumeNotificationIntent(intent)
        applyIncomingCallWindowPolicy(intent)
    }

    private fun consumeNotificationIntent(intent: Intent?) {''',1)
s=s.replace('''        notificationCallId = intent?.getStringExtra("call_id")
    }
}''','''        notificationCallId = intent?.getStringExtra("call_id")
        notificationCallAction = intent?.getStringExtra("call_action")
        openCallsFromNotification = intent?.getBooleanExtra("open_calls", false) == true
    }

    private fun applyIncomingCallWindowPolicy(intent: Intent?) {
        if(intent?.getBooleanExtra("incoming_call", false) != true) return
        if(android.os.Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true); setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
    }
}''',1)
p.write_text(s)

p=pkg/"MessengerApp.kt"
s=p.read_text()
s=s.replace('''    initialOpenUpdates: Boolean = false,
    initialCallId: String? = null,
    onInitialDestinationConsumed: () -> Unit = {}
''','''    initialOpenUpdates: Boolean = false,
    initialCallId: String? = null,
    initialCallAction: String? = null,
    initialOpenCalls: Boolean = false,
    onInitialDestinationConsumed: () -> Unit = {}
''',1)
anchor='    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }\n'
insert='''    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val notificationAnswerPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(granted) vm.acceptVoiceCall() else vm.lastToast="Microphone permission is required to answer calls"
        onInitialDestinationConsumed()
    }
'''
if anchor not in s: raise SystemExit("permission launcher missing")
s=s.replace(anchor,insert,1)
old='''    LaunchedEffect(initialCallId, vm.serverMode) {
        if (!initialCallId.isNullOrBlank() && vm.serverMode) {
            vm.openVoiceCallFromNotification(initialCallId)
            onInitialDestinationConsumed()
        }
    }
'''
new='''    LaunchedEffect(initialCallId, vm.serverMode) {
        if (!initialCallId.isNullOrBlank() && vm.serverMode) {
            vm.openVoiceCallFromNotification(initialCallId)
            if(initialCallAction!="answer") onInitialDestinationConsumed()
        }
    }
    LaunchedEffect(initialCallId, initialCallAction, vm.activeVoiceCall?.callId, vm.activeVoiceCall?.phase) {
        val call=vm.activeVoiceCall
        if(initialCallAction=="answer" && !initialCallId.isNullOrBlank() && call?.callId==initialCallId && call.phase==VoiceCallPhase.INCOMING_RINGING) {
            if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) {
                vm.acceptVoiceCall(); onInitialDestinationConsumed()
            } else notificationAnswerPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    LaunchedEffect(initialOpenCalls, vm.screen) {
        if(initialOpenCalls && vm.screen==AppScreen.Home) {
            vm.selectedTab=MainTab.CALLS
            onInitialDestinationConsumed()
        }
    }
'''
if old not in s: raise SystemExit("initial call effect missing")
p.write_text(s.replace(old,new,1))

p=pkg/"AppViewModel.kt"
s=p.read_text()
s=s.replace('current?.let { NotificationHelper.cancelIncomingCall(getApplication(),it.callId) }','current?.let { NotificationHelper.markCallTerminal(getApplication(),it.callId) }',1)
s=s.replace('''            "call.accepted" -> {
                val remote=event.payload.optJSONObject("call")?.let(api::parseVoiceCallPayload) ?: return
                applyRemoteCall(remote)
''','''            "call.accepted" -> {
                val remote=event.payload.optJSONObject("call")?.let(api::parseVoiceCallPayload) ?: return
                NotificationHelper.cancelIncomingCall(getApplication(),remote.id)
                applyRemoteCall(remote)
''',1)
old='''            "call.ended" -> {
                val remote=event.payload.optJSONObject("call")?.let(api::parseVoiceCallPayload)
                if(remote!=null && activeVoiceCall?.callId==remote.id) finishVoiceCall(remote,false) else refreshServerCallsAsync()
            }
'''
new='''            "call.ended" -> {
                val remote=event.payload.optJSONObject("call")?.let(api::parseVoiceCallPayload)
                if(remote!=null) {
                    NotificationHelper.markCallTerminal(getApplication(),remote.id)
                    if(remote.incoming && remote.state=="MISSED" && !NotificationRuntime.appVisible) NotificationHelper.showMissedCall(getApplication(),remote.id,remote.peerName)
                }
                if(remote!=null && activeVoiceCall?.callId==remote.id) finishVoiceCall(remote,false) else refreshServerCallsAsync()
            }
'''
if old not in s: raise SystemExit("call ended event missing")
p.write_text(s.replace(old,new,1))

p=root/"app/src/main/AndroidManifest.xml"
s=p.read_text()
anchor='''        <service
            android:name=".CallForegroundService"
            android:exported="false"
            android:foregroundServiceType="microphone" />
'''
if anchor not in s: raise SystemExit("manifest call service missing")
s=s.replace(anchor,anchor+'''\n        <receiver
            android:name=".CallNotificationActionReceiver"
            android:exported="false" />
''',1)
p.write_text(s)

print("incoming/background call lifecycle 2.14 applied")
