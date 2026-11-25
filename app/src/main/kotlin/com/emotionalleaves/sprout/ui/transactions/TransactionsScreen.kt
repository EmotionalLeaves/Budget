package com.emotionalleaves.sprout.ui.transactions

import androidx.compose.ui.Alignment
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.rounded.Add
import androidx.compose.runtime.LaunchedEffect
import com.emotionalleaves.sprout.ui.common.showUndoable
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

data class TxFilter(
    val query: String = "",
    val uncategorizedOnly: Boolean = false,
    val pendingOnly: Boolean = false,
    val accountId: String? = null,
    val categoryId: Long? = null,
    val tagId: Long? = null,
)

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
                val sq = com.emotionalleaves.sprout.domain.search.SearchQuery.parse(f.query)
                c.db.transactions().pagingRows(
                    query = sq.text,
                    categoryId = if (f.uncategorizedOnly) uncat ?: -1 else f.categoryId,
                    pendingOnly = f.pendingOnly,
                    accountId = f.accountId,
                    minAbs = sq.minAbsMinor,
                    maxAbs = sq.maxAbsMinor,
                    tagId = f.tagId,
                )
            }.flow
        }
        .cachedIn(viewModelScope)

    fun setQuery(q: String) { _filter.value = _filter.value.copy(query = q) }
    fun toggleUncategorized() { _filter.value = _filter.value.copy(uncategorizedOnly = !_filter.value.uncategorizedOnly) }
    fun togglePending() { _filter.value = _filter.value.copy(pendingOnly = !_filter.value.pendingOnly) }
    fun setAccount(id: String?) { _filter.value = _filter.value.copy(accountId = id) }
    fun setCategory(id: Long?) { _filter.value = _filter.value.copy(categoryId = id, uncategorizedOnly = false) }
    fun setTag(id: Long?) { _filter.value = _filter.value.copy(tagId = id) }
    val inboxCount = c.db.transactions().observeInboxCount()
    val tags: StateFlow<List<com.emotionalleaves.sprout.data.db.TagEntity>> = c.db.tags().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val accounts: StateFlow<List<com.emotionalleaves.sprout.data.db.AccountEntity>> = c.db.accounts().observeVisible()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories: StateFlow<List<com.emotionalleaves.sprout.data.db.CategoryEntity>> = c.db.categories().observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    /** Rolls over at midnight so "Today"/"Yesterday" headers stay right while the app is open. */
    val today: StateFlow<LocalDate> = c.budgetRepository.todayFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalDate.now(c.clock.withZone(ZoneId.systemDefault())))
}

@Composable
fun TransactionsScreen(snackbar: SnackbarHostState, onReview: () -> Unit, initialTagId: Long? = null) {
    val vm = sproutViewModel { TransactionsViewModel(it) }
    val filter by vm.filter.collectAsStateWithLifecycle()
    val uncategorized by vm.uncategorizedCount.collectAsStateWithLifecycle()
    val total by vm.total.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    LaunchedEffect(initialTagId) { if (initialTagId != null) vm.setTag(initialTagId) }
    val items = vm.pages.collectAsLazyPagingItems()
    val today by vm.today.collectAsStateWithLifecycle()
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    openId?.let { id ->
        TransactionDetailSheet(id, onDismiss = { openId = null }, onMessage = { msg, undo -> scope.showUndoable(snackbar, msg, undo) })
    }
    var adding by rememberSaveable { mutableStateOf(false) }
    if (adding) AddTransactionSheet(onDismiss = { adding = false }, onAdded = { adding = false; scope.launch { snackbar.showSnackbar("Added") } })

    Box(Modifier.fillMaxSize()) {
    androidx.compose.material3.ExtendedFloatingActionButton(
        onClick = { adding = true },
        icon = { Icon(androidx.compose.material.icons.Icons.Rounded.Add, contentDescription = null) },
        text = { Text("Add") },
        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).zIndex(1f),
    )
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = filter.query,
            onValueChange = vm::setQuery,
            placeholder = { Text("Search, or an amount like 42.10 or >100") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
            trailingIcon = {
                if (filter.query.isNotEmpty()) {
                    IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Clear, contentDescription = "Clear search") }
                }
            },
            singleLine = true,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        val inbox by remember { vm.inboxCount }.collectAsStateWithLifecycle(0)
        val navigate = com.emotionalleaves.sprout.ui.common.LocalNavigate.current
        if (inbox > 0) {
            Banner("$inbox new to review", actionLabel = "Review", onAction = { navigate("inbox") }, warning = false)
        }
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
            if (accounts.size > 1) {
                PickerChip(
                    label = accounts.firstOrNull { it.id == filter.accountId }?.label ?: "Any account",
                    selected = filter.accountId != null,
                    options = listOf<Pair<String?, String>>(null to "Any account") + accounts.map { it.id to it.label },
                    onPick = vm::setAccount,
                )
            }
            PickerChip(
                label = categories.firstOrNull { it.id == filter.categoryId }?.name ?: "Any category",
                selected = filter.categoryId != null,
                options = listOf<Pair<Long?, String>>(null to "Any category") + categories.map { it.id to it.name },
                onPick = vm::setCategory,
            )
            if (tags.isNotEmpty()) {
                PickerChip(
                    label = tags.firstOrNull { it.id == filter.tagId }?.let { "#" + it.name } ?: "Any tag",
                    selected = filter.tagId != null,
                    options = listOf<Pair<Long?, String>>(null to "Any tag") + tags.map { it.id to "#" + it.name },
                    onPick = vm::setTag,
                )
            }
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

                }
            }
            val d = floatingDate
            if (d != null && (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0)) {
                DayHeader(Format.dayHeader(d, today))
            }
        }
    }
    }
}

@Composable
private fun <T> PickerChip(label: String, selected: Boolean, options: List<Pair<T, String>>, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(selected, onClick = { open = true }, label = { Text(label) }, modifier = Modifier.heightIn(min = 48.dp))
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, text) ->
                androidx.compose.material3.DropdownMenuItem(text = { Text(text) }, onClick = { onPick(value); open = false })
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
