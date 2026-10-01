from pathlib import Path
root = Path('/tmp/messenger-build/messenger_live')
pkg = root / 'app/src/main/java/com/example/messengerui'

build = root / 'app/build.gradle.kts'
s = build.read_text().replace('versionCode = 15', 'versionCode = 16').replace('versionName = "2.4"', 'versionName = "2.5"')
build.write_text(s)

cfg = pkg / 'ApiConfig.kt'
s = cfg.read_text()
s = s.replace('    const val FIREBASE_APP_ID = ""', '    // Legacy fallback only; production Firebase client values are fetched from the Messenger API.\n    const val FIREBASE_APP_ID = ""')
cfg.write_text(s)

(pkg / 'FirebaseClientStore.kt').write_text('''package com.example.messengerui

import android.content.Context

data class FirebaseClientConfig(
    val applicationId: String,
    val apiKey: String,
    val projectId: String,
    val senderId: String
) {
    val ready: Boolean
        get() = applicationId.isNotBlank() && apiKey.isNotBlank() && projectId.isNotBlank() && senderId.isNotBlank()
}

object FirebaseClientStore {
    private const val PREFS = "messenger_firebase_client"
    private const val APP_ID = "application_id"
    private const val API_KEY = "api_key"
    private const val PROJECT_ID = "project_id"
    private const val SENDER_ID = "sender_id"

    fun load(context: Context): FirebaseClientConfig? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = FirebaseClientConfig(
            prefs.getString(APP_ID, "").orEmpty(),
            prefs.getString(API_KEY, "").orEmpty(),
            prefs.getString(PROJECT_ID, "").orEmpty(),
            prefs.getString(SENDER_ID, "").orEmpty()
        )
        if (stored.ready) return stored
        val fallback = FirebaseClientConfig(
            ApiConfig.FIREBASE_APP_ID,
            ApiConfig.FIREBASE_API_KEY,
            ApiConfig.FIREBASE_PROJECT_ID,
            ApiConfig.FIREBASE_SENDER_ID
        )
        return fallback.takeIf { it.ready }
    }

    fun save(context: Context, config: FirebaseClientConfig) {
        if (!config.ready) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(APP_ID, config.applicationId)
            .putString(API_KEY, config.apiKey)
            .putString(PROJECT_ID, config.projectId)
            .putString(SENDER_ID, config.senderId)
            .apply()
    }
}
''')

(pkg / 'PushRegistration.kt').write_text('''package com.example.messengerui

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging

object PushRegistration {
    @Volatile private var lastRegistrationState: String = "Not configured"

    fun initialize(context: Context): Boolean {
        val config = FirebaseClientStore.load(context.applicationContext) ?: run {
            lastRegistrationState = "Firebase client config missing"
            return false
        }
        if (FirebaseApp.getApps(context).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApplicationId(config.applicationId)
                .setApiKey(config.apiKey)
                .setProjectId(config.projectId)
                .setGcmSenderId(config.senderId)
                .build()
            FirebaseApp.initializeApp(context.applicationContext, options)
        }
        val ready = FirebaseApp.getApps(context).isNotEmpty()
        lastRegistrationState = if (ready) "Firebase initialized" else "Firebase initialization failed"
        return ready
    }

    fun register(context: Context, serverToken: String, api: ApiClient = ApiClient()) {
        if (serverToken.isBlank()) return
        val appContext = context.applicationContext
        Thread {
            val remote = runCatching { api.firebaseBootstrap(serverToken) }.getOrNull()
            if (remote != null && remote.ready) FirebaseClientStore.save(appContext, remote)
            if (!initialize(appContext)) return@Thread
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { pushToken ->
                    Thread {
                        runCatching {
                            val db = LocalMessengerDb(appContext)
                            api.registerPushToken(
                                token = serverToken,
                                pushToken = pushToken,
                                installationId = DeviceInstallation.id(appContext),
                                deviceName = DeviceInstallation.deviceName(),
                                appVersion = DeviceInstallation.appVersion(appContext),
                                locale = DeviceInstallation.locale(),
                                messagesEnabled = db.getBooleanSetting("notifications_messages", true),
                                groupsEnabled = db.getBooleanSetting("notifications_groups", true),
                                statusEnabled = db.getBooleanSetting("notifications_status", false),
                                previewMode = db.getStringSetting("notifications_preview", NotificationPreviewMode.FULL.name)
                            )
                        }.onSuccess { lastRegistrationState = "FCM token registered" }
                            .onFailure { lastRegistrationState = "Token registration failed" }
                    }.start()
                }
                .addOnFailureListener { lastRegistrationState = "FCM token unavailable" }
        }.start()
    }

    fun unregister(context: Context, serverToken: String, api: ApiClient = ApiClient()) {
        if (serverToken.isBlank()) return
        val appContext = context.applicationContext
        if (!initialize(appContext)) {
            Thread { runCatching { api.unregisterPushToken(serverToken, null, DeviceInstallation.id(appContext)) } }.start()
            return
        }
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            val token = if (task.isSuccessful) task.result else null
            Thread {
                runCatching { api.unregisterPushToken(serverToken, token, DeviceInstallation.id(appContext)) }
                    .onSuccess { lastRegistrationState = "FCM token unregistered" }
            }.start()
        }
    }
}
''')

