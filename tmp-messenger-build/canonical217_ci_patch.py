from pathlib import Path
root=Path('/tmp/messenger-build/messenger_live')

p=root/'app/build.gradle.kts'
s=p.read_text().replace('versionCode = 27','versionCode = 28').replace('versionName = "2.16"','versionName = "2.17"')
p.write_text(s)

p=root/'app/src/main/java/com/example/messengerui/LocalMessengerDb.kt'
s=p.read_text()
s=s.replace('        seed(db)\n','        initializeDefaults(db)\n',1)
start=s.index('    private fun seed(db: SQLiteDatabase) {')
end=s.index('    fun isLoggedIn(): Boolean',start)
defaults='''    private fun initializeDefaults(db: SQLiteDatabase) {\n        setState(db, "logged_in", "0")\n        setState(db, "profile_name", "You")\n        setState(db, "profile_phone", "")\n        setState(db, "profile_about", "Hey there! I am using Messenger.")\n        setState(db, "read_receipts", "1")\n        setState(db, "show_last_seen", "1")\n        setState(db, "demo_seeded", "0")\n        setState(db, "server_sync_cursor", "0")\n    }\n\n'''
s=s[:start]+defaults+s[end:]
anchor='''        if (oldVersion < 10) {\n            safeAlter(db, "ALTER TABLE statuses ADD COLUMN muted INTEGER NOT NULL DEFAULT 0")\n        }\n'''
if anchor not in s: raise SystemExit('db-upgrade-anchor')
s=s.replace(anchor,anchor+'''        if (oldVersion < 11) {\n            if (getState("demo_seeded", "0") == "1") {\n                db.delete("starred_messages", null, null); db.delete("messages", null, null); db.delete("chat_members", null, null); db.delete("chats", null, null); db.delete("contacts", null, null); db.delete("statuses", null, null); db.delete("call_logs", null, null); db.delete("sync_queue", null, null)\n                setState(db, "demo_seeded", "0")\n            }\n        }\n''',1)
s=s.replace('if (getState("demo_seeded", "0") == "1" || (previousServerUser.isNotBlank() && previousServerUser != user.id)) {','if (previousServerUser.isNotBlank() && previousServerUser != user.id) {')
s=s.replace('        private const val DB_VERSION = 10','        private const val DB_VERSION = 11')
p.write_text(s)

p=root/'app/src/main/java/com/example/messengerui/Models.kt'
s=p.read_text().replace('    data class DemoCall(val contactName: String, val type: CallType) : AppScreen\n','')
p.write_text(s)

p=root/'app/src/main/java/com/example/messengerui/MessengerApp.kt'
s=p.read_text()
for needle in [
'            is AppScreen.DemoCall -> DemoCallScreen(vm, screen.contactName, screen.type)\n',
'        item { SettingsRow(Icons.Outlined.AccountCircle, "Account", "Security notifications, change number") { vm.lastToast = "Account controls ready for API integration" } }\n',
'        item { SettingsRow(Icons.Outlined.HelpOutline, "Help", "Help centre and app info") { vm.lastToast = "Help centre placeholder" } }\n',
'            TextButton(onClick = { vm.lastToast = "Profile photo picker placeholder" }) { Text("Change photo") }\n',
'                        ActionCard(Icons.Outlined.Videocam, "Video") { vm.lastToast = "Video calling is unavailable" }\n',
'                SettingsRow(Icons.Outlined.PhotoLibrary, "Media, links and docs", "Shared content in this conversation") { vm.lastToast = "Media gallery UI placeholder" }\n',
'            item { ToggleRow("Profile photo", "Allow contacts to see your photo", vm.showProfilePhoto) { vm.setProfilePhotoVisibility(it) } }\n',
'            item { SectionLabel("Messages") }\n',
'            item { ToggleRow("Default disappearing messages", "Automatically expire new chat messages", vm.disappearingMessages) { vm.updateDisappearingMessages(it) } }\n',
'            item { SectionLabel("App protection") }\n',
'            item { ToggleRow("Biometric app lock", "Require device authentication to open Messenger", vm.appLock) { vm.updateAppLock(it) } }\n',
'            item { SettingsRow(Icons.Outlined.Block, "Blocked contacts", "0 blocked contacts") { vm.lastToast = "Blocked contacts list is empty" } }\n',
]: s=s.replace(needle,'')
s=s.replace('            item { SectionLabel("Who can see my personal info") }\n','            item { SectionLabel("Privacy") }\n')
s=s.replace('Show blue ticks after reading','Show read receipts after opening messages')
marker='modifier = Modifier.clickable { vm.lastToast = "New contact form will write to device/server contact source later" }'
if marker in s:
    i=s.rfind('                ListItem(',0,s.index(marker)); j=s.index('                SectionLabel("Contacts on Messenger")',s.index(marker)); s=s[:i]+s[j:]
