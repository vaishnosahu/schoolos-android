from pathlib import Path
root=Path('/tmp/messenger-build/messenger_live')

# Version
p=root/'app/build.gradle.kts'
s=p.read_text().replace('versionCode = 32','versionCode = 33').replace('versionName = "2.21"','versionName = "2.22"')
p.write_text(s)

# ApiClient
p=root/'app/src/main/java/com/example/messengerui/ApiClient.kt'
s=p.read_text()
anchor='''    fun deleteMessageForEveryone(token: String, messageId: String) {
        request("POST", "messages/delete.php", JSONObject().put("message_id", messageId), token)
    }
'''
if anchor not in s: raise SystemExit('Api delete anchor missing')
s=s.replace(anchor,anchor+'''
    fun hideMessageForMe(token: String, messageId: String) {
        request("POST", "messages/hide.php", JSONObject().put("message_id", messageId), token)
    }

    fun clearConversation(token: String, conversationId: String) {
        request("POST", "conversations/clear.php", JSONObject().put("conversation_id", conversationId), token)
    }

    fun deleteConversation(token: String, conversationId: String) {
        request("POST", "conversations/delete.php", JSONObject().put("conversation_id", conversationId), token)
    }

    fun deleteCallLog(token: String, callId: String) {
        request("POST", "calls/delete.php", JSONObject().put("call_id", callId), token)
    }

    fun clearCallLog(token: String) {
        request("POST", "calls/clear.php", JSONObject(), token)
    }

    fun setUserBlocked(token: String, userId: String, blocked: Boolean) {
        request("POST", "privacy/block.php", JSONObject().put("user_id", userId).put("blocked", blocked), token)
    }

    fun reportUser(token: String, userId: String?, conversationId: String?, reason: String = "user_report") {
        val body=JSONObject().put("reason",reason)
        if(!userId.isNullOrBlank()) body.put("user_id",userId)
        if(!conversationId.isNullOrBlank()) body.put("conversation_id",conversationId)
        request("POST", "privacy/report.php", body, token)
    }
''',1)
p.write_text(s)

# Local DB helpers
p=root/'app/src/main/java/com/example/messengerui/LocalMessengerDb.kt'
s=p.read_text()
anchor='''    fun deleteMessage(id: Long) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("starred_messages", "message_id=?", arrayOf(id.toString()))
            writableDatabase.delete("messages", "id=?", arrayOf(id.toString()))
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
        enqueue("DELETE", "message", id.toString(), "{}")
    }
'''
if anchor not in s: raise SystemExit('db delete message anchor missing')
s=s.replace(anchor,anchor+'''
    fun clearChatMessages(chatId: String) {
        val db=writableDatabase
        db.beginTransaction()
        try {
            db.delete("starred_messages", "message_id IN (SELECT id FROM messages WHERE chat_id=?)", arrayOf(chatId))
            db.delete("messages","chat_id=?",arrayOf(chatId))
            db.update("chats", ContentValues().apply { put("subtitle","No messages"); put("last_time",""); put("unread",0) }, "id=?", arrayOf(chatId))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun deleteCallLog(callId: String) { writableDatabase.delete("call_logs","id=?",arrayOf(callId)) }
    fun clearCallLogs() { writableDatabase.delete("call_logs",null,null) }
''',1)
p.write_text(s)

