from pathlib import Path
root=Path('/tmp/messenger-build/messenger_live')

p=root/'app/build.gradle.kts'
s=p.read_text().replace('versionCode = 33','versionCode = 34').replace('versionName = "2.22"','versionName = "2.23"')
p.write_text(s)

# models screens
p=root/'app/src/main/java/com/example/messengerui/Models.kt'
s=p.read_text()
s=s.replace('    data class CallDetails(val callId: String) : AppScreen\n','    data class CallDetails(val callId: String) : AppScreen\n    data class ForwardMessage(val messageId: Long) : AppScreen\n    data class MediaBrowser(val chatId: String) : AppScreen\n')
p.write_text(s)

# API forward
p=root/'app/src/main/java/com/example/messengerui/ApiClient.kt'
s=p.read_text()
anchor='''    fun reactMessage(token: String, messageId: String, reaction: String?) {
        val body = JSONObject().put("message_id", messageId)
        if (reaction == null) body.put("reaction", JSONObject.NULL) else body.put("reaction", reaction)
        request("POST", "messages/react.php", body, token)
    }
'''
if anchor not in s: raise SystemExit('react anchor missing')
s=s.replace(anchor,anchor+'''
    fun forwardMessage(token: String, messageId: String, conversationIds: Collection<String>): List<RemoteMessage> {
        val ids=JSONArray().apply { conversationIds.forEach { put(it) } }
        val json=request("POST","messages/forward.php",JSONObject().put("message_id",messageId).put("conversation_ids",ids),token)
        return json.optJSONArray("messages")?.mapObjects { it.toRemoteMessage() } ?: emptyList()
    }
''',1)
p.write_text(s)

# DB known contact
p=root/'app/src/main/java/com/example/messengerui/LocalMessengerDb.kt'
s=p.read_text()
anchor='''    fun loadContacts(): List<Contact> = readableDatabase.rawQuery("SELECT id,name,phone,about,online,last_seen FROM contacts ORDER BY name COLLATE NOCASE", null).use { c ->
'''
if anchor not in s: raise SystemExit('loadContacts anchor missing')
s=s.replace(anchor,'''    fun hasContactId(id:String):Boolean = id.isNotBlank() && readableDatabase.rawQuery("SELECT 1 FROM contacts WHERE id=? LIMIT 1", arrayOf(id)).use { it.moveToFirst() }

'''+anchor,1)
p.write_text(s)

# ViewModel advanced parity
p=root/'app/src/main/java/com/example/messengerui/AppViewModel.kt'
s=p.read_text()
# state
s=s.replace('''    var notificationsStatusEnabled by mutableStateOf(db.getBooleanSetting("notifications_status", false))
        private set''','''    var notificationsStatusEnabled by mutableStateOf(db.getBooleanSetting("notifications_status", false))
        private set
    var silenceUnknownCallers by mutableStateOf(db.getBooleanSetting("silence_unknown_callers", false))
        private set''')
s=s.replace('''    var activeVoiceCall by mutableStateOf<VoiceCallUi?>(null)
        private set''','''    var activeVoiceCall by mutableStateOf<VoiceCallUi?>(null)
        private set
    var waitingVoiceCall by mutableStateOf<RemoteVoiceCall?>(null)
        private set''')
# update setting near other prefs
anchor='''    fun updateNotificationsStatusEnabled(value: Boolean) {
        notificationsStatusEnabled = value
        db.setBooleanSetting("notifications_status", value)
        syncNotificationPreferences()
    }
'''
if anchor not in s: raise SystemExit('notification status setter missing')
s=s.replace(anchor,anchor+'''
    fun updateSilenceUnknownCallers(value:Boolean) {
        silenceUnknownCallers=value
        db.setBooleanSetting("silence_unknown_callers",value)
    }
''',1)

# forward + open URL before delete section
marker='''    fun deleteMessageForMe(messageId: Long) {
'''
insert='''    fun forwardMessage(messageId:Long,targetChatIds:Set<String>) {
        val msg=messages.firstOrNull { it.id==messageId } ?: return
        if(msg.serverId.isNullOrBlank() || targetChatIds.isEmpty()){ lastToast="Select at least one destination"; return }
        val token=db.serverToken()
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO){ api.forwardMessage(token,msg.serverId,targetChatIds.take(5)) } }
                .onSuccess { remote ->
                    remote.forEach { db.upsertRemoteMessage(it,db.serverUserId()) }
                    reloadCollections()
                    selectedMessageId=null
                    screen=AppScreen.Home
                    lastToast="Message forwarded"
                }.onFailure { lastToast=it.userMessage("Unable to forward message") }
        }
    }

    fun openUrl(url:String) {
        val normalized=url.trim()
        if(!normalized.startsWith("http://") && !normalized.startsWith("https://")) return
        runCatching {
            val intent=android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(normalized)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            getApplication<Application>().startActivity(intent)
        }.onFailure { lastToast="Unable to open link" }
    }

'''
if marker not in s: raise SystemExit('delete marker missing')
s=s.replace(marker,insert+marker,1)

