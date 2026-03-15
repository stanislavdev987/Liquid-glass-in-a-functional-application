package com.liquidmusicglass.engine

import android.app.Service
import android.content.Intent
import android.os.IBinder

class AudioService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}