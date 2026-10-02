package com.example.messengerui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CallEnd
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.messengerui.ui.theme.MessengerTheme

class IncomingCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        enableEdgeToEdge()
        val callId=intent.getStringExtra(EXTRA_CALL_ID).orEmpty()
        if(callId.isBlank()){ finish(); return }
        val callerName=intent.getStringExtra(EXTRA_CALLER_NAME).orEmpty().ifBlank { "Messenger caller" }
        val callerPhone=intent.getStringExtra(EXTRA_CALLER_PHONE).orEmpty()
        setContent {
            MessengerTheme {
                IncomingCallSurface(callerName,callerPhone,onAnswer={
                    val open=Intent(this,MainActivity::class.java).apply {
                        action="com.example.messengerui.ANSWER_CALL"
                        flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("call_id",callId); putExtra("incoming_call",true); putExtra("call_action","answer")
                    }
                    startActivity(open); finish()
                },onDecline={
                    sendBroadcast(Intent(this,CallNotificationActionReceiver::class.java).apply {
                        action=CallNotificationActionReceiver.ACTION_DECLINE
                        putExtra(CallNotificationActionReceiver.EXTRA_CALL_ID,callId)
                    })
                    finish()
                })
            }
        }
    }

    @Composable
    private fun IncomingCallSurface(callerName:String,callerPhone:String,onAnswer:()->Unit,onDecline:()->Unit){
        Column(
            modifier=Modifier.fillMaxSize().padding(horizontal=28.dp,vertical=56.dp),
            horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.Center
        ){
            Text("Incoming voice call",style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            Text(callerName,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
            if(callerPhone.isNotBlank()) Text(callerPhone,style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(48.dp))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)){
                OutlinedButton(onClick=onDecline,modifier=Modifier.weight(1f)){
                    Icon(Icons.Outlined.CallEnd,null); Spacer(Modifier.padding(4.dp)); Text("Decline")
                }
                Button(onClick=onAnswer,modifier=Modifier.weight(1f)){
                    Icon(Icons.Outlined.Call,null); Spacer(Modifier.padding(4.dp)); Text("Answer")
                }
            }
        }
    }

    companion object {
        const val EXTRA_CALL_ID="call_id"
        const val EXTRA_CALLER_NAME="caller_name"
        const val EXTRA_CALLER_PHONE="caller_phone"
    }
}
