from pathlib import Path

root = Path("/tmp/messenger-build/messenger_live/app/src/main/java/com/example/messengerui")
api = root / "ApiClient.kt"
s = api.read_text()
needle = '    fun muteStatusOwner(token: String, ownerId: String, muted: Boolean) {\n'
if 'fun parseStatusPayload(' not in s:
    if needle not in s:
        raise SystemExit("ApiClient status parser anchor missing")
    s = s.replace(needle, '    fun parseStatusPayload(value: JSONObject): RemoteStatus = value.toRemoteStatus()\n\n' + needle, 1)
api.write_text(s)

vm = root / "AppViewModel.kt"
s = vm.read_text()
old = 'event.payload.optJSONObject("status")?.toRemoteStatus() ?: return'
new = 'event.payload.optJSONObject("status")?.let(api::parseStatusPayload) ?: return'
if old in s:
    s = s.replace(old, new, 1)
elif new not in s:
    raise SystemExit("AppViewModel status parser call anchor missing")
vm.write_text(s)
