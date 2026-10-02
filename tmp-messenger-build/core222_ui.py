from pathlib import Path
root=Path('/tmp/messenger-build/messenger_live')

src=Path('tmp-messenger-build/IncomingCallActivity222.kt').read_text()
(root/'app/src/main/java/com/example/messengerui/IncomingCallActivity.kt').write_text(src)

p=root/'app/src/main/AndroidManifest.xml'
s=p.read_text()
anchor='''        <activity
            android:name=".MainActivity"
'''
if anchor not in s: raise SystemExit('manifest main activity anchor missing')
activity='''        <activity
            android:name=".IncomingCallActivity"
            android:exported="false"
            android:excludeFromRecents="true"
            android:launchMode="singleTask"
            android:showWhenLocked="true"
            android:turnScreenOn="true"
            android:theme="@style/Theme.MessengerUI" />

'''
s=s.replace(anchor,activity+anchor,1)
p.write_text(s)

p=root/'app/src/main/java/com/example/messengerui/NotificationHelper.kt'
s=p.read_text()
s=s.replace('fun showIncomingCall(context:Context,callId:String,callerName:String):Boolean {','fun showIncomingCall(context:Context,callId:String,callerName:String,callerPhone:String=""):Boolean {')
old='''        val openIntent=Intent(context,MainActivity::class.java).apply {
            action = "com.example.messengerui.INCOMING_CALL"
            data = android.net.Uri.parse("messenger://call/$callId")
            flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("call_id",callId); putExtra("incoming_call",true)
        }'''
new='''        val openIntent=Intent(context,IncomingCallActivity::class.java).apply {
            action = "com.example.messengerui.INCOMING_CALL"
            data = android.net.Uri.parse("messenger://call/$callId")
            flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(IncomingCallActivity.EXTRA_CALL_ID,callId)
            putExtra(IncomingCallActivity.EXTRA_CALLER_NAME,callerName)
            putExtra(IncomingCallActivity.EXTRA_CALLER_PHONE,callerPhone)
        }'''
if old not in s: raise SystemExit('incoming notification open intent anchor missing')
s=s.replace(old,new,1)
p.write_text(s)

p=root/'app/src/main/java/com/example/messengerui/MessengerFirebaseService.kt'
s=p.read_text().replace('NotificationHelper.showIncomingCall(this,callId,data["caller_name"].orEmpty().ifBlank { "Messenger User" })','NotificationHelper.showIncomingCall(this,callId,data["caller_name"].orEmpty().ifBlank { "Messenger User" },data["caller_phone"].orEmpty())')
p.write_text(s)

p=root/'app/src/main/java/com/example/messengerui/AppViewModel.kt'
s=p.read_text().replace('NotificationHelper.showIncomingCall(getApplication(),remote.id,remote.peerName)','NotificationHelper.showIncomingCall(getApplication(),remote.id,remote.peerName,remote.peerPhone)')
p.write_text(s)

p=root/'app/src/main/java/com/example/messengerui/MessengerApp.kt'
s=p.read_text()
start=s.index('@Composable\nprivate fun SelectedMessageBar')
end=s.index('\n@Composable\nprivate fun MessageBubble',start)
replacement='''@Composable
private fun SelectedMessageBar(vm: AppViewModel, selectedId: Long) {
    val msg = vm.messages.firstOrNull { it.id == selectedId } ?: return
    var showDelete by remember(selectedId) { mutableStateOf(false) }
    if(showDelete){
        AlertDialog(
            onDismissRequest={showDelete=false},
            title={Text("Delete message?")},
            text={Text(if(msg.outgoing && !msg.serverId.isNullOrBlank()) "Choose whether to delete this message only for you or for everyone." else "Delete this message for you?")},
            confirmButton={
                Column {
                    if(msg.outgoing && !msg.serverId.isNullOrBlank()) TextButton(onClick={showDelete=false;vm.deleteMessageForEveryone(selectedId)}){Text("Delete for everyone")}
                    TextButton(onClick={showDelete=false;vm.deleteMessageForMe(selectedId)}){Text("Delete for me")}
                }
            },
            dismissButton={TextButton(onClick={showDelete=false}){Text("Cancel")}}
        )
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, tonalElevation = 4.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                    Text("Message selected", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Text(if (msg.kind == MessageKind.TEXT) msg.text else (msg.fileName ?: msg.kind.name.lowercase().replaceFirstChar { it.uppercase() }),maxLines = 1,overflow = TextOverflow.Ellipsis,style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { vm.setSelectedMessage(null) }) { Icon(Icons.Filled.Close, "Close selection") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                if (!msg.deleted) IconButton(onClick = { vm.startReply(selectedId) }) { Icon(Icons.Outlined.Reply, "Reply") }
                if (!msg.deleted) IconButton(onClick = { vm.toggleReaction(selectedId) }) { Icon(Icons.Outlined.FavoriteBorder, "React") }
                IconButton(onClick = { vm.starMessage(selectedId) }) { Icon(if (vm.starredMessageIds.contains(selectedId)) Icons.Filled.Star else Icons.Outlined.StarOutline, "Star") }
                if (msg.outgoing && msg.kind == MessageKind.TEXT && !msg.deleted) IconButton(onClick = { vm.startEdit(selectedId) }) { Icon(Icons.Outlined.Edit, "Edit") }
                IconButton(onClick = { showDelete=true }) { Icon(Icons.Outlined.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
'''
s=s[:start]+replacement+s[end:]

