from pathlib import Path
root = Path("/tmp/messenger-build/messenger_live")
pkg = root / "app/src/main/java/com/example/messengerui"

build = root / "app/build.gradle.kts"
s = build.read_text().replace("versionCode = 16", "versionCode = 17").replace('versionName = "2.5"', 'versionName = "2.6"')
build.write_text(s)

models = pkg / "ApiModels.kt"
s = models.read_text().replace(
    "data class PollResult(val cursor: Long, val events: List<RemoteEvent>)",
    "data class PollResult(val cursor: Long, val events: List<RemoteEvent>, val hasMore: Boolean = false)"
)
models.write_text(s)

api = pkg / "ApiClient.kt"
s = api.read_text().replace(
    'return PollResult(json.optLong("cursor", after), events)',
    'return PollResult(json.optLong("cursor", after), events, json.optBoolean("has_more", false))'
)
api.write_text(s)

db = pkg / "LocalMessengerDb.kt"
s = db.read_text()
s = s.replace("private const val DB_VERSION = 8", "private const val DB_VERSION = 9")
s = s.replace(
    'db.execSQL("CREATE TABLE sync_queue (id INTEGER PRIMARY KEY AUTOINCREMENT, operation_type TEXT NOT NULL, entity_type TEXT NOT NULL, entity_id TEXT NOT NULL, payload_json TEXT NOT NULL, created_at_ms INTEGER NOT NULL, attempt_count INTEGER NOT NULL DEFAULT 0, next_attempt_at_ms INTEGER NOT NULL DEFAULT 0, last_error TEXT, state TEXT NOT NULL DEFAULT \'PENDING\')")',
    'db.execSQL("CREATE TABLE sync_queue (id INTEGER PRIMARY KEY AUTOINCREMENT, operation_type TEXT NOT NULL, entity_type TEXT NOT NULL, entity_id TEXT NOT NULL, payload_json TEXT NOT NULL, created_at_ms INTEGER NOT NULL, attempt_count INTEGER NOT NULL DEFAULT 0, next_attempt_at_ms INTEGER NOT NULL DEFAULT 0, last_error TEXT, state TEXT NOT NULL DEFAULT \'PENDING\')")\n        db.execSQL("CREATE TABLE receipt_queue (message_id TEXT PRIMARY KEY, status TEXT NOT NULL, attempt_count INTEGER NOT NULL DEFAULT 0, next_attempt_at_ms INTEGER NOT NULL DEFAULT 0, last_error TEXT, updated_at_ms INTEGER NOT NULL)")'
)
upgrade = '''        if (oldVersion < 8) {
            safeAlter(db, "ALTER TABLE contacts ADD COLUMN last_seen_at TEXT")
            safeAlter(db, "ALTER TABLE messages ADD COLUMN retry_count INTEGER NOT NULL DEFAULT 0")
            safeAlter(db, "ALTER TABLE messages ADD COLUMN next_retry_at_ms INTEGER NOT NULL DEFAULT 0")
            safeAlter(db, "ALTER TABLE messages ADD COLUMN last_error TEXT")
        }
'''
if "oldVersion < 9" not in s:
    s = s.replace(upgrade, upgrade + '''        if (oldVersion < 9) {
            runCatching { db.execSQL("CREATE TABLE IF NOT EXISTS receipt_queue (message_id TEXT PRIMARY KEY, status TEXT NOT NULL, attempt_count INTEGER NOT NULL DEFAULT 0, next_attempt_at_ms INTEGER NOT NULL DEFAULT 0, last_error TEXT, updated_at_ms INTEGER NOT NULL)") }
        }
''')
receipt_methods = '''
    data class PendingReceiptAck(
        val messageId: String,
        val status: MessageStatus,
        val attemptCount: Int
    )

    fun enqueueReceiptAck(messageId: String, status: MessageStatus) {
        if (messageId.isBlank() || status == MessageStatus.SENDING || status == MessageStatus.SENT) return
        val current = readableDatabase.rawQuery(
            "SELECT status FROM receipt_queue WHERE message_id=? LIMIT 1",
            arrayOf(messageId)
        ).use { c -> if (c.moveToFirst()) enumValueOrDefault(c.getString(0), MessageStatus.DELIVERED) else null }
        val target = if (current == null || statusRank(status) > statusRank(current)) status else current
        val values = ContentValues().apply {
            put("message_id", messageId)
            put("status", target.name)
            put("attempt_count", 0)
            put("next_attempt_at_ms", 0)
            putNull("last_error")
            put("updated_at_ms", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("receipt_queue", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun loadDueReceiptAcks(limit: Int = 100): List<PendingReceiptAck> = readableDatabase.rawQuery(
        "SELECT message_id,status,attempt_count FROM receipt_queue WHERE next_attempt_at_ms<=? ORDER BY updated_at_ms,message_id LIMIT ?",
        arrayOf(System.currentTimeMillis().toString(), limit.coerceIn(1, 250).toString())
    ).use { c -> buildList { while (c.moveToNext()) add(PendingReceiptAck(c.getString(0), enumValueOrDefault(c.getString(1), MessageStatus.DELIVERED), c.getInt(2))) } }

    fun markReceiptAckSuccess(messageId: String) {
        writableDatabase.delete("receipt_queue", "message_id=?", arrayOf(messageId))
    }

    fun markReceiptAckFailure(messageId: String, error: String) {
        val count = readableDatabase.rawQuery("SELECT attempt_count FROM receipt_queue WHERE message_id=?", arrayOf(messageId)).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 } + 1
        val delayMs = (1_500L * (1L shl (count - 1).coerceIn(0, 6))).coerceAtMost(90_000L)
        writableDatabase.update("receipt_queue", ContentValues().apply {
            put("attempt_count", count)
            put("next_attempt_at_ms", System.currentTimeMillis() + delayMs)
            put("last_error", error.take(240))
            put("updated_at_ms", System.currentTimeMillis())
        }, "message_id=?", arrayOf(messageId))
    }

    fun makeReceiptAcksRetryableNow() {
        writableDatabase.execSQL("UPDATE receipt_queue SET next_attempt_at_ms=0")
    }

'''
if "data class PendingReceiptAck" not in s:
    s = s.replace("    fun applyPresence(items: List<PresenceItem>) {", receipt_methods + "    fun applyPresence(items: List<PresenceItem>) {", 1)
