package com.pfm.app

import android.app.Application
import com.pfm.app.data.AppDatabase
import com.pfm.app.data.FinanceRepository
import com.pfm.app.work.Notifier
import com.pfm.app.work.Scheduler

class PfmApp : Application() {
    val db by lazy { AppDatabase.get(this) }
    val repo by lazy { FinanceRepository(this, db) }

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        Scheduler.scheduleAll(this)
    }
}
