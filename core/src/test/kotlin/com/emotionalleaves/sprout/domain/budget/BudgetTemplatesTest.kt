package com.emotionalleaves.sprout.domain.budget

import com.emotionalleaves.sprout.domain.budget.BudgetTemplates.Input
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetTemplatesTest {
    private val cats = listOf(
        Input(1, "rent_housing", 100_000), Input(2, "groceries", 50_000),
        Input(3, "eating_out", 20_000), Input(4, "shopping", 0), Input(5, null, 9_000), Input(6, "income", 0),
    )

    @Test fun splitsByRecentSpendingAndNeverExceeds() {
        val b = BudgetTemplates.fiftyThirtyTwenty(300_000, cats)
        // Needs pot 150,000 split 2:1; wants pot 90,000 all to Eating Out (Shopping had no spending).
        assertEquals(100_000L, b[1]); assertEquals(50_000L, b[2]); assertEquals(90_000L, b[3]); assertEquals(0L, b[4])
        assertTrue(5L !in b && 6L !in b)
        assertTrue(b.values.sum() <= 240_000)
    }

    @Test fun equalSplitWithoutHistoryAndNoIncome() {
        val b = BudgetTemplates.fiftyThirtyTwenty(100_000, listOf(Input(1, "rent_housing", 0), Input(2, "groceries", 0)))
        assertEquals(25_000L, b[1]); assertEquals(25_000L, b[2])
        assertTrue(BudgetTemplates.fiftyThirtyTwenty(0, cats).isEmpty())
    }
}