models = pkg / 'ApiModels.kt'
s = models.read_text()
if 'data class PushHealth(' not in s:
    s += '''\n\ndata class PushHealth(\n    val firebaseEnabled: Boolean,\n    val clientConfigReady: Boolean,\n    val oauthReady: Boolean,\n    val enabledDevices: Int,\n    val thisInstallationRegistered: Boolean,\n    val queuePending: Int,\n    val queueFailed: Int,\n    val lastError: String?\n)\n\ndata class PushTestResult(\n    val queued: Int,\n    val sent: Int,\n    val failed: Int,\n    val disabled: Int,\n    val message: String\n)\n'''
models.write_text(s)

api = pkg / 'ApiClient.kt'
s = api.read_text()
if 'fun firebaseBootstrap(' not in s:
    needle = '''    fun ackMessage(token: String, messageId: String, status: MessageStatus) {\n        request("POST", "messages/ack.php", JSONObject().put("message_id", messageId).put("status", status.name), token)\n    }\n'''
    addition = '''    fun firebaseBootstrap(token: String): FirebaseClientConfig {\n        val json = request("GET", "notifications/bootstrap.php", token = token)\n        val fb = json.optJSONObject("firebase") ?: JSONObject()\n        return FirebaseClientConfig(\n            applicationId = fb.optString("application_id"),\n            apiKey = fb.optString("api_key"),\n            projectId = fb.optString("project_id"),\n            senderId = fb.optString("sender_id")\n        )\n    }\n\n    fun pushHealth(token: String, installationId: String): PushHealth {\n        val path = "notifications/health.php?installation_id=" + URLEncoder.encode(installationId, StandardCharsets.UTF_8.name())\n        val json = request("GET", path, token = token)\n        val firebase = json.optJSONObject("firebase") ?: JSONObject()\n        val devices = json.optJSONObject("devices") ?: JSONObject()\n        val queue = json.optJSONObject("queue") ?: JSONObject()\n        return PushHealth(\n            firebaseEnabled = firebase.optBoolean("enabled", false),\n            clientConfigReady = firebase.optBoolean("client_config_ready", false),\n            oauthReady = firebase.optBoolean("oauth_ready", false),\n            enabledDevices = devices.optInt("enabled", 0),\n            thisInstallationRegistered = devices.optBoolean("this_installation_registered", false),\n            queuePending = queue.optInt("pending", 0),\n            queueFailed = queue.optInt("failed", 0),\n            lastError = queue.optStringOrNull("last_error")\n        )\n    }\n\n    fun sendPushTest(token: String, installationId: String): PushTestResult {\n        val json = request("POST", "notifications/test.php", JSONObject().put("installation_id", installationId), token)\n        val dispatch = json.optJSONObject("dispatch") ?: JSONObject()\n        return PushTestResult(\n            queued = json.optInt("queued", 0),\n            sent = dispatch.optInt("sent", 0),\n            failed = dispatch.optInt("failed", 0),\n            disabled = dispatch.optInt("disabled", 0),\n            message = json.optString("message", "Push test queued")\n        )\n    }\n\n'''
    if needle not in s: raise SystemExit('ApiClient anchor missing')
    s = s.replace(needle, addition + needle, 1)