db.write_text(s)

worker = pkg / "PushAckWorker.kt"
s = worker.read_text()
old = '''        runCatching { ApiClient().ackMessage(token, messageId, MessageStatus.DELIVERED) }
            .fold(
                onSuccess = { Result.success() },
                onFailure = { error ->
                    if (error is ApiException && error.statusCode in listOf(401, 403, 404, 422)) Result.success()
                    else Result.retry()
                }
            )'''
new = '''        db.enqueueReceiptAck(messageId, MessageStatus.DELIVERED)
        runCatching { ApiClient().ackMessage(token, messageId, MessageStatus.DELIVERED) }
            .fold(
                onSuccess = { db.markReceiptAckSuccess(messageId); Result.success() },
                onFailure = { error ->
                    if (error is ApiException && error.statusCode in listOf(401, 403, 404, 422)) {
                        db.markReceiptAckSuccess(messageId)
                        Result.success()
                    } else {
                        db.markReceiptAckFailure(messageId, error.message ?: "Delivery receipt failed")
                        Result.retry()
                    }
                }
            )'''
if old not in s:
    raise SystemExit("PushAckWorker anchor missing")
worker.write_text(s.replace(old, new, 1))

vm = pkg / "AppViewModel.kt"
s = vm.read_text()
s = s.replace("    private var realtimeJob: Job? = null\n", "    private var realtimeJob: Job? = null\n    private var backgroundedAtMs: Long? = null\n", 1)
old_cb = '''    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { viewModelScope.launch { networkAvailable = true; if (serverMode) { connectionState = SyncConnectionState.CONNECTING; startServerSync() } } }
        override fun onLost(network: Network) { viewModelScope.launch { networkAvailable = isNetworkAvailable(); if (!networkAvailable) connectionState = SyncConnectionState.OFFLINE } }
    }'''