s=s.replace('                Triple(Icons.Filled.Headphones, "Audio", MessageKind.AUDIO),\n                Triple(Icons.Filled.LocationOn, "Location", MessageKind.LOCATION)\n','                Triple(Icons.Filled.Headphones, "Audio", MessageKind.AUDIO)\n')
old='''                trailingContent = {\n                    IconButton(onClick = { if (call.type == CallType.AUDIO) requestRedial() else vm.lastToast = "Video calling is unavailable" }) {\n                        Icon(if (call.type == CallType.VIDEO) Icons.Outlined.Videocam else Icons.Outlined.Call, "Call")\n                    }\n                }\n'''
new='''                trailingContent = {\n                    if (call.type == CallType.AUDIO) {\n                        IconButton(onClick = { requestRedial() }) { Icon(Icons.Outlined.Call, "Call") }\n                    } else {\n                        Icon(Icons.Outlined.Videocam, "Video call history", tint = MaterialTheme.colorScheme.onSurfaceVariant)\n                    }\n                }\n'''
if old in s: s=s.replace(old,new,1)
if 'private fun DemoCallScreen' in s:
    st=s.index('@Composable\nprivate fun DemoCallScreen'); nx=s.find('\n@Composable',st+12); s=s[:st].rstrip()+"\n" if nx==-1 else s[:st]+s[nx+1:]
p.write_text(s)

p=root/'app/src/main/java/com/example/messengerui/AppViewModel.kt'
s=p.read_text()
for line in [
'    var showProfilePhoto by mutableStateOf(db.getBooleanSetting("show_profile_photo", true))\n        private set\n',
'    var appLock by mutableStateOf(db.getBooleanSetting("app_lock", false))\n        private set\n',
'    var disappearingMessages by mutableStateOf(db.getBooleanSetting("disappearing_messages", false))\n        private set\n',
'    fun setProfilePhotoVisibility(value: Boolean) { showProfilePhoto = value; db.setBooleanSetting("show_profile_photo", value) }\n',
'    fun updateDisappearingMessages(value: Boolean) { disappearingMessages = value; db.setBooleanSetting("disappearing_messages", value) }\n',
'    fun updateAppLock(value: Boolean) { appLock = value; db.setBooleanSetting("app_lock", value) }\n']:
    s=s.replace(line,'')
