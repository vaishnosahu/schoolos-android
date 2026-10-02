from pathlib import Path
root=Path('/tmp/messenger-build/messenger_live')

p=root/'app/build.gradle.kts'
s=p.read_text().replace('versionCode = 35','versionCode = 36').replace('versionName = "2.24"','versionName = "2.25"')
p.write_text(s)

p=root/'app/src/main/java/com/example/messengerui/NotificationHelper.kt'
s=p.read_text().replace('const val CALLS_CHANNEL = "calls_v3"','const val CALLS_CHANNEL = "calls_v4"').replace('const val CALLS_CHANNEL = "calls_v2"','const val CALLS_CHANNEL = "calls_v4"')
s=s.replace('listOf("calls","calls_v2").forEach','listOf("calls","calls_v2","calls_v3").forEach')
p.write_text(s)

p=root/'app/src/main/java/com/example/messengerui/IncomingCallActivity.kt'
s=p.read_text()
old='''        if (android.os.Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        enableEdgeToEdge()'''
new='''        if (android.os.Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()'''
if old not in s: raise SystemExit('incoming activity window anchor missing')
s=s.replace(old,new,1)
p.write_text(s)


# Startup sync: keep local data visible, silently retry transient first failures.
p=root/'app/src/main/java/com/example/messengerui/AppViewModel.kt'
s=p.read_text()
import re
s,count=re.subn(
    r'lastToast=error\.userMessage\("Server sync unavailable; local data is still available"\)',
    'scheduleStartupSyncRetry()',
    s,
    count=1
)
if count!=1: raise SystemExit('server sync fallback expression missing')
anchor='''    private fun restartServerSync() {
        realtimeJob?.cancel()
        realtimeJob = null
        startServerSync()
    }
'''
retry='''    private var startupSyncRetryCount = 0
    private var startupSyncRetryJob: kotlinx.coroutines.Job? = null

    private fun scheduleStartupSyncRetry() {
        if(!serverMode || !networkAvailable) return
        if(startupSyncRetryJob?.isActive==true) return
        if(startupSyncRetryCount>=2) return
        startupSyncRetryCount++
        startupSyncRetryJob=viewModelScope.launch {
            delay(if(startupSyncRetryCount==1) 900L else 2_000L)
            if(serverMode && networkAvailable) restartServerSync()
        }
    }

'''
if anchor not in s: raise SystemExit('restartServerSync anchor missing')
s=s.replace(anchor,retry+anchor,1)
s=s.replace('''                if (failures == 0) connectionState = SyncConnectionState.ONLINE''','''                if (failures == 0) { connectionState = SyncConnectionState.ONLINE; startupSyncRetryCount = 0 }''',1)
p.write_text(s)

print('SYNC_TEXT_CANDIDATES_START')
for kt in (root/'app/src/main/java/com/example/messengerui').rglob('*.kt'):
    for no,line in enumerate(kt.read_text().splitlines(),1):
        low=line.lower()
        if 'local data' in low or 'server sync' in low or ('sync' in low and 'unavailable' in low):
            print(f"{kt.name}:{no}:{line.strip()}")
print('SYNC_TEXT_CANDIDATES_END')

print('Messenger 2.25 lockscreen channel hardening applied')
