from pathlib import Path
import re
root=Path("/tmp/messenger-build/messenger_live")
pkg=root/"app/src/main/java/com/example/messengerui"

# version
p=root/"app/build.gradle.kts"
s=p.read_text().replace("versionCode = 27","versionCode = 28").replace('versionName = "2.16"','versionName = "2.17"')
p.write_text(s)

# New installs must start from server authority, never demo contacts/chats/statuses/calls.
p=pkg/"LocalMessengerDb.kt"
s=p.read_text().replace("        seed(db)\n","")
start=s.find("    private fun seed(db: SQLiteDatabase) {")
end=s.find("    fun isLoggedIn(): Boolean", start)
if start < 0 or end < 0:
    raise SystemExit("demo seed block not found")
s=s[:start]+s[end:]
p.write_text(s)

# Remove demo-only screen model.
p=pkg/"Models.kt"
s=p.read_text().replace("    data class DemoCall(val contactName: String, val type: CallType) : AppScreen\n","")
p.write_text(s)

# Remove placeholder/fake ViewModel features, retain read receipts which are functional.
p=pkg/"AppViewModel.kt"
s=p.read_text()
for line in [
    '    var showLastSeen by mutableStateOf(db.getBooleanSetting("show_last_seen", true))\n',
    '    var showProfilePhoto by mutableStateOf(db.getBooleanSetting("show_profile_photo", true))\n',
    '    var appLock by mutableStateOf(db.getBooleanSetting("app_lock", false))\n',
    '    var disappearingMessages by mutableStateOf(db.getBooleanSetting("disappearing_messages", false))\n',
    '    fun setLastSeen(value: Boolean) { showLastSeen = value; db.setBooleanSetting("show_last_seen", value) }\n',
    '    fun setProfilePhotoVisibility(value: Boolean) { showProfilePhoto = value; db.setBooleanSetting("show_profile_photo", value) }\n',
    '    fun updateDisappearingMessages(value: Boolean) { disappearingMessages = value; db.setBooleanSetting("disappearing_messages", value) }\n',
    '    fun updateAppLock(value: Boolean) { appLock = value; db.setBooleanSetting("app_lock", value) }\n',
]:
    s=s.replace(line,"")

# Local-only fake attachment action is not a production feature.
start=s.find("    fun addMediaMessage(chatId: String, kind: MessageKind) {")
end=s.find("    fun importAttachment(",start)
if start >= 0 and end >= 0:
    s=s[:start]+s[end:]

# Status posting must use server authority.
old='''        if (!serverMode) {
            val item=StatusItem("status${System.currentTimeMillis()}",profile.name,"Just now",mine=true,caption=text,kind=kind,privacy=privacy)
            statuses.add(0,item); db.addStatus(item); lastToast="Status saved locally"; return
        }
'''
if old not in s:
    raise SystemExit("status local fallback not found")
s=s.replace(old,'        if (!serverMode) { lastToast="Connect to the server before posting status"; return }\n')
p.write_text(s)

# UI: expose only wired functionality.
p=pkg/"MessengerApp.kt"
s=p.read_text()
s=s.replace("            is AppScreen.DemoCall -> DemoCallScreen(vm, screen.contactName, screen.type)\n","")
for line in [
    '            TextButton(onClick = { vm.lastToast = "Photo picker will connect to device storage in the media phase" }) { Text("Add profile photo") }\n',
    '            ListItem(headlineContent={Text("Product updates")},supportingContent={Text("Messenger updates")},leadingContent={Avatar("Product")},trailingContent={AssistChip(onClick={vm.lastToast="Following product updates"},label={Text("Follow")})})\n',
    '        item { SettingsRow(Icons.Outlined.Chat, "Chats", "Theme, wallpaper, chat history") { vm.lastToast = "Chat appearance settings opened" } }\n',
    '        item { SettingsRow(Icons.Outlined.AccountCircle, "Account", "Security notifications, change number") { vm.lastToast = "Account controls ready for API integration" } }\n',
    '        item { SettingsRow(Icons.Outlined.HelpOutline, "Help", "Help centre and app info") { vm.lastToast = "Help centre placeholder" } }\n',
    '            TextButton(onClick = { vm.lastToast = "Profile photo picker placeholder" }) { Text("Change photo") }\n',
    '            item { ToggleRow("Last seen & online", "Show your activity status", vm.showLastSeen) { vm.setLastSeen(it) } }\n',
    '            item { ToggleRow("Profile photo", "Allow contacts to see your photo", vm.showProfilePhoto) { vm.setProfilePhotoVisibility(it) } }\n',
    '            item { ToggleRow("Default disappearing messages", "Automatically expire new chat messages", vm.disappearingMessages) { vm.updateDisappearingMessages(it) } }\n',
    '            item { ToggleRow("Biometric app lock", "Require device authentication to open Messenger", vm.appLock) { vm.updateAppLock(it) } }\n',
    '            item { SettingsRow(Icons.Outlined.Block, "Blocked contacts", "0 blocked contacts") { vm.lastToast = "Blocked contacts list is empty" } }\n',
    '                IconButton(onClick = { vm.lastToast = "Forward flow selected"; vm.setSelectedMessage(null) }) { Icon(Icons.Outlined.Forward, "Forward") }\n',
    '                        ActionCard(Icons.Outlined.Videocam, "Video") { vm.lastToast = "Video calling is unavailable" }\n',
    '                SettingsRow(Icons.Outlined.PhotoLibrary, "Media, links and docs", "Shared content in this conversation") { vm.lastToast = "Media gallery UI placeholder" }\n',
]:
    s=s.replace(line,"")

