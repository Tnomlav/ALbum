package com.example.album.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.album.data.LicenseCatalog
import com.example.album.data.LicenseDocument

/**
 * Lists the third-party components inside the APK and opens their license
 * texts. LGPL-licensed libraries must stay inspectable from the app itself.
 */
@Composable
fun VaultLicensesSheet(english: Boolean, onDismiss: () -> Unit) {
    var openDocument by remember { mutableStateOf<LicenseDocument?>(null) }
    VaultBottomSheet(if (english) "Open-source licenses" else "开源许可", onDismiss) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
            item {
                Text(
                    if (english) {
                        "Album bundles the components below. Their license texts are stored in the app and can be opened offline."
                    } else {
                        "Album 随包分发以下第三方组件。它们的许可证全文存放在应用内，可离线查看。"
                    },
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            }
            items(LicenseCatalog.components) { component ->
                Column(
                    Modifier.fillMaxWidth()
                        .clickable {
                            LicenseCatalog.documents.firstOrNull {
                                it.assetPath == component.licenseAsset
                            }?.let { openDocument = it }
                        }
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text(component.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                    Text(
                        "${component.version} · ${component.licenseName}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    if (english) "License texts" else "许可证全文",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
            items(LicenseCatalog.documents) { document ->
                Text(
                    document.title,
                    modifier = Modifier.fillMaxWidth()
                        .clickable { openDocument = document }
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp
                )
            }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).height(48.dp)) {
            Text(
                if (english) "Done" else "知道了",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }

    openDocument?.let { document ->
        VaultLicenseTextSheet(document, english) { openDocument = null }
    }
}

@Composable
private fun VaultLicenseTextSheet(
    document: LicenseDocument,
    english: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lines = remember(document.assetPath) {
        LicenseCatalog.read(context, document.assetPath).split('\n')
    }
    VaultBottomSheet(document.title, onDismiss) {
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 560.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
        ) {
            items(lines) { line ->
                Text(
                    line,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).height(48.dp)) {
            Text(
                if (english) "Close" else "关闭",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
