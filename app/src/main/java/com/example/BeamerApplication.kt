package com.example

import android.app.Application

class BeamerApplication : Application() {
    lateinit var container: BeamerAppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = BeamerAppContainer(this)
    }

    override fun onTerminate() {
        super.onTerminate()
        container.externalDisplayController.release()
    }
}
