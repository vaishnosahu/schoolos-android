from pathlib import Path
p=Path("/tmp/messenger-build/app/src/main/java/com/example/messengerui/WebRtcVoiceEngine.kt")
s=p.read_text()
old='''private object MessengerWebRtcRuntime {
    @Volatile private var initialized = false
    fun ensureInitialized(context: Context) {
        if(initialized) return
        synchronized(this) {
            if(initialized) return
            PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context.applicationContext).createInitializationOptions())
            initialized = true
        }
    }
}

'''
if old not in s: raise SystemExit("runtime wrapper missing")
s=s.replace(old,"",1)
needle='MessengerWebRtcRuntime.ensureInitialized(appContext)'
if s.count(needle)!=1: raise SystemExit("init call mismatch")
s=s.replace(needle,'PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(appContext).createInitializationOptions())',1)
p.write_text(s)
print("WEBRTC_INIT_ROLLBACK_APPLIED")
