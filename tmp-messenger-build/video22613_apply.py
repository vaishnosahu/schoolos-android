from pathlib import Path
import sys
root=Path(sys.argv[1])

def rep(rel, old, new, n=1):
    p=root/rel
    s=p.read_text()
    if old not in s:
        raise SystemExit(f'missing {rel}: {old[:120]!r}')
    s=s.replace(old,new,n) if n!=-1 else s.replace(old,new)
    p.write_text(s)

rep('app/build.gradle.kts','versionCode = 49','versionCode = 50')
rep('app/build.gradle.kts','versionName = "2.26.12"','versionName = "2.26.13"')

rel='app/src/main/java/com/example/messengerui/WebRtcVoiceEngine.kt'
p=root/rel
s=p.read_text()

s=s.replace('''    @Volatile private var preferredRemoteVideoTrackId: String? = null
    private var lastInboundVideoFramesSeen: Long = 0L
''','',1)
s=s.replace('''                var strongestInboundVideoBytes=-1L
                var strongestInboundVideoTrackId:String?=null
''','',1)
s=s.replace('''                        val trackId=stat.members["trackIdentifier"]?.toString()?.takeIf { it.isNotBlank() }
                        if(trackId!=null && streamBytes>strongestInboundVideoBytes) {
                            strongestInboundVideoBytes=streamBytes
                            strongestInboundVideoTrackId=trackId
                        }
''','',1)
s=s.replace('''                strongestInboundVideoTrackId?.let { trackId ->
                    if(preferredRemoteVideoTrackId!=trackId) {
                        preferredRemoteVideoTrackId=trackId
                        syncRemoteVideoTrackFromTransceivers()
                    }
                }
                if(inboundVideoFrames>lastInboundVideoFramesSeen) reportRemoteVideoAvailable(true)
                lastInboundVideoFramesSeen=inboundVideoFrames
''','',1)
s=s.replace('''        preferredRemoteVideoTrackId=null
''','',1)

old='''        val preferredId=preferredRemoteVideoTrackId
        val preferred=preferredId?.let { wanted -> candidates.firstOrNull { candidate -> remoteVideoTrackId(candidate)==wanted } }
        val currentStillPresent=current!=null && isRemoteVideoTrackUsable(current) && candidates.any { it===current }
        val chosen=preferred ?: current?.takeIf { currentStillPresent } ?: candidates.firstOrNull()
        if(chosen!=null) bindRemoteVideoTrack(chosen)
        else if(current!=null) clearRemoteVideoTrack(current)
'''
new='''        val currentStillPresent=current!=null && isRemoteVideoTrackUsable(current) && candidates.any { it===current }
        val chosen=current?.takeIf { currentStillPresent } ?: candidates.firstOrNull()
        if(chosen!=null) bindRemoteVideoTrack(chosen)
        else if(current!=null) clearRemoteVideoTrack(current)
'''
if old not in s:
    raise SystemExit('sync selection block missing')
s=s.replace(old,new,1)

p.write_text(s)
print('v2.26.13 safe renderer rollback/stabilization applied')
