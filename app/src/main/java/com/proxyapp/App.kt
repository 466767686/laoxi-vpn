package com.proxyapp

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashCatcher.init(this)
        LogHelper.init(this)
    }
}
