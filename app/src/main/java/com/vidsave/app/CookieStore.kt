package com.vidsave.app

import android.content.Context
import android.webkit.CookieManager
import java.io.File

data class Site(val name: String, val domain: String, val loginUrl: String)

val SITES = listOf(
    Site("Instagram", "instagram.com", "https://www.instagram.com/accounts/login/"),
    Site("Facebook", "facebook.com", "https://m.facebook.com/login"),
    Site("X / Twitter", "x.com", "https://x.com/i/flow/login"),
    Site("TikTok", "tiktok.com", "https://www.tiktok.com/login"),
)

/**
 * Exports the sessions you logged into inside the app (WebView) to a cookies.txt file
 * that yt-dlp understands. The file stays private inside the app's own storage.
 */
object CookieStore {
    fun file(ctx: Context) = File(ctx.filesDir, "cookies.txt")

    /** Writes cookies for all known sites. Returns the number of cookies saved. */
    fun save(ctx: Context): Int {
        val cm = CookieManager.getInstance()
        cm.flush()
        val sb = StringBuilder("# Netscape HTTP Cookie File\n")
        var count = 0
        for (site in SITES) {
            val raw = cm.getCookie("https://www.${site.domain}") ?: continue
            for (part in raw.split(";")) {
                val kv = part.trim().split("=", limit = 2)
                if (kv.size == 2 && kv[0].isNotEmpty()) {
                    // domain, include-subdomains, path, secure, expiry, name, value
                    sb.append(".${site.domain}\tTRUE\t/\tTRUE\t2147483647\t${kv[0]}\t${kv[1]}\n")
                    count++
                }
            }
        }
        if (count > 0) file(ctx).writeText(sb.toString()) else file(ctx).delete()
        return count
    }

    fun clear(ctx: Context) {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        file(ctx).delete()
    }
}
