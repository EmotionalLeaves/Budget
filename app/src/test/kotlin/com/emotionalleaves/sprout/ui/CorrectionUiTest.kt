package com.emotionalleaves.sprout.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.emotionalleaves.sprout.data.db.CategoryEntity
import com.emotionalleaves.sprout.data.db.TransactionRow
import com.emotionalleaves.sprout.domain.categorize.CategoryKind
import com.emotionalleaves.sprout.domain.categorize.CategorySource
import com.emotionalleaves.sprout.domain.split.SplitPart
import com.emotionalleaves.sprout.ui.budgets.EditBudgetDialog
import com.emotionalleaves.sprout.ui.common.TransactionItem
import com.emotionalleaves.sprout.ui.transactions.SplitDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The main correction paths, driven through the real composables. */
@RunWith(RobolectricTestRunner::class)
class CorrectionUiTest {
    @get:Rule val rule = createComposeRule()

    private fun cat(id: Long, name: String) = CategoryEntity(id, null, name, CategoryKind.EXPENSE, "custom", 0, id.toInt(), false)
    private val groceries = cat(1, "Groceries")
    private val shopping = cat(2, "Shopping")
    private val gifts = cat(3, "Gifts")

    @Test fun budgetDialogSavesEveryMonthWithRollover() {
        var saved: Triple<Long, Boolean, Boolean>? = null
        rule.setContent {
            EditBudgetDialog("Groceries", 30_000, hasBudget = true, rolloverOn = false, onDismiss = {}, onSave = { a, m, r -> saved = Triple(a, m, r) }, onRemove = {})
        }
        rule.onNodeWithText("300").performTextReplacement("412.50")
        rule.onNodeWithText("Roll over leftover money").performClick()
        rule.onNodeWithText("Save").performClick()
        assertEquals(Triple(41_250L, false, true), saved)
    }

    @Test fun budgetDialogThisMonthOnlyAndBadInput() {
        var saved: Triple<Long, Boolean, Boolean>? = null
        rule.setContent {
            EditBudgetDialog("Groceries", null, hasBudget = false, onDismiss = {}, onSave = { a, m, r -> saved = Triple(a, m, r) }, onRemove = {})
        }
        rule.onNodeWithText("Save").assertIsNotEnabled()
        rule.onNodeWithText("Amount per month").performTextReplacement("abc")
        rule.onNodeWithText("Save").assertIsNotEnabled()
        rule.onNodeWithText("Amount per month").performTextReplacement("50")
        rule.onNodeWithText("This month only").performClick()
        rule.onNodeWithText("Save").assertIsEnabled().performClick()
        assertEquals(Triple(5_000L, true, false), saved)
    }

    @Test fun splitDialogMovesPartAndShowsWhatStays() {
        var saved: List<SplitPart>? = null
        rule.setContent {
            SplitDialog(-10_000, groceries, listOf(groceries, shopping, gifts), emptyList(), onDismiss = {}, onSave = { saved = it }, problem = null)
        }
        rule.onNodeWithText("Category").performClick()
        rule.onNodeWithText("Shopping").performClick()
        rule.onNodeWithText("Amount").performTextReplacement("30")
        rule.onNodeWithText("Stays in Groceries: $70.00").assertExists()
        rule.onNodeWithText("Save").performClick()
        assertEquals(listOf(SplitPart(2, 3_000)), saved)
    }

    @Test fun splitDialogRefusesTooMuchAndHidesOwnCategory() {
        rule.setContent {
            SplitDialog(-10_000, groceries, listOf(groceries, shopping), emptyList(), onDismiss = {}, onSave = {}, problem = null)
        }
        rule.onNodeWithText("Category").performClick()
        assertEquals(0, rule.onAllNodesWithText("Groceries").fetchSemanticsNodes().size)
        rule.onNodeWithText("Shopping").performClick()
        rule.onNodeWithText("Amount").performTextReplacement("120")
        rule.onNodeWithText("The parts add up to more than the transaction.").assertExists()
        rule.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test fun splitDialogEditsExistingParts() {
        var saved: List<SplitPart>? = null
        rule.setContent {
            SplitDialog(-10_000, groceries, listOf(groceries, shopping), listOf(SplitPart(2, -2_500)), onDismiss = {}, onSave = { saved = it }, problem = null)
        }
        rule.onNodeWithText("25").assertExists()
        rule.onNodeWithText("Stays in Groceries: $75.00").assertExists()
        rule.onNodeWithText("Save").performClick()
        assertEquals(listOf(SplitPart(2, 2_500)), saved)
    }

    @Test fun listRowSpeaksSplitAndPending() {
        val row = TransactionRow(
            id = "x", accountId = "a", description = "TARGET 1234", merchantKey = "TARGET", amountMinor = -4_200,
            occurredAt = 1_763_500_000, pending = false, note = null, categoryId = 1, categoryName = "Groceries",
            categoryColor = 0, categorySource = CategorySource.BUILT_IN, accountName = "Checking", splitCount = 1,
        )
        rule.setContent { TransactionItem(row) }
        rule.onNodeWithContentDescription("split across categories", substring = true).assertExists()
    }
}
