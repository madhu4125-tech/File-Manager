@file:OptIn(ExperimentalFoundationApi::class)

package com.madhu.filemanager

import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import java.io.File

// ----------------------------------------------------------------- dialogs

@Composable
fun FmDialog(
    title: String,
    onDismiss: () -> Unit,
    confirm: (@Composable () -> Unit)? = null,
    dismiss: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val p = LocalPal.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surface,
        titleContentColor = p.text,
        textContentColor = p.text,
        title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = content,
        confirmButton = { confirm?.invoke() },
        dismissButton = dismiss
    )
}

@Composable
fun DialogHost(vm: FmViewModel) {
    val d = vm.dialog ?: return
    val p = LocalPal.current
    when (d) {
        is Dlg.Menu -> FmDialog(d.title, onDismiss = { vm.dialog = null }) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                d.entries.forEach { e ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            vm.dialog = null
                            e.action()
                        }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(e.icon, fontSize = 20.sp, modifier = Modifier.width(34.dp))
                        Text(e.label, color = if (e.danger) p.danger else p.text, fontSize = 16.sp)
                    }
                }
            }
        }

        is Dlg.Prompt -> {
            val dot = d.initial.lastIndexOf('.')
            val end = if (d.selectBase && dot > 0) dot else d.initial.length
            var tf by remember(d) { mutableStateOf(TextFieldValue(d.initial, TextRange(0, end))) }
            val focus = remember { FocusRequester() }
            LaunchedEffect(d) { focus.requestFocus() }
            val submit = {
                vm.dialog = null
                d.onOk(tf.text.trim())
            }
            FmDialog(
                d.title,
                onDismiss = { vm.dialog = null },
                confirm = { TextButton(onClick = submit) { Text(d.label, color = p.accent, fontWeight = FontWeight.Bold) } },
                dismiss = { TextButton(onClick = { vm.dialog = null }) { Text("Cancel", color = p.muted) } }
            ) {
                OutlinedTextField(
                    value = tf,
                    onValueChange = { tf = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus)
                )
            }
        }

        is Dlg.Confirm -> FmDialog(
            d.title,
            onDismiss = { vm.dialog = null },
            confirm = {
                TextButton(onClick = {
                    vm.dialog = null
                    d.onOk()
                }) { Text(d.label, color = if (d.danger) p.danger else p.accent, fontWeight = FontWeight.Bold) }
            },
            dismiss = { TextButton(onClick = { vm.dialog = null }) { Text("Cancel", color = p.muted) } }
        ) { Text(d.msg, color = p.muted) }

        is Dlg.Props -> {
            val pr = d.props
            FmDialog(
                d.item.name,
                onDismiss = { vm.dialog = null },
                confirm = { TextButton(onClick = { vm.copyPath(d.item.path) }) { Text("Copy path", color = p.accent, fontWeight = FontWeight.Bold) } },
                dismiss = { TextButton(onClick = { vm.dialog = null }) { Text("Close", color = p.muted) } }
            ) {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PropLine("Type", if (d.item.isDir) "Folder" else d.item.ext.uppercase().ifEmpty { "File" })
                    PropLine("Size", "${fmtSize(pr.totalSize)} (${"%,d".format(pr.totalSize)} bytes)")
                    if (d.item.isDir) PropLine("Contains", "${pr.files} files, ${pr.folders} folders")
                    PropLine("Modified", fmtDateTime(pr.mtime))
                    PropLine("Location", File(d.item.path).parent ?: "")
                    PropLine("Access", listOfNotNull(if (pr.canRead) "Read" else null, if (pr.canWrite) "Write" else null).joinToString(" + "))
                }
            }
        }
    }
}

@Composable
fun PropLine(label: String, value: String) {
    val p = LocalPal.current
    Column {
        Text(label, color = p.muted, fontSize = 12.sp)
        Text(value, color = p.text, fontSize = 14.sp)
    }
}

