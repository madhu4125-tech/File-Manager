package com.madhu.filemanager

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FmViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Context get() = getApplication<Application>()
    private val prefs = ctx.getSharedPreferences("fm", Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- state
    var gate by mutableStateOf(false)
    var volumes by mutableStateOf<List<Volume>>(emptyList())
    var tab by mutableStateOf(NavTab.FILES)
    var cwd by mutableStateOf<String?>(null)
    var entries by mutableStateOf<List<FileItem>>(emptyList())
    var results by mutableStateOf<Results?>(null)
    var selection by mutableStateOf<Set<String>>(emptySet())
    var clip by mutableStateOf<Clip?>(null)
    var searching by mutableStateOf(false)
    var query by mutableStateOf("")
    var searchAll by mutableStateOf(false)
    var dialog by mutableStateOf<Dlg?>(null)
    var viewer by mutableStateOf<Viewer?>(null)
    var viewerDirty by mutableStateOf(false)
    var busy by mutableStateOf<String?>(null)
    var busyCancelable by mutableStateOf(false)
    var an by mutableStateOf<An?>(null)
    var trash by mutableStateOf<List<TrashItem>?>(null)
    var dups by mutableStateOf<List<DupGroup>?>(null)
    var dupSel by mutableStateOf<Set<String>>(emptySet())
    var marks by mutableStateOf(
        prefs.getString("marks", "")!!.split("\n").filter { it.isNotBlank() }
    )

    // settings (persisted)
    var grid by mutableStateOf(prefs.getBoolean("grid", false))
    var sortKey by mutableStateOf(runCatching { SortKey.valueOf(prefs.getString("sortKey", "NAME")!!) }.getOrDefault(SortKey.NAME))
    var sortAsc by mutableStateOf(prefs.getBoolean("sortAsc", true))
    var showHidden by mutableStateOf(prefs.getBoolean("hidden", false))
    var useTrash by mutableStateOf(prefs.getBoolean("useTrash", true))
    var dark by mutableStateOf(
        prefs.getBoolean(
            "dark",
            (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        )
    )

    private var busyJob: Job? = null
    private var anJob: Job? = null

    /** Items currently shown: search/category results, or the sorted, filtered folder listing. */
    val items: List<FileItem> by derivedStateOf {
        val r = results
        val base = r?.items ?: entries
        val filtered = base.filter { showHidden || !it.hidden }
        if (r != null) filtered else filtered.sortedWith(comparator())
    }

    private fun comparator(): Comparator<FileItem> {
        val dir = if (sortAsc) 1 else -1
        val key = sortKey
        return Comparator { a, b ->
            if (a.isDir != b.isDir) {
                if (a.isDir) -1 else 1
            } else {
                val c = when (key) {
                    SortKey.SIZE -> a.size.compareTo(b.size)
                    SortKey.DATE -> a.mtime.compareTo(b.mtime)
                    SortKey.TYPE -> {
                        val t = a.ext.compareTo(b.ext)
                        if (t != 0) t else a.name.compareTo(b.name, ignoreCase = true)
                    }
                    SortKey.NAME -> a.name.compareTo(b.name, ignoreCase = true)
                }
                c * dir
            }
        }
    }

    // ------------------------------------------------------------- helpers
    fun toast(msg: String) {
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    }

    private fun savePrefs() {
        prefs.edit()
            .putBoolean("grid", grid)
            .putString("sortKey", sortKey.name)
            .putBoolean("sortAsc", sortAsc)
            .putBoolean("hidden", showHidden)
            .putBoolean("dark", dark)
            .putBoolean("useTrash", useTrash)
            .putString("marks", marks.joinToString("\n"))
            .apply()
    }

    private fun op(
        msg: String,
        ok: String? = null,
        after: (Boolean) -> Unit = {},
        block: suspend () -> Unit
    ) {
        busy = msg
        busyCancelable = false
        busyJob = viewModelScope.launch {
            var success = false
            try {
                withContext(Dispatchers.IO) { block() }
                success = true
            } catch (e: CancellationException) {
                busy = null
                throw e
            } catch (e: Exception) {
                toast(e.message ?: "Something went wrong")
            }
            busy = null
            if (success && ok != null) toast(ok)
            after(success)
        }
    }

    private fun scan(
        msg: String,
        title: String,
        from: ResFrom,
        newestFirst: Boolean,
        block: suspend () -> Pair<List<FileItem>, Boolean>
    ) {
        busy = msg
        busyCancelable = true
        busyJob = viewModelScope.launch {
            try {
                val (list, truncated) = withContext(Dispatchers.IO) { block() }
                busy = null
                results = Results(title, if (newestFirst) list.sortedByDescending { it.mtime } else list, from, truncated)
                tab = NavTab.FILES
                selection = emptySet()
            } catch (e: CancellationException) {
                busy = null
                throw e
            } catch (e: Exception) {
                busy = null
                toast(e.message ?: "Scan failed")
            }
        }
    }

    fun cancelBusy() {
        busyJob?.cancel()
        busy = null
    }

    // ------------------------------------------------------- access / boot
    fun checkAccess() {
        if (FileOps.hasAllFilesAccess(ctx)) {
            if (gate || volumes.isEmpty()) {
                gate = false
                volumes = FileOps.volumes(ctx)
            }
        } else {
            gate = true
        }
    }

    fun onResume() {
        checkAccess()
        val c = cwd
        if (!gate && c != null && viewer == null && selection.isEmpty() && dialog == null && busy == null && results == null) {
            navigate(c, keep = true)
        }
    }

    // ----------------------------------------------------------- navigation
    fun navigate(path: String, keep: Boolean = false) {
        viewModelScope.launch {
            try {
                val list = withContext(Dispatchers.IO) { FileOps.list(path) }
                results = null
                searching = false
                query = ""
                cwd = path
                entries = list
                tab = NavTab.FILES
                if (!keep) selection = emptySet()
            } catch (e: Exception) {
                toast(e.message ?: "Cannot open folder")
            }
        }
    }

    fun goUp() {
        val c = cwd ?: return
        if (volumes.any { it.path == c } || c == "/") {
            cwd = null
            selection = emptySet()
        } else {
            navigate(File(c).parent ?: "/")
        }
    }

    fun leaveResults() {
        val from = results?.from
        results = null
        searching = false
        query = ""
        selection = emptySet()
        if (from == ResFrom.CATS) tab = NavTab.CATS
    }

    fun closeSearch() {
        searching = false
        if (results?.from == ResFrom.SEARCH) results = null
    }

    fun crumbs(): List<Pair<String, String>> {
        val c = cwd ?: return emptyList()
        val vol = volumes.filter { c == it.path || c.startsWith(it.path + "/") }.maxByOrNull { it.path.length }
        val base = vol?.path ?: ""
        val out = mutableListOf<Pair<String, String>>((vol?.name ?: "/") to base.ifEmpty { "/" })
        var p = base
        c.removePrefix(base).split("/").filter { it.isNotEmpty() }.forEach {
            p += "/$it"
            out += it to p
        }
        return out
    }

    fun quickAccess(): List<Triple<String, String, String>> {
        val base = volumes.firstOrNull()?.path ?: return emptyList()
        val list = mutableListOf(
            Triple("⬇️", "Downloads", "$base/Download"),
            Triple("📷", "Camera", "$base/DCIM"),
            Triple("🖼️", "Pictures", "$base/Pictures"),
            Triple("📄", "Documents", "$base/Documents"),
            Triple("🎬", "Movies", "$base/Movies"),
            Triple("🎵", "Music", "$base/Music")
        )
        marks.forEach { list.add(Triple("⭐", File(it).name, it)) }
        return list
    }

    fun onBack(): Boolean {
        if (viewer != null) { closeViewer(); return true }
        if (trash != null || dups != null) { closePanels(); return true }
        if (selection.isNotEmpty()) { selection = emptySet(); return true }
        if (searching) { closeSearch(); return true }
        if (results != null) { leaveResults(); return true }
        if (tab != NavTab.FILES) { tab = NavTab.FILES; return true }
        if (cwd != null) { goUp(); return true }
        return false
    }

    // ------------------------------------------------------------ selection
    fun tap(item: FileItem) {
        if (selection.isNotEmpty()) toggleSel(item) else openItem(item)
    }

    fun toggleSel(item: FileItem) {
        selection = if (item.path in selection) selection - item.path else selection + item.path
    }

    fun selectAll() {
        selection = items.map { it.path }.toSet()
    }

    fun clearSel() {
        selection = emptySet()
    }

    private fun afterChange(removed: List<String>? = null) {
        selection = emptySet()
        val r = results
        val c = cwd
        if (r != null && removed != null) {
            val rs = removed.toSet()
            results = r.copy(items = r.items.filter { it.path !in rs })
        } else if (c != null) {
            navigate(c, keep = true)
        }
    }

    // --------------------------------------------------------------- opening
    fun openItem(item: FileItem) {
        if (item.isDir) {
            navigate(item.path)
            return
        }
        when {
            item.isRasterImage -> {
                val imgs = items.filter { it.isRasterImage }
                val idx = imgs.indexOfFirst { it.path == item.path }
                viewer = if (idx >= 0) Viewer.Images(imgs, idx) else Viewer.Images(listOf(item), 0)
            }
            item.kind == Kind.VIDEO || item.kind == Kind.AUDIO -> viewer = Viewer.Media(item, item.kind == Kind.VIDEO)
            item.kind == Kind.TEXT -> openText(item)
            item.ext == "zip" -> dialog = Dlg.Menu(
                item.name,
                listOf(
                    MenuEntry("📂", "Extract here") { extract(item, true) },
                    MenuEntry("🗂️", "Extract to “${item.name.removeSuffix(".zip")}” folder") { extract(item, false) },
                    MenuEntry("↗️", "Open with…") { openExternal(item.path, true) }
                )
            )
            else -> openExternal(item.path, false)
        }
    }

    fun openExternal(path: String, chooser: Boolean) {
        try {
            FileOps.open(ctx, path, chooser)
        } catch (e: Exception) {
            toast(e.message ?: "Cannot open file")
        }
    }

    fun share(paths: List<String>) {
        try {
            FileOps.share(ctx, paths)
        } catch (e: Exception) {
            toast(e.message ?: "Cannot share")
        }
    }

    private fun openText(item: FileItem) {
        viewModelScope.launch {
            try {
                val (t, trunc) = withContext(Dispatchers.IO) { FileOps.readText(File(item.path), 300_000) }
                viewerDirty = false
                viewer = Viewer.TextDoc(item, t, trunc)
            } catch (e: Exception) {
                toast(e.message ?: "Cannot read file")
            }
        }
    }

    fun saveText(v: Viewer.TextDoc, text: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { FileOps.writeText(File(v.item.path), text) }
                viewer = v.copy(text = text)
                viewerDirty = false
                toast("Saved")
            } catch (e: Exception) {
                toast(e.message ?: "Could not save")
            }
        }
    }

    fun closeViewer() {
        if (viewerDirty) {
            dialog = Dlg.Confirm("Discard changes?", "You have unsaved edits.", "Discard", true) {
                viewer = null
                viewerDirty = false
            }
        } else {
            viewer = null
        }
    }

    // ------------------------------------------------------------ operations
    fun confirmDelete(paths: List<String>) {
        confirmDeleteThen(paths) { afterChange(paths) }
    }

    private fun confirmDeleteThen(paths: List<String>, then: () -> Unit) {
        val name = if (paths.size == 1) "“${File(paths[0]).name}”" else "${paths.size} items"
        val bin = useTrash
        val msg = if (bin) "It will stay in the recycle bin for 30 days." else "This cannot be undone."
        dialog = Dlg.Confirm("Delete $name?", msg, if (bin) "Move to bin" else "Delete", !bin) {
            runDelete(paths, then)
        }
    }

    private fun runDelete(paths: List<String>, then: () -> Unit) {
        val bin = useTrash
        op(
            if (bin) "Moving to recycle bin…" else "Deleting…",
            if (bin) "Moved to recycle bin" else "Deleted",
            after = { then() }
        ) {
            if (bin) FileOps.moveToTrash(paths) else FileOps.delete(paths)
        }
    }

    fun rename(item: FileItem) {
        dialog = Dlg.Prompt("Rename", item.name, "Rename", !item.isDir) { nn ->
            if (nn.isNotBlank() && nn != item.name) {
                op("Renaming…", "Renamed", after = { ok -> if (ok) afterChange(listOf(item.path)) }) {
                    FileOps.rename(File(item.path), nn)
                }
            }
        }
    }

    fun zip(paths: List<String>) {
        val c = cwd
        val dest = if (results == null && c != null) c else File(paths.first()).parent ?: return
        val def = if (paths.size == 1) File(paths[0]).nameWithoutExtension else "Archive"
        dialog = Dlg.Prompt("Create zip", def, "Zip", false) { name ->
            if (name.isNotBlank()) {
                op("Zipping…", "Zip created", after = { afterChange() }) {
                    FileOps.zip(paths, File(dest), name.removeSuffix(".zip"))
                }
            }
        }
    }

    fun extract(item: FileItem, here: Boolean) {
        val dir = File(item.path).parentFile ?: return
        val dest = if (here) dir else File(dir, item.name.removeSuffix(".zip").removeSuffix(".ZIP"))
        var n = 0
        op("Extracting…", null, after = { ok ->
            if (ok) toast("Extracted $n file${if (n == 1) "" else "s"}")
            afterChange()
        }) {
            n = FileOps.unzip(File(item.path), dest)
        }
    }

    fun setClip(cut: Boolean, paths: List<String>) {
        clip = Clip(cut, paths)
        selection = emptySet()
        if (results != null) leaveResults()
        toast("Open the destination folder and tap Paste here")
    }

    fun cancelClip() {
        clip = null
    }

    fun paste() {
        val c = clip ?: return
        val dest = cwd ?: return
        op(
            if (c.cut) "Moving…" else "Copying…",
            if (c.cut) "Moved" else "Copied",
            after = { ok ->
                if (ok) clip = null
                navigate(dest, keep = true)
            }
        ) {
            if (c.cut) FileOps.move(c.paths, File(dest)) else FileOps.copy(c.paths, File(dest))
        }
    }

    fun newFolder() {
        val c = cwd ?: return
        dialog = Dlg.Prompt("New folder", "", "Create", false) { n ->
            if (n.isNotBlank()) {
                op("Creating…", "Folder created", after = { navigate(c, keep = true) }) {
                    FileOps.mkdir(File(c, n))
                }
            }
        }
    }

    fun newTextFile() {
        val c = cwd ?: return
        dialog = Dlg.Prompt("New text file", "note.txt", "Create", true) { n ->
            if (n.isNotBlank()) {
                val f = File(c, n)
                op("Creating…", "File created", after = { ok ->
                    navigate(c, keep = true)
                    if (ok) openText(FileOps.item(f))
                }) {
                    if (f.exists()) throw java.io.IOException("That name already exists")
                    FileOps.writeText(f, "")
                }
            }
        }
    }

    fun props(item: FileItem) {
        viewModelScope.launch {
            try {
                val p = withContext(Dispatchers.IO) { FileOps.props(File(item.path)) }
                dialog = Dlg.Props(item, p)
            } catch (e: Exception) {
                toast(e.message ?: "Cannot read properties")
            }
        }
    }

    fun copyPath(path: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("path", path))
        toast("Path copied")
    }

    fun toggleMark(path: String) {
        val had = path in marks
        marks = if (had) marks - path else marks + path
        savePrefs()
        toast(if (had) "Removed from Quick access" else "Added to Quick access")
    }

    // --------------------------------------------------- search / categories
    fun openSearch() {
        searching = true
    }

    fun doSearch() {
        val q = query.trim()
        if (q.isEmpty()) return
        val c = cwd
        val rootVol = volumes.firstOrNull()?.path
        val root: String = if (searchAll) (rootVol ?: return) else (c ?: rootVol ?: return)
        scan("Searching…", "Results", ResFrom.SEARCH, false) {
            FileOps.search(File(root), q, emptySet(), 1500)
        }
    }

    fun runCategory(c: Cat) {
        val root = volumes.firstOrNull()?.path ?: return
        scan("Scanning storage…", c.label, ResFrom.CATS, true) {
            FileOps.search(File(root), "", c.exts, 5000)
        }
    }

    fun analyze(path: String) {
        anJob?.cancel()
        an = An(path, loading = true)
        anJob = viewModelScope.launch {
            try {
                val d = withContext(Dispatchers.IO) { FileOps.analyze(File(path)) }
                an = An(path, data = d)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                an = An(path, error = e.message ?: "Scan failed")
            }
        }
    }

    fun anDelete(path: String) {
        confirmDeleteThen(listOf(path)) { an?.let { analyze(it.path) } }
    }

    // ------------------------------------------------- recycle bin and duplicates
    fun closePanels() {
        trash = null
        dups = null
        dupSel = emptySet()
    }

    fun openTrash() {
        viewModelScope.launch {
            try {
                val l = withContext(Dispatchers.IO) {
                    FileOps.purgeOldTrash(30)
                    FileOps.listTrash()
                }
                dups = null
                dupSel = emptySet()
                trash = l
            } catch (e: Exception) {
                toast(e.message ?: "Cannot open the recycle bin")
            }
        }
    }

    private fun reloadTrash() {
        viewModelScope.launch {
            try {
                val l = withContext(Dispatchers.IO) { FileOps.listTrash() }
                trash = l
            } catch (e: Exception) {
                toast(e.message ?: "Cannot read the recycle bin")
            }
        }
    }

    fun showTrashMenu() {
        dialog = Dlg.Menu(
            "Recycle bin",
            listOf(MenuEntry("🧹", "Empty recycle bin", danger = true) { confirmEmptyTrash() })
        )
    }

    fun showTrashItemMenu(t: TrashItem) {
        dialog = Dlg.Menu(
            t.name,
            listOf(
                MenuEntry("♻️", "Restore to original folder") { restoreTrashItem(t) },
                MenuEntry("🗑️", "Delete forever", danger = true) { deleteTrashItem(t) }
            )
        )
    }

    private fun restoreTrashItem(t: TrashItem) {
        op("Restoring…", "Restored", after = { reloadTrash() }) { FileOps.restoreTrash(t) }
    }

    private fun deleteTrashItem(t: TrashItem) {
        dialog = Dlg.Confirm("Delete forever?", "“${t.name}” will be permanently deleted.", "Delete", true) {
            op("Deleting…", "Deleted", after = { reloadTrash() }) { FileOps.deleteTrash(t) }
        }
    }

    private fun confirmEmptyTrash() {
        dialog = Dlg.Confirm("Empty recycle bin?", "Everything in it will be permanently deleted.", "Empty", true) {
            op("Emptying…", "Recycle bin emptied", after = { reloadTrash() }) { FileOps.emptyTrash() }
        }
    }

    fun findDuplicates() {
        val root = volumes.firstOrNull()?.path ?: return
        busy = "Looking for duplicate files… this can take a while"
        busyCancelable = true
        busyJob = viewModelScope.launch {
            try {
                val g = withContext(Dispatchers.IO) { FileOps.findDuplicates(File(root), 10L * 1024L) }
                busy = null
                trash = null
                dupSel = emptySet()
                dups = g
            } catch (e: CancellationException) {
                busy = null
                throw e
            } catch (e: Exception) {
                busy = null
                toast(e.message ?: "Scan failed")
            }
        }
    }

    fun dupToggle(path: String) {
        dupSel = if (path in dupSel) dupSel - path else dupSel + path
    }

    /** Selects every copy except the oldest one in each set. */
    fun dupAutoSelect() {
        val g = dups ?: return
        val extra = HashSet<String>()
        for (group in g) for (f in group.items.drop(1)) extra.add(f.path)
        dupSel = extra
    }

    fun dupClear() {
        dupSel = emptySet()
    }

    fun dupDeleteSelected() {
        val sel = dupSel.toList()
        if (sel.isEmpty()) return
        var freed = 0L
        dups?.forEach { g -> freed += g.items.count { it.path in dupSel } * g.size }
        val bin = useTrash
        val msg = "Frees ${fmtSize(freed)}. " + if (bin) "They go to the recycle bin." else "This cannot be undone."
        dialog = Dlg.Confirm("Delete ${sel.size} duplicate file${if (sel.size == 1) "" else "s"}?", msg, if (bin) "Move to bin" else "Delete", !bin) {
            runDelete(sel) { removeFromDups(sel) }
        }
    }

    private fun removeFromDups(paths: List<String>) {
        val gone = paths.toSet()
        val current = dups ?: return
        val updated = ArrayList<DupGroup>()
        for (g in current) {
            val left = g.items.filter { it.path !in gone }
            if (left.size > 1) updated.add(DupGroup(g.size, left))
        }
        dups = updated
        dupSel = dupSel - gone
    }

    // ----------------------------------------------------------------- menus
    fun showMainMenu() {
        val e = mutableListOf<MenuEntry>()
        val browsing = tab == NavTab.FILES && results == null && cwd != null
        if (browsing) {
            e += MenuEntry("📁", "New folder") { newFolder() }
            e += MenuEntry("📝", "New text file") { newTextFile() }
            e += MenuEntry("☑️", "Select all") { selectAll() }
        }
        if (tab == NavTab.FILES && (cwd != null || results != null)) {
            val arrow = if (sortAsc) "↑" else "↓"
            e += MenuEntry("↕️", "Sort by: ${sortKey.name.lowercase()} $arrow") { showSortMenu() }
            e += MenuEntry(if (grid) "☰" else "▦", if (grid) "List view" else "Grid view") {
                grid = !grid
                savePrefs()
            }
        }
        e += MenuEntry("🗑️", "Recycle bin") { openTrash() }
        e += MenuEntry("🧬", "Find duplicate files") { findDuplicates() }
        e += MenuEntry("♻️", if (useTrash) "Recycle bin: ON (tap to turn off)" else "Recycle bin: OFF (tap to turn on)") {
            useTrash = !useTrash
            savePrefs()
        }
        e += MenuEntry(if (showHidden) "🙈" else "👁️", if (showHidden) "Hide hidden files" else "Show hidden files") {
            showHidden = !showHidden
            savePrefs()
        }
        e += MenuEntry(if (dark) "☀️" else "🌙", if (dark) "Light mode" else "Dark mode") {
            dark = !dark
            savePrefs()
        }
        if (browsing) e += MenuEntry("🔄", "Refresh") { cwd?.let { navigate(it, keep = true) } }
        dialog = Dlg.Menu("Menu", e)
    }

    private fun showSortMenu() {
        fun entry(key: SortKey, label: String) = MenuEntry(
            if (sortKey == key) (if (sortAsc) "↑" else "↓") else "•", label
        ) {
            if (sortKey == key) sortAsc = !sortAsc else {
                sortKey = key
                sortAsc = true
            }
            savePrefs()
        }
        dialog = Dlg.Menu(
            "Sort by (tap again to reverse)",
            listOf(entry(SortKey.NAME, "Name"), entry(SortKey.DATE, "Date modified"), entry(SortKey.SIZE, "Size"), entry(SortKey.TYPE, "Type"))
        )
    }

    fun showItemMenu(item: FileItem) {
        val e = mutableListOf<MenuEntry>()
        e += MenuEntry("📂", if (item.isDir) "Open folder" else "Open") { openItem(item) }
        if (!item.isDir) e += MenuEntry("↗️", "Open with…") { openExternal(item.path, true) }
        if (results != null) e += MenuEntry("📍", "Show in folder") { navigate(File(item.path).parent ?: "/") }
        if (item.ext == "zip") {
            e += MenuEntry("📦", "Extract here") { extract(item, true) }
            e += MenuEntry("🗂️", "Extract to folder") { extract(item, false) }
        }
        e += MenuEntry("✏️", "Rename") { rename(item) }
        e += MenuEntry("📋", "Copy") { setClip(false, listOf(item.path)) }
        e += MenuEntry("✂️", "Move") { setClip(true, listOf(item.path)) }
        e += MenuEntry("🗜️", "Zip") { zip(listOf(item.path)) }
        if (!item.isDir) e += MenuEntry("⤴️", "Share") { share(listOf(item.path)) }
        if (item.isDir) {
            e += MenuEntry("⭐", if (item.path in marks) "Remove from Quick access" else "Add to Quick access") { toggleMark(item.path) }
        }
        e += MenuEntry("ℹ️", "Properties") { props(item) }
        e += MenuEntry("🗑️", "Delete", danger = true) { confirmDelete(listOf(item.path)) }
        dialog = Dlg.Menu(item.name, e)
    }

    fun showSelectionMore() {
        val paths = selection.toList()
        val e = mutableListOf<MenuEntry>()
        if (paths.size == 1) {
            val sel = items.firstOrNull { x -> x.path == paths[0] }
            if (sel != null) {
                e += MenuEntry("✏️", "Rename") { rename(sel) }
                e += MenuEntry("ℹ️", "Properties") { props(sel) }
                if (sel.ext == "zip") e += MenuEntry("📦", "Extract here") { extract(sel, true) }
            }
        }
        e += MenuEntry("☑️", "Select all") { selectAll() }
        dialog = Dlg.Menu("${paths.size} selected", e)
    }
}
