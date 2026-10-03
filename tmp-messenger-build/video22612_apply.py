from pathlib import Path
import sys

root=Path(sys.argv[1])

def rep(rel, old, new, n=1):
    p=root/rel
    s=p.read_text()
    if old not in s:
        raise SystemExit(f"missing {rel}: {old[:100]!r}")
    s=s.replace(old,new,n) if n!=-1 else s.replace(old,new)
    p.write_text(s)

rep("app/build.gradle.kts",'versionCode = 48','versionCode = 49')
rep("app/build.gradle.kts",'versionName = "2.26.11"','versionName = "2.26.12"')

rel="app/src/main/java/com/example/messengerui/WebRtcVoiceEngine.kt"
p=root/rel
s=p.read_text()

s=s.replace(
'''    private var remoteVideoSink: VideoSink? = null
    private var usingFrontCamera = true
''',
'''    private var remoteVideoSink: VideoSink? = null
    private var remoteVideoSinkTrack: VideoTrack? = null
    private var remoteFrameMonitorTrack: VideoTrack? = null
    @Volatile private var preferredRemoteVideoTrackId: String? = null
    private var lastInboundVideoFramesSeen: Long = 0L
    private var usingFrontCamera = true
''',1)

s=s.replace(
'''            val previousSink=remoteVideoSink
            val currentTrack=remoteVideoTrack
            if(previousSink !== sink){
                if(previousSink!=null && isRemoteVideoTrackUsable(currentTrack)) {
                    runCatching { currentTrack?.removeSink(previousSink) }
                }
                remoteVideoSink=sink
            }
''',
'''            val previousSink=remoteVideoSink
            if(previousSink !== sink){
                if(previousSink!=null) {
                    remoteVideoSinkTrack?.let { bound -> runCatching { bound.removeSink(previousSink) } }
                }
                remoteVideoSinkTrack=null
                remoteVideoSink=sink
            }
''',1)

s=s.replace(
'''                var inboundVideoFrames=0L
                var outboundVideoFrames=0L
                values.forEach { stat ->
''',
'''                var inboundVideoFrames=0L
                var outboundVideoFrames=0L
                var strongestInboundVideoBytes=-1L
                var strongestInboundVideoTrackId:String?=null
                values.forEach { stat ->
''',1)

s=s.replace(
'''                    if(mediaKind=="video" && stat.type=="inbound-rtp") {
                        inboundVideoBytes += (stat.members["bytesReceived"] as? Number)?.toLong() ?: 0L
                        inboundVideoFrames += ((stat.members["framesDecoded"] ?: stat.members["framesReceived"]) as? Number)?.toLong() ?: 0L
                    }
''',
'''                    if(mediaKind=="video" && stat.type=="inbound-rtp") {
                        val streamBytes=(stat.members["bytesReceived"] as? Number)?.toLong() ?: 0L
                        inboundVideoBytes += streamBytes
                        inboundVideoFrames += ((stat.members["framesDecoded"] ?: stat.members["framesReceived"]) as? Number)?.toLong() ?: 0L
                        val trackId=stat.members["trackIdentifier"]?.toString()?.takeIf { it.isNotBlank() }
                        if(trackId!=null && streamBytes>strongestInboundVideoBytes) {
                            strongestInboundVideoBytes=streamBytes
                            strongestInboundVideoTrackId=trackId
                        }
                    }
''',1)

needle='''                val outboundVideoKbps=if(lastStatsAtMs>0L && outboundVideoBytes>=lastOutboundVideoBytes)(((outboundVideoBytes-lastOutboundVideoBytes).toDouble()*8.0)/deltaMs).toInt().coerceAtLeast(0) else null
'''
if needle not in s: raise SystemExit("video bitrate marker missing")
s=s.replace(needle,needle+'''                strongestInboundVideoTrackId?.let { trackId ->
                    if(preferredRemoteVideoTrackId!=trackId) {
                        preferredRemoteVideoTrackId=trackId
                        syncRemoteVideoTrackFromTransceivers()
                    }
                }
                if(inboundVideoFrames>lastInboundVideoFramesSeen) reportRemoteVideoAvailable(true)
                lastInboundVideoFramesSeen=inboundVideoFrames
''',1)

