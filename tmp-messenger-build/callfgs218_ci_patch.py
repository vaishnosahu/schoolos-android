from pathlib import Path
root=Path('/tmp/messenger-build/messenger_live')

p=root/'app/build.gradle.kts'
s=p.read_text().replace('versionCode = 28','versionCode = 29').replace('versionName = "2.17"','versionName = "2.18"')
p.write_text(s)

src=Path('tmp-messenger-build/CallForegroundService218.kt').read_text()
(root/'app/src/main/java/com/example/messengerui/CallForegroundService.kt').write_text(src)

print('call foreground service 2.18 hardening applied')
