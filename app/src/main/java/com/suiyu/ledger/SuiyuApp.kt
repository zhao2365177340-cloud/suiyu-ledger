package com.suiyu.ledger

import android.app.Application
import com.suiyu.ledger.AppGraph

class SuiyuApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
    }

    companion object {
        lateinit var instance: SuiyuApp
            private set
    }
}