new_contact='''                ListItem(
                    modifier = Modifier.clickable { vm.lastToast = "New contact form will write to device/server contact source later" },
                    leadingContent = { RoundIcon(Icons.Filled.PersonAdd) },
                    headlineContent = { Text("New contact", fontWeight = FontWeight.Medium) },
                    trailingContent = { Icon(Icons.Outlined.QrCode, null) }
                )
'''
s=s.replace(new_contact,"")

s=s.replace(
'''                Triple(Icons.Filled.Headphones, "Audio", MessageKind.AUDIO),
                Triple(Icons.Filled.LocationOn, "Location", MessageKind.LOCATION)
''',
'''                Triple(Icons.Filled.Headphones, "Audio", MessageKind.AUDIO)
'''
)

# Remove demo call composable.
start=s.find("@Composable\nprivate fun DemoCallScreen")
if start >= 0:
    brace=s.find("{",start)
    depth=0
    end=None
    for i in range(brace,len(s)):
        if s[i]=="{": depth+=1
        elif s[i]=="}":
            depth-=1
            if depth==0:
                end=i+1
                break
    if end is None:
        raise SystemExit("DemoCallScreen end not found")
    s=s[:start]+s[end:]

# Remove fake moderation actions if exact text remains.
s=re.sub(r'\n\s*ListItem\(\n\s*modifier = Modifier\.clickable \{ vm\.lastToast = "Block action prepared for server-side persistence" \},.*?\n\s*\)\n','\n',s,flags=re.S)
s=re.sub(r'\n\s*ListItem\(\n\s*modifier = Modifier\.clickable \{ vm\.lastToast = "Report action prepared for moderation API" \},.*?\n\s*\)\n','\n',s,flags=re.S)
s=s.replace(
    'IconButton(onClick = { if (call.type == CallType.AUDIO) requestRedial() else vm.lastToast = "Video calling is unavailable" }) {',
    'IconButton(onClick = { if (call.type == CallType.AUDIO) requestRedial() }) {'
)
p.write_text(s)

# One canonical incoming-call channel; remove obsolete historical channels.
p=pkg/"NotificationHelper.kt"
s=p.read_text()
needle='        val manager = context.getSystemService(NotificationManager::class.java)\n'
if needle not in s:
    raise SystemExit("NotificationHelper manager anchor missing")
s=s.replace(needle,needle+'        listOf("calls", "calls_v2").forEach { oldId -> runCatching { manager.deleteNotificationChannel(oldId) } }\n',1)
p.write_text(s)

(root/"CANONICAL-INTEGRITY.md").write_text("""# Messenger Canonical Integrity
This tree is the direct consolidated Messenger Android source authority.
- No demo seed on new installs.
- No placeholder-only actions exposed.
- One incoming-call notification channel authority.
- Existing REST/realtime/Firebase/groups/status/media/voice/TURN paths preserved.
- OFFER/ANSWER/ICE signalling unchanged.
""")

print("canonical Messenger 2.17 cleanup applied")
