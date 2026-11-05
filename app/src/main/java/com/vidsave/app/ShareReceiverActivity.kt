package com.vidsave.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity

/**
 * Target of the system Share sheet. Grabs the link, starts the download in the
 * background and closes immediately so the user stays in the app they were in.
 */
class ShareReceiverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = Links.extract(intent.getStringExtra(Intent.EXTRA_TEXT))
        if (url == null) {
            Toast.makeText(this, "No link found in the shared content", Toast.LENGTH_SHORT).show()
        } else {
            DownloadService.start(this, url)
            Toast.makeText(this, "Downloading in background…", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
