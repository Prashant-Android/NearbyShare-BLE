package org.googlenearby.project

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FileTransferDialog(
    isReceiving: Boolean,
    fileName: String,
    fileSize: Long,
    progress: Float,
    speed: String,
    isTransferComplete: Boolean,
    onDismiss: () -> Unit,
    onCancel: () -> Unit,
    onOpenFile: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isReceiving) "Receiving" else "Transferring") },
        text = {
            Column {
                Text("File: $fileName")
                Text("Size: ${formatFileSize(fileSize)}")
                LinearProgressIndicator(progress = progress)
                Text("Progress: ${(progress * 100).toInt()}%")
                Text("Speed: $speed")
            }
        },
        confirmButton = {
            Row {

                if (isTransferComplete && ! isReceiving) {
                    TextButton(onClick = onOpenFile) {
                        Text("Open File Location")
                    }
                }

            }
        }
    )
}

private fun formatFileSize(size: Long): String {
    val kb = size / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1 -> String.format("%.2f GB", gb)
        mb >= 1 -> String.format("%.2f MB", mb)
        kb >= 1 -> String.format("%.2f KB", kb)
        else -> "$size bytes"
    }
}