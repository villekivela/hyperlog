package com.hyperlog.app.ui.coach

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import com.hyperlog.app.R

/**
 * The hand-off to the Claude app (ADR 0001): the prompt goes out through the share sheet, and the
 * reply comes back through the clipboard. Nothing leaves the phone any other way.
 */
object AskClaudeShare {
    /** The share sheet with [prompt] as plain text, for the Claude app. */
    fun intent(context: Context, prompt: String): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, prompt)
        }
        return Intent.createChooser(send, context.getString(R.string.coach_ask_chooser))
    }

    /** The clipboard's text; empty when it holds none. */
    fun clipboard(context: Context): String {
        val clip = clipboardManager(context).primaryClip ?: return ""
        return (0 until clip.itemCount).joinToString("\n") { clip.getItemAt(it).coerceToText(context).toString() }
    }

    fun copy(context: Context, label: String, text: String) {
        clipboardManager(context).setPrimaryClip(ClipData.newPlainText(label, text))
    }

    private fun clipboardManager(context: Context) = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
}
