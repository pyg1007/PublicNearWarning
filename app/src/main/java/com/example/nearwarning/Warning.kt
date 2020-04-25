package com.example.nearwarning

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri

class Warning : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // This method is called when the BroadcastReceiver is receiving an Intent broadcast.
        val uri : Uri? = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val ringtone = RingtoneManager.getRingtone(context, uri)
        if (intent.action!! == "Near"){
            if (!ringtone.isPlaying)
                ringtone.play()
        }else if (intent.action!! == "Far"){
            if (ringtone.isPlaying)
                ringtone.stop()
        }
    }
}