@Composable
fun BusyHost(vm: FmViewModel) {
    val msg = vm.busy ?: return
    val p = LocalPal.current
    val cancelBtn: @Composable () -> Unit = {
        TextButton(onClick = { vm.cancelBusy() }) { Text("Cancel", color = p.muted) }
    }
    AlertDialog(
        onDismissRequest = { if (vm.busyCancelable) vm.cancelBusy() },
        containerColor = p.surface,
        properties = DialogProperties(dismissOnClickOutside = false, dismissOnBackPress = vm.busyCancelable),
        confirmButton = {},
        dismissButton = if (vm.busyCancelable) cancelBtn else null,
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = p.accent, modifier = Modifier.width(32.dp).height(32.dp))
                Spacer(Modifier.width(16.dp))
                Text(msg, color = p.text, fontSize = 16.sp)
            }
        }
    )
}

// ----------------------------------------------------------------- viewers

@Composable
fun ViewerHost(vm: FmViewModel) {
    val v = vm.viewer ?: return
    Box(Modifier.fillMaxSize().background(Color(0xFF05070D))) {
        when (v) {
            is Viewer.Images -> ImageViewer(vm, v)
            is Viewer.Media -> MediaViewer(vm, v)
            is Viewer.TextDoc -> TextViewer(vm, v)
        }
    }
}

@Composable
fun ViewerBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().background(Color(0x99000000)).padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBtn("←", "Close", onClick = onBack)
        Text(
            title, Modifier.weight(1f).padding(horizontal = 6.dp), color = Color.White,
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        actions()
    }
}

@Composable
fun ImageViewer(vm: FmViewModel, v: Viewer.Images) {
    val pager = rememberPagerState(initialPage = v.index) { v.items.size }
    val current = v.items[pager.currentPage.coerceIn(0, v.items.lastIndex)]
    Column(Modifier.fillMaxSize()) {
        ViewerBar(current.name, onBack = { vm.closeViewer() }) {
            IconBtn("⤴️", "Share") { vm.share(listOf(current.path)) }
            IconBtn("↗️", "Open with") { vm.openExternal(current.path, true) }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            AsyncImage(
                model = File(v.items[page].path),
                contentDescription = v.items[page].name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun MediaViewer(vm: FmViewModel, v: Viewer.Media) {
    Column(Modifier.fillMaxSize()) {
        ViewerBar(v.item.name, onBack = { vm.closeViewer() }) {
            IconBtn("⤴️", "Share") { vm.share(listOf(v.item.path)) }
            IconBtn("↗️", "Open with") { vm.openExternal(v.item.path, true) }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (!v.video) Text("🎵", fontSize = 72.sp, modifier = Modifier.padding(bottom = 160.dp))
            AndroidView(
                factory = { c ->
                    VideoView(c).apply {
                        val mc = MediaController(c)
                        mc.setAnchorView(this)
                        setMediaController(mc)
                        setVideoPath(v.item.path)
                        setOnPreparedListener { it.start() }
                    }
                },
                onRelease = { it.stopPlayback() },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun TextViewer(vm: FmViewModel, v: Viewer.TextDoc) {
    var text by remember(v) { mutableStateOf(v.text) }
    LaunchedEffect(text, v) { vm.viewerDirty = text != v.text }
    Column(Modifier.fillMaxSize()) {
        ViewerBar(v.item.name, onBack = { vm.closeViewer() }) {
            if (!v.truncated) IconBtn("💾", "Save") { vm.saveText(v, text) }
            IconBtn("↗️", "Open with") { vm.openExternal(v.item.path, true) }
        }
        Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF0D1220))) {
            BasicTextField(
                value = text,
                onValueChange = { if (!v.truncated) text = it },
                readOnly = v.truncated,
                textStyle = TextStyle(color = Color(0xFFE8ECF4), fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp),
                cursorBrush = SolidColor(Color(0xFFF2B232)),
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)
            )
        }
        if (v.truncated) {
            Text(
                "Large file: showing the first 300 KB. Editing is turned off.",
                Modifier.fillMaxWidth().background(Color(0xFFE8A317)).padding(10.dp),
                color = Color(0xFF14213D), fontWeight = FontWeight.SemiBold, fontSize = 13.sp
            )
        }
    }
}
