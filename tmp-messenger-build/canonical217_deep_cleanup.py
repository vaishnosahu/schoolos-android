from pathlib import Path
root=Path("/tmp/messenger-build/messenger_live")
pkg=root/"app/src/main/java/com/example/messengerui"

# Fix privacy state after placeholder-only settings removal.
p=pkg/"AppViewModel.kt"
s=p.read_text()
s=s.replace(
'''    var readReceipts by mutableStateOf(db.getBooleanSetting("read_receipts", true))
        private set
        private set
        private set
        private set
        private set
''',
'''    var readReceipts by mutableStateOf(db.getBooleanSetting("read_receipts", true))
        private set
''',
1
)
s=s.replace('    val pendingSyncCount: Int get() = db.pendingSyncCount()\n','')
p.write_text(s)

# Attachment sheet has no fake Location item; unreachable fallback must not reference removed local-only API.
p=pkg/"MessengerApp.kt"
s=p.read_text().replace('                                    else -> vm.addMediaMessage(chatId, kind)','                                    else -> Unit')
s=s.replace('                        IconButton(enabled = false, onClick = {}) { Icon(Icons.Outlined.Videocam, "Video call unavailable") }\n','')
p.write_text(s)

# Retire the unused generic sync_queue subsystem. Production sync is direct REST/realtime,
# message retry fields, and receipt_queue. Old sync_queue is dropped on DB v11 upgrade.
p=pkg/"LocalMessengerDb.kt"
s=p.read_text()
for exact in [
    '        db.execSQL("CREATE TABLE sync_queue (id INTEGER PRIMARY KEY AUTOINCREMENT, operation_type TEXT NOT NULL, entity_type TEXT NOT NULL, entity_id TEXT NOT NULL, payload_json TEXT NOT NULL, created_at_ms INTEGER NOT NULL, attempt_count INTEGER NOT NULL DEFAULT 0, next_attempt_at_ms INTEGER NOT NULL DEFAULT 0, last_error TEXT, state TEXT NOT NULL DEFAULT \'PENDING\')")\n',
    '        db.execSQL("CREATE INDEX idx_sync_queue_due ON sync_queue(state, next_attempt_at_ms, created_at_ms, id)")\n',
    '            safeAlter(db, "ALTER TABLE sync_queue ADD COLUMN next_attempt_at_ms INTEGER NOT NULL DEFAULT 0")\n',
    '            safeAlter(db, "ALTER TABLE sync_queue ADD COLUMN state TEXT NOT NULL DEFAULT \'PENDING\'")\n',
    '            runCatching { db.execSQL("DROP INDEX IF EXISTS idx_sync_queue_created") }\n',
    '            runCatching { db.execSQL("CREATE INDEX IF NOT EXISTS idx_sync_queue_due ON sync_queue(state, next_attempt_at_ms, created_at_ms, id)") }\n',
    '        enqueue("UPSERT", "message", id.toString(), messagePayload(message))\n',
    '        enqueue("UPSERT", "message", message.id.toString(), messagePayload(message))\n',
    '        enqueue("DELETE", "message", id.toString(), "{}")\n',
    '        enqueue(if (starred) "STAR" else "UNSTAR", "message", messageId.toString(), "{}")\n',
    '        if (queue) enqueue("UPSERT", "chat", chat.id, JSONObject().put("title", chat.title).put("is_group", chat.isGroup).toString())\n',
    '        enqueue("UPSERT", "call", item.id, JSONObject().put("contact_name", item.contactName).put("type", item.type.name).toString())\n',
]:
    s=s.replace(exact,'')

start=s.find('    fun pendingSyncCount(): Int')
end=s.find('    fun hasServerSession(): Boolean',start)
if start < 0 or end < 0:
    raise SystemExit("legacy sync queue method block missing")
s=s[:start]+s[end:]
s=s.replace('                db.delete("sync_queue", null, null)\n','')
s=s.replace('            db.delete("sync_queue", null, null)\n','')
anchor='''        if (oldVersion < 10) {
            safeAlter(db, "ALTER TABLE statuses ADD COLUMN muted INTEGER NOT NULL DEFAULT 0")
        }
'''
if anchor not in s:
    raise SystemExit("DB upgrade anchor missing")
s=s.replace(anchor,anchor+'''        if (oldVersion < 11) {
            runCatching { db.execSQL("DROP TABLE IF EXISTS sync_queue") }
        }
''',1)
s=s.replace('private const val DB_VERSION = 10','private const val DB_VERSION = 11')
p.write_text(s)

# Remove now-unused sync model and source files.
p=pkg/"Models.kt"
s=p.read_text()
start=s.find('data class SyncQueueItem(')
end=s.find('sealed interface AppScreen',start)
if start >= 0 and end >= 0:
    s=s[:start]+s[end:]
p.write_text(s)
for name in ["SyncCoordinator.kt","SyncQueuePolicy.kt"]:
    q=pkg/name
    if q.exists(): q.unlink()

# Sensitive Messenger local database/preferences must not be Android cloud-backup material.
p=root/"app/src/main/AndroidManifest.xml"
s=p.read_text().replace('android:allowBackup="true"','android:allowBackup="false"')
p.write_text(s)

# Use a silent low-importance channel for the foreground-service ongoing call notification.
# Reusing the ringing channel can cause unwanted ringtone/vibration when a call becomes active.
p=pkg/"NotificationHelper.kt"
s=p.read_text()
if 'ONGOING_CALLS_CHANNEL' not in s:
    s=s.replace(
        '    private const val MISSED_CALLS_CHANNEL = "missed_calls"\n',
        '    private const val MISSED_CALLS_CHANNEL = "missed_calls"\n    private const val ONGOING_CALLS_CHANNEL = "ongoing_calls"\n',
        1
    )
    marker='manager.createNotificationChannel(NotificationChannel(MISSED_CALLS_CHANNEL'
    start=s.find(marker)
    if start < 0:
        raise SystemExit("missed-call channel marker missing")
    close=s.find('\n        })',start)
    if close < 0:
        raise SystemExit("missed-call channel close missing")
    close += len('\n        })')
    block='''\n        manager.createNotificationChannel(NotificationChannel(ONGOING_CALLS_CHANNEL, "Ongoing calls", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Active Messenger call status"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        })'''
    s=s[:close]+block+s[close:]
s=s.replace('NotificationCompat.Builder(context,CALLS_CHANNEL).setSmallIcon','NotificationCompat.Builder(context,ONGOING_CALLS_CHANNEL).setSmallIcon',1)
p.write_text(s)

print("canonical Messenger 2.17 deep cleanup applied")
