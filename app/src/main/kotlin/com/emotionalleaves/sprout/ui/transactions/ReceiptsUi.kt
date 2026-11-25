package com.emotionalleaves.sprout.ui.transactions

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emotionalleaves.sprout.AppContainer
import com.emotionalleaves.sprout.data.db.ReceiptEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Receipt photos on a transaction: thumbnails, "Add receipt", tap to view or delete. */
@Composable
fun TransactionReceipts(c: AppContainer, transactionId: String) {
    val receipts by remember(transactionId) { c.db.receipts().observeFor(transactionId) }.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var viewing by remember { mutableStateOf<ReceiptEntity?>(null) }
    var failed by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { failed = !c.receiptRepository.add(transactionId, uri) }
    }
    Text("Receipts", style = MaterialTheme.typography.titleMedium)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(receipts, key = { it.id }) { r ->
            val bmp = com.emotionalleaves.sprout.ui.common.rememberLoaded(r.fileName) {
                withContext(Dispatchers.IO) {
                    runCatching { BitmapFactory.decodeFile(c.receiptRepository.file(r).path, BitmapFactory.Options().apply { inSampleSize = 8 }) }.getOrNull()
                }
            }
            bmp?.let {
                Image(it.asImageBitmap(), contentDescription = "Receipt photo", contentScale = ContentScale.Crop,
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).clickable { viewing = r })
            }
        }
        item {
            AssistChip(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                label = { Text("Add receipt") }, leadingIcon = { Icon(Icons.Rounded.AddAPhoto, null) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
    if (failed) Text("Couldn't read that photo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    viewing?.let { r ->
        val full = com.emotionalleaves.sprout.ui.common.rememberLoaded(r.fileName) {
            withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(c.receiptRepository.file(r).path) }.getOrNull() }
        }
        AlertDialog(
            onDismissRequest = { viewing = null },
            text = {
                Column {
                    full?.let { Image(it.asImageBitmap(), contentDescription = "Receipt photo", modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth) }
                }
            },
            confirmButton = { TextButton(onClick = { viewing = null }) { Text("Close") } },
            dismissButton = {
                TextButton(onClick = { scope.launch { c.receiptRepository.delete(r) }; viewing = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }
}
