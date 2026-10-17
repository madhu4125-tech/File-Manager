@file:OptIn(ExperimentalFoundationApi::class)

package com.madhu.filemanager

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File

// ---------------------------------------------------------------- app root

@Composable
fun App(vm: FmViewModel, onGrant: () -> Unit) {
    FmTheme(vm.dark) {
        val p = LocalPal.current
        Box(Modifier.fillMaxSize().background(p.bg)) {
            if (vm.gate) Gate(onGrant) else MainScreen(vm)
            ViewerHost(vm)
            DialogHost(vm)
            BusyHost(vm)
        }
    }
}

@Composable
fun Gate(onGrant: () -> Unit) {
    val p = LocalPal.current
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🗂️", fontSize = 64.sp)
        Spacer(Modifier.height(14.dp))
        Text("Allow access to your files", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = p.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "To browse, move, zip and clean up files, File Manager needs “All files access”. Tap the button, switch it on, then come back.",
            color = p.muted, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = onGrant,
            colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.accentInk)
        ) { Text("Grant access", fontWeight = FontWeight.ExtraBold) }
    }
}

@Composable
fun MainScreen(vm: FmViewModel) {
    Column(Modifier.fillMaxSize()) {
        TopBar(vm)
        PasteBar(vm)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (vm.tab) {
                NavTab.FILES -> if (vm.results == null && vm.cwd == null) HomeScreen(vm) else FileListScreen(vm)
                NavTab.CATS -> CategoriesScreen(vm)
                NavTab.STORAGE -> StorageScreen(vm)
            }
        }
        BottomBar(vm)
    }
}

// ----------------------------------------------------------------- bars

@Composable
fun IconBtn(label: String, desc: String, color: Color = Color.White, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick).semantics { contentDescription = desc },
        contentAlignment = Alignment.Center
    ) { Text(label, fontSize = 20.sp, color = color) }
}

@Composable
fun TopBar(vm: FmViewModel) {
    val p = LocalPal.current
    Row(
        Modifier.fillMaxWidth().background(p.top).padding(horizontal = 6.dp, vertical = 6.dp).heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val results = vm.results
        when {
            vm.selection.isNotEmpty() -> {
                IconBtn("✕", "Cancel selection") { vm.clearSel() }
                BarTitle("${vm.selection.size} selected", Modifier.weight(1f))
                IconBtn("☑", "Select all") { vm.selectAll() }
            }
            vm.searching -> SearchField(vm)
            vm.tab == NavTab.FILES && results != null -> {
                IconBtn("←", "Back") { vm.leaveResults() }
                BarTitle(results.title, Modifier.weight(1f))
                IconBtn("⋮", "Menu") { vm.showMainMenu() }
            }
            vm.tab == NavTab.FILES && vm.cwd != null -> {
                IconBtn("←", "Up") { vm.goUp() }
                Crumbs(vm, Modifier.weight(1f))
                IconBtn("🔍", "Search") { vm.openSearch() }
                IconBtn("⋮", "Menu") { vm.showMainMenu() }
            }
            else -> {
                val title = when (vm.tab) {
                    NavTab.FILES -> "File Manager"
                    NavTab.CATS -> "Categories"
                    NavTab.STORAGE -> "Storage"
                }
                BarTitle(title, Modifier.weight(1f))
                if (vm.tab == NavTab.FILES) IconBtn("🔍", "Search") { vm.openSearch() }
                IconBtn("⋮", "Menu") { vm.showMainMenu() }
            }
        }
    }
}

