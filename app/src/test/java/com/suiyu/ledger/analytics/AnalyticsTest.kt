package com.suiyu.ledger.analytics

import com.suiyu.ledger.data.DefaultCategories
import com.suiyu.ledger.data.SeedData
import com.suiyu.ledger.model.BillSource
import com.suiyu.ledger.model.Category
import com.suiyu.ledger.model.Member
import com.suiyu.ledger.model.TxRecord
import com.suiyu.ledger.model.TxType
import com.suiyu.ledger.util.Dates
import com.suiyu.ledger.util.MonthKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AnalyticsTest {

    private val members = listOf(
        Member(id = "m1", name = "我", role = "本人", isSelf = true, order = 0),
        Member(id = "m2", name = "爱人", role = "配偶", order = 1),
    )
    private val categories: List<Category> = DefaultCategories.all

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        Dates.fromLocalDate(LocalDate.of(year, month, day), hour, 0)

    private fun record(
        id: String,
        type: TxType,
        cents: Long,
        categoryId: String,
        memberId: String,
        epoch: Long,
    ) = TxRecord(
        id = id,
        type = type,
        amountCents = cents,
        categoryId = categoryId,
        memberId = memberId,
        occurredAt = epoch,
        merchant = "测试商户",
        includeInStats = true,
    )

    private val sample = listOf(
        record("1", TxType.EXPENSE, 6_800_000L, "cat_housing", "m1", at(2026, 11, 5)),
        record("2", TxType.EXPENSE, 320_000L, "cat_food", "m1", at(2026, 11, 8)),
        record("3", TxType.EXPENSE, 180_000L, "cat_food", "m1", at(2026, 11, 9)),
        record("4", TxType.EXPENSE, 120_000L, "cat_shopping", "m2", at(2026, 11, 12)),
        record("5", TxType.EXPENSE, 200_000L, "cat_transport", "m2", at(2026, 11, 15)),
        record("6", TxType.INCOME, 3_200_000L, "cat_salary", "m1", at(2026, 11, 10)),
        record("7", TxType.INCOME, 2_400_000L, "cat_salary", "m2", at(2026, 11, 15)),
        // 上月
        record("8", TxType.EXPENSE, 7_400_000L, "cat_housing", "m1", at(2026, 10, 5)),
        record("9", TxType.EXPENSE, 100_000L, "cat_food", "m1", at(2026, 10, 9)),
        record("10", TxType.EXPENSE, 300_000L, "cat_food", "m2", at(2026, 10, 19)),
        record("11", TxType.INCOME, 3_200_000L, "cat_salary", "m1", at(2026, 10, 10)),
        // 去年同月
        record("12", TxType.EXPENSE, 650_000L, "cat_food", "m1", at(2025, 11, 11)),
        record("13", TxType.EXPENSE, 6_000_000L, "cat_housing", "m1", at(2025, 11, 5)),
        record("14", TxType.INCOME, 2_800_000L, "cat_salary", "m1", at(2025, 11, 10)),
    )

    @Test
    fun `summary totals`() {
        val summary = Analytics.summary(sample, MonthKey(2026, 11))
        assertEquals(7_620_000L, summary.expenseCents)
        assertEquals(5_600_000L, summary.incomeCents)
        assertEquals(-2_020_000L, summary.balanceCents)
        assertEquals(5, summary.expenseCount)
        assertEquals(2, summary.incomeCount)
        assertTrue(summary.savingsRate < 0)
    }

    @Test
    fun `comparison handles month over month and year over year`() {
        val comparison = Analytics.comparison(sample, MonthKey(2026, 11))
        assertEquals(7_620_000L, comparison.current.expenseCents)
        assertEquals(7_800_000L, comparison.previous.expenseCents)
        assertEquals(-180_000L, comparison.expenseMoM.diff)
        assertEquals(-180_000L / 7_800_000.0, comparison.expenseMoM.pct!!, 1e-9)
        assertEquals(6_650_000L, comparison.lastYear.expenseCents)
        assertEquals(970_000L, comparison.expenseYoY.diff)
        assertTrue(comparison.expenseYoY.direction > 0)
    }

    @Test
    fun `comparison with empty base returns null percent`() {
        val comparison = Analytics.comparison(sample, MonthKey(2027, 3))
        assertNull(comparison.expenseMoM.pct)
        assertTrue(!comparison.expenseMoM.hasBase)
        assertTrue(!comparison.expenseYoY.hasBase)
    }

    @Test
    fun `category slices are sorted and ratios sum to one`() {
        val slices = Analytics.categorySlices(sample, categories, MonthKey(2026, 11))
        assertEquals("cat_housing", slices.first().categoryId)
        assertEquals(1.0, slices.sumOf { it.ratio }, 1e-6)
        assertEquals(5, slices.sumOf { it.count })
        val food = slices.first { it.categoryId == "cat_food" }
        assertEquals(500_000L, food.totalCents)
        assertEquals(2, food.count)
    }

    @Test
    fun `category slices can compare with previous month`() {
        val slices = Analytics.categorySlices(
            sample, categories, MonthKey(2026, 11), baseMonth = MonthKey(2026, 10),
        )
        val food = slices.first { it.categoryId == "cat_food" }
        assertEquals(500_000L, food.totalCents)
        assertEquals(400_000L, food.baseCents)
        assertEquals(100_000L, food.change.diff)
    }

    @Test
    fun `member slices share the household expense`() {
        val slices = Analytics.memberSlices(sample, members, MonthKey(2026, 11))
        val totalExpense = slices.sumOf { it.expenseCents }
        assertEquals(7_620_000L, totalExpense)
        assertEquals(1.0, slices.sumOf { it.expenseRatio }, 1e-6)
        val self = slices.first { it.memberId == "m1" }
        assertEquals(7_300_000L, self.expenseCents)
        assertTrue(self.expenseRatio > 0.95)
        assertEquals(3_200_000L, self.incomeCents)
    }

    @Test
    fun `month points fill empty months with zero`() {
        val points = Analytics.monthPoints(sample, MonthKey(2026, 11), 4)
        assertEquals(4, points.size)
        assertEquals(MonthKey(2026, 8), points.first().month)
        assertEquals(0L, points.first().expenseCents)
        assertEquals(MonthKey(2026, 11), points.last().month)
        assertEquals(7_620_000L, points.last().expenseCents)
    }

    @Test
    fun `category changes are sorted by absolute delta`() {
        val changes = Analytics.categoryChanges(sample, categories, MonthKey(2026, 11))
        assertEquals("cat_housing", changes.first().categoryId)
        assertEquals(-600_000L, changes.first().change.diff)
        assertTrue(changes.zipWithNext().all { (a, b) ->
            kotlin.math.abs(a.change.diff) >= kotlin.math.abs(b.change.diff)
        })
    }

    @Test
    fun `health metrics expose savings and fixed cost`() {
        val health = Analytics.healthMetrics(sample, categories, MonthKey(2026, 11))
        assertNotNull(health.largestExpense)
        assertEquals(6_800_000L, health.largestExpense!!.amountCents)
        assertTrue(health.topCategoryRatio > 0.8)
        assertTrue(health.fixedExpenseRatio > 0.8)
        assertEquals(0.0, health.passiveIncomeRatio, 1e-6)
    }

    @Test
    fun `seed demo covers current and last year same month`() {
        val now = Dates.fromLocalDate(LocalDate.of(2026, 11, 21), 12, 0)
        val records = SeedData.demoRecords(now)
        assertTrue(records.size > 300)
        val currentMonth = MonthKey(2026, 11)
        assertTrue(records.any { currentMonth.contains(it.occurredAt) })
        assertTrue(records.any { MonthKey(2025, 11).contains(it.occurredAt) })
        assertTrue(records.all { it.amountCents > 0L })
        assertTrue(records.all { it.memberId in SeedData.members.map { member -> member.id } })
        assertTrue(records.any { it.source != BillSource.MANUAL })
        assertEquals(SeedData.demoRecords(now).size, records.size)
    }
}
