from pathlib import Path
root=Path('/tmp/messenger-build/messenger_live')

# NotificationHelper silent incoming calls
p=root/'app/src/main/java/com/example/messengerui/NotificationHelper.kt'
s=p.read_text()
s=s.replace('    private const val MISSED_CALLS_CHANNEL = "missed_calls"','    private const val MISSED_CALLS_CHANNEL = "missed_calls"\n    private const val SILENT_CALLS_CHANNEL = "calls_silent"')
anchor='''        manager.createNotificationChannel(NotificationChannel(MISSED_CALLS_CHANNEL, "Missed calls", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Missed Messenger calls"
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        })'''
if anchor not in s: raise SystemExit('missed channel anchor missing')
s=s.replace(anchor,anchor+'''
        manager.createNotificationChannel(NotificationChannel(SILENT_CALLS_CHANNEL, "Silenced unknown calls", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Unknown Messenger calls that do not ring"
            enableVibration(false)
            setSound(null, null)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        })''',1)
insert_before='''    fun cancelIncomingCall(context:Context,callId:String){'''
method='''    fun showSilentIncomingCall(context:Context,callId:String,callerName:String,callerPhone:String=""):Boolean {
        if(!canNotify(context) || callId.isBlank()) return false
        if(CallAlertStore.isTerminal(context,callId)) return false
        createChannels(context)
        val openIntent=Intent(context,IncomingCallActivity::class.java).apply {
            action="com.example.messengerui.INCOMING_CALL"
            data=android.net.Uri.parse("messenger://call/$callId")
            flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(IncomingCallActivity.EXTRA_CALL_ID,callId)
            putExtra(IncomingCallActivity.EXTRA_CALLER_NAME,callerName)
            putExtra(IncomingCallActivity.EXTRA_CALLER_PHONE,callerPhone)
        }
        val openPi=PendingIntent.getActivity(context,("silent-call:$callId").hashCode(),openIntent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val declineIntent=Intent(context,CallNotificationActionReceiver::class.java).apply {
            action=CallNotificationActionReceiver.ACTION_DECLINE
            putExtra(CallNotificationActionReceiver.EXTRA_CALL_ID,callId)
        }
        val declinePi=PendingIntent.getBroadcast(context,("silent-decline:$callId").hashCode(),declineIntent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n=NotificationCompat.Builder(context,SILENT_CALLS_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(callerName)
            .setContentText(if(callerPhone.isBlank())"Silenced unknown call" else "Silenced unknown call · "+callerPhone)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(openPi)
            .setAutoCancel(true)
            .setSilent(true)
            .addAction(R.drawable.ic_notification,"Decline",declinePi)
            .build()
        NotificationManagerCompat.from(context).notify(incomingCallNotificationId(callId),n)
        CallAlertStore.markIncomingShown(context,callId)
        return true
    }

'''
if insert_before not in s: raise SystemExit('cancel incoming anchor missing')
s=s.replace(insert_before,method+insert_before,1)
p.write_text(s)

# Firebase unknown caller silence
p=root/'app/src/main/java/com/example/messengerui/MessengerFirebaseService.kt'
s=p.read_text()
import re
pattern=r'''if\s*\(callId\.isNotBlank\(\)\s*&&\s*!CallAlertStore\.isTerminal\(this,\s*callId\)\)\s*\{\s*NotificationHelper\.showIncomingCall\([^\n]+\)\s*\}'''
new='''if(callId.isNotBlank() && !CallAlertStore.isTerminal(this,callId)) {
                    val callerId=data["caller_id"].orEmpty()
                    val name=data["caller_name"].orEmpty().ifBlank { "Messenger User" }
                    val phone=data["caller_phone"].orEmpty()
                    val silenceUnknown=db.getBooleanSetting("silence_unknown_callers",false) && !db.hasContactId(callerId)
                    if(silenceUnknown) NotificationHelper.showSilentIncomingCall(this,callId,name,phone)
                    else NotificationHelper.showIncomingCall(this,callId,name,phone)
                }'''
s,count=re.subn(pattern,new,s,count=1,flags=re.MULTILINE)
if count!=1: raise SystemExit('firebase incoming semantic block missing')
p.write_text(s)

# MessengerApp routing + call switch + privacy + forward/media screens
p=root/'app/src/main/java/com/example/messengerui/MessengerApp.kt'
s=p.read_text()
s=s.replace('vm.openVoiceCallFromNotification(initialCallId)','vm.openVoiceCallFromNotification(initialCallId, initialCallAction=="answer")')
s=s.replace('            is AppScreen.CallDetails -> CallDetailsScreen(vm, screen.callId)\n','            is AppScreen.CallDetails -> CallDetailsScreen(vm, screen.callId)\n            is AppScreen.ForwardMessage -> ForwardMessageScreen(vm, screen.messageId)\n            is AppScreen.MediaBrowser -> MediaBrowserScreen(vm, screen.chatId)\n')
s=s.replace('''            item { ToggleRow("Read receipts", "Show read receipts after opening messages", vm.readReceipts) { vm.updateReadReceipts(it) } }''',
'''            item { ToggleRow("Read receipts", "Show read receipts after opening messages", vm.readReceipts) { vm.updateReadReceipts(it) } }
            item { ToggleRow("Silence unknown callers", "Unknown numbers still appear in call history but do not ring", vm.silenceUnknownCallers) { vm.updateSilenceUnknownCallers(it) } }''',1)
