from pathlib import Path
root=Path('/tmp/messenger-build/messenger_live')

p=root/'app/build.gradle.kts'
s=p.read_text().replace('versionCode = 35','versionCode = 36').replace('versionName = "2.24"','versionName = "2.25"')
p.write_text(s)

# Fresh incoming-call channel + stronger full-screen lifecycle.
p=root/'app/src/main/java/com/example/messengerui/NotificationHelper.kt'
s=p.read_text()
s=s.replace('const val CALLS_CHANNEL = "calls_v3"','const val CALLS_CHANNEL = "calls_v4"')
# Also normalize if later source already changed the literal differently.
s=s.replace('const val CALLS_CHANNEL = "calls_v2"','const val CALLS_CHANNEL = "calls_v4"')
old='''    fun cleanupLegacyChannels(context:Context) {
        if(Build.VERSION.SDK_INT<26) return
        val manager=context.getSystemService(NotificationManager::class.java)
        listOf("calls","calls_v2").forEach { id -> if(id!=CALLS_CHANNEL) runCatching { manager.deleteNotificationChannel(id) } }
    }'''
new='''    fun cleanupLegacyChannels(context:Context) {
        if(Build.VERSION.SDK_INT<26) return
        val manager=context.getSystemService(NotificationManager::class.java)
        listOf("calls","calls_v2","calls_v3").forEach { id -> if(id!=CALLS_CHANNEL) runCatching { manager.deleteNotificationChannel(id) } }
    }'''
if old in s: s=s.replace(old,new,1)
p.write_text(s)

p=root/'app/src/main/java/com/example/messengerui/IncomingCallActivity.kt'
s=p.read_text()
s=s.replace('''        if (android.os.Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        enableEdgeToEdge()''','''        if (android.os.Build.VERSION.SDK_INT >= 27) {
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
        enableEdgeToEdge()''',1)
p.write_text(s)

# Silence one-time startup sync fallback and retry before surfacing a problem.
p=root/'app/src/main/java/com/example/messengerui/AppViewModel.kt'
s=p.read_text()
literal='Server sync is unavailable. Local data is available'
if literal not in s:
    literal='Server sync is unavailable, local data is available'
if literal not in s:
    raise SystemExit('server sync fallback literal missing')

# Replace the first user-facing assignment/expression containing the literal with a silent bounded retry.
lines=s.splitlines()
idx=next(i for i,l in enumerate(lines) if literal in l)
indent=lines[idx][:len(lines[idx])-len(lines[idx].lstrip())]
# Preserve control flow by replacing just the toast/status line.
lines[idx]=indent+'scheduleStartupSyncRetry()'
s='\n'.join(lines)+'\n'

anchor='''    private fun restartServerSync() {
        realtimeJob?.cancel()
        realtimeJob = null
        startServerSync()
    }
'''
if anchor not in s:
    raise SystemExit('restartServerSync anchor missing')
retry='''    private var startupSyncRetryCount = 0
    private var startupSyncRetryJob: kotlinx.coroutines.Job? = null

    private fun scheduleStartupSyncRetry() {
        if(!serverMode || !networkAvailable) return
        if(startupSyncRetryJob?.isActive==true) return
        if(startupSyncRetryCount>=2) {
            connectionState=SyncConnectionState.OFFLINE
            return
        }
        startupSyncRetryCount++
        startupSyncRetryJob=viewModelScope.launch {
            delay(if(startupSyncRetryCount==1) 900L else 2_000L)
            if(serverMode && networkAvailable) restartServerSync()
        }
    }

'''
s=s.replace(anchor,retry+anchor,1)
# Reset retry counter whenever normal online state is restored.
s=s.replace('connectionState = SyncConnectionState.ONLINE','connectionState = SyncConnectionState.ONLINE; startupSyncRetryCount = 0',1)
p.write_text(s)

print('Messenger 2.25 lockscreen + startup sync hardening applied')
