from pathlib import Path
root=Path('/tmp/messenger-build/messenger_live')

p=root/'app/build.gradle.kts'
s=p.read_text().replace('versionCode = 31','versionCode = 32').replace('versionName = "2.20"','versionName = "2.21"')
p.write_text(s)

p=root/'app/src/main/java/com/example/messengerui/Models.kt'
s=p.read_text()
old='''data class CallLog(
    val id: String,
    val contactName: String,
    val time: String,
    val type: CallType,
    val direction: CallDirection
)'''
new='''data class CallLog(
    val id: String,
    val contactName: String,
    val contactPhone: String = "",
    val peerId: String = "",
    val time: String,
    val type: CallType,
    val direction: CallDirection,
    val state: String = "ENDED",
    val durationSeconds: Long = 0L,
    val createdAtMs: Long = 0L,
    val endedAtMs: Long = 0L
)'''
if old not in s: raise SystemExit('CallLog model anchor missing')
s=s.replace(old,new,1)
s=s.replace('    data class VoiceCall(val callId: String) : AppScreen\n','    data class VoiceCall(val callId: String) : AppScreen\n    data class CallDetails(val callId: String) : AppScreen\n')
p.write_text(s)

p=root/'app/src/main/java/com/example/messengerui/LocalMessengerDb.kt'
s=p.read_text()
s=s.replace("CREATE TABLE call_logs (id TEXT PRIMARY KEY, contact_name TEXT NOT NULL, time_label TEXT NOT NULL, type TEXT NOT NULL, direction TEXT NOT NULL, created_at_ms INTEGER NOT NULL)",
"CREATE TABLE call_logs (id TEXT PRIMARY KEY, contact_name TEXT NOT NULL, contact_phone TEXT NOT NULL DEFAULT '', peer_id TEXT NOT NULL DEFAULT '', time_label TEXT NOT NULL, type TEXT NOT NULL, direction TEXT NOT NULL, state TEXT NOT NULL DEFAULT 'ENDED', duration_seconds INTEGER NOT NULL DEFAULT 0, created_at_ms INTEGER NOT NULL, ended_at_ms INTEGER NOT NULL DEFAULT 0)")
anchor='''        if (oldVersion < 11) {
            if (getState("demo_seeded", "0") == "1") {
                db.delete("starred_messages", null, null); db.delete("messages", null, null); db.delete("chat_members", null, null); db.delete("chats", null, null); db.delete("contacts", null, null); db.delete("statuses", null, null); db.delete("call_logs", null, null); db.delete("sync_queue", null, null)
                setState(db, "demo_seeded", "0")
            }
        }
'''
if anchor not in s: raise SystemExit('upgrade anchor missing')
s=s.replace(anchor,anchor+'''        if (oldVersion < 12) {
            safeAlter(db, "ALTER TABLE call_logs ADD COLUMN contact_phone TEXT NOT NULL DEFAULT ''")
            safeAlter(db, "ALTER TABLE call_logs ADD COLUMN peer_id TEXT NOT NULL DEFAULT ''")
            safeAlter(db, "ALTER TABLE call_logs ADD COLUMN state TEXT NOT NULL DEFAULT 'ENDED'")
            safeAlter(db, "ALTER TABLE call_logs ADD COLUMN duration_seconds INTEGER NOT NULL DEFAULT 0")
            safeAlter(db, "ALTER TABLE call_logs ADD COLUMN ended_at_ms INTEGER NOT NULL DEFAULT 0")
        }
''',1)
old='''    fun loadCalls(): List<CallLog> = readableDatabase.rawQuery("SELECT id,contact_name,time_label,type,direction FROM call_logs ORDER BY created_at_ms DESC", null).use { c ->
        buildList { while (c.moveToNext()) add(CallLog(c.getString(0), c.getString(1), c.getString(2), enumValueOrDefault(c.getString(3), CallType.AUDIO), enumValueOrDefault(c.getString(4), CallDirection.OUTGOING))) }
    }'''
new='''    fun loadCalls(): List<CallLog> = readableDatabase.rawQuery("SELECT id,contact_name,contact_phone,peer_id,time_label,type,direction,state,duration_seconds,created_at_ms,ended_at_ms FROM call_logs ORDER BY created_at_ms DESC", null).use { c ->
        buildList {
            while (c.moveToNext()) add(CallLog(
                id=c.getString(0), contactName=c.getString(1), contactPhone=c.getString(2), peerId=c.getString(3), time=c.getString(4),
                type=enumValueOrDefault(c.getString(5), CallType.AUDIO), direction=enumValueOrDefault(c.getString(6), CallDirection.OUTGOING),
                state=c.getString(7), durationSeconds=c.getLong(8), createdAtMs=c.getLong(9), endedAtMs=c.getLong(10)
            ))
        }
    }'''
