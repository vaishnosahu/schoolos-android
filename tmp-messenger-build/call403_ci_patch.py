from pathlib import Path
root=Path("/tmp/messenger-build/messenger_live")
api=root/"app/src/main/java/com/example/messengerui/ApiClient.kt"
s=api.read_text()
old='request("GET","calls/config.php",token=token)'
new='request("GET","calls/ice.php",token=token)'
if old not in s:
    raise SystemExit("calls/config.php anchor missing")
api.write_text(s.replace(old,new,1))

build=root/"app/build.gradle.kts"
s=build.read_text().replace("versionCode = 21","versionCode = 22").replace('versionName = "2.10"','versionName = "2.11"')
build.write_text(s)
