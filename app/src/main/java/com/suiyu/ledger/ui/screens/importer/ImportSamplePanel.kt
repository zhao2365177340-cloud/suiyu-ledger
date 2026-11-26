package com.suiyu.ledger.ui.screens.importer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.suiyu.ledger.model.Category
import com.suiyu.ledger.ocr.BillSamples
import com.suiyu.ledger.ocr.SampleReport
import com.suiyu.ledger.ui.components.SyButton
import com.suiyu.ledger.ui.components.SyButtonStyle
import com.suiyu.ledger.ui.components.SyCard
import com.suiyu.ledger.ui.components.SySectionHeader
import com.suiyu.ledger.ui.haptics.HapticEffect
import com.suiyu.ledger.ui.haptics.Haptics
import com.suiyu.ledger.ui.theme.SyTheme

/**
 * 「样例自测」折叠区：把内置的 12 条真实格式样例现场跑一遍，显示通过 / 失败。
 *
 * 为什么放在导入页：用户对截图记账最大的疑虑是「识别准不准」。
 * 让解析规则可以被当场验证（而不是只写在文档里），比一句「识别率 95%」更可信；
 * 自测同样只在本机运行，不联网。
 */
@Composable
fun SampleSelfTestSection(categories: List<Category>) {
    var expanded by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<SampleReport?>(null) }

    SySectionHeader(
        title = "样例自测",
        subtitle = "内置 ${BillSamples.all.size} 条真实格式样例，验证解析规则",
        trailing = if (expanded) "收起" else "展开",
        onTrailingClick = {
            Haptics.play(HapticEffect.Select)
            expanded = !expanded
        },
    )
    if (!expanded) return

    SyCard {
        report?.let { result ->
            val xm = SyTheme.colors
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (result.allPassed) "全部通过" else "存在失败项",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (result.allPassed) xm.income else xm.expense,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${result.passedCount} / ${result.total}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            result.results.forEach { item ->
                Row(modifier = Modifier.padding(vertical = 3.dp)) {
                    Text(
                        text = if (item.passed) "✓" else "✗",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (item.passed) xm.income else xm.expense,
                        modifier = Modifier.width(20.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (!item.passed) {
                            Text(
                                text = item.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = xm.expense,
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        } ?: run {
            Text(
                text = "样例覆盖微信账单详情 / 收支明细、支付宝账单详情 / 列表、银行 App 明细、银行短信、退款与红包收入。点下方按钮即可在本机跑完并查看结果。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
        SyButton(
            text = if (report == null) "运行样例自测" else "重新运行",
            onClick = {
                Haptics.play(HapticEffect.Confirm)
                report = BillSamples.runSelfTest(categories)
            },
            style = SyButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
