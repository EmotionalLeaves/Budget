package com.emotionalleaves.sprout.ui.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.emotionalleaves.sprout.AppContainer
import com.emotionalleaves.sprout.data.db.CategoryEntity
import com.emotionalleaves.sprout.data.db.TransactionRow
import com.emotionalleaves.sprout.domain.categorize.DefaultCategories
import com.emotionalleaves.sprout.domain.categorize.MerchantNormalizer
import com.emotionalleaves.sprout.ui.common.EmptyState
import com.emotionalleaves.sprout.ui.common.Format
import com.emotionalleaves.sprout.ui.common.sproutViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ReviewViewModel(private val c: AppContainer) : ViewModel() {
    private val skipped = MutableStateFlow(emptySet<String>())

    @OptIn(ExperimentalCoroutinesApi::class)
    val queue: StateFlow<List<TransactionRow>?> = flow { emit(c.db.categories().getByKey(DefaultCategories.UNCATEGORIZED)?.id) }
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else c.db.transactions().observeRows("", 500, id) }
        .combine(skipped) { rows, skip -> rows.filter { it.id !in skip } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val categories: StateFlow<List<CategoryEntity>> =
        c.db.categories().observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun pick(row: TransactionRow, category: CategoryEntity, always: Boolean) = viewModelScope.launch {
        c.categoryRepository.recategorize(row.id, category.id, always)
        c.alertRepository.evaluateBudgets()
    }

    fun skip(row: TransactionRow) = skipped.update { it + row.id }
}

/** One uncategorized transaction at a time; one tap categorizes it and shows the next. */
@Composable
fun ReviewScreen(onBack: () -> Unit) {
    val vm = sproutViewModel { ReviewViewModel(it) }
    val queue by vm.queue.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    var always by rememberSaveable { mutableStateOf(true) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Review uncategorized", style = MaterialTheme.typography.titleLarge)
        }
        val list = queue ?: return@Column
        val row = list.firstOrNull()
        if (row == null) {
            EmptyState("All caught up", "Every transaction has a category.")
            return@Column
        }
        var startCount by rememberSaveable { mutableStateOf(0) }
        if (list.size > startCount) startCount = list.size
        val done = startCount - list.size
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${list.size} left", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            if (done > 0) Text("$done done", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        com.emotionalleaves.sprout.ui.common.FatProgress(if (startCount == 0) 0f else done.toFloat() / startCount, MaterialTheme.colorScheme.primary)
        val merchant = com.emotionalleaves.sprout.ui.common.merchantName(row.merchantKey)
        val c = com.emotionalleaves.sprout.ui.theme.LocalSproutColors.current
        androidx.compose.animation.AnimatedContent(targetState = row, label = "review-card") { r ->
            com.emotionalleaves.sprout.ui.common.HeroCard(c.hero, inset = 0.dp) {
                Text(com.emotionalleaves.sprout.ui.common.merchantName(r.merchantKey), style = MaterialTheme.typography.headlineSmall, color = c.onHero)
                Text(Format.amount(r.amountMinor), style = MaterialTheme.typography.displaySmall, color = c.onHero)
                Text(r.description, style = MaterialTheme.typography.bodyMedium, color = c.onHero.copy(alpha = 0.85f))
                Text("${Format.dateTime(r.occurredAt)} · ${r.accountName}", style = MaterialTheme.typography.bodySmall, color = c.onHero.copy(alpha = 0.8f))
            }
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Switch) { always = !always },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Always use this for $merchant", Modifier.weight(1f))
            Switch(checked = always, onCheckedChange = null)
        }
        CategoryGrid(categories.filter { it.key != DefaultCategories.UNCATEGORIZED }, null) { vm.pick(row, it, always) }
        OutlinedButton(onClick = { vm.skip(row) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Skip for now") }
    }
}
