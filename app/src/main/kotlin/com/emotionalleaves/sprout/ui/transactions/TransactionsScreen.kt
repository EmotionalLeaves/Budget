package com.emotionalleaves.sprout.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.emotionalleaves.sprout.AppContainer
import com.emotionalleaves.sprout.data.db.TransactionRow
import com.emotionalleaves.sprout.domain.categorize.DefaultCategories
import com.emotionalleaves.sprout.ui.common.Banner
import com.emotionalleaves.sprout.ui.common.EmptyState
import com.emotionalleaves.sprout.ui.common.Format
import com.emotionalleaves.sprout.ui.common.TransactionItem
import com.emotionalleaves.sprout.ui.common.sproutViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class TxFilter(val query: String = "", val uncategorizedOnly: Boolean = false, val pendingOnly: Boolean = false)

class TransactionsViewModel(private val c: AppContainer) : ViewModel() {
    private val _filter = MutableStateFlow(TxFilter())
    val filter: StateFlow<TxFilter> = _filter.asStateFlow()

    private val uncategorizedId = flow { emit(c.db.categories().getByKey(DefaultCategories.UNCATEGORIZED)?.id) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uncategorizedCount: StateFlow<Int> = uncategorizedId
        .flatMapLatest { id -> if (id == null) flowOf(0) else c.db.transactions().observeCountInCategory(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val total: StateFlow<Int?> = c.db.transactions().observeCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val pages: Flow<PagingData<TransactionRow>> = combine(_filter.debounce(200), uncategorizedId) { f, uncat -> f to uncat }
        .flatMapLatest { (f, uncat) ->
            Pager(PagingConfig(pageSize = 60, prefetchDistance = 60, enablePlaceholders = false)) {
                c.db.transactions().pagingRows(
                    query = f.query.trim().take(80),
                    categoryId = if (f.uncategorizedOnly) uncat ?: -1 else null,
                    pendingOnly = f.pendingOnly,
                )
            }.flow
        }
        .cachedIn(viewModelScope)

    fun setQuery(q: String) { _filter.value = _filter.value.copy(query = q) }
    fun toggleUncategorized() { _filter.value = _filter.value.copy(uncategorizedOnly = !_filter.value.uncategorizedOnly) }
    fun togglePending() { _filter.value = _filter.value.copy(pendingOnly = !_filter.value.pendingOnly) }
    fun today(): LocalDate = LocalDate.now(c.clock.withZone(ZoneId.systemDefault()))
}

@Composable
fun TransactionsScreen(snackbar: SnackbarHostState, onReview: () -> Unit) {
    val vm = sproutViewModel { TransactionsViewModel(it) }
    val filter by vm.filter.collectAsStateWithLifecycle()
    val uncategorized by vm.uncategorizedCount.collectAsStateWithLifecycle()
    val total by vm.total.collectAsStateWithLifecycle()
    val items = vm.pages.collectAsLazyPagingItems()
    val today = vm.today()
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    openId?.let { id ->
        TransactionDetailSheet(id, onDismiss = { openId = null }, onMessage = { msg -> scope.launch { snackbar.showSnackbar(msg) } })
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = filter.query,
            onValueChange = vm::setQuery,
            placeholder = { Text("Search description, merchant or note") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
            trailingIcon = {
                if (filter.query.isNotEmpty()) {
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
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(filter.uncategorizedOnly, vm::toggleUncategorized, label = { Text("Uncategorized") }, modifier = Modifier.heightIn(min = 48.dp))
            FilterChip(filter.pendingOnly, vm::togglePending, label = { Text("Pending only") }, modifier = Modifier.heightIn(min = 48.dp))
        }

        if (items.itemCount == 0) {
            if (total == 0 || filter != TxFilter()) {
                EmptyState(
                    if (filter == TxFilter()) "No transactions yet" else "Nothing matches",
                    if (filter == TxFilter()) "They'll appear here after your first sync." else "Try a different word or filter.",
                )
            }
            return@Column
        }

        // The list is paged, so day headers are rows; this floating copy keeps the current day visible while scrolling.
        val floatingDate by remember(items) {
            derivedStateOf {
                val idx = listState.firstVisibleItemIndex
                if (idx < items.itemCount) items.peek(idx)?.let { Format.localDate(it.occurredAt) } else null
            }
        }
        Box(Modifier.fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(count = items.itemCount, key = items.itemKey { it.id }) { index ->
                    val row = items[index] ?: return@items
                    val date = Format.localDate(row.occurredAt)
                    val prev = if (index > 0) items.peek(index - 1)?.let { Format.localDate(it.occurredAt) } else null
                    if (prev != date) DayHeader(Format.dayHeader(date, today))
                    TransactionItem(row) { openId = row.id }
                    HorizontalDivider(Modifier.padding(start = 16.dp))
                }
            }
            val d = floatingDate
            if (d != null && (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0)) {
                DayHeader(Format.dayHeader(d, today))
            }
        }
    }
}

@Composable
private fun DayHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .semantics { heading() },
    )
}
