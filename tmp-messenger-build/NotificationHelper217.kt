package com.example.messengerui

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object NotificationHelper {
    private const val MESSAGES_CHANNEL = "messages"
    private const val GROUPS_CHANNEL = "group_messages"
    private const val STATUS_CHANNEL = "status_updates"
    const val CALLS_CHANNEL = "calls_v3"
    private const val MISSED_CALLS_CHANNEL = "missed_calls"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        cleanupLegacyChannels(manager)
        manager.createNotificationChannel(NotificationChannel(MESSAGES_CHANNEL, "Messages", NotificationManager.IMPORTANCE_HIGH).apply { description = "Direct message alerts" })
        manager.createNotificationChannel(NotificationChannel(GROUPS_CHANNEL, "Group messages", NotificationManager.IMPORTANCE_HIGH).apply { description = "Group message alerts" })
        manager.createNotificationChannel(NotificationChannel(STATUS_CHANNEL, "Status updates", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Optional status/story alerts" })
        manager.createNotificationChannel(NotificationChannel(CALLS_CHANNEL, "Incoming calls", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Incoming Messenger calls"
            enableVibration(true)
            vibrationPattern = longArrayOf(0,700,450,700,450,700)
            val ringtone = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
            setSound(ringtone, android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        })
        manager.createNotificationChannel(NotificationChannel(MISSED_CALLS_CHANNEL, "Missed calls", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Missed Messenger calls"
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        })
    }

    fun showMessage(context: Context, conversationId: String, title: String, body: String, isGroup: Boolean, unreadCount: Int = 0): Boolean {
        if (!canNotify(context)) return false
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("chat_id", conversationId)
        }
        val pi = PendingIntent.getActivity(context, conversationId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val channel = if (isGroup) GROUPS_CHANNEL else MESSAGES_CHANNEL
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body)).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(pi).setAutoCancel(true).setOnlyAlertOnce(false).setGroup("conversation:$conversationId")
            .apply { if (unreadCount > 0) setNumber(unreadCount) }.build()
        NotificationManagerCompat.from(context).notify(messageNotificationId(conversationId), notification)
        return true
    }

    fun showStatus(context: Context, title: String, body: String, statusId: String): Boolean {
        if (!canNotify(context)) return false
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_updates", true)
        }
        val pi = PendingIntent.getActivity(context, ("status:$statusId").hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, STATUS_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body)).setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(pi).setAutoCancel(true).build()
        NotificationManagerCompat.from(context).notify(("status:$statusId").hashCode(), notification)
        return true
    }

    fun showIncomingCall(context:Context,callId:String,callerName:String):Boolean {
        if(!canNotify(context) || callId.isBlank()) return false
        if(CallAlertStore.isTerminal(context,callId)) return false
        createChannels(context)
        val openIntent=Intent(context,MainActivity::class.java).apply {
            flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("call_id",callId); putExtra("incoming_call",true)
        }
        val fullScreenPi=PendingIntent.getActivity(context,("call:$callId").hashCode(),openIntent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val answerIntent=Intent(context,MainActivity::class.java).apply {
            flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("call_id",callId); putExtra("incoming_call",true); putExtra("call_action","answer")
        }
        val answerPi=PendingIntent.getActivity(context,("answer:$callId").hashCode(),answerIntent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val declineIntent=Intent(context,CallNotificationActionReceiver::class.java).apply {
            action=CallNotificationActionReceiver.ACTION_DECLINE
            putExtra(CallNotificationActionReceiver.EXTRA_CALL_ID,callId)
        }
        val declinePi=PendingIntent.getBroadcast(context,("decline:$callId").hashCode(),declineIntent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alreadyShown=CallAlertStore.wasIncomingRecentlyShown(context,callId)
        val ringtone=android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
        val fullScreenAllowed=CallPushDiagnostics.fullScreenAllowed(context)
        val builder=NotificationCompat.Builder(context,CALLS_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(callerName).setContentText("Incoming voice call")
            .setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setContentIntent(fullScreenPi).setOngoing(true)
            .setAutoCancel(false).setOnlyAlertOnce(alreadyShown).setTimeoutAfter(45_000).setSound(ringtone)
            .setVibrate(longArrayOf(0,700,450,700,450,700))
            .addAction(R.drawable.ic_notification,"Decline",declinePi).addAction(R.drawable.ic_notification,"Answer",answerPi)
        if(fullScreenAllowed) builder.setFullScreenIntent(fullScreenPi,true)
        NotificationManagerCompat.from(context).notify(incomingCallNotificationId(callId),builder.build())
        CallAlertStore.markIncomingShown(context,callId)
        CallPushDiagnostics.recordNotificationShown(context,fullScreenAllowed)
        return true
    }

    fun cancelIncomingCall(context:Context,callId:String){
        NotificationManagerCompat.from(context).cancel(incomingCallNotificationId(callId))
        CallAlertStore.clearIncoming(context,callId)
    }

    fun markCallTerminal(context:Context,callId:String){
        cancelIncomingCall(context,callId)
        CallAlertStore.markTerminal(context,callId)
    }

    fun showMissedCall(context:Context,callId:String,callerName:String):Boolean {
        if(!canNotify(context) || callId.isBlank()) return false
        markCallTerminal(context,callId)
        val openIntent=Intent(context,MainActivity::class.java).apply {
            flags=Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_calls",true)
        }
        val openPi=PendingIntent.getActivity(context,("missed:$callId").hashCode(),openIntent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val dismissIntent=Intent(context,CallNotificationActionReceiver::class.java).apply {
            action=CallNotificationActionReceiver.ACTION_DISMISS_MISSED
            putExtra(CallNotificationActionReceiver.EXTRA_CALL_ID,callId)
        }
        val dismissPi=PendingIntent.getBroadcast(context,("dismiss-missed:$callId").hashCode(),dismissIntent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n=NotificationCompat.Builder(context,MISSED_CALLS_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("Missed call").setContentText(callerName)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setContentIntent(openPi).setAutoCancel(true)
            .setOnlyAlertOnce(true).addAction(R.drawable.ic_notification,"Dismiss",dismissPi).build()
        NotificationManagerCompat.from(context).notify(missedCallNotificationId(callId),n)
        return true
    }

    fun cancelMissedCall(context:Context,callId:String){ NotificationManagerCompat.from(context).cancel(missedCallNotificationId(callId)) }
    fun incomingCallNotificationId(callId:String):Int=("incoming-call:$callId").hashCode()
    fun missedCallNotificationId(callId:String):Int=("missed-call:$callId").hashCode()
    fun ongoingCallNotificationId(callId:String):Int=("ongoing-call:$callId").hashCode()

    fun buildOngoingCall(context:Context,callId:String,peerName:String):android.app.Notification {
        val intent=Intent(context,MainActivity::class.java).apply{flags=Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP;putExtra("call_id",callId)}
        val pi=PendingIntent.getActivity(context,("ongoing:$callId").hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context,CALLS_CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(peerName).setContentText("Voice call in progress").setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setOngoing(true).setContentIntent(pi).build()
    }

    fun cancelConversation(context: Context, conversationId: String) {
        NotificationManagerCompat.from(context).cancel(messageNotificationId(conversationId))
    }

    fun cancelAll(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
    }

    fun showLocalMessageTest(context: Context, title: String, body: String): Boolean =
        showMessage(context, "local-test", title, body, false)

    fun showServerPushTest(context: Context, title: String, body: String): Boolean {
        if (!canNotify(context)) return false
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(context, 0x4D5347, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, MESSAGES_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body)).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS).setContentIntent(pi).setAutoCancel(true).build()
        NotificationManagerCompat.from(context).notify(0x4D5347, notification)
        return true
    }

    private fun cleanupLegacyChannels(manager: NotificationManager) {
        listOf("calls", "calls_v2").forEach { legacy ->
            if (legacy != CALLS_CHANNEL) runCatching { manager.deleteNotificationChannel(legacy) }
        }
    }

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun messageNotificationId(conversationId: String): Int = ("message:$conversationId").hashCode()
}
