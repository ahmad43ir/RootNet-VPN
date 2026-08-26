package com.chobgroup.rootnet

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.chobgroup.rootnet.core.theme.RootNetColors
import com.chobgroup.rootnet.core.theme.RootNetTheme
import com.chobgroup.rootnet.ui.RootNetApp
import com.chobgroup.rootnet.util.ConfigActions
import java.io.IOException
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A VPN config file received via Android's "Open with RootNet" chooser. */
data class ReceivedFile(
    val filename: String,
    val sizeBytes: Long,
    /** Raw file content as text (npvt/npv content is ASCII/base64 anyway). */
    val content: String,
) {
    val extension: String get() = filename.substringAfterLast('.', "").uppercase()
    val isEncrypted: Boolean get() = extension == "NPVT" || extension == "NPV"
}

/** File types RootNet offers to open (kept in sync with the manifest filter). */
private val SUPPORTED_FILE_EXTENSIONS = listOf(".npvt", ".npv", ".npt", ".sip")

/** Safety cap — never read a huge file into memory from an external app. */
private const val MAX_RECEIVED_FILE_BYTES = 10 * 1024 * 1024 // 10 MB

/**
 * Single-activity Compose host. The VPN daemon process, notification
 * permissions and auth deep links are gone in v2.0 — the app is a config
 * launcher, so this is just the theme + root composable.
 *
 * v2.1.1: also receives ACTION_VIEW intents ("Open with RootNet" on a
 * .npvt/.npv/.npt/.sip file). The raw file is read and offered for Copy —
 * RootNet never decrypts or parses configs itself.
 */
class MainActivity : ComponentActivity() {

    private var receivedFile by mutableStateOf<ReceivedFile?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            RootNetTheme {
                RootNetApp()
                receivedFile?.let { file ->
                    ReceivedFileDialog(file, onDismiss = { receivedFile = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Picks up ACTION_VIEW intents ("open with RootNet" on a config file). */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        val name = queryDisplayName(uri) ?: "config-file"
        if (SUPPORTED_FILE_EXTENSIONS.none { name.lowercase().endsWith(it) }) {
            toast("Not a supported config file")
            return
        }
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) { readReceivedFile(uri, name) }
            if (file == null) toast("Could not read the file")
            else receivedFile = file
        }
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }.getOrNull()

    /** Reads the received file (name + size + raw content), capped at 10 MB. */
    private fun readReceivedFile(uri: Uri, name: String): ReceivedFile? = try {
        val size = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (idx >= 0 && cursor.moveToFirst() && !cursor.isNull(idx)) cursor.getLong(idx) else -1L
        } ?: -1L
        if (size > MAX_RECEIVED_FILE_BYTES) return null

        contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readBytes()
            if (bytes.size > MAX_RECEIVED_FILE_BYTES) return null
            ReceivedFile(
                filename = name,
                sizeBytes = bytes.size.toLong(),
                content = String(bytes, StandardCharsets.UTF_8),
            )
        }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

/** Themed dialog shown when RootNet is opened with a config file. */
@Composable
private fun ReceivedFileDialog(file: ReceivedFile, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RootNetColors.BgCard,
        title = {
            Text(
                text = "📁 ${file.filename}",
                color = RootNetColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                val kb = (file.sizeBytes / 1024.0).let { if (it < 10) "%.1f".format(it) else it.toInt().toString() }
                val lock = if (file.isEncrypted) " · 🔒 encrypted" else ""
                Text(
                    text = "Type: ${file.extension}$lock · $kb KB",
                    color = RootNetColors.AccentNeon,
                )
                Text(
                    text = "RootNet doesn't decrypt configs — copy the file and import it in your client app (v2rayNG, NekoBox, Hiddify…).",
                    color = RootNetColors.TextSecondary,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                ConfigActions.copyToClipboard(context, file.filename, file.content)
                Toast.makeText(context, "Copied — paste it into your VPN client app", Toast.LENGTH_SHORT).show()
                onDismiss()
            }) {
                Text("Copy to clipboard", color = RootNetColors.AccentNeon)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = RootNetColors.TextMuted)
            }
        },
    )
}
