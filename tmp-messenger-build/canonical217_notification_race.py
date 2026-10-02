from pathlib import Path
pkg=Path("/tmp/messenger-build/messenger_live/app/src/main/java/com/example/messengerui")

p=pkg/"MessengerFirebaseService.kt"
s=p.read_text()
start=s.find('        if (eventType == "call.ended") {')
end=s.find('        if (eventType == "push.test") {',start)
if start<0 or end<0: raise SystemExit("firebase call.ended boundaries missing")
block='''        if (eventType == "call.ended") {
            val callId=data["call_id"].orEmpty()
            if(callId.isBlank()) return
            val incoming=data["incoming"] == "1"
            val state=data["state"].orEmpty().uppercase()
            val peer=data["peer_name"].orEmpty().ifBlank { data["caller_name"].orEmpty().ifBlank { "Messenger User" } }
            val terminalAlready=CallAlertStore.isTerminal(this,callId)
            NotificationHelper.markCallTerminal(this,callId)
            if(incoming && state=="MISSED" && !terminalAlready) NotificationHelper.showMissedCall(this,callId,peer)
            return
        }
'''
s=s[:start]+block+s[end:]
p.write_text(s)

p=pkg/"AppViewModel.kt"
s=p.read_text()
start=s.find('            "call.incoming" -> {')
end=s.find('            "call.accepted" -> {',start)
if start<0 or end<0: raise SystemExit("vm call.incoming boundaries missing")
block='''            "call.incoming" -> {
                val obj=event.payload.optJSONObject("call") ?: return
                val remote=api.parseVoiceCallPayload(obj)
                val alertAlreadyHandled=PushDeduper.alreadyHandled(getApplication(), "call:${remote.id}")
                voiceCallConfig=runCatching { withContext(Dispatchers.IO){ api.voiceCallConfig(db.serverToken()) } }.getOrNull()
                callDiagnostics=callDiagnostics.copy(networkLabel=currentCallNetworkLabel(),turnConfigured=voiceCallConfig?.turnConfigured==true)
                callNetworkHandle=connectivityManager.activeNetwork?.networkHandle
                applyRemoteCall(remote)
                if(NotificationRuntime.appVisible){ navigate(AppScreen.VoiceCall(remote.id)) }
                else if(!alertAlreadyHandled) NotificationHelper.showIncomingCall(getApplication(),remote.id,remote.peerName)
                startCallStateLoop()
            }
'''
s=s[:start]+block+s[end:]
start=s.find('            "call.ended" -> {')
end=s.find('            "message.receipt" -> {',start)
if start<0 or end<0: raise SystemExit("vm call.ended boundaries missing")
block='''            "call.ended" -> {
                val remote=event.payload.optJSONObject("call")?.let(api::parseVoiceCallPayload)
                if(remote!=null) {
                    val terminalAlready=CallAlertStore.isTerminal(getApplication(),remote.id)
                    NotificationHelper.markCallTerminal(getApplication(),remote.id)
                    if(remote.incoming && remote.state=="MISSED" && !terminalAlready && !NotificationRuntime.appVisible) NotificationHelper.showMissedCall(getApplication(),remote.id,remote.peerName)
                }
                if(remote!=null && activeVoiceCall?.callId==remote.id) finishVoiceCall(remote,false) else refreshServerCallsAsync()
            }
'''
s=s[:start]+block+s[end:]
p.write_text(s)

p=pkg/"NotificationHelper.kt"
s=p.read_text().replace('setCategory(NotificationCompat.CATEGORY_CALL).setPriority(NotificationCompat.PRIORITY_HIGH).setOngoing(true)','setCategory(NotificationCompat.CATEGORY_CALL).setPriority(NotificationCompat.PRIORITY_LOW).setOngoing(true)')
p.write_text(s)
print("canonical notification race hardening applied")
