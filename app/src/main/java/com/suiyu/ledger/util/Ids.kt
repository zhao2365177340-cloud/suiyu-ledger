package com.suiyu.ledger.util

import java.util.UUID

/** 生成短 id：前缀 + 8 位随机串，本地账本足够用，且便于日志排查 */
fun newId(prefix: String): String = prefix + "_" + UUID.randomUUID().toString().replace("-", "").take(8)
