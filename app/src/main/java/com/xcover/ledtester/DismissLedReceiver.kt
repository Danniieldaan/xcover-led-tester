package com.xcover.ledtester

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DismissLedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == XCoverLedManager.ACTION_DISMISS_LED) {
            XCoverLedManager(context.applicationContext).clearAll()
        }
    }
}
