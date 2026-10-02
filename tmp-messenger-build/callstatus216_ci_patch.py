from pathlib import Path
root=Path('/tmp/messenger-build/messenger_live')
p=root/'app/build.gradle.kts'; s=p.read_text().replace('versionCode = 26','versionCode = 27').replace('versionName = "2.15"','versionName = "2.16"'); p.write_text(s)
pkg=root/'app/src/main/java/com/example/messengerui'
(pkg/'CallPushDiagnostics.kt').write_text('''package com.example.messengerui

import android.content.Context
import android.os.Build
import android.app.NotificationManager

object CallPushDiagnostics {
    private const val PREFS = "messenger_call_push_diagnostics"
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun recordIncomingPush(context: Context, callId: String, sentAtMs: Long?) { val now=System.currentTimeMillis(); prefs(context).edit().putLong("last_received_at_ms",now).putLong("last_sent_at_ms",sentAtMs?:0L).putString("last_call_id",callId).apply() }
    fun recordNotificationShown(context: Context, fullScreenAllowed: Boolean) { prefs(context).edit().putLong("last_notification_at_ms",System.currentTimeMillis()).putBoolean("last_full_screen_allowed",fullScreenAllowed).apply() }
    fun fullScreenAllowed(context: Context): Boolean { if(Build.VERSION.SDK_INT<34) return true; val m=context.getSystemService(NotificationManager::class.java); return runCatching{m.canUseFullScreenIntent()}.getOrDefault(false) }
    fun summary(context: Context): String { val p=prefs(context); val r=p.getLong("last_received_at_ms",0L); val s=p.getLong("last_sent_at_ms",0L); val d=if(r>0L&&s>0L&&r>=s) r-s else -1L; return "Full-screen ${if(fullScreenAllowed(context)) "allowed" else "needs permission"}${if(d>=0L) " · last push ${d}ms" else ""}" }
}
''')
p=pkg/'NotificationHelper.kt'; s=p.read_text().replace('const val CALLS_CHANNEL = "calls_v2"','const val CALLS_CHANNEL = "calls_v3"')
old='''        val alreadyShown=CallAlertStore.wasIncomingRecentlyShown(context,callId)
        val ringtone=android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
        val n=NotificationCompat.Builder(context,CALLS_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(callerName)
            .setContentText("Incoming voice call")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(fullScreenPi)
            .setFullScreenIntent(fullScreenPi,true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(alreadyShown)
            .setTimeoutAfter(45_000)
            .setSound(ringtone)
            .setVibrate(longArrayOf(0,700,450,700,450,700))
            .addAction(R.drawable.ic_notification,"Decline",declinePi)
            .addAction(R.drawable.ic_notification,"Answer",answerPi)
            .build()
        NotificationManagerCompat.from(context).notify(incomingCallNotificationId(callId),n)
        CallAlertStore.markIncomingShown(context,callId)
        return true
'''
new='''        val alreadyShown=CallAlertStore.wasIncomingRecentlyShown(context,callId)
        val ringtone=android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
        val fullScreenAllowed=CallPushDiagnostics.fullScreenAllowed(context)
        val builder=NotificationCompat.Builder(context,CALLS_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(callerName).setContentText("Incoming voice call")
            .setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_CALL).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(fullScreenPi).setOngoing(true).setAutoCancel(false).setOnlyAlertOnce(alreadyShown).setTimeoutAfter(45_000)
            .setSound(ringtone).setVibrate(longArrayOf(0,700,450,700,450,700))
            .addAction(R.drawable.ic_notification,"Decline",declinePi).addAction(R.drawable.ic_notification,"Answer",answerPi)
        if(fullScreenAllowed) builder.setFullScreenIntent(fullScreenPi,true)
        val n=builder.build()
        NotificationManagerCompat.from(context).notify(incomingCallNotificationId(callId),n)
        CallAlertStore.markIncomingShown(context,callId); CallPushDiagnostics.recordNotificationShown(context,fullScreenAllowed)
        return true
'''
if old not in s: raise SystemExit('NotificationHelper anchor missing')
p.write_text(s.replace(old,new))
p=pkg/'MessengerFirebaseService.kt'; s=p.read_text().replace('import com.google.firebase.messaging.RemoteMessage\n','import com.google.firebase.messaging.RemoteMessage\nimport android.content.Context\nimport android.os.PowerManager\n')
old='''        if (eventType == "call.incoming") {
            val callId=data["call_id"].orEmpty()
            if(callId.isNotBlank() && !CallAlertStore.isTerminal(this,callId)) {
                NotificationHelper.showIncomingCall(this,callId,data["caller_name"].orEmpty().ifBlank { "Messenger User" })
            }
            return
        }
'''
new='''        if (eventType == "call.incoming") {
            val callId=data["call_id"].orEmpty(); CallPushDiagnostics.recordIncomingPush(this,callId,data["sent_at_ms"]?.toLongOrNull())
            val pm=getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Messenger:IncomingCallPush").apply{setReferenceCounted(false)}
            runCatching{wakeLock.acquire(15_000L)}
            try { if(callId.isNotBlank()&&!CallAlertStore.isTerminal(this,callId)) NotificationHelper.showIncomingCall(this,callId,data["caller_name"].orEmpty().ifBlank{"Messenger User"}) }
            finally { if(wakeLock.isHeld) runCatching{wakeLock.release()} }
            return
        }
'''
if old not in s: raise SystemExit('Firebase call anchor missing')
p.write_text(s.replace(old,new))
p=pkg/'AppViewModel.kt'; s=p.read_text(); anchor='''    fun showTestNotification() {
        val ok = NotificationHelper.showLocalMessageTest(getApplication(), "Messenger", "Notifications are ready for backend-delivered messages.")
        lastToast = if (ok) "Test notification sent" else "Allow notification permission first"
    }
'''
insert='''
    fun callNotificationReadinessText(): String = CallPushDiagnostics.summary(getApplication())
    fun openFullScreenCallSettings() {
        val context=getApplication<Application>()
        if(android.os.Build.VERSION.SDK_INT<34){ lastToast="Full-screen incoming calls are supported on this Android version"; return }
        runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply { data=Uri.parse("package:${context.packageName}"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) }
            .onFailure { lastToast="Unable to open full-screen call settings" }
    }
'''
if anchor not in s: raise SystemExit('ViewModel anchor missing')
p.write_text(s.replace(anchor,anchor+insert))
p=pkg/'MessengerApp.kt'; s=p.read_text(); anchor='''            item { SectionLabel("Android controls") }
            item { SettingsRow(Icons.Outlined.Tune, "Sound, vibration & importance", "Open Android notification channels and alert controls") { vm.openSystemNotificationSettings() } }
'''
new='''            item { SectionLabel("Incoming calls") }
            item { SettingsRow(Icons.Outlined.PhoneInTalk, "Full-screen incoming calls", vm.callNotificationReadinessText()) { vm.openFullScreenCallSettings() } }
            item { SectionLabel("Android controls") }
            item { SettingsRow(Icons.Outlined.Tune, "Sound, vibration & importance", "Open Android notification channels and alert controls") { vm.openSystemNotificationSettings() } }
'''
if anchor not in s: raise SystemExit('MessengerApp anchor missing')
p.write_text(s.replace(anchor,new))
p=root/'app/src/main/AndroidManifest.xml'; s=p.read_text(); old='''        <service
            android:name=".MessengerFirebaseService"
            android:exported="false">
'''; new='''        <service
            android:name=".MessengerFirebaseService"
            android:exported="false"
            android:stopWithTask="false">
'''
if old not in s: raise SystemExit('manifest anchor missing')
p.write_text(s.replace(old,new))
print('call/status 2.16 Android hardening applied')
