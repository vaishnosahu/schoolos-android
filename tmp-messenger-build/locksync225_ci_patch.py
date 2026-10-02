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

print('SYNC_TEXT_CANDIDATES_START')
for kt in (root/'app/src/main/java/com/example/messengerui').rglob('*.kt'):
    for no,line in enumerate(kt.read_text().splitlines(),1):
        low=line.lower()
        if 'local data' in low or 'server sync' in low or ('sync' in low and 'unavailable' in low):
            print(f"{kt.name}:{no}:{line.strip()}")
print('SYNC_TEXT_CANDIDATES_END')

print('Messenger 2.25 lockscreen channel hardening applied')