needle='    fun addMediaMessage(chatId: String, kind: MessageKind) {\n'
if needle in s: s=s.replace(needle,needle+'        if (kind == MessageKind.LOCATION) { lastToast = "Location sharing is not enabled"; return }\n',1)
s=s.replace('        lastToast = if (kind == MessageKind.LOCATION) "Location placeholder saved locally" else "$label saved locally"\n','        lastToast = "$label saved locally"\n')
old='''            "call.incoming" -> {\n                val obj=event.payload.optJSONObject("call") ?: return\n                val remote=api.parseVoiceCallPayload(obj)\n                voiceCallConfig=runCatching { withContext(Dispatchers.IO){ api.voiceCallConfig(db.serverToken()) } }.getOrNull()\n                callDiagnostics=callDiagnostics.copy(networkLabel=currentCallNetworkLabel(),turnConfigured=voiceCallConfig?.turnConfigured==true)\n                callNetworkHandle=connectivityManager.activeNetwork?.networkHandle\n                applyRemoteCall(remote)\n                if(NotificationRuntime.appVisible){ navigate(AppScreen.VoiceCall(remote.id)) }\n                else NotificationHelper.showIncomingCall(getApplication(),remote.id,remote.peerName)\n                startCallStateLoop()\n            }\n'''
new='''            "call.incoming" -> {\n                val obj=event.payload.optJSONObject("call") ?: return\n                val remote=api.parseVoiceCallPayload(obj)\n                voiceCallConfig=runCatching { withContext(Dispatchers.IO){ api.voiceCallConfig(db.serverToken()) } }.getOrNull()\n                callDiagnostics=callDiagnostics.copy(networkLabel=currentCallNetworkLabel(),turnConfigured=voiceCallConfig?.turnConfigured==true)\n                callNetworkHandle=connectivityManager.activeNetwork?.networkHandle\n                applyRemoteCall(remote)\n                if(NotificationRuntime.appVisible){\n                    navigate(AppScreen.VoiceCall(remote.id))\n                } else if(!PushDeduper.alreadyHandled(getApplication(), "call:${remote.id}")) {\n                    NotificationHelper.showIncomingCall(getApplication(),remote.id,remote.peerName)\n                }\n                startCallStateLoop()\n            }\n'''
if old not in s: raise SystemExit('call-incoming-anchor')
s=s.replace(old,new,1)
s=s.replace('''            "call.accepted" -> {\n                val remote=event.payload.optJSONObject("call")?.let(api::parseVoiceCallPayload) ?: return\n                NotificationHelper.cancelIncomingCall(getApplication(),remote.id)\n''','''            "call.accepted" -> {\n                val remote=event.payload.optJSONObject("call")?.let(api::parseVoiceCallPayload) ?: return\n                PushDeduper.alreadyHandled(getApplication(), "call-state:${remote.id}:ACCEPTED")\n                NotificationHelper.cancelIncomingCall(getApplication(),remote.id)\n''',1)
old='''            "call.ended" -> {\n                val remote=event.payload.optJSONObject("call")?.let(api::parseVoiceCallPayload)\n                if(remote!=null) {\n                    NotificationHelper.markCallTerminal(getApplication(),remote.id)\n                    if(remote.incoming && remote.state=="MISSED" && !NotificationRuntime.appVisible) NotificationHelper.showMissedCall(getApplication(),remote.id,remote.peerName)\n                }\n'''
new='''            "call.ended" -> {\n                val remote=event.payload.optJSONObject("call")?.let(api::parseVoiceCallPayload)\n                if(remote!=null) {\n                    val eventKey="call-state:${remote.id}:${remote.state}"\n                    val alreadyHandled=PushDeduper.alreadyHandled(getApplication(),eventKey)\n                    NotificationHelper.markCallTerminal(getApplication(),remote.id)\n                    if(!alreadyHandled && remote.incoming && remote.state=="MISSED" && !NotificationRuntime.appVisible) NotificationHelper.showMissedCall(getApplication(),remote.id,remote.peerName)\n                }\n'''
if old not in s: raise SystemExit('call-ended-anchor')
s=s.replace(old,new,1)
p.write_text(s)

p=root/'app/src/main/java/com/example/messengerui/NotificationRuntime.kt'
s=p.read_text(); start=s.index('object PushDeduper {')
s=s[:start]+r'''object PushDeduper {
    private const val PREFS = "messenger_push_dedupe_v2"
    private const val KEY_RECENT = "recent_keys_v2"
    private const val MAX_KEYS = 512
    private const val TTL_MS = 24L * 60L * 60L * 1000L
    @Synchronized fun alreadyHandled(context: Context, eventKey: String): Boolean {
        if (eventKey.isBlank()) return false
        val now=System.currentTimeMillis(); val prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val entries=prefs.getString(KEY_RECENT,"").orEmpty().lineSequence().mapNotNull { line ->
            val pos=line.lastIndexOf('|'); if(pos<=0) return@mapNotNull null
            val key=line.substring(0,pos); val at=line.substring(pos+1).toLongOrNull() ?: return@mapNotNull null
            if(now-at<=TTL_MS) key to at else null
        }.toMutableList()
        if(entries.any{it.first==eventKey}) return true
        entries.add(eventKey to now)
        prefs.edit().putString(KEY_RECENT,entries.takeLast(MAX_KEYS).joinToString("\n"){"${it.first}|${it.second}"}).apply(); return false
    }
}
'''
p.write_text(s)

p=root/'app/src/main/java/com/example/messengerui/NotificationHelper.kt'
s=p.read_text()
if 'cleanupLegacyChannels(manager)' not in s:
    s=s.replace('        val manager = context.getSystemService(NotificationManager::class.java)\n','        val manager = context.getSystemService(NotificationManager::class.java)\n        cleanupLegacyChannels(manager)\n',1)
    pos=s.index('    private fun canNotify')
    s=s[:pos]+'''    private fun cleanupLegacyChannels(manager: NotificationManager) {\n        listOf("calls", "calls_v2").forEach { legacy -> if (legacy != CALLS_CHANNEL) runCatching { manager.deleteNotificationChannel(legacy) }\n    }\n\n'''+s[pos:]
p.write_text(s)
# Replace notification handling with one canonical full-file authority rather than patching historical variants.
notification_authority = Path("tmp-messenger-build/NotificationHelper217.kt")
(root/"app/src/main/java/com/example/messengerui/NotificationHelper.kt").write_text(notification_authority.read_text())
print('canonical 2.17 applied')