new_cb = '''    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            viewModelScope.launch {
                networkAvailable = true
                if (serverMode) {
                    db.makePendingMessagesRetryableNow()
                    db.makeReceiptAcksRetryableNow()
                    connectionState = SyncConnectionState.CONNECTING
                    restartServerSync()
                }
            }
        }
        override fun onLost(network: Network) {
            viewModelScope.launch {
                networkAvailable = isNetworkAvailable()
                if (!networkAvailable) {
                    connectionState = SyncConnectionState.OFFLINE
                    typingByChat.clear()
                }
            }
        }
    }'''
if old_cb not in s: raise SystemExit("network callback anchor missing")
s = s.replace(old_cb, new_cb, 1)
s = s.replace("            db.makePendingMessagesRetryableNow()\n            refreshFailedMessages()",
              "            db.makePendingMessagesRetryableNow()\n            db.makeReceiptAcksRetryableNow()\n            refreshFailedMessages()", 1)

lifecycle = '''    fun onAppBackgrounded() {
        backgroundedAtMs = System.currentTimeMillis()
        (screen as? AppScreen.ChatDetail)?.chatId?.let { stopTyping(it) }
    }

    fun onAppForegrounded() {
        val awayMs = backgroundedAtMs?.let { (System.currentTimeMillis() - it).coerceAtLeast(0L) } ?: 0L
        backgroundedAtMs = null
        if (!serverMode) return
        val activeChat = (screen as? AppScreen.ChatDetail)?.chatId
        if (awayMs >= 3_000L) {
            db.forceFullServerSync()
            restartServerSync()
        } else if (!activeChat.isNullOrBlank()) {
            reconcileVisibleChat(activeChat)
        }
    }

    private fun reconcileVisibleChat(chatId: String) {
        NotificationHelper.cancelConversation(getApplication(), chatId)
        val index = chats.indexOfFirst { it.id == chatId }
        if (index >= 0 && chats[index].unread != 0) {
            chats[index] = chats[index].copy(unread = 0)
            db.upsertChat(chats[index], queue = false)
        }
        if (readReceipts) acknowledgeChatRead(chatId)
    }

'''
s = s.replace("    fun navigate(to: AppScreen) {", lifecycle + "    fun navigate(to: AppScreen) {", 1)

s = s.replace("    private fun startServerSync() {",
              "    private fun restartServerSync() {\n        realtimeJob?.cancel()\n        realtimeJob = null\n        startServerSync()\n    }\n\n    private fun startServerSync() {", 1)

s = s.replace("                if (failures == 0) connectionState = SyncConnectionState.ONLINE\n                flushPendingServerMessages()",
              "                if (failures == 0) connectionState = SyncConnectionState.ONLINE\n                flushPendingReceiptAcks()\n                flushPendingServerMessages()", 1)

old_boot = '''            val incoming=data.messages.filter { !it.outgoing }.map { it.id }
            if(incoming.isNotEmpty()) viewModelScope.launch(Dispatchers.IO){ incoming.forEach { id -> runCatching { api.ackMessage(token,id,MessageStatus.DELIVERED) } } }'''
new_boot = '''            val incoming=data.messages.filter { !it.outgoing }.map { it.id }
            if (incoming.isNotEmpty()) {
                withContext(Dispatchers.IO) { incoming.forEach { id -> db.enqueueReceiptAck(id, MessageStatus.DELIVERED) } }
                flushPendingReceiptAcks()
            }
            (screen as? AppScreen.ChatDetail)?.chatId?.takeIf { NotificationRuntime.appVisible }?.let { reconcileVisibleChat(it) }'''
if old_boot not in s: raise SystemExit("bootstrap receipt anchor missing")
s = s.replace(old_boot, new_boot, 1)

