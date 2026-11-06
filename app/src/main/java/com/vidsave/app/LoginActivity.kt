package com.vidsave.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/** Lets you sign in to a site once; the session is then used for downloads. */
class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { VidSaveTheme { LoginScreen(onClose = { finish() }) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LoginScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var selected by remember { mutableStateOf(SITES[0]) }
    var web by remember { mutableStateOf<WebView?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log in") },
                actions = {
                    TextButton(onClick = {
                        CookieStore.clear(ctx)
                        web?.loadUrl(selected.loginUrl)
                        Toast.makeText(ctx, "Logged out of all sites", Toast.LENGTH_SHORT).show()
                    }) { Text("Log out all") }
                    TextButton(onClick = {
                        val n = CookieStore.save(ctx)
                        Toast.makeText(
                            ctx,
                            if (n > 0) "Login saved. You can download now." else "No login found yet",
                            Toast.LENGTH_LONG
                        ).show()
                        if (n > 0) onClose()
                    }) { Text("Save & close") }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad)) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)
            ) {
                SITES.forEach { site ->
                    FilterChip(
                        selected = selected == site,
                        onClick = {
                            selected = site
                            web?.loadUrl(site.loginUrl)
                        },
                        label = { Text(site.name) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
            AndroidView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                factory = { c ->
                    WebView(c).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // Look like normal mobile Chrome, since some sites block embedded WebViews.
                        settings.userAgentString =
                            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                                "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = WebViewClient()
                        loadUrl(selected.loginUrl)
                        web = this
                    }
                }
            )
        }
    }
}
