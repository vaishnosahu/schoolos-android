from pathlib import Path
root=Path('/tmp/messenger-build/messenger_live')

p=root/'app/build.gradle.kts'
s=p.read_text().replace('versionCode = 29','versionCode = 30').replace('versionName = "2.18"','versionName = "2.19"')
p.write_text(s)

p=root/'app/src/main/java/com/example/messengerui/AppViewModel.kt'
s=p.read_text()
s=s.replace('fun onAppForegrounded() {','fun onAppForegrounded(skipCallRecovery: Boolean = false) {')
s=s.replace('        recoverVoiceCallAsync()\n        val activeChat', '        if (!skipCallRecovery) recoverVoiceCallAsync()\n        val activeChat',1)
s=s.replace('                if(remote==null){ if(activeVoiceCall!=null) finishVoiceCall(null,false); return@onSuccess }','                if(remote==null){ return@onSuccess }')
p.write_text(s)

p=root/'app/src/main/java/com/example/messengerui/MessengerApp.kt'
s=p.read_text()
s=s.replace('Lifecycle.Event.ON_START -> vm.onAppForegrounded()','Lifecycle.Event.ON_START -> vm.onAppForegrounded(skipCallRecovery = !initialCallId.isNullOrBlank())')
p.write_text(s)

p=root/'app/src/main/AndroidManifest.xml'
s=p.read_text()
old='''        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">'''
new='''        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTop"
            android:showWhenLocked="true"
            android:turnScreenOn="true"
            android:windowSoftInputMode="adjustResize">'''
if old not in s: raise SystemExit('manifest activity anchor not found')
s=s.replace(old,new,1)
p.write_text(s)

p=root/'app/src/main/java/com/example/messengerui/NotificationHelper.kt'
s=p.read_text()
old='''        val openIntent=Intent(context,MainActivity::class.java).apply {
            flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("call_id",callId)'''
new='''        val openIntent=Intent(context,MainActivity::class.java).apply {
            action = "com.example.messengerui.INCOMING_CALL"
            data = android.net.Uri.parse("messenger://call/$callId")
            flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("call_id",callId)'''
if old not in s: raise SystemExit('open intent anchor not found')
s=s.replace(old,new,1)
old2='''        val answerIntent=Intent(context,MainActivity::class.java).apply {
            flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("call_id",callId)'''
new2='''        val answerIntent=Intent(context,MainActivity::class.java).apply {
            action = "com.example.messengerui.ANSWER_CALL"
            data = android.net.Uri.parse("messenger://call/$callId/answer")
            flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("call_id",callId)'''
if old2 not in s: raise SystemExit('answer intent anchor not found')
s=s.replace(old2,new2,1)
p.write_text(s)

print('incoming call 2.19 hardening applied')
