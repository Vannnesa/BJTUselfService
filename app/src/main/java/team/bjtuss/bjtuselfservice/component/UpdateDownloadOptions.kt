package team.bjtuss.bjtuselfservice.component

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Direct and mirror download choices for a GitHub Release URL.
 *
 * A mirror is used as: prefix + "/" + original URL, for example
 * https://gh-proxy.org/https://github.com/owner/repo/releases/tag/v1.
 */
@Composable
fun UpdateDownloadOptions(
    releaseUrl: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var selectedMirror by remember { mutableStateOf("git.vannesa.top/") }
    var showCustomDialog by remember { mutableStateOf(false) }
    var customPrefix by remember { mutableStateOf("") }
    var customError by remember { mutableStateOf<String?>(null) }

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    fun openMirror(prefix: String) {
        val normalized = prefix.trim().trimEnd('/')
        if (normalized.isBlank()) return
        val full = if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            normalized
        } else {
            "https://" + normalized
        }
        openUrl(full + "/" + releaseUrl)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "下载方式",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip(
                selected = false,
                onClick = { openUrl(releaseUrl) },
                label = { Text("直连") },
            )
            FilterChip(
                selected = selectedMirror == "git.vannesa.top/",
                onClick = {
                    selectedMirror = "git.vannesa.top/"
                    openMirror(selectedMirror)
                },
                label = { Text("git.vannesa.top/") },
            )
            FilterChip(
                selected = selectedMirror == "gh-proxy.org/",
                onClick = {
                    selectedMirror = "gh-proxy.org/"
                    openMirror(selectedMirror)
                },
                label = { Text("gh-proxy.org/") },
            )
            FilterChip(
                selected = false,
                onClick = { showCustomDialog = true },
                label = { Text("自定义") },
            )
        }
        Text(
            text = "镜像格式：镜像前缀 + 原始 GitHub 地址",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showCustomDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = { Text("自定义镜像") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "输入镜像前缀，例如 https://gh-proxy.org/",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = customPrefix,
                        onValueChange = {
                            customPrefix = it
                            customError = null
                        },
                        singleLine = true,
                        label = { Text("镜像前缀") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    customError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    Text(
                        "将打开：前缀 + " + releaseUrl,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val prefix = customPrefix.trim().trimEnd('/')
                    if (prefix.isBlank()) {
                        customError = "请输入镜像前缀"
                    } else {
                        showCustomDialog = false
                        openMirror(prefix)
                    }
                }) { Text("打开镜像") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) { Text("取消") }
            },
        )
    }
}
