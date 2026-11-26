package com.suiyu.ledger

import android.content.Context
import com.suiyu.ledger.data.LedgerRepository
import com.suiyu.ledger.ui.haptics.Haptics

/**
 * 极简依赖入口：单例仓库 + 触感。之所以不引入 DI 框架，
 * 是为了让构建链路短、启动快，也便于在 3000 行内的小项目里保持可读。
 */
object AppGraph {

    lateinit var appContext: Context
        private set

    lateinit var ledger: LedgerRepository
        private set

    val isReady: Boolean
        get() = ::ledger.isInitialized

    fun init(context: Context) {
        if (::ledger.isInitialized) return
        appContext = context.applicationContext
        Haptics.init(appContext)
        ledger = LedgerRepository(appContext)
        val settings = ledger.settings.value
        Haptics.setEnabled(settings.hapticsEnabled)
        Haptics.setStrength(settings.hapticStrength)
    }
}
