from pathlib import Path
root=Path("/tmp/messenger-build/messenger_live/app/src/main/java/com/example/messengerui")
vm=root/"AppViewModel.kt"
s=vm.read_text()
needle='''    fun startVoiceCall(chatId: String) {
        val chat=chats.firstOrNull { it.id==chatId } ?: return
'''
repl='''    fun startVoiceCall(chatId: String) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(getApplication(), android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            lastToast = "Microphone permission is required for voice calls"
            return
        }
        val chat=chats.firstOrNull { it.id==chatId } ?: return
'''
if needle not in s and repl not in s:
    raise SystemExit("startVoiceCall permission anchor missing")
if needle in s:
    s=s.replace(needle,repl,1)
vm.write_text(s)

p=root/"MessengerApp.kt"
s=p.read_text()
old='''@Composable
private fun CallsTab(vm: AppViewModel) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ListItem(
                headlineContent = { Text("Create call link", fontWeight = FontWeight.Medium) },
                supportingContent = { Text("Share a link for a future call") },
                leadingContent = { Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) { Icon(Icons.Filled.Link, null, modifier = Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.primary) } },
                modifier = Modifier.clickable { vm.lastToast = "Local demo call link copied" }
            )
            Text("Recent", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
        }
        items(vm.calls, key = { it.id }) { call ->
'''
new='''@Composable
private fun CallsTab(vm: AppViewModel) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { Text("Recent", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp)) }
        items(vm.calls, key = { it.id }) { call ->
            val requestRedial = rememberVoiceCallPermissionAction(vm) { vm.redialVoiceCall(call.contactName) }
'''
if old in s:
    s=s.replace(old,new,1)
elif "val requestRedial = rememberVoiceCallPermissionAction" not in s:
    raise SystemExit("CallsTab anchor missing")
s=s.replace('''IconButton(onClick = { if (call.type == CallType.AUDIO) vm.redialVoiceCall(call.contactName) else vm.lastToast = "Video calling is unavailable" }) {''','''IconButton(onClick = { if (call.type == CallType.AUDIO) requestRedial() else vm.lastToast = "Video calling is unavailable" }) {''',1)
old2='''@Composable
private fun ChatInfoScreen(vm: AppViewModel, chatId: String) {
    val chat = vm.chats.firstOrNull { it.id == chatId } ?: return
    SimpleBackScaffold(title = "Chat info", onBack = { vm.back() }) { padding ->
'''
new2='''@Composable
private fun ChatInfoScreen(vm: AppViewModel, chatId: String) {
    val chat = vm.chats.firstOrNull { it.id == chatId } ?: return
    val requestAudioCall = rememberVoiceCallPermissionAction(vm) { vm.startVoiceCall(chatId) }
    SimpleBackScaffold(title = "Chat info", onBack = { vm.back() }) { padding ->
'''
if old2 in s:
    s=s.replace(old2,new2,1)
elif "val requestAudioCall = rememberVoiceCallPermissionAction" not in s:
    raise SystemExit("ChatInfo anchor missing")
s=s.replace('''ActionCard(Icons.Outlined.Call, "Audio") { vm.startVoiceCall(chatId) }''','''ActionCard(Icons.Outlined.Call, "Audio") { requestAudioCall() }''',1)
helper='''@Composable
private fun rememberVoiceCallPermissionAction(vm: AppViewModel, onGranted: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) onGranted()
        else vm.lastToast = "Microphone permission is required for voice calls"
    }
    return {
        val permissions = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
        }.toTypedArray()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) onGranted()
        else launcher.launch(permissions)
    }
}

'''
anchor='''@Composable
private fun CallsTab(vm: AppViewModel) {
'''
if helper not in s:
    if anchor not in s:
        raise SystemExit("permission helper anchor missing")
    s=s.replace(anchor,helper+anchor,1)
p.write_text(s)