old='''            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                if (chat.isGroup) {
                    ListItem(
                        modifier = Modifier.clickable { vm.leaveGroup(chatId) },
                        leadingContent = { Icon(Icons.Outlined.ExitToApp, null, tint = MaterialTheme.colorScheme.error) },
                        headlineContent = { Text("Leave group", color = MaterialTheme.colorScheme.error) }
                    )
                } else {
                    ListItem(
                        modifier = Modifier,
                        leadingContent = { Icon(Icons.Outlined.Block, null, tint = MaterialTheme.colorScheme.error) },
                        headlineContent = { Text("Block ${chat.title}", color = MaterialTheme.colorScheme.error) }
                    )
                }
                ListItem(
                    modifier = Modifier,
                    leadingContent = { Icon(Icons.Outlined.Report, null, tint = MaterialTheme.colorScheme.error) },
                    headlineContent = { Text("Report", color = MaterialTheme.colorScheme.error) }
                )
            }'''
new='''            item {
                var confirmClear by remember { mutableStateOf(false) }
                var confirmDelete by remember { mutableStateOf(false) }
                var confirmBlock by remember { mutableStateOf(false) }
                var confirmReport by remember { mutableStateOf(false) }
                if(confirmClear) AlertDialog(onDismissRequest={confirmClear=false},title={Text("Clear chat?")},text={Text("Messages will be removed from your history. The conversation stays available.")},confirmButton={TextButton(onClick={confirmClear=false;vm.clearChat(chatId)}){Text("Clear")}},dismissButton={TextButton(onClick={confirmClear=false}){Text("Cancel")}})
                if(confirmDelete) AlertDialog(onDismissRequest={confirmDelete=false},title={Text("Delete chat?")},text={Text("This removes the conversation and its message history for you.")},confirmButton={TextButton(onClick={confirmDelete=false;vm.deleteChat(chatId)}){Text("Delete")}},dismissButton={TextButton(onClick={confirmDelete=false}){Text("Cancel")}})
                if(confirmBlock) AlertDialog(onDismissRequest={confirmBlock=false},title={Text(if(vm.isUserBlocked(chat.memberIds.firstOrNull().orEmpty()))"Unblock contact?" else "Block contact?")},text={Text("Blocked contacts cannot message or call you.")},confirmButton={TextButton(onClick={confirmBlock=false;val id=chat.memberIds.firstOrNull().orEmpty();vm.setChatBlocked(chatId,!vm.isUserBlocked(id))}){Text(if(vm.isUserBlocked(chat.memberIds.firstOrNull().orEmpty()))"Unblock" else "Block")}},dismissButton={TextButton(onClick={confirmBlock=false}){Text("Cancel")}})
                if(confirmReport) AlertDialog(onDismissRequest={confirmReport=false},title={Text("Report conversation?")},text={Text("A report will be sent for review.")},confirmButton={TextButton(onClick={confirmReport=false;vm.reportChat(chatId)}){Text("Report")}},dismissButton={TextButton(onClick={confirmReport=false}){Text("Cancel")}})
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                ListItem(modifier=Modifier.clickable{confirmClear=true},leadingContent={Icon(Icons.Outlined.DeleteSweep,null)},headlineContent={Text("Clear chat")})
                if (chat.isGroup) {
                    ListItem(modifier = Modifier.clickable { vm.leaveGroup(chatId) },leadingContent = { Icon(Icons.Outlined.ExitToApp, null, tint = MaterialTheme.colorScheme.error) },headlineContent = { Text("Leave group", color = MaterialTheme.colorScheme.error) })
                } else {
                    ListItem(modifier=Modifier.clickable{confirmDelete=true},leadingContent={Icon(Icons.Outlined.Delete,null,tint=MaterialTheme.colorScheme.error)},headlineContent={Text("Delete chat",color=MaterialTheme.colorScheme.error)})
                    val peerId=chat.memberIds.firstOrNull().orEmpty()
                    val blocked=vm.isUserBlocked(peerId)
                    ListItem(modifier = Modifier.clickable { confirmBlock=true },leadingContent = { Icon(Icons.Outlined.Block, null, tint = MaterialTheme.colorScheme.error) },headlineContent = { Text((if(blocked)"Unblock " else "Block ")+chat.title, color = MaterialTheme.colorScheme.error) })
                }
                ListItem(modifier = Modifier.clickable { confirmReport=true },leadingContent = { Icon(Icons.Outlined.Report, null, tint = MaterialTheme.colorScheme.error) },headlineContent = { Text("Report", color = MaterialTheme.colorScheme.error) })
            }'''
if old not in s: raise SystemExit('chat info action block anchor missing')
s=s.replace(old,new,1)

s=s.replace('item { Text("Recent calls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp)) }',
'''item {
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
                Text("Recent calls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier=Modifier.weight(1f))
                if(vm.calls.isNotEmpty()) TextButton(onClick={vm.clearCallHistory()}){Text("Clear")}
            }
        }''',1)

s=s.replace('''            ListItem(headlineContent={Text("Duration")},supportingContent={Text(duration)})
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){''',
'''            ListItem(headlineContent={Text("Duration")},supportingContent={Text(duration)})
            ListItem(modifier=Modifier.clickable{vm.deleteCallFromHistory(call.id)},leadingContent={Icon(Icons.Outlined.Delete,null,tint=MaterialTheme.colorScheme.error)},headlineContent={Text("Remove from call log",color=MaterialTheme.colorScheme.error)})
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){''',1)

p.write_text(s)
print('2.22 UI and dedicated incoming call applied')