if old not in s: raise SystemExit('loadCalls anchor missing')
s=s.replace(old,new,1)
s=s.replace('items.forEachIndexed { index,item -> insertCall(db,item,now-index) }','items.forEachIndexed { index,item -> insertCall(db,item,item.createdAtMs.takeIf { it > 0L } ?: now-index) }')
old='''            put("id", item.id); put("contact_name", item.contactName); put("time_label", item.time); put("type", item.type.name); put("direction", item.direction.name); put("created_at_ms", createdAt)'''
new='''            put("id", item.id); put("contact_name", item.contactName); put("contact_phone", item.contactPhone); put("peer_id", item.peerId)
            put("time_label", item.time); put("type", item.type.name); put("direction", item.direction.name); put("state", item.state)
            put("duration_seconds", item.durationSeconds); put("created_at_ms", item.createdAtMs.takeIf { it > 0L } ?: createdAt); put("ended_at_ms", item.endedAtMs)'''
if old not in s: raise SystemExit('insertCall anchor missing')
s=s.replace(old,new,1).replace('private const val DB_VERSION = 11','private const val DB_VERSION = 12')
p.write_text(s)

p=root/'app/src/main/java/com/example/messengerui/AppViewModel.kt'
s=p.read_text()
start=s.index('    private fun remoteCallLog(remote:RemoteVoiceCall):CallLog {')
end=s.index('    fun setLastSeen',start)
newblock='''    private fun remoteCallLog(remote:RemoteVoiceCall):CallLog {
        val state=remote.state.uppercase()
        val direction=when {
            remote.incoming && state=="MISSED" -> CallDirection.MISSED
            remote.incoming -> CallDirection.INCOMING
            else -> CallDirection.OUTGOING
        }
        fun epoch(value:String?):Long = value?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L
        val created=epoch(remote.createdAtIso)
        val connected=epoch(remote.connectedAtIso)
        val ended=epoch(remote.endedAtIso)
        val duration=if(connected>0L && ended>=connected) (ended-connected)/1000L else 0L
        val time=created.takeIf { it>0L }?.let { ms ->
            java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().format(java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a"))
        } ?: "Recent"
        return CallLog(
            id=remote.id,
            contactName=remote.peerName.ifBlank { remote.peerPhone.ifBlank { "Unknown caller" } },
            contactPhone=remote.peerPhone,
            peerId=remote.peerId,
            time=time,
            type=remote.type,
            direction=direction,
            state=state,
            durationSeconds=duration,
            createdAtMs=created,
            endedAtMs=ended
        )
    }

    fun callById(callId:String):CallLog? = calls.firstOrNull { it.id==callId }

    fun redialVoiceCall(call: CallLog) {
        val chat=chats.firstOrNull { !it.isGroup && call.peerId.isNotBlank() && it.memberIds.contains(call.peerId) }
        if(chat!=null) startVoiceCall(chat.id) else lastToast="Conversation for this caller is not available"
    }

    fun openConversationForCall(call: CallLog) {
        val chat=chats.firstOrNull { !it.isGroup && call.peerId.isNotBlank() && it.memberIds.contains(call.peerId) }
        if(chat!=null){ openChat(chat.id); return }
        val contact=contacts.firstOrNull { (call.peerId.isNotBlank() && it.id==call.peerId) || (call.contactPhone.isNotBlank() && it.phone==call.contactPhone) }
        if(contact!=null) createDirectChat(contact) else lastToast="This caller is not in your contacts yet"
    }

    fun addCall(contactName: String, type: CallType) {
        val now=System.currentTimeMillis()
        val item=CallLog(id="call"+now,contactName=contactName,time="Just now",type=type,direction=CallDirection.OUTGOING,state="ENDED",createdAtMs=now)
        calls.add(0,item); db.addCall(item)
    }

'''
s=s[:start]+newblock+s[end:]
old='''    private fun Throwable.userMessage(fallback: String): String = when (this) {
        is ApiException -> message ?: fallback
        else -> message?.takeIf { it.isNotBlank() } ?: fallback
    }'''
new='''    private fun Throwable.userMessage(fallback: String): String {
        val candidate=if(this is ApiException) message.orEmpty().trim() else ""
        val technical=candidate.contains("Exception",true) || candidate.contains("Coroutine",true) ||
            candidate.contains("java.",true) || candidate.contains("kotlin.",true) || candidate.contains("android.",true) ||
            candidate.contains("SQLSTATE",true) || candidate.contains("stack",true) || candidate.length>180
        return if(candidate.isNotBlank() && !technical) candidate else fallback
    }'''
if old not in s: raise SystemExit('userMessage anchor missing')
s=s.replace(old,new,1)
p.write_text(s)

print('2.21 data history and error sanitization applied')
