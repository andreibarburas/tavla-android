package com.brbrs.tavla.sharesend

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * Sends this card's title + description to another BarburasLab app as a generic share
 * (ACTION_SEND), the same mechanism used for sharing from any other app into Tavla, Koto, or
 * Nóta. Each receiving app already has its own ShareReceiverActivity (or equivalent) that
 * turns `EXTRA_TEXT` into a new item of its own -- Koto also reads `EXTRA_SUBJECT` as its
 * task title. This deliberately reuses that existing path rather than talking to the other
 * app's Nextcloud API directly, so it stays small and keeps working even if that app's own
 * storage format changes.
 */
object SendToAppHelper {

    fun isInstalled(context: Context, appPackage: String): Boolean =
        try {
            context.packageManager.getPackageInfo(appPackage, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) { false }

    /** Sends [title] + [body] to [appPackage], then opens it so the person sees the result. */
    fun send(context: Context, appPackage: String, title: String, body: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, if (body.isBlank()) title else "$title\n\n$body".take(4000))
            setPackage(appPackage)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(shareIntent)
        openApp(context, appPackage)
    }

    fun openApp(context: Context, appPackage: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(appPackage)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        if (intent != null) context.startActivity(intent) else openPlayStore(context, appPackage)
    }

    fun openPlayStore(context: Context, appPackage: String) {
        val uri = Uri.parse("market://details?id=$appPackage")
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        }.onFailure {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$appPackage"))
                    .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
            )
        }
    }
}