api.write_text(s)

helper = pkg / 'NotificationHelper.kt'
s = helper.read_text()
if 'fun showServerPushTest(' not in s:
    needle = '''    private fun canNotify(context: Context): Boolean =\n        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED\n'''
    addition = '''    fun showServerPushTest(context: Context, title: String, body: String): Boolean {\n        if (!canNotify(context)) return false\n        val intent = Intent(context, MainActivity::class.java).apply {\n            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP\n        }\n        val pi = PendingIntent.getActivity(context, 0x4D5347, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)\n        val notification = NotificationCompat.Builder(context, MESSAGES_CHANNEL)\n            .setSmallIcon(R.drawable.ic_notification)\n            .setContentTitle(title)\n            .setContentText(body)\n            .setStyle(NotificationCompat.BigTextStyle().bigText(body))\n            .setPriority(NotificationCompat.PRIORITY_HIGH)\n            .setCategory(NotificationCompat.CATEGORY_STATUS)\n            .setContentIntent(pi)\n            .setAutoCancel(true)\n            .build()\n        NotificationManagerCompat.from(context).notify(0x4D5347, notification)\n        return true\n    }\n\n'''
    if needle not in s: raise SystemExit('NotificationHelper anchor missing')
    s = s.replace(needle, addition + needle, 1)
helper.write_text(s)

service = pkg / 'MessengerFirebaseService.kt'
s = service.read_text()
if 'eventType == "push.test"' not in s:
    needle = '''        val db = LocalMessengerDb(applicationContext)\n        if (eventType == "status.created") {\n'''
    addition = '''        val db = LocalMessengerDb(applicationContext)\n        if (eventType == "push.test") {\n            NotificationHelper.showServerPushTest(\n                this,\n                data["title"].orEmpty().ifBlank { "Messenger push test" },\n                data["body"].orEmpty().ifBlank { "Firebase background delivery is working." }\n            )\n            return\n        }\n        if (eventType == "status.created") {\n'''
    if needle not in s: raise SystemExit('Firebase service anchor missing')
    s = s.replace(needle, addition, 1)
service.write_text(s)

vm = pkg / 'AppViewModel.kt'
s = vm.read_text()
if 'var pushHealthText' not in s:
    needle = '    val contacts = mutableStateListOf<Contact>().apply { addAll(db.loadContacts()) }\n'
    addition = '''    var pushHealthText by mutableStateOf("Checking push delivery…")\n        private set\n    var pushHealthReady by mutableStateOf(false)\n        private set\n\n'''
    if needle not in s: raise SystemExit('VM state anchor missing')
    s = s.replace(needle, addition + needle, 1)
