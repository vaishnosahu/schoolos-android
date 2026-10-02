from pathlib import Path
root=Path('/tmp/messenger-build/messenger_live')
p=root/'app/src/main/java/com/example/messengerui/MessengerApp.kt'
s=p.read_text()
s=s.replace('            is AppScreen.VoiceCall -> VoiceCallScreen(vm, screen.callId)\n','            is AppScreen.VoiceCall -> VoiceCallScreen(vm, screen.callId)\n            is AppScreen.CallDetails -> CallDetailsScreen(vm, screen.callId)\n')
start=s.index('@Composable\nprivate fun CallsTab(vm: AppViewModel) {')
end=s.index('\n@Composable\nprivate fun SettingsTab',start)
replacement='''@Composable
private fun CallsTab(vm: AppViewModel) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { Text("Recent calls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp)) }
        if(vm.calls.isEmpty()) {
            item { Text("No calls yet", modifier=Modifier.padding(horizontal=16.dp, vertical=24.dp), color=MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(vm.calls, key = { it.id }) { call ->
            val requestRedial = rememberVoiceCallPermissionAction(vm) { vm.redialVoiceCall(call) }
            val stateLabel=when(call.state.uppercase()){
                "MISSED" -> "Missed"
                "DECLINED" -> "Declined"
                "CANCELLED" -> "Cancelled"
                "FAILED" -> "Failed"
                "CONNECTED","ENDED" -> if(call.direction==CallDirection.INCOMING) "Incoming" else "Outgoing"
                else -> if(call.direction==CallDirection.INCOMING) "Incoming" else "Outgoing"
            }
            val duration=if(call.durationSeconds>0) {
                val m=call.durationSeconds/60
                val sec=call.durationSeconds%60
                if(m>0) m.toString()+"m "+sec.toString()+"s" else sec.toString()+"s"
            } else null
            ListItem(
                modifier=Modifier.clickable { vm.navigate(AppScreen.CallDetails(call.id)) },
                leadingContent = { Avatar(call.contactName.ifBlank { call.contactPhone }) },
                headlineContent = { Text(call.contactName.ifBlank { call.contactPhone.ifBlank { "Unknown caller" } }, fontWeight = FontWeight.Medium) },
                supportingContent = {
                    Column {
                        if(call.contactPhone.isNotBlank()) Text(call.contactPhone, style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val missed=call.state.equals("MISSED",true) || call.direction==CallDirection.MISSED
                            val icon = if (call.direction == CallDirection.OUTGOING) Icons.Filled.CallMade else Icons.Filled.CallReceived
                            Icon(icon, null, modifier = Modifier.size(15.dp), tint = if (missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                            Text(buildString {
                                append(stateLabel); append(" · "); append(call.time)
                                duration?.let { append(" · "); append(it) }
                            }, color = if (missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                trailingContent = {
                    if (call.type == CallType.AUDIO) IconButton(onClick = { requestRedial() }) { Icon(Icons.Outlined.Call, "Call") }
                    else Icon(Icons.Outlined.Videocam, "Video call history", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            )
        }
    }
}

@Composable
private fun CallDetailsScreen(vm: AppViewModel, callId:String) {
    val call=vm.callById(callId)
    if(call==null){
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)){
            TextButton(onClick={vm.navigate(AppScreen.Home)}){Text("Back")}
            Text("Call details unavailable")
        }
        return
    }
    val requestRedial=rememberVoiceCallPermissionAction(vm){ vm.redialVoiceCall(call) }
    val label=when(call.state.uppercase()){
        "MISSED"->"Missed call"
        "DECLINED"->"Declined call"
        "CANCELLED"->"Cancelled call"
        "FAILED"->"Failed call"
        else->if(call.direction==CallDirection.INCOMING)"Incoming call" else "Outgoing call"
    }
    val duration=if(call.durationSeconds>0){
        val m=call.durationSeconds/60
        val sec=call.durationSeconds%60
        if(m>0) m.toString()+"m "+sec.toString()+"s" else sec.toString()+"s"
    } else "Not connected"
    Column(Modifier.fillMaxSize().systemBarsPadding()){
        Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick={vm.navigate(AppScreen.Home)}){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")}
            Text("Call details",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
        }
        Column(Modifier.padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally){
            Avatar(call.contactName.ifBlank { call.contactPhone },72.dp)
            Spacer(Modifier.height(12.dp))
            Text(call.contactName.ifBlank { call.contactPhone.ifBlank { "Unknown caller" } },style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            if(call.contactPhone.isNotBlank()) Text(call.contactPhone,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            ListItem(headlineContent={Text(label)},supportingContent={Text(call.time)})
            ListItem(headlineContent={Text("Duration")},supportingContent={Text(duration)})
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                Button(onClick={requestRedial},modifier=Modifier.weight(1f)){Icon(Icons.Outlined.Call,null);Spacer(Modifier.width(6.dp));Text("Call")}
                OutlinedButton(onClick={vm.openConversationForCall(call)},modifier=Modifier.weight(1f)){Icon(Icons.Outlined.Chat,null);Spacer(Modifier.width(6.dp));Text("Message")}
            }
        }
    }
}
'''
s=s[:start]+replacement+s[end:]
for needle in [
'            TextButton(onClick = { vm.lastToast = "Photo picker will connect to device storage in the media phase" }) { Text("Add profile photo") }\n',
'            ListItem(headlineContent={Text("Product updates")},supportingContent={Text("Messenger updates")},leadingContent={Avatar("Product")},trailingContent={AssistChip(onClick={vm.lastToast="Following product updates"},label={Text("Follow")})})\n',
'        item { SettingsRow(Icons.Outlined.Chat, "Chats", "Theme, wallpaper, chat history") { vm.lastToast = "Chat appearance settings opened" } }\n',
'                IconButton(onClick = { vm.lastToast = "Forward flow selected"; vm.setSelectedMessage(null) }) { Icon(Icons.Outlined.Forward, "Forward") }\n',
]:
    s=s.replace(needle,'')
s=s.replace('modifier = Modifier.clickable { vm.lastToast = "Block action prepared for server-side persistence" },','modifier = Modifier,')
s=s.replace('modifier = Modifier.clickable { vm.lastToast = "Report action prepared for moderation API" },','modifier = Modifier,')
p.write_text(s)
print('2.21 calls UI and placeholder cleanup applied')