# notification open signature
s=s.replace('fun openVoiceCallFromNotification(callId:String) {','fun openVoiceCallFromNotification(callId:String, switchFromCurrent:Boolean=false) {')
s=s.replace('''        val token=db.serverToken()
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO){ api.voiceCallState(token,callId,0L) } }''','''        val token=db.serverToken()
        viewModelScope.launch {
            if(switchFromCurrent && activeVoiceCall?.callId!=null && activeVoiceCall?.callId!=callId){
                closeVoiceEngine()
                CallForegroundService.stop(getApplication())
                activeVoiceCall=null
                waitingVoiceCall=null
            }
            runCatching { withContext(Dispatchers.IO){ api.voiceCallState(token,callId,0L) } }''',1)

# call incoming event waiting/silence
old='''            "call.incoming" -> {
                val obj=event.payload.optJSONObject("call") ?: return
                val remote=api.parseVoiceCallPayload(obj)
                voiceCallConfig=runCatching { withContext(Dispatchers.IO){ api.voiceCallConfig(db.serverToken()) } }.getOrNull()
                callDiagnostics=callDiagnostics.copy(networkLabel=currentCallNetworkLabel(),turnConfigured=voiceCallConfig?.turnConfigured==true)
                callNetworkHandle=connectivityManager.activeNetwork?.networkHandle
                applyRemoteCall(remote)
                if(NotificationRuntime.appVisible){
                    navigate(AppScreen.VoiceCall(remote.id))
                } else if(!PushDeduper.alreadyHandled(getApplication(), "call:${remote.id}")) {
                    NotificationHelper.showIncomingCall(getApplication(),remote.id,remote.peerName,remote.peerPhone)
                }
                startCallStateLoop()
            }'''
new='''            "call.incoming" -> {
                val obj=event.payload.optJSONObject("call") ?: return
                val remote=api.parseVoiceCallPayload(obj)
                val unknownSilenced=silenceUnknownCallers && !db.hasContactId(remote.peerId)
                if(unknownSilenced){
                    NotificationHelper.showSilentIncomingCall(getApplication(),remote.id,remote.peerName,remote.peerPhone)
                    return
                }
                val current=activeVoiceCall
                if(current!=null && current.callId!=remote.id && current.phase !in listOf(VoiceCallPhase.ENDED,VoiceCallPhase.FAILED)){
                    waitingVoiceCall=remote
                    NotificationHelper.showIncomingCall(getApplication(),remote.id,remote.peerName,remote.peerPhone)
                    return
                }
                voiceCallConfig=runCatching { withContext(Dispatchers.IO){ api.voiceCallConfig(db.serverToken()) } }.getOrNull()
                callDiagnostics=callDiagnostics.copy(networkLabel=currentCallNetworkLabel(),turnConfigured=voiceCallConfig?.turnConfigured==true)
                callNetworkHandle=connectivityManager.activeNetwork?.networkHandle
                applyRemoteCall(remote)
                if(NotificationRuntime.appVisible){
                    navigate(AppScreen.VoiceCall(remote.id))
                } else if(!PushDeduper.alreadyHandled(getApplication(), "call:${remote.id}")) {
                    NotificationHelper.showIncomingCall(getApplication(),remote.id,remote.peerName,remote.peerPhone)
                }
                startCallStateLoop()
            }'''
if old not in s: raise SystemExit('call incoming block missing')
s=s.replace(old,new,1)
# call ended clears waiting
s=s.replace('''                if(remote!=null) {
                    val eventKey="call-state:${remote.id}:${remote.state}"''','''                if(remote!=null) {
                    if(waitingVoiceCall?.id==remote.id) waitingVoiceCall=null
                    val eventKey="call-state:${remote.id}:${remote.state}"''',1)

p.write_text(s)

print('2.23 runtime data parity applied')
