from pathlib import Path
root=Path("/tmp/messenger-build/messenger_live")
pkg=root/"app/src/main/java/com/example/messengerui"

p=root/"app/build.gradle.kts"
s=p.read_text().replace("versionCode = 20","versionCode = 21").replace('versionName = "2.9"','versionName = "2.10"')
p.write_text(s)

p=pkg/"AppViewModel.kt"
s=p.read_text()
s=s.replace('var phone by mutableStateOf(db.loadProfile().phone)','var phone by mutableStateOf(db.loadProfile().phone.ifBlank { "+91 " })')
s=s.replace("""    fun sendOtp() {
        val digits = phone.filter(Char::isDigit)
        if (digits.length < 8) {
            lastToast = "Enter a valid mobile number"
            return
        }
""","""    fun updatePhoneInput(raw: String) {
        val trimmed = raw.trim()
        val digits = trimmed.filter(Char::isDigit)
        phone = when {
            trimmed.startsWith("+") -> "+" + digits.take(15)
            digits.startsWith("91") && digits.length > 10 -> "+" + digits.take(12)
            digits.startsWith("0") && digits.length > 1 -> "+91 " + digits.drop(1).take(10)
            else -> "+91 " + digits.takeLast(10)
        }
    }

    private fun normalizedLoginPhone(): String {
        val raw = phone.trim()
        val digits = raw.filter(Char::isDigit)
        return when {
            raw.startsWith("+") && digits.length >= 8 -> "+$digits"
            digits.length == 10 -> "+91$digits"
            digits.length == 11 && digits.startsWith("0") -> "+91${digits.drop(1)}"
            digits.length == 12 && digits.startsWith("91") -> "+$digits"
            else -> raw
        }
    }

    fun sendOtp() {
        val normalized = normalizedLoginPhone()
        val digits = normalized.filter(Char::isDigit)
        if (digits.length < 10) {
            lastToast = "Enter a valid mobile number"
            return
        }
        phone = normalized
""",1)
s=s.replace("""    fun verifyOtp() {
        if (!ApiConfig.isConfigured) {""","""    fun verifyOtp() {
        phone = normalizedLoginPhone()
        if (!ApiConfig.isConfigured) {""",1)
s=s.replace("""        phone = ""
        otp = """"","""        phone = "+91 "
        otp = """"",1)
s=s.replace("""            db.makeReceiptAcksRetryableNow()
            refreshFailedMessages()
            refreshServerCallsAsync()""","""            db.makeReceiptAcksRetryableNow()
            refreshFailedMessages()
            refreshServerContactsAsync()
            refreshServerCallsAsync()""",1)
s=s.replace("""                        history.clear()
                        screen = AppScreen.Home
                        startServerSync()""","""                        history.clear()
                        screen = AppScreen.Home
                        refreshServerContactsAsync()
                        startServerSync()""",1)
s=s.replace("""            result.onSuccess { users ->
                withContext(Dispatchers.IO) { db.replaceServerContacts(users) }
                contacts.clear(); contacts.addAll(db.loadContacts())
            }
""","""            result.onSuccess { users ->
                if (users.isNotEmpty()) {
                    withContext(Dispatchers.IO) { db.mergeServerContacts(users) }
                    contacts.clear(); contacts.addAll(db.loadContacts())
                }
            }.onFailure { error ->
                if (error is ApiException && error.statusCode == 401) {
                    db.clearServerSession(); db.setLoggedIn(false); screen = AppScreen.Login
                }
            }
""",1)
s=s.replace("""                db.replaceServerContacts(data.contacts); data.conversations.forEach { db.upsertRemoteConversation(it) }; data.messages.forEach { db.upsertRemoteMessage(it,data.user.id) }""","""                if (data.contacts.isNotEmpty()) db.mergeServerContacts(data.contacts)
                data.conversations.forEach { db.upsertRemoteConversation(it) }
                data.messages.forEach { db.upsertRemoteMessage(it,data.user.id) }""",1)
p.write_text(s)

p=pkg/"LocalMessengerDb.kt"
s=p.read_text()
old="""    fun replaceServerContacts(users: List<RemoteUser>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("contacts", null, null)
            users.forEach { user -> insertContact(db, Contact(user.id, user.name, user.phone, user.about, user.online, user.lastSeenIso)) }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }"""
new="""    fun mergeServerContacts(users: List<RemoteUser>) {
        if (users.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("contacts", "id IN (?,?,?,?,?,?)", arrayOf("c1","c2","c3","c4","c5","c6"))
            users.forEach { user -> insertContact(db, Contact(user.id, user.name, user.phone, user.about, user.online, user.lastSeenIso)) }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun replaceServerContacts(users: List<RemoteUser>) = mergeServerContacts(users)"""
if old not in s: raise SystemExit("contact DB anchor missing")
p.write_text(s.replace(old,new,1))

p=pkg/"MessengerApp.kt"
s=p.read_text().replace('onValueChange = { vm.phone = it },','onValueChange = { vm.updatePhoneInput(it) },',1)
p.write_text(s)

p=pkg/"ApiClient.kt"
s=p.read_text()
old='val json = runCatching { JSONObject(raw) }.getOrElse { throw ApiException("invalid_response", "Server returned an invalid response.", status) }'
first=s.find(old)
if first>=0:
    s=s[:first]+'val json = parseJsonResponse(raw, status, "media/upload.php")'+s[first+len(old):]
last=s.rfind(old)
if last>=0:
    s=s[:last]+'val json = parseJsonResponse(raw, status, path)'+s[last+len(old):]
helper="""    private fun parseJsonResponse(rawBody: String, status: Int, path: String): JSONObject {
        val clean = rawBody.trim().trimStart('\\uFEFF')
        runCatching { JSONObject(clean) }.getOrNull()?.let { return it }
        val first = clean.indexOf('{')
        val last = clean.lastIndexOf('}')
        if (first >= 0 && last > first) {
            runCatching { JSONObject(clean.substring(first, last + 1)) }.getOrNull()?.let { return it }
        }
        val endpoint = path.substringBefore('?')
        val detail = when {
            status >= 500 -> "Server error at $endpoint (HTTP $status)."
            status == 404 -> "Server endpoint is missing: $endpoint."
            else -> "Server API returned non-JSON data at $endpoint (HTTP $status)."
        }
        throw ApiException("invalid_response", detail, status)
    }

"""
anchor='    private fun request(\n'
if anchor not in s: raise SystemExit("request anchor missing")
s=s.replace(anchor,helper+anchor,1)
p.write_text(s)