if 'fun refreshPushHealth()' not in s:
    needle = '    private fun setTransferProgress(messageId: Long, progress: Int?) {\n'
    addition = '''    fun refreshPushHealth() {\n        if (!serverMode) {\n            pushHealthReady = false\n            pushHealthText = "Sign in to check server push delivery"\n            return\n        }\n        val token = db.serverToken()\n        val installationId = DeviceInstallation.id(getApplication())\n        viewModelScope.launch {\n            PushRegistration.register(getApplication(), token, api)\n            runCatching { withContext(Dispatchers.IO) { api.pushHealth(token, installationId) } }\n                .onSuccess { health ->\n                    pushHealthReady = health.firebaseEnabled && health.clientConfigReady && health.oauthReady && health.thisInstallationRegistered\n                    pushHealthText = when {\n                        !health.clientConfigReady -> "Firebase Android config is not enabled on the server"\n                        !health.firebaseEnabled -> "Firebase server credentials are not enabled"\n                        !health.oauthReady -> "Firebase server authorization is not ready"\n                        !health.thisInstallationRegistered -> "This phone is waiting for FCM token registration"\n                        health.queueFailed > 0 -> "Push connected · ${health.queueFailed} delivery retry pending"\n                        else -> "Push connected · ${health.enabledDevices} active device${if (health.enabledDevices == 1) "" else "s"}"\n                    }\n                }\n                .onFailure {\n                    pushHealthReady = false\n                    pushHealthText = "Unable to verify push server"\n                }\n        }\n    }\n\n    fun sendServerPushTest() {\n        if (!serverMode) {\n            lastToast = "Sign in before testing server push"\n            return\n        }\n        val token = db.serverToken()\n        val installationId = DeviceInstallation.id(getApplication())\n        viewModelScope.launch {\n            PushRegistration.register(getApplication(), token, api)\n            runCatching { withContext(Dispatchers.IO) { api.sendPushTest(token, installationId) } }\n                .onSuccess { result ->\n                    lastToast = when {\n                        result.sent > 0 -> "Server push sent · background the app to test delivery"\n                        result.queued > 0 && result.failed > 0 -> "Push queued but Firebase send needs attention"\n                        else -> result.message\n                    }\n                    refreshPushHealth()\n                }\n                .onFailure { lastToast = it.userMessage("Unable to send server push test") }\n        }\n    }\n\n'''
    if needle not in s: raise SystemExit('VM method anchor missing')
    s = s.replace(needle, addition + needle, 1)
vm.write_text(s)

ui = pkg / 'MessengerApp.kt'
s = ui.read_text()
if 'LaunchedEffect(Unit) { vm.refreshPushHealth() }' not in s:
    s = s.replace('''private fun NotificationsScreen(vm: AppViewModel) {\n    SimpleBackScaffold(title = "Notifications", onBack = { vm.back() }) { padding ->\n''', '''private fun NotificationsScreen(vm: AppViewModel) {\n    LaunchedEffect(Unit) { vm.refreshPushHealth() }\n    SimpleBackScaffold(title = "Notifications", onBack = { vm.back() }) { padding ->\n''', 1)
if '"Firebase push status"' not in s:
    needle = '''            item { SectionLabel("Android controls") }\n            item { SettingsRow(Icons.Outlined.Tune, "Sound, vibration & importance", "Open Android notification channels and alert controls") { vm.openSystemNotificationSettings() } }\n            item { SectionLabel("Test") }\n            item { SettingsRow(Icons.Outlined.NotificationsActive, "Send local test notification", "Verify Android permission, channel and preview presentation") { vm.showTestNotification() } }\n'''
    replacement = '''            item { SectionLabel("Push delivery") }\n            item {\n                SettingsRow(\n                    if (vm.pushHealthReady) Icons.Outlined.CloudDone else Icons.Outlined.CloudSync,\n                    "Firebase push status",\n                    vm.pushHealthText\n                ) { vm.refreshPushHealth() }\n            }\n            item { SettingsRow(Icons.Outlined.SendToMobile, "Send server push test", "Tests server credentials, this phone's FCM token and Android delivery") { vm.sendServerPushTest() } }\n            item { SectionLabel("Android controls") }\n            item { SettingsRow(Icons.Outlined.Tune, "Sound, vibration & importance", "Open Android notification channels and alert controls") { vm.openSystemNotificationSettings() } }\n            item { SectionLabel("Local test") }\n            item { SettingsRow(Icons.Outlined.NotificationsActive, "Send local test notification", "Verify Android permission, channel and preview presentation") { vm.showTestNotification() } }\n'''
    if needle not in s: raise SystemExit('UI anchor missing')
    s = s.replace(needle, replacement, 1)
ui.write_text(s)
