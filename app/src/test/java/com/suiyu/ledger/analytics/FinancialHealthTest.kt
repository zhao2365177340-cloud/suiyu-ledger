package com.suiyu.ledger.analytics

import com.suiyu.ledger.data.SeedData
import com.suiyu.ledger.model.Asset
import com.suiyu.ledger.model.AssetCategory
import com.suiyu.ledger.model.EmploymentType
import com.suiyu.ledger.model.FinancialProfile
import com.suiyu.ledger.model.Liability
import com.suiyu.ledger.model.LiabilityCategory
import com.suiyu.ledger.model.TxRecord
import com.suiyu.ledger.model.TxType
import com.suiyu.ledger.util.Dates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FinancialHealthTest {

    private val now = Dates.fromLocalDate(LocalDate.of(2026, 11, 21), 12, 0)

    private fun record(
        id: String,
        type: TxType,
        cents: Long,
        categoryId: String,
        year: Int = 2026,
        month: Int = 11,
        day: Int = 10,
    ) = TxRecord(
        id = id,
        type = type,
        amountCents = cents,
        categoryId = categoryId,
        memberId = "mem_self",
        occurredAt = Dates.fromLocalDate(LocalDate.of(year, month, day), 12, 0),
    )

    private fun salaryRecords(monthlySalaryCents: Long, months: Int = 6): List<TxRecord> = buildList {
        for (offset in 0 until months) {
            val month = 11 - offset
            val year = 2026
            add(record("salary_$offset", TxType.INCOME, monthlySalaryCents, "cat_salary", year, month, 10))
            add(record("rent_$offset", TxType.EXPENSE, 6_800_00L, "cat_housing", year, month, 5))
            add(record("food_$offset", TxType.EXPENSE, 2_000_00L, "cat_food", year, month, 12))
        }
    }

    private fun asset(id: String, category: AssetCategory, valueCents: Long) =
        Asset(id = id, name = id, category = category, valueCents = valueCents)

    private fun liability(id: String, balanceCents: Long, monthly: Long) =
        Liability(id = id, name = id, category = LiabilityCategory.MORTGAGE, balanceCents = balanceCents, monthlyPaymentCents = monthly)

    @Test
    fun `demo seed produces healthy household with correct net worth`() {
        val report = FinancialHealth.evaluate(
            records = SeedData.demoRecords(now),
            assets = SeedData.demoAssets(),
            liabilities = SeedData.demoLiabilities(),
            profile = SeedData.demoProfile(),
            nowMillis = now,
        )
        val expectedAssets = 3_953_000_00L
        val expectedLiabilities = 1_823_500_00L
        assertEquals(expectedAssets, report.totalAssetsCents)
        assertEquals(expectedLiabilities, report.totalLiabilitiesCents)
        assertEquals(expectedAssets - expectedLiabilities, report.netWorthCents)
        assertEquals(418_000_00L, report.liquidAssetsCents)
        assertTrue("应急金月数应大于 6：${report.emergencyMonths}", report.emergencyMonths > 6)
        assertTrue("demo 家庭评分应在健康档：${report.score}", report.score >= 70)
        assertEquals(6, report.dimensions.size)
        assertNotNull(report.advice)
        assertTrue(report.hasAnyData)
        assertTrue(report.hasLedgerData)
    }

    @Test
    fun `no data at all does not crash and reports no assets`() {
        val report = FinancialHealth.evaluate(
            records = emptyList(),
            assets = emptyList(),
            liabilities = emptyList(),
            profile = FinancialProfile(),
            nowMillis = now,
        )
        assertEquals(0L, report.totalAssetsCents)
        assertEquals(0L, report.netWorthCents)
        assertEquals(6, report.dimensions.size)
        assertEquals(6, report.metrics.size)
        assertTrue(!report.hasAnyData)
        assertTrue(!report.hasAssets)
    }

    @Test
    fun `emergency months and low liquidity advice`() {
        val records = salaryRecords(20_000_00L)
        val report = FinancialHealth.evaluate(
            records = records,
            assets = listOf(asset("cash", AssetCategory.CASH, 20_000_00L)),
            liabilities = emptyList(),
            profile = FinancialProfile(employmentType = EmploymentType.GOVERNMENT, jobYears = 6, spouseWorks = true),
            nowMillis = now,
        )
        // 刚性支出 = 房贷 6800（food 不计入 fixed）
        assertEquals(6_800_00L, report.cashflow.monthlyFixedExpenseCents)
        assertEquals(20_000_00.0 / 6_800_00.0, report.emergencyMonths, 1e-6)
        assertTrue("应急金不足应给出建议", report.advice.any { it.title.contains("应急") })
        assertTrue("流动性维度应偏低", report.dimensions.first { it.key == "liquidity" }.score < 70)
    }

    @Test
    fun `manual income and fixed expense override ledger baseline`() {
        val profile = FinancialProfile(
            manualMonthlyIncomeCents = 30_000_00L,
            manualMonthlyFixedExpenseCents = 9_000_00L,
        )
        val report = FinancialHealth.evaluate(
            records = emptyList(),
            assets = listOf(asset("cash", AssetCategory.CASH, 100_000_00L)),
            liabilities = emptyList(),
            profile = profile,
            nowMillis = now,
        )
        assertEquals(30_000_00L, report.cashflow.monthlyFixedIncomeCents)
        assertEquals(9_000_00L, report.cashflow.monthlyFixedExpenseCents)
        assertTrue(report.cashflow.incomeFromManual)
        assertTrue(report.cashflow.expenseFromManual)
        assertEquals(100_000_00.0 / 9_000_00.0, report.emergencyMonths, 1e-6)
        assertEquals(0, report.cashflow.monthsUsed)
    }

    @Test
    fun `high debt lowers score and generates debt advice`() {
        val records = salaryRecords(15_000_00L)
        val report = FinancialHealth.evaluate(
            records = records,
            assets = listOf(
                asset("home", AssetCategory.PROPERTY_HOME, 1_000_000_00L),
                asset("cash", AssetCategory.CASH, 20_000_00L),
            ),
            liabilities = listOf(liability("mortgage", 900_000_00L, 12_000_00L)),
            profile = FinancialProfile(employmentType = EmploymentType.PRIVATE, jobYears = 3),
            nowMillis = now,
        )
        assertEquals(900_000_00.0 / 1_020_000_00.0, report.debtRatio, 1e-6)
        assertTrue("月供收入比应大于 50%", (report.debtServiceRatio ?: 0.0) > 0.5)
        assertTrue("负债维度应偏低：${report.dimensions.first { it.key == "debt" }.score}", report.dimensions.first { it.key == "debt" }.score < 60)
        assertTrue(report.advice.any { it.title.contains("负债") })
        assertTrue("综合评分应显著低于健康线：${report.score}", report.score < 70)
    }

    @Test
    fun `stable government job scores higher than freelance volatile income`() {
        val records = salaryRecords(20_000_00L)
        val stable = FinancialHealth.evaluate(
            records, emptyList(), emptyList(),
            FinancialProfile(employmentType = EmploymentType.GOVERNMENT, jobYears = 8, incomeStability = 3, spouseWorks = true),
            now,
        )
        val freelance = FinancialHealth.evaluate(
            records, emptyList(), emptyList(),
            FinancialProfile(employmentType = EmploymentType.FREELANCE, jobYears = 1, incomeStability = 1, spouseWorks = false),
            now,
        )
        val stableDim = stable.dimensions.first { it.key == "income" }.score
        val freelanceDim = freelance.dimensions.first { it.key == "income" }.score
        assertTrue("稳定工作收入维度应更高：$stableDim vs $freelanceDim", stableDim > freelanceDim)
        assertTrue(stable.score > freelance.score)
    }

    @Test
    fun `diversified assets score higher than single cash pile`() {
        val profile = FinancialProfile(employmentType = EmploymentType.PRIVATE, jobYears = 5)
        val single = FinancialHealth.evaluate(
            emptyList(),
            listOf(asset("cash", AssetCategory.CASH, 500_000_00L)),
            emptyList(), profile, now,
        )
        val diversified = FinancialHealth.evaluate(
            emptyList(),
            listOf(
                asset("cash", AssetCategory.CASH, 100_000_00L),
                asset("deposit", AssetCategory.DEPOSIT, 150_000_00L),
                asset("wealth", AssetCategory.WEALTH, 100_000_00L),
                asset("invest", AssetCategory.INVEST, 100_000_00L),
                asset("home", AssetCategory.PROPERTY_HOME, 50_000_00L),
            ),
            emptyList(), profile, now,
        )
        val singleDim = single.dimensions.first { it.key == "allocation" }.score
        val diversifiedDim = diversified.dimensions.first { it.key == "allocation" }.score
        assertTrue("分散配置得分应更高：$singleDim vs $diversifiedDim", diversifiedDim > singleDim)
    }

    @Test
    fun `insurance gap reduces guarantee dimension`() {
        val noInsurance = FinancialHealth.evaluate(
            emptyList(),
            listOf(asset("cash", AssetCategory.CASH, 300_000_00L)),
            listOf(liability("mortgage", 1_000_000_00L, 8_000_00L)),
            FinancialProfile(
                hasMedicalInsurance = false,
                hasCriticalIllnessInsurance = false,
                hasLifeInsurance = false,
                insuranceCoverageCents = 0L,
                manualMonthlyIncomeCents = 25_000_00L,
                manualMonthlyFixedExpenseCents = 10_000_00L,
            ),
            now,
        )
        val insured = FinancialHealth.evaluate(
            emptyList(),
            listOf(asset("cash", AssetCategory.CASH, 300_000_00L)),
            listOf(liability("mortgage", 1_000_000_00L, 8_000_00L)),
            FinancialProfile(
                hasMedicalInsurance = true,
                hasCriticalIllnessInsurance = true,
                hasLifeInsurance = true,
                insuranceCoverageCents = 800_000_00L,
                manualMonthlyIncomeCents = 25_000_00L,
                manualMonthlyFixedExpenseCents = 10_000_00L,
            ),
            now,
        )
        assertTrue(insured.dimensions.first { it.key == "insurance" }.score >
            noInsurance.dimensions.first { it.key == "insurance" }.score)
        assertTrue(noInsurance.advice.any { it.title.contains("保障") })
    }

    @Test
    fun `share text contains core figures`() {
        val report = FinancialHealth.evaluate(
            records = salaryRecords(20_000_00L),
            assets = SeedData.demoAssets(),
            liabilities = SeedData.demoLiabilities(),
            profile = SeedData.demoProfile(),
            nowMillis = now,
        )
        val text = FinancialHealth.toShareText(report, "我家")
        assertTrue(text.contains("家庭财务体检"))
        assertTrue(text.contains("净资产"))
        assertTrue(text.contains("六维评分"))
        assertTrue(text.contains("建议"))
    }
}