start = s.index("    private suspend fun catchUpServerEvents(")
end = s.index("\n    private fun flushPendingServerMessagesAsync()", start)
catchup = '''    private suspend fun catchUpServerEvents(startCursor: Long, targetCursor: Long) {
        val token = db.serverToken()
        if (token.isBlank()) return
        var cursor = startCursor
        var pages = 0
        var noProgress = 0
        while (cursor < targetCursor && pages < 60 && networkAvailable) {
            val poll = runCatching { withContext(Dispatchers.IO) { api.poll(token, cursor, 1) } }.getOrElse { break }
            val ordered = poll.events.sortedBy { it.id }.filter { it.id > cursor }
            ordered.forEach { handleRemoteEvent(it) }
            val nextCursor = maxOf(cursor, poll.cursor, ordered.maxOfOrNull { it.id } ?: cursor)
            if (nextCursor <= cursor) {
                noProgress++
                if (noProgress >= 2) {
                    bootstrapServer(advanceCursor = true)
                    return
                }
                delay(180)
            } else {
                cursor = nextCursor
                db.setServerSyncCursor(cursor)
                noProgress = 0
                pages++
            }
            if (!poll.hasMore && cursor >= targetCursor) break
        }
        if (cursor < targetCursor && networkAvailable) bootstrapServer(advanceCursor = true)
    }
'''
s = s[:start] + catchup + s[end:]

receipt = '''    private fun flushPendingReceiptAcksAsync() {
        if (!serverMode) return
        viewModelScope.launch { flushPendingReceiptAcks() }
    }

    private suspend fun flushPendingReceiptAcks() {
        if (!serverMode || !networkAvailable) return
        val token = db.serverToken()
        if (token.isBlank()) return
        val pending = withContext(Dispatchers.IO) { db.loadDueReceiptAcks(100) }
        for (ack in pending) {
            runCatching { withContext(Dispatchers.IO) { api.ackMessage(token, ack.messageId, ack.status) } }
                .onSuccess { withContext(Dispatchers.IO) { db.markReceiptAckSuccess(ack.messageId) } }
                .onFailure { error ->
                    if (error is ApiException && error.statusCode in listOf(401, 403, 404, 422)) {
                        withContext(Dispatchers.IO) { db.markReceiptAckSuccess(ack.messageId) }
                    } else {
                        withContext(Dispatchers.IO) { db.markReceiptAckFailure(ack.messageId, error.message ?: "Receipt sync failed") }
                    }
                }
        }
    }

'''
s = s.replace("    private fun flushPendingServerMessagesAsync() {", receipt + "    private fun flushPendingServerMessagesAsync() {", 1)

old_ack = '''                val token = db.serverToken()
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching { api.ackMessage(token, messageId, MessageStatus.DELIVERED) }
                    if (openNow && readReceipts) runCatching { api.ackMessage(token, messageId, MessageStatus.READ) }
                }'''
new_ack = '''                withContext(Dispatchers.IO) {
                    db.enqueueReceiptAck(messageId, if (openNow && readReceipts) MessageStatus.READ else MessageStatus.DELIVERED)
                }
                flushPendingReceiptAcksAsync()'''
if old_ack not in s: raise SystemExit("message ack anchor missing")
s = s.replace(old_ack, new_ack, 1)

old_read = '''    private fun acknowledgeChatRead(chatId: String) {
        if (!serverMode || !readReceipts) return
        val token = db.serverToken()
        val ids = db.incomingServerMessageIds(chatId)
        if (token.isBlank() || ids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            ids.forEach { id -> runCatching { api.ackMessage(token, id, MessageStatus.READ) } }
        }
    }'''
new_read = '''    private fun acknowledgeChatRead(chatId: String) {
        if (!serverMode || !readReceipts) return
        val ids = db.incomingServerMessageIds(chatId)
        if (ids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            ids.forEach { id -> db.enqueueReceiptAck(id, MessageStatus.READ) }
            withContext(Dispatchers.Main) { flushPendingReceiptAcksAsync() }
        }
    }'''
if old_read not in s: raise SystemExit("read ack anchor missing")
s = s.replace(old_read, new_read, 1)
vm.write_text(s)

ui = pkg / "MessengerApp.kt"
s = ui.read_text()
s = s.replace("import androidx.lifecycle.viewmodel.compose.viewModel\n",
              "import androidx.lifecycle.viewmodel.compose.viewModel\nimport androidx.lifecycle.Lifecycle\nimport androidx.lifecycle.LifecycleEventObserver\nimport androidx.lifecycle.compose.LocalLifecycleOwner\n", 1)
s = s.replace("    val context = LocalContext.current\n",
              '''    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> vm.onAppForegrounded()
                Lifecycle.Event.ON_STOP -> vm.onAppBackgrounded()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
''', 1)
ui.write_text(s)

print("rt26 patch applied")
