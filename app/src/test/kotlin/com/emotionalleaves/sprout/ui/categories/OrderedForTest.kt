package com.emotionalleaves.sprout.ui.categories

import com.emotionalleaves.sprout.data.db.CategoryEntity
import com.emotionalleaves.sprout.domain.categorize.CategoryKind
import org.junit.Assert.assertEquals
import org.junit.Test

class OrderedForTest {
    private fun c(id: Long, kind: CategoryKind) = CategoryEntity(id, null, "c$id", kind, "custom", 0, id.toInt(), false)
    private val list = listOf(c(1, CategoryKind.INCOME), c(2, CategoryKind.EXPENSE), c(3, CategoryKind.TRANSFER), c(4, CategoryKind.EXPENSE))

    @Test fun moneyOutPutsSpendingFirstAndIncomeLast() =
        assertEquals(listOf(2L, 4L, 3L, 1L), list.orderedFor(-500).map { it.id })

    @Test fun moneyInPutsIncomeFirst() =
        assertEquals(listOf(1L, 3L, 2L, 4L), list.orderedFor(500).map { it.id })
}