# forward icon before delete
s=s.replace('''                if (msg.outgoing && msg.kind == MessageKind.TEXT && !msg.deleted) IconButton(onClick = { vm.startEdit(selectedId) }) { Icon(Icons.Outlined.Edit, "Edit") }
                IconButton(onClick = { showDelete=true })''',
'''                if (msg.outgoing && msg.kind == MessageKind.TEXT && !msg.deleted) IconButton(onClick = { vm.startEdit(selectedId) }) { Icon(Icons.Outlined.Edit, "Edit") }
                if(!msg.deleted && !msg.serverId.isNullOrBlank()) IconButton(onClick={vm.navigate(AppScreen.ForwardMessage(selectedId))}) { Icon(Icons.Outlined.Forward,"Forward") }
                IconButton(onClick = { showDelete=true })''',1)
# media row in chat info before mute
s=s.replace('''                SettingsRow(Icons.Outlined.NotificationsOff, if (chat.muted) "Unmute notifications" else "Mute notifications", "Control alerts for this conversation") { vm.muteChat(chatId) }''',
'''                SettingsRow(Icons.Outlined.PermMedia, "Media, links and docs", "Browse shared photos, videos, files and links") { vm.navigate(AppScreen.MediaBrowser(chatId)) }
                SettingsRow(Icons.Outlined.NotificationsOff, if (chat.muted) "Unmute notifications" else "Mute notifications", "Control alerts for this conversation") { vm.muteChat(chatId) }''',1)

# append screens before formatFileSize
marker='''private fun formatFileSize(bytes: Long): String = when {'''
screens='''@Composable
private fun ForwardMessageScreen(vm:AppViewModel,messageId:Long){
    val msg=vm.messages.firstOrNull { it.id==messageId }
    val selected=remember { mutableStateListOf<String>() }
    SimpleBackScaffold(title="Forward message",onBack={vm.back()}) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)){
            if(msg==null){ Text("Message unavailable",modifier=Modifier.padding(16.dp)); return@Column }
            Text("Select up to 5 chats",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(16.dp))
            LazyColumn(Modifier.weight(1f)){
                items(vm.chats.filter { !it.archived },key={it.id}) { chat ->
                    val checked=selected.contains(chat.id)
                    ListItem(
                        modifier=Modifier.clickable {
                            if(checked) selected.remove(chat.id) else if(selected.size<5) selected.add(chat.id)
                        },
                        leadingContent={Checkbox(checked,onCheckedChange={ value -> if(value){if(selected.size<5&&!selected.contains(chat.id))selected.add(chat.id)}else selected.remove(chat.id)})},
                        headlineContent={Text(chat.title)},
                        supportingContent={Text(if(chat.isGroup)"Group" else chat.subtitle,maxLines=1,overflow=TextOverflow.Ellipsis)}
                    )
                }
            }
            Button(onClick={vm.forwardMessage(messageId,selected.toSet())},enabled=selected.isNotEmpty(),modifier=Modifier.fillMaxWidth().padding(16.dp)){Text("Forward")}
        }
    }
}

@Composable
private fun MediaBrowserScreen(vm:AppViewModel,chatId:String){
    var tab by remember { mutableIntStateOf(0) }
    val all=vm.messages.filter { it.chatId==chatId && !it.deleted }
    val media=all.filter { it.kind==MessageKind.IMAGE || it.kind==MessageKind.VIDEO || it.kind==MessageKind.AUDIO }
    val docs=all.filter { it.kind==MessageKind.DOCUMENT }
    val linkRegex=remember { Regex("https?://[^\\\\s]+",RegexOption.IGNORE_CASE) }
    val links=all.mapNotNull { m -> linkRegex.find(m.text)?.value?.let { m to it } }
    SimpleBackScaffold(title="Media, links and docs",onBack={vm.back()}) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)){
            Row(Modifier.fillMaxWidth().padding(8.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                listOf("Media","Links","Docs").forEachIndexed { index,label -> FilterChip(selected=tab==index,onClick={tab=index},label={Text(label)}) }
            }
            LazyColumn(Modifier.fillMaxSize()){
                when(tab){
                    0 -> {
                        if(media.isEmpty()) item{Text("No shared media",modifier=Modifier.padding(20.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)}
                        items(media,key={it.id}) { m -> ListItem(modifier=Modifier.clickable{vm.openAttachment(m.id)},leadingContent={Icon(iconForMessageKind(m.kind),null)},headlineContent={Text(m.fileName ?: m.kind.name.lowercase().replaceFirstChar{it.uppercase()})},supportingContent={Text(m.time)}) }
                    }
                    1 -> {
                        if(links.isEmpty()) item{Text("No shared links",modifier=Modifier.padding(20.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)}
                        items(links,key={it.first.id}) { pair -> ListItem(modifier=Modifier.clickable{vm.openUrl(pair.second)},leadingContent={Icon(Icons.Outlined.Link,null)},headlineContent={Text(pair.second,maxLines=2,overflow=TextOverflow.Ellipsis)},supportingContent={Text(pair.first.time)}) }
                    }
                    else -> {
                        if(docs.isEmpty()) item{Text("No shared documents",modifier=Modifier.padding(20.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)}
                        items(docs,key={it.id}) { m -> ListItem(modifier=Modifier.clickable{vm.openAttachment(m.id)},leadingContent={Icon(Icons.Outlined.Description,null)},headlineContent={Text(m.fileName ?: "Document")},supportingContent={Text((if(m.fileSizeBytes>0) formatFileSize(m.fileSizeBytes)+" · " else "")+m.time)}) }
                    }
                }
            }
        }
    }
}

'''
if marker not in s: raise SystemExit('format marker missing')
s=s.replace(marker,screens+marker,1)
p.write_text(s)

print('2.23 runtime UI parity applied')
