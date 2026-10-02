from pathlib import Path
pkg=Path("/tmp/messenger-build/messenger_live/app/src/main/java/com/example/messengerui")

p=pkg/"MessengerFirebaseService.kt"
s=p.read_text()
old='''        if (eventType == "call.ended") {
            val callId=data["call_id"].orEmpty()
            if(callId.isBlank()) return
            val incoming=data["incoming"] == "1"
            val state=data["state"].orEmpty().uppercase()
            val peer=data["peer_name"].orEmpty().ifBlank { data["caller_name"].orEmpty().ifBlank { "Messenger User" } }
            NotificationHelper.markCallTerminal(this,callId)
            if(incoming && state=="MISSED") NotificationHelper.showMissedCall(this,callId,peer)
            return
        }
'''
new='''        if (eventType == "call.ended") {
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
if old not in s: raise SystemExit("firebase ended anchor missing")
p.write_text(s.replace(old,new,1))

p=pkg/"AppViewModel.kt"
s=p.read_text()
old='''            "call.incoming" -> {
                val obj=event.payload.optJSONObject("call") ?: return
                val remote=api.parseVoiceCallPayload(obj)
                voiceCallConfig=runCatching { withContext(Dispatchers.IO){ api.voiceCallConfig(db.serverToken()) } }.getOrNull()
                callDiagnostics=callDiagnostics.copy(networkLabel=currentCallNetworkLabel(),turnConfigured=voiceCallConfig?.turnConfigured==true)
                callNetworkHandle=connectivityManager.activeNetwork?.networkHandle
                applyRemoteCall(remote)
                if(NotificationRuntime.appVisible){ navigate(AppScreen.VoiceCall(remote.id)) }
                else NotificationHelper.showIncomingCall(getApplication(),remote.id,remote.peerName)
                startCallStateLoop()
            }
'''
new='''            "call.incoming" -> {
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
if old not in s: raise SystemExit("vm incoming anchor missing")
s=s.replace(old,new,1)
old='''            "call.ended" -> {
                val remote=event.payload.optJSONObject("call")?.let(api::parseVoiceCallPayload)
                if(remote!=null) {
                    NotificationHelper.markCallTerminal(getApplication(),remote.id)
                    if(remote.incoming && remote.state=="MISSED" && !NotificationRuntime.appVisible) NotificationHelper.showMissedCall(getApplication(),remote.id,remote.peerName)
                }
                if(remote!=null && activeVoiceCall?.callId==remote.id) finishVoiceCall(remote,false) else refreshServerCallsAsync()
            }
'''
new='''            "call.ended" -> {
                val remote=event.payload.optJSONObject("call")?.let(api::parseVoiceCallPayload)
                if(remote!=null) {
                    val terminalAlready=CallAlertStore.isTerminal(getApplication(),remote.id)
                    NotificationHelper.markCallTerminal(getApplication(),remote.id)
                    if(remote.incoming && remote.state=="MISSED" && !terminalAlready && !NotificationRuntime.appVisible) NotificationHelper.showMissedCall(getApplication(),remote.id,remote.peerName)
                }
                if(remote!=null && activeVoiceCall?.callId==remote.id) finishVoiceCall(remote,false) else refreshServerCallsAsync()
            }
'''
if old not in s: raise SystemExit("vm ended anchor missing")
p.write_text(s.replace(old,new,1))

p=pkg/"NotificationHelper.kt"
s=p.read_text().replace('setCategory(NotificationCompat.CATEGORY_CALL).setPriority(NotificationCompat.PRIORITY_HIGH).setOngoing(true)','setCategory(NotificationCompat.CATEGORY_CALL).setPriority(NotificationCompat.PRIORITY_LOW).setOngoing(true)')
p.write_text(s)
print("canonical notification race hardening applied")