s=s.replace(
'''        remoteVideoSink=null
        remoteVideoTrack=null
        reportRemoteVideoAvailable(false)
''',
'''        remoteVideoSink=null
        remoteVideoSinkTrack=null
        remoteFrameMonitorTrack=null
        preferredRemoteVideoTrackId=null
        remoteVideoTrack=null
        reportRemoteVideoAvailable(false)
''',1)

s=s.replace(
'''    private fun detachRemoteVideoTrack(track:VideoTrack?) {
        if(track==null) return
        remoteVideoSink?.let { sink -> runCatching { track.removeSink(sink) } }
        runCatching { track.removeSink(remoteFrameMonitor) }
    }
''',
'''    private fun detachRemoteVideoTrack(track:VideoTrack?) {
        if(track==null) return
        if(remoteVideoSinkTrack===track) {
            remoteVideoSink?.let { sink -> runCatching { track.removeSink(sink) } }
            remoteVideoSinkTrack=null
        }
        if(remoteFrameMonitorTrack===track) {
            runCatching { track.removeSink(remoteFrameMonitor) }
            remoteFrameMonitorTrack=null
        }
    }
''',1)

s=s.replace(
'''    private fun attachRemoteTrackSinks(track:VideoTrack):Boolean {
        if(!isRemoteVideoTrackUsable(track)) return false
        runCatching { track.removeSink(remoteFrameMonitor) }
        if(runCatching { track.addSink(remoteFrameMonitor) }.isFailure) return false
        remoteVideoSink?.let { sink ->
            runCatching { track.removeSink(sink) }
            if(runCatching { track.addSink(sink) }.isFailure){
                runCatching { track.removeSink(remoteFrameMonitor) }
                return false
            }
        }
        return true
    }
''',
'''    private fun attachRemoteTrackSinks(track:VideoTrack):Boolean {
        if(!isRemoteVideoTrackUsable(track)) return false
        if(remoteFrameMonitorTrack!==track) {
            remoteFrameMonitorTrack?.let { old -> runCatching { old.removeSink(remoteFrameMonitor) } }
            if(runCatching { track.addSink(remoteFrameMonitor) }.isFailure) return false
            remoteFrameMonitorTrack=track
        }
        remoteVideoSink?.let { sink ->
            if(remoteVideoSinkTrack!==track) {
                remoteVideoSinkTrack?.let { old -> runCatching { old.removeSink(sink) } }
                if(runCatching { track.addSink(sink) }.isFailure) return false
                remoteVideoSinkTrack=track
            }
        }
        return true
    }
''',1)

s=s.replace(
'''        val now=System.currentTimeMillis()
        val currentFresh=current!=null && isRemoteVideoTrackUsable(current) && lastRemoteVideoFrameAtMs>0L && now-lastRemoteVideoFrameAtMs<2_500L
        val replacement=candidates.firstOrNull { it !== current }
        when {
            currentFresh -> bindRemoteVideoTrack(current)
            replacement!=null -> bindRemoteVideoTrack(replacement)
            isRemoteVideoTrackUsable(current) -> bindRemoteVideoTrack(current)
            else -> {
                if(current!=null) clearRemoteVideoTrack(current)
                candidates.firstOrNull()?.let(::bindRemoteVideoTrack)
            }
        }
''',
'''        val preferredId=preferredRemoteVideoTrackId
        val preferred=preferredId?.let { wanted -> candidates.firstOrNull { candidate -> remoteVideoTrackId(candidate)==wanted } }
        val currentStillPresent=current!=null && isRemoteVideoTrackUsable(current) && candidates.any { it===current }
        val chosen=preferred ?: current?.takeIf { currentStillPresent } ?: candidates.firstOrNull()
        if(chosen!=null) bindRemoteVideoTrack(chosen)
        else if(current!=null) clearRemoteVideoTrack(current)
''',1)

p.write_text(s)
print("v2.26.12 renderer ownership transform applied")