@Composable
fun BarTitle(text: String, modifier: Modifier) {
    Text(
        text, modifier = modifier.padding(horizontal = 8.dp), color = Color.White, fontSize = 19.sp,
        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun Crumbs(vm: FmViewModel, modifier: Modifier) {
    val crumbs = vm.crumbs()
    val state = rememberLazyListState()
    LaunchedEffect(crumbs.size) { if (crumbs.isNotEmpty()) state.scrollToItem(crumbs.lastIndex) }
    LazyRow(modifier, state = state, verticalAlignment = Alignment.CenterVertically) {
        items(crumbs.size) { i ->
            val (label, path) = crumbs[i]
            val last = i == crumbs.lastIndex
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (i > 0) Text("›", color = Color.White.copy(alpha = 0.4f))
                Text(
                    label,
                    color = Color.White.copy(alpha = if (last) 1f else 0.75f),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.navigate(path) }.padding(horizontal = 8.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
fun RowScope.SearchField(vm: FmViewModel) {
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { focus.requestFocus() }
    IconBtn("←", "Close search") { vm.closeSearch() }
    TextField(
        value = vm.query,
        onValueChange = { vm.query = it },
        singleLine = true,
        placeholder = { Text("Search file names…") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            focusManager.clearFocus()
            vm.doSearch()
        }),
        shape = RoundedCornerShape(24.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White.copy(alpha = 0.14f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.14f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedPlaceholderColor = Color.White.copy(alpha = 0.6f),
            unfocusedPlaceholderColor = Color.White.copy(alpha = 0.6f),
            cursorColor = Color.White,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        modifier = Modifier.weight(1f).heightIn(min = 48.dp).focusRequester(focus)
    )
    IconBtn(if (vm.searchAll) "🌐" else "📁", if (vm.searchAll) "Searching all storage" else "Searching this folder") {
        vm.searchAll = !vm.searchAll
        vm.toast(if (vm.searchAll) "Searching all storage" else "Searching this folder")
    }
}

@Composable
fun PasteBar(vm: FmViewModel) {
    val p = LocalPal.current
    val c = vm.clip
    if (c == null || vm.tab != NavTab.FILES || vm.results != null || vm.cwd == null || vm.selection.isNotEmpty()) return
    Row(
        Modifier.fillMaxWidth().background(p.accent).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${if (c.cut) "Move" else "Copy"} ${c.paths.size} item${if (c.paths.size > 1) "s" else ""}",
            Modifier.weight(1f), color = p.accentInk, fontWeight = FontWeight.SemiBold
        )
        Text(
            "Cancel", color = p.accentInk, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { vm.cancelClip() }.padding(horizontal = 14.dp, vertical = 8.dp)
        )
        Text(
            "Paste here", color = Color.White, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(p.accentInk).clickable { vm.paste() }.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun BottomBar(vm: FmViewModel) {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth().background(p.surface)) {
        if (vm.selection.isNotEmpty()) {
            val paths = vm.selection.toList()
            BarAction("📋", "Copy", p.text) { vm.setClip(false, paths) }
            BarAction("✂️", "Move", p.text) { vm.setClip(true, paths) }
            BarAction("🗜️", "Zip", p.text) { vm.zip(paths) }
            BarAction("⤴️", "Share", p.text) { vm.share(paths) }
            BarAction("🗑️", "Delete", p.danger) { vm.confirmDelete(paths) }
            BarAction("⋯", "More", p.text) { vm.showSelectionMore() }
        } else {
            NavItem("📁", "Files", vm.tab == NavTab.FILES) { vm.tab = NavTab.FILES; vm.searching = false }
            NavItem("🗂️", "Categories", vm.tab == NavTab.CATS) { vm.tab = NavTab.CATS; vm.searching = false }
            NavItem("📊", "Storage", vm.tab == NavTab.STORAGE) { vm.tab = NavTab.STORAGE; vm.searching = false }
        }
    }
}

@Composable
fun RowScope.BarAction(icon: String, label: String, color: Color, onClick: () -> Unit) {
    Column(
        Modifier.weight(1f).clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, fontSize = 20.sp)
        Text(label, fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun RowScope.NavItem(icon: String, label: String, on: Boolean, onClick: () -> Unit) {
    val p = LocalPal.current
    Column(
        Modifier.weight(1f).clickable(onClick = onClick).padding(top = 8.dp, bottom = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, fontSize = 21.sp)
        Text(label, fontSize = 11.5.sp, color = if (on) p.text else p.muted, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Box(Modifier.width(22.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (on) p.accent else Color.Transparent))
    }
}

// ------------------------------------------------------------------- home

@Composable
fun HomeScreen(vm: FmViewModel) {
    val p = LocalPal.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
        vm.volumes.forEach { v ->
            val used = v.total - v.free
            Column(
                Modifier.fillMaxWidth().padding(10.dp).clip(RoundedCornerShape(14.dp)).background(p.surface)
                    .clickable { vm.navigate(v.path) }.padding(16.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${if (v.name == "SD card") "💾" else "📱"} ${v.name}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = p.text)
                    Text("${fmtSize(v.free)} free", color = p.muted, fontSize = 13.sp)
                }
                Spacer(Modifier.height(10.dp))
                UsageBar(used.toFloat() / v.total.coerceAtLeast(1))
                Spacer(Modifier.height(6.dp))
                Text("${fmtSize(used)} used of ${fmtSize(v.total)}", color = p.muted, fontSize = 12.5.sp)
            }
        }
        Text("Quick access", Modifier.padding(start = 16.dp, top = 14.dp, bottom = 2.dp), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = p.text)
        vm.quickAccess().chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (icon, name, path) ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(p.surface).clickable { vm.navigate(path) }.padding(vertical = 14.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(icon, fontSize = 26.sp)
                        Text(name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun UsageBar(fraction: Float) {
    val p = LocalPal.current
    Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(p.line)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(p.accent))
    }
}

// ----------------------------------------------------------- file listing

@Composable
fun Empty(icon: String, text: String) {
    val p = LocalPal.current
    Column(Modifier.fillMaxWidth().padding(vertical = 60.dp, horizontal = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 44.sp)
        Spacer(Modifier.height(10.dp))
        Text(text, color = p.muted, textAlign = TextAlign.Center)
    }
}

@Composable
fun FileListScreen(vm: FmViewModel) {
    val p = LocalPal.current
    val list = vm.items
    val res = vm.results
    Column(Modifier.fillMaxSize()) {
        if (res != null) {
            Text(
                "${list.size} item${if (list.size == 1) "" else "s"}${if (res.truncated) " (showing the first ${list.size})" else ""}",
                Modifier.padding(start = 16.dp, top = 12.dp, bottom = 2.dp), fontWeight = FontWeight.Bold, color = p.text
            )
        }
        if (list.isEmpty()) {
            Empty(if (res != null) "🔎" else "📭", if (res != null) "No matching files" else "This folder is empty")
        } else {
            key(vm.cwd, res?.title, res?.from) {
                if (vm.grid) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(104.dp),
                        state = rememberLazyGridState(),
                        contentPadding = PaddingValues(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(list, key = { it.path }) { item -> FileTile(vm, item) }
                    }
                } else {
                    LazyColumn(
                        state = rememberLazyListState(),
                        modifier = Modifier.fillMaxSize().padding(10.dp).clip(RoundedCornerShape(14.dp)).background(p.surface)
                    ) {
                        items(list, key = { it.path }) { item ->
                            FileRow(vm, item, showPath = res != null)
                            Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Thumb(item: FileItem, modifier: Modifier, fontSize: TextUnit = 24.sp) {
    val p = LocalPal.current
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(p.bg), contentAlignment = Alignment.Center) {
        if (item.isRasterImage) {
            val ctx = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(ctx).data(File(item.path)).size(180).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(item.icon, fontSize = fontSize)
        }
    }
}

@Composable
fun FileRow(vm: FmViewModel, item: FileItem, showPath: Boolean) {
    val p = LocalPal.current
    val haptic = LocalHapticFeedback.current
    val selected = item.path in vm.selection
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) p.sel else Color.Transparent)
            .combinedClickable(
                onClick = { vm.tap(item) },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.toggleSel(item)
                }
            )
            .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Thumb(item, Modifier.size(42.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, fontWeight = FontWeight.SemiBold, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = if (item.isDir) "${item.size} item${if (item.size == 1L) "" else "s"}" else fmtSize(item.size)
            Text("$sub · ${fmtDate(item.mtime)}", color = p.muted, fontSize = 12.5.sp, maxLines = 1)
            if (showPath) Text(File(item.path).parent ?: "", color = p.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (vm.selection.isNotEmpty()) {
            SelectDot(selected)
        } else {
            IconBtn("⋮", "Actions for ${item.name}", p.muted) { vm.showItemMenu(item) }
        }
    }
}

@Composable
fun SelectDot(on: Boolean) {
    val p = LocalPal.current
    Box(
        Modifier.padding(horizontal = 10.dp).size(24.dp).clip(CircleShape).background(if (on) p.accent else p.line),
        contentAlignment = Alignment.Center
    ) { if (on) Text("✓", color = p.accentInk, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp) }
}

@Composable
fun FileTile(vm: FmViewModel, item: FileItem) {
    val p = LocalPal.current
    val haptic = LocalHapticFeedback.current
    val selected = item.path in vm.selection
    Column(
        Modifier.clip(RoundedCornerShape(14.dp))
            .background(if (selected) p.sel else p.surface)
            .combinedClickable(
                onClick = { vm.tap(item) },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.toggleSel(item)
                }
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Thumb(item, Modifier.fillMaxWidth().height(84.dp), fontSize = 38.sp)
        Spacer(Modifier.height(6.dp))
        Text(item.name, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = p.text, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

// ------------------------------------------------------------- categories

@Composable
fun CategoriesScreen(vm: FmViewModel) {
    val p = LocalPal.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp)) {
        val tiles: List<Triple<String, String, Color>> =
            CATS.map { Triple(it.icon, it.label, it.color) } + Triple("⬇️", "Downloads", Color(0xFF14213D))
        tiles.chunked(2).forEachIndexed { rowIdx, row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEachIndexed { colIdx, (icon, label, color) ->
                    val idx = rowIdx * 2 + colIdx
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(p.surface).clickable {
                            if (idx < CATS.size) vm.runCategory(CATS[idx])
                            else vm.volumes.firstOrNull()?.let { vm.navigate(it.path + "/Download") }
                        }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(color), contentAlignment = Alignment.Center) {
                            Text(icon, fontSize = 22.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(label, fontWeight = FontWeight.SemiBold, color = p.text, fontSize = 15.sp)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Text(
            "Categories scan your whole internal storage, so large phones can take a few seconds.",
            color = p.muted, fontSize = 12.5.sp, modifier = Modifier.padding(8.dp)
        )
    }
}

// ---------------------------------------------------------------- storage

@Composable
fun StorageScreen(vm: FmViewModel) {
    val p = LocalPal.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
        vm.volumes.forEach { v ->
            val used = v.total - v.free
            Column(Modifier.fillMaxWidth().padding(10.dp).clip(RoundedCornerShape(14.dp)).background(p.surface).padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(v.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = p.text)
                    Text("${fmtSize(used)} of ${fmtSize(v.total)}", color = p.muted, fontSize = 13.sp)
                }
                Spacer(Modifier.height(10.dp))
                UsageBar(used.toFloat() / v.total.coerceAtLeast(1))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${fmtSize(v.free)} free", color = p.muted, fontSize = 12.5.sp)
                    Button(
                        onClick = { vm.analyze(v.path) },
                        colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.accentInk)
                    ) { Text("Analyze", fontWeight = FontWeight.Bold) }
                }
            }
        }
        val a = vm.an
        when {
            a == null -> Empty("📊", "Tap Analyze to see what is using your space.")
            a.loading -> Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = p.accent)
                Spacer(Modifier.height(12.dp))
                Text("Scanning ${a.path}…", color = p.muted, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            a.error != null -> Text("Could not scan: ${a.error}", Modifier.padding(16.dp), color = p.danger)
            else -> {
                val d = a.data
                if (d != null) AnalysisView(vm, a, d)
            }
        }
    }
}

@Composable
fun AnalysisView(vm: FmViewModel, a: An, d: AnalyzeResult) {
    val p = LocalPal.current
    val isRoot = vm.volumes.any { it.path == a.path }
    Column(Modifier.fillMaxWidth().padding(10.dp).clip(RoundedCornerShape(14.dp)).background(p.surface).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(File(a.path).name.ifEmpty { a.path }, fontWeight = FontWeight.Bold, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${fmtSize(d.total)} in this folder", color = p.muted, fontSize = 12.5.sp)
            }
            if (!isRoot) {
                Button(
                    onClick = { File(a.path).parent?.let { vm.analyze(it) } },
                    colors = ButtonDefaults.buttonColors(containerColor = p.bg, contentColor = p.text)
                ) { Text("Up") }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(7.dp)).background(p.line)) {
            d.categories.filter { it.bytes > 0 }.forEach { c ->
                val w = (c.bytes.toFloat() / d.total.coerceAtLeast(1)).coerceAtLeast(0.004f)
                Box(Modifier.weight(w).fillMaxHeight().background(CAT_COLORS[c.name] ?: Color.Gray))
            }
        }
        Spacer(Modifier.height(10.dp))
        d.categories.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { c ->
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(CAT_COLORS[c.name] ?: Color.Gray))
                        Spacer(Modifier.width(7.dp))
                        Text(c.name, fontSize = 13.sp, color = p.text, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(fmtSize(c.bytes), fontSize = 13.sp, color = p.muted)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }

    Text("Biggest items here", Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp), fontWeight = FontWeight.Bold, color = p.text)
    val max = (d.children.firstOrNull()?.size ?: 1L).coerceAtLeast(1)
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp).clip(RoundedCornerShape(14.dp)).background(p.surface)) {
        d.children.take(25).forEach { c ->
            Row(
                Modifier.fillMaxWidth().clickable { if (c.isDir) vm.analyze(c.path) else vm.openItem(c) }.padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Thumb(c, Modifier.size(40.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.name, fontWeight = FontWeight.SemiBold, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(fmtSize(c.size), color = p.muted, fontSize = 12.5.sp)
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth(c.size.toFloat() / max).height(4.dp).clip(RoundedCornerShape(2.dp)).background(p.accent))
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
        }
    }

    if (d.largest.isNotEmpty()) {
        Text("Largest files", Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp), fontWeight = FontWeight.Bold, color = p.text)
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp).clip(RoundedCornerShape(14.dp)).background(p.surface)) {
            d.largest.forEach { f ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.openItem(f) }.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Thumb(f, Modifier.size(40.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.name, fontWeight = FontWeight.SemiBold, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${fmtSize(f.size)} · ${File(f.path).parent ?: ""}", color = p.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconBtn("🗑️", "Delete ${f.name}", p.muted) { vm.anDelete(f.path) }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
            }
        }
    }
}
