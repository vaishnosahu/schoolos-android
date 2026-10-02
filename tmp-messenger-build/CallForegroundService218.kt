package com.example.messengerui

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

class CallForegroundService : Service() {
    private var promoted = false

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        promoteImmediately("starting", "Messenger call")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val callId = intent?.getStringExtra(EXTRA_CALL_ID).orEmpty()
        val peer = intent?.getStringExtra(EXTRA_PEER_NAME).orEmpty().ifBlank { "Messenger call" }

        if (callId.isBlank()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val notification = NotificationHelper.buildOngoingCall(this, callId, peer)
        ServiceCompat.startForeground(
            this,
            NotificationHelper.ongoingCallNotificationId(callId),
            notification,
            foregroundServiceType()
        )
        promoted = true
        return START_NOT_STICKY
    }

    private fun promoteImmediately(callId: String, peerName: String) {
        if (promoted) return
        val notification = NotificationHelper.buildOngoingCall(this, callId, peerName)
        ServiceCompat.startForeground(
            this,
            NotificationHelper.ongoingCallNotificationId(callId),
            notification,
            foregroundServiceType()
        )
        promoted = true
    }

    private fun foregroundServiceType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0

    companion object {
        private const val EXTRA_CALL_ID = "call_id"
        private const val EXTRA_PEER_NAME = "peer_name"

        fun start(context: Context, callId: String, peerName: String) {
            if (callId.isBlank()) return
            val intent = Intent(context, CallForegroundService::class.java)
                .putExtra(EXTRA_CALL_ID, callId)
                .putExtra(EXTRA_PEER_NAME, peerName)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CallForegroundService::class.java))
        }
    }
}
