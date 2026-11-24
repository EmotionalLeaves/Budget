package com.emotionalleaves.sprout.ui.transactions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.emotionalleaves.sprout.domain.categorize.DefaultCategories
import com.emotionalleaves.sprout.ui.common.Banner
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.emotionalleaves.sprout.AppContainer
import com.emotionalleaves.sprout.data.db.TransactionRow
import com.emotionalleaves.sprout.ui.common.EmptyState
import com.emotionalleaves.sprout.ui.common.Format
import com.emotionalleaves.sprout.ui.common.TransactionItem
import com.emotionalleaves.sprout.ui.common.sproutViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

class TransactionsViewModel(private val c: AppContainer) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val _uncategorizedOnly = MutableStateFlow(false)
    val uncategorizedOnly: StateFlow<Boolean> = _uncategorizedOnly.asStateFlow()

    private val uncategorizedId = flow { emit(c.db.categories().getByKey(DefaultCategories.UNCATEGORIZED)?.id) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uncategorizedCount: StateFlow<Int> = uncategorizedId
        .flatMapLatest { id -> if (id == null) flowOf(0) else c.db.transactions().observeCountInCategory(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val rows: StateFlow<List<TransactionRow>?> = combine(_query.debounce(200), _uncategorizedOnly, uncategorizedId) { q, only, uncat ->
        Triple(q, only, uncat)
    }
        .flatMapLatest { (q, only, uncat) ->
            c.db.transactions().observeRows(q.trim().take(80), MAX_ROWS, if (only) uncat ?: -1 else null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setQuery(q: String) { _query.value = q }
    fun setUncategorizedOnly(on: Boolean) { _uncategorizedOnly.value = on }
    fun today(): LocalDate = LocalDate.now(c.clock.withZone(ZoneId.systemDefault()))

    companion object {
        /** Until the paged list lands (backlog), show the newest rows only. */
        const val MAX_ROWS = 3000
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionsScreen(snackbar: SnackbarHostState, onReview: () -> Unit) {
    val vm = sproutViewModel { TransactionsViewModel(it) }
    val query by vm.query.collectAsStateWithLifecycle()
    val rows by vm.rows.collectAsStateWithLifecycle()
    val uncategorized by vm.uncategorizedCount.collectAsStateWithLifecycle()
    val onlyUncategorized by vm.uncategorizedOnly.collectAsStateWithLifecycle()
    val today = vm.today()
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    openId?.let { id ->
        TransactionDetailSheet(id, onDismiss = { openId = null }, onMessage = { msg -> scope.launch { snackbar.showSnackbar(msg) } })
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = vm::setQuery,
            placeholder = { Text("Search description, merchant or note") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Clear, contentDescription = "Clear search") }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (uncategorized > 0) {
            Banner(
                "$uncategorized uncategorized transaction${if (uncategorized == 1) "" else "s"}",
                actionLabel = "Review",
                onAction = onReview,
                warning = false,
            )
        }
        Row(Modifier.padding(horizontal = 16.dp)) {
            FilterChip(
                selected = onlyUncategorized,
                onClick = { vm.setUncategorizedOnly(!onlyUncategorized) },
                label = { Text("Uncategorized only") },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
        val list = rows
        when {
            list == null -> Unit
            list.isEmpty() -> EmptyState(
                if (query.isBlank()) "No transactions yet" else "Nothing matches",
                if (query.isBlank()) "They'll appear here after your first sync." else "Try a different word.",
            )
            else -> {
                val grouped = list.groupBy { Format.localDate(it.occurredAt) }
                LazyColumn(Modifier.fillMaxSize()) {
                    grouped.forEach { (date, dayRows) ->
                        stickyHeader(key = "h-$date") {
                            Text(
                                Format.dayHeader(date, today),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .semantics { heading() },
                            )
                        }
                        items(dayRows, key = { it.id }) { row ->
                            TransactionItem(row) { openId = row.id }
                            HorizontalDivider(Modifier.padding(start = 16.dp))
                        }
                    }
                }
            }
        }
    }
}