# ViewModel controls
p=root/'app/src/main/java/com/example/messengerui/AppViewModel.kt'
s=p.read_text()
start=s.index('    fun deleteMessage(messageId: Long) {')
end=s.index('    private fun refreshChatPreviewFromMessages',start)
replacement='''    fun deleteMessageForMe(messageId: Long) {
        val message=messages.firstOrNull { it.id==messageId } ?: return
        val chatId=message.chatId
        if(playingAudioMessageId==messageId) stopAudioPlayback()
        fun removeLocal() {
            messages.removeAll { it.id==messageId }
            starredMessageIds.remove(messageId)
            db.deleteMessage(messageId)
            attachmentStore.deleteIfExists(message.attachmentPath)
            selectedMessageId=null
            refreshChatPreviewFromMessages(chatId)
        }
        if(serverMode && !message.serverId.isNullOrBlank()) {
            val token=db.serverToken()
            viewModelScope.launch {
                runCatching { withContext(Dispatchers.IO){ api.hideMessageForMe(token,message.serverId) } }
                    .onSuccess { removeLocal(); lastToast="Message deleted for you" }
                    .onFailure { lastToast=it.userMessage("Unable to delete message") }
            }
        } else {
            removeLocal(); lastToast="Message deleted for you"
        }
    }

    fun deleteMessageForEveryone(messageId: Long) {
        val message=messages.firstOrNull { it.id==messageId } ?: return
        if(!message.outgoing || message.serverId.isNullOrBlank()) { deleteMessageForMe(messageId); return }
        if(playingAudioMessageId==messageId) stopAudioPlayback()
        val tombstone=message.copy(text="Message deleted",deleted=true,attachmentPath=null,remoteMediaId=null,reaction=null)
        val idx=messages.indexOfFirst { it.id==messageId }
        if(idx>=0) messages[idx]=tombstone
        db.updateMessage(tombstone)
        attachmentStore.deleteIfExists(message.attachmentPath)
        val token=db.serverToken()
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO){ api.deleteMessageForEveryone(token,message.serverId) } }
                .onSuccess { lastToast="Message deleted for everyone" }
                .onFailure { lastToast=it.userMessage("Unable to delete for everyone") }
        }
        selectedMessageId=null
        refreshChatPreviewFromMessages(message.chatId)
    }

    fun clearChat(chatId:String) {
        val chat=chats.firstOrNull { it.id==chatId } ?: return
        fun clearLocal() {
            messages.removeAll { it.chatId==chatId }
            db.clearChatMessages(chatId)
            val i=chats.indexOfFirst { it.id==chatId }
            if(i>=0) chats[i]=chat.copy(subtitle="No messages",lastTime="",unread=0)
            NotificationHelper.cancelConversation(getApplication(),chatId)
        }
        if(serverMode && isServerConversationId(chatId)) {
            val token=db.serverToken()
            viewModelScope.launch {
                runCatching { withContext(Dispatchers.IO){ api.clearConversation(token,chatId) } }
                    .onSuccess { clearLocal(); lastToast="Chat cleared" }
                    .onFailure { lastToast=it.userMessage("Unable to clear chat") }
            }
        } else { clearLocal(); lastToast="Chat cleared" }
    }

    fun deleteChat(chatId:String) {
        val chat=chats.firstOrNull { it.id==chatId } ?: return
        if(chat.isGroup){ lastToast="Leave the group before deleting it"; return }
        fun removeLocal() {
            messages.removeAll { it.chatId==chatId }
            db.removeChat(chatId)
            chats.removeAll { it.id==chatId }
            NotificationHelper.cancelConversation(getApplication(),chatId)
            if((screen as? AppScreen.ChatDetail)?.chatId==chatId || (screen as? AppScreen.ChatInfo)?.chatId==chatId) screen=AppScreen.Home
        }
        if(serverMode && isServerConversationId(chatId)) {
            val token=db.serverToken()
            viewModelScope.launch {
                runCatching { withContext(Dispatchers.IO){ api.deleteConversation(token,chatId) } }
                    .onSuccess { removeLocal(); lastToast="Chat deleted" }
                    .onFailure { lastToast=it.userMessage("Unable to delete chat") }
            }
        } else { removeLocal(); lastToast="Chat deleted" }
    }

    fun deleteCallFromHistory(callId:String) {
        fun local(){ db.deleteCallLog(callId); calls.removeAll { it.id==callId }; if((screen as? AppScreen.CallDetails)?.callId==callId) screen=AppScreen.Home }
        if(serverMode){
            val token=db.serverToken()
            viewModelScope.launch { runCatching { withContext(Dispatchers.IO){ api.deleteCallLog(token,callId) } }.onSuccess { local() }.onFailure { lastToast=it.userMessage("Unable to remove call") } }
        } else local()
    }

    fun clearCallHistory() {
        fun local(){ db.clearCallLogs(); calls.clear() }
        if(serverMode){
            val token=db.serverToken()
            viewModelScope.launch { runCatching { withContext(Dispatchers.IO){ api.clearCallLog(token) } }.onSuccess { local(); lastToast="Call history cleared" }.onFailure { lastToast=it.userMessage("Unable to clear call history") } }
        } else { local(); lastToast="Call history cleared" }
    }

    private val blockedUserIds: MutableSet<String> by lazy {
        db.getStringSetting("blocked_user_ids","").split(',').map { it.trim() }.filter { it.isNotBlank() }.toMutableSet()
    }
    fun isUserBlocked(userId:String):Boolean=blockedUserIds.contains(userId)
    fun setChatBlocked(chatId:String,blocked:Boolean) {
        val peerId=chats.firstOrNull { it.id==chatId && !it.isGroup }?.memberIds?.firstOrNull() ?: return
        if(!serverMode){ lastToast="Server connection is required"; return }
        val token=db.serverToken()
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO){ api.setUserBlocked(token,peerId,blocked) } }.onSuccess {
                if(blocked) blockedUserIds.add(peerId) else blockedUserIds.remove(peerId)
                db.setStringSetting("blocked_user_ids",blockedUserIds.joinToString(","))
                lastToast=if(blocked)"Contact blocked" else "Contact unblocked"
            }.onFailure { lastToast=it.userMessage(if(blocked)"Unable to block contact" else "Unable to unblock contact") }
        }
    }
    fun reportChat(chatId:String) {
        val chat=chats.firstOrNull { it.id==chatId } ?: return
        val peerId=if(chat.isGroup)null else chat.memberIds.firstOrNull()
        if(!serverMode){ lastToast="Server connection is required"; return }
        val token=db.serverToken()
        viewModelScope.launch { runCatching { withContext(Dispatchers.IO){ api.reportUser(token,peerId,chatId) } }.onSuccess { lastToast="Report submitted" }.onFailure { lastToast=it.userMessage("Unable to submit report") } }
    }

'''
s=s[:start]+replacement+s[end:]

# remote event local authority
marker='''            "message.deleted" -> {
'''
insert='''            "message.hidden" -> {
                val serverId=event.payload.optString("message_id")
                val local=messages.firstOrNull { it.serverId==serverId }
                if(local!=null){ messages.removeAll { it.id==local.id }; db.deleteMessage(local.id); refreshChatPreviewFromMessages(local.chatId) }
            }
            "conversation.cleared" -> {
                val chatId=event.payload.optString("conversation_id")
                if(chatId.isNotBlank()){ messages.removeAll { it.chatId==chatId }; db.clearChatMessages(chatId); reloadCollections() }
            }
            "conversation.deleted" -> {
                val chatId=event.payload.optString("conversation_id")
                if(chatId.isNotBlank()){ messages.removeAll { it.chatId==chatId }; db.removeChat(chatId); reloadCollections() }
            }
'''
if marker not in s: raise SystemExit('event marker missing')
s=s.replace(marker,insert+marker,1)
p.write_text(s)

print('2.22 data controls applied')
