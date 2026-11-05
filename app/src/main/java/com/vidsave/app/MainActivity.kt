package com.vidsave.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent { VidSaveTheme { HomeScreen() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val items by Downloads.items.collectAsState()
    var link by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VidSave") },
                actions = {
                    TextButton(onClick = {
                        Toast.makeText(ctx, "Updating engine…", Toast.LENGTH_SHORT).show()
                        scope.launch {
                            val msg = withContext(Dispatchers.IO) { Engine.forceUpdate(ctx.applicationContext) }
                            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                        }
                    }) { Text("Update engine") }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                label = { Text("Paste video link") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    TextButton(onClick = {
                        clipboard.getText()?.text?.let { link = it }
                    }) { Text("Paste") }
                }
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val url = Links.extract(link)
                    if (url == null) {
                        Toast.makeText(ctx, "Paste a valid link first", Toast.LENGTH_SHORT).show()
                    } else {
                        DownloadService.start(ctx, url)
                        link = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Download (best quality)") }

            Spacer(Modifier.height(16.dp))

            if (items.isEmpty()) {
                Text(
                    "Paste a link above, or open any video in another app and tap " +
                        "Share → VidSave. Downloads are saved to Movies/VidSave.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Downloads", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { Downloads.clearFinished() }) { Text("Clear finished") }
                }
                LazyColumn {
                    items(items.asReversed(), key = { it.id }) { DownloadRow(it) }
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(item: DownloadItem) {
    val ctx = LocalContext.current
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                item.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.height(4.dp))
            Text(
                item.note,
                style = MaterialTheme.typography.bodySmall,
                color = if (item.status == Status.FAILED) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            when (item.status) {
                Status.RUNNING -> {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { item.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(onClick = { DownloadService.cancel(ctx, item.id) }) { Text("Cancel") }
                }
                Status.DONE -> item.uri?.let { uri ->
                    TextButton(onClick = { openVideo(ctx, uri) }) { Text("Open") }
                }
                Status.FAILED -> TextButton(onClick = { DownloadService.start(ctx, item.url) }) {
                    Text("Retry")
                }
            }
        }
    }
}

private fun openVideo(ctx: Context, uri: Uri) {
    runCatching {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "video/*")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }.onFailure {
        Toast.makeText(ctx, "No video player found", Toast.LENGTH_SHORT).show()
    }
}
