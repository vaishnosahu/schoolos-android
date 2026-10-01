from pathlib import Path
p=Path("/tmp/messenger-build/messenger_live/app/src/main/java/com/example/messengerui/MessengerApp.kt")
s=p.read_text()
s=s.replace(
    "Text(formatDuration(vm.voiceRecordingElapsedMs), style = MaterialTheme.typography.labelSmall)",
    "Text(formatVoiceRecordingDuration(vm.voiceRecordingElapsedMs), style = MaterialTheme.typography.labelSmall)"
)
if "private fun formatVoiceRecordingDuration" not in s:
    s += """\n\nprivate fun formatVoiceRecordingDuration(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}
"""
p.write_text(s)
