package com.iporadar.app

import android.app.Application
import com.iporadar.app.di.ServiceLocator
import com.iporadar.app.notif.IpoSyncWorker
import com.iporadar.app.notif.Notifier

class IpoRadarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        Notifier.ensureChannel(this)
        IpoSyncWorker.schedule(this)
    }
}
