package com.madhu.filemanager

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Locale
import java.util.PriorityQueue
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object FileOps {

    // ------------------------------------------------------------ permissions

    fun hasAllFilesAccess(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

    // ---------------------------------------------------------------- storage

    fun volumes(ctx: Context): List<Volume> {
        val out = mutableListOf<Volume>()
        out += volume("Internal storage", Environment.getExternalStorageDirectory())
        val dirs = ctx.getExternalFilesDirs(null)
        for (i in 1 until dirs.size) {
            val d = dirs[i] ?: continue
            val p = d.absolutePath
            val idx = p.indexOf("/Android")
            if (idx > 0) {
                val root = File(p.substring(0, idx))
                if (root.exists()) out += volume("SD card", root)
            }
        }
        return out
    }

    private fun volume(name: String, root: File): Volume {
        val sf = StatFs(root.absolutePath)
        return Volume(name, root.absolutePath, sf.totalBytes, sf.availableBytes)
    }

    // ----------------------------------------------------------------- browse

    fun item(f: File): FileItem {
        val d = f.isDirectory
        val size = if (d) (f.list()?.size ?: 0).toLong() else f.length()
        return FileItem(f.name, f.absolutePath, d, size, f.lastModified())
    }

    fun list(path: String): List<FileItem> {
        val dir = File(path)
        if (!dir.exists()) throw IOException("Folder not found")
        val files = dir.listFiles() ?: throw IOException("Cannot read this folder. Check All files access.")
        return files.map { item(it) }
    }

    fun props(f: File): FileProps {
        var size = 0L
        var files = 0L
        var folders = 0L
        fun walk(d: File) {
            val kids = d.listFiles() ?: return
            for (k in kids) {
                if (k.isDirectory) {
                    folders++
                    walk(k)
                } else {
                    files++
                    size += k.length()
                }
            }
        }
        if (f.isDirectory) walk(f) else {
            size = f.length()
            files = 1
        }
        return FileProps(size, files, folders, f.canRead(), f.canWrite(), f.lastModified())
    }

    // ------------------------------------------------------------- operations

    private fun unique(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (f.exists()) {
            f = File(dir, "$base ($i)$ext")
            i++
        }
        return f
    }

    private fun copyFile(src: File, dst: File) {
        BufferedInputStream(FileInputStream(src)).use { input ->
            BufferedOutputStream(FileOutputStream(dst)).use { output -> input.copyTo(output) }
        }
        dst.setLastModified(src.lastModified())
    }

    private fun copyRec(src: File, dstDir: File) {
        val dst = unique(dstDir, src.name)
        if (src.isDirectory) {
            if (!dst.mkdirs()) throw IOException("Cannot create folder ${dst.name}")
            src.listFiles()?.forEach { copyRec(it, dst) }
        } else {
            copyFile(src, dst)
        }
    }

    private fun deleteRec(f: File): Boolean {
        if (f.isDirectory) f.listFiles()?.forEach { deleteRec(it) }
        return f.delete()
    }

    private fun guardInside(src: File, destDir: File) {
        if (!src.exists()) throw IOException("${src.name} no longer exists")
        if (src.isDirectory) {
            val s = src.canonicalPath + File.separator
            val d = destDir.canonicalPath + File.separator
            if (d.startsWith(s)) throw IOException("Cannot put a folder inside itself")
        }
    }

    fun mkdir(f: File) {
        if (f.name.isBlank() || f.name.contains('/')) throw IOException("Invalid name")
        if (f.exists()) throw IOException("A file or folder with that name already exists")
        if (!f.mkdirs()) throw IOException("Could not create folder")
    }

    fun rename(f: File, newName: String) {
        val nn = newName.trim()
        if (nn.isEmpty() || nn.contains('/')) throw IOException("Invalid name")
        val dst = File(f.parentFile, nn)
        if (dst.exists()) throw IOException("A file or folder with that name already exists")
        if (!f.renameTo(dst)) throw IOException("Rename failed")
    }

    fun delete(paths: List<String>) {
        var failed = 0
        for (p in paths) if (!deleteRec(File(p))) failed++
        if (failed > 0) throw IOException("$failed item(s) could not be deleted")
    }

    fun copy(paths: List<String>, destDir: File) {
        for (p in paths) {
            val s = File(p)
            guardInside(s, destDir)
            copyRec(s, destDir)
        }
    }

    fun move(paths: List<String>, destDir: File) {
        for (p in paths) {
            val s = File(p)
            guardInside(s, destDir)
            if (s.parentFile?.canonicalPath == destDir.canonicalPath) continue
            val dst = unique(destDir, s.name)
            if (!s.renameTo(dst)) {
                copyRec(s, destDir)
                deleteRec(s)
            }
        }
    }

    fun writeText(f: File, text: String) {
        f.writeText(text, Charsets.UTF_8)
    }

    fun readText(f: File, max: Int): Pair<String, Boolean> {
        val buf = ByteArray(max)
        var n = 0
        FileInputStream(f).use { input ->
            while (n < max) {
                val r = input.read(buf, n, max - n)
                if (r <= 0) break
                n += r
            }
        }
        return String(buf, 0, n, Charsets.UTF_8) to (f.length() > max)
    }

    // ------------------------------------------------------------- zip / unzip

    fun zip(paths: List<String>, destDir: File, name: String): File {
        val out = unique(destDir, "$name.zip")
        ZipOutputStream(BufferedOutputStream(FileOutputStream(out))).use { z ->
            for (p in paths) {
                val f = File(p)
                addZip(z, f, f.name, out)
            }
        }
        return out
    }

    private fun addZip(z: ZipOutputStream, f: File, entry: String, self: File) {
        if (f.canonicalPath == self.canonicalPath) return
        if (f.isDirectory) {
            val kids = f.listFiles()
            if (kids == null || kids.isEmpty()) {
                z.putNextEntry(ZipEntry("$entry/"))
                z.closeEntry()
                return
            }
            for (k in kids) addZip(z, k, "$entry/${k.name}", self)
        } else {
            val e = ZipEntry(entry)
            e.time = f.lastModified()
            z.putNextEntry(e)
            FileInputStream(f).use { it.copyTo(z) }
            z.closeEntry()
        }
    }

    /** Extracts into destDir. Entries that would escape destDir (zip-slip) are skipped. */
    fun unzip(zip: File, destDir: File): Int {
        if (!destDir.exists() && !destDir.mkdirs()) throw IOException("Cannot create destination folder")
        val canon = destDir.canonicalPath + File.separator
        var count = 0
        ZipInputStream(BufferedInputStream(FileInputStream(zip))).use { zis ->
            var e: ZipEntry? = zis.nextEntry
            while (e != null) {
                val out = File(destDir, e.name)
                val cp = out.canonicalPath + (if (e.isDirectory) File.separator else "")
                if (cp.startsWith(canon)) {
                    if (e.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        BufferedOutputStream(FileOutputStream(out)).use { os -> zis.copyTo(os) }
                        if (e.time > 0) out.setLastModified(e.time)
                        count++
                    }
                }
                e = zis.nextEntry
            }
        }
        return count
    }

    // ------------------------------------------------------------------ search

    /** Cancellable recursive search. With exts set, matches files by extension (and optional name filter). */
    suspend fun search(root: File, query: String, exts: Set<String>, max: Int): Pair<List<FileItem>, Boolean> {
        val ql = query.lowercase(Locale.ROOT)
        val out = ArrayList<FileItem>()
        val stack = ArrayDeque<File>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val d = stack.removeLast()
            val kids = d.listFiles() ?: continue
            for (k in kids) {
                if (k.name == TRASH_NAME) continue
                val dir = k.isDirectory
                if (dir && !Files.isSymbolicLink(k.toPath())) stack.addLast(k)
                val nl = k.name.lowercase(Locale.ROOT)
                val match = if (exts.isNotEmpty()) {
                    !dir && k.extension.lowercase(Locale.ROOT) in exts && (ql.isEmpty() || nl.contains(ql))
                } else {
                    ql.isNotEmpty() && nl.contains(ql)
                }
                if (match) {
                    out += item(k)
                    if (out.size >= max) return out to true
                }
            }
        }
        return out to false
    }

    // ---------------------------------------------------------------- analyzer

    private class Acc {
        val cats: LinkedHashMap<String, LongArray> = linkedMapOf(
            "Images" to LongArray(2), "Videos" to LongArray(2), "Audio" to LongArray(2),
            "Documents" to LongArray(2), "Archives" to LongArray(2), "APKs" to LongArray(2),
            "Other" to LongArray(2)
        )
        val heap = PriorityQueue<Pair<File, Long>>(compareBy<Pair<File, Long>> { it.second })
    }

    private fun category(name: String): String {
        val i = FileItem(name, name, false, 0, 0)
        return when (i.kind) {
            Kind.IMAGE -> "Images"
            Kind.VIDEO -> "Videos"
            Kind.AUDIO -> "Audio"
            Kind.PDF, Kind.DOC -> "Documents"
            Kind.TEXT -> if (i.ext in setOf("txt", "csv", "md")) "Documents" else "Other"
            Kind.ARCHIVE -> "Archives"
            Kind.APK -> "APKs"
            Kind.OTHER -> "Other"
        }
    }

    private suspend fun sizeRec(f: File, acc: Acc): Long {
        if (f.isDirectory) {
            currentCoroutineContext().ensureActive()
            if (Files.isSymbolicLink(f.toPath())) return 0L
            var t = 0L
            val kids = f.listFiles()
            if (kids != null) for (k in kids) t += sizeRec(k, acc)
            return t
        }
        val s = f.length()
        val c = acc.cats.getValue(category(f.name))
        c[0] = c[0] + s
        c[1] = c[1] + 1L
        acc.heap.add(f to s)
        if (acc.heap.size > 30) acc.heap.poll()
        return s
    }

    suspend fun analyze(root: File): AnalyzeResult {
        val kids = root.listFiles() ?: throw IOException("Cannot read this folder")
        val acc = Acc()
        var total = 0L
        val sized = ArrayList<Pair<File, Long>>()
        for (k in kids) {
            val s = sizeRec(k, acc)
            total += s
            sized.add(k to s)
        }
        sized.sortByDescending { it.second }
        val children = sized.take(60).map { (f, s) -> FileItem(f.name, f.absolutePath, f.isDirectory, s, f.lastModified()) }
        val cats = acc.cats.map { (n, v) -> CatStat(n, v[0], v[1]) }
        val largest = acc.heap.sortedByDescending { it.second }
            .map { (f, s) -> FileItem(f.name, f.absolutePath, false, s, f.lastModified()) }
        return AnalyzeResult(total, cats, children, largest)
    }

    // ------------------------------------------------------------ open / share

    fun mime(name: String): String {
        val e = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(e) ?: "*/*"
    }

    private fun uriFor(ctx: Context, f: File): Uri =
        FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)

    fun open(ctx: Context, path: String, chooser: Boolean) {
        val f = File(path)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uriFor(ctx, f), mime(f.name))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            if (chooser) {
                ctx.startActivity(Intent.createChooser(i, "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } else {
                ctx.startActivity(i)
            }
        } catch (e: ActivityNotFoundException) {
            throw IOException("No app found that can open this file")
        }
    }

    fun share(ctx: Context, paths: List<String>) {
        val files = paths.map { File(it) }.filter { it.isFile }
        if (files.isEmpty()) throw IOException("Only files can be shared. Zip a folder first.")
        val uris = ArrayList(files.map { uriFor(ctx, it) })
        val i: Intent
        if (uris.size == 1) {
            i = Intent(Intent.ACTION_SEND)
            i.putExtra(Intent.EXTRA_STREAM, uris[0])
            i.type = mime(files[0].name)
        } else {
            i = Intent(Intent.ACTION_SEND_MULTIPLE)
            i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            i.type = "*/*"
        }
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // ------------------------------------------------------------ recycle bin

    fun trashRoot(): File = File(Environment.getExternalStorageDirectory(), TRASH_NAME)

    private fun dirSize(d: File): Long {
        var t = 0L
        val kids = d.listFiles() ?: return 0L
        for (k in kids) t += if (k.isDirectory) dirSize(k) else k.length()
        return t
    }

    /** Moves each path into the bin as <id>/<name> plus an <id>.meta file recording where it came from. */
    fun moveToTrash(paths: List<String>) {
        val root = trashRoot()
        if (!root.exists() && !root.mkdirs()) throw IOException("Cannot create the recycle bin")
        var n = 0
        for (p in paths) {
            val f = File(p)
            if (!f.exists()) continue
            // Anything already inside the bin (or the bin itself) is deleted for good instead of nested.
            val rootPath = root.canonicalPath
            val fPath = f.canonicalPath
            if (fPath == rootPath || fPath.startsWith(rootPath + File.separator)) {
                if (!deleteRec(f)) throw IOException("Could not delete ${f.name}")
                continue
            }
            val id = "${System.currentTimeMillis()}_$n"
            n++
            val dir = File(root, id)
            if (!dir.mkdirs()) throw IOException("Cannot create the recycle bin")
            val dst = File(dir, f.name)
            if (!f.renameTo(dst)) {
                copyRec(f, dir)
                if (!deleteRec(f)) throw IOException("Could not remove ${f.name}")
            }
            File(root, "$id.meta").writeText(f.absolutePath + "\n" + System.currentTimeMillis(), Charsets.UTF_8)
        }
    }

    fun listTrash(): List<TrashItem> {
        val root = trashRoot()
        val metas = root.listFiles()?.filter { it.isFile && it.name.endsWith(".meta") } ?: return emptyList()
        val out = ArrayList<TrashItem>()
        for (m in metas) {
            val id = m.name.removeSuffix(".meta")
            val inner = File(root, id).listFiles()?.firstOrNull() ?: continue
            val text = runCatching { m.readText(Charsets.UTF_8) }.getOrNull() ?: continue
            val lines = text.split("\n")
            val orig = lines[0]
            val at = lines.getOrNull(1)?.trim()?.toLongOrNull() ?: m.lastModified()
            val size = if (inner.isDirectory) dirSize(inner) else inner.length()
            out.add(TrashItem(id, inner.name, orig, at, size, inner.isDirectory))
        }
        out.sortByDescending { it.deletedAt }
        return out
    }

    fun restoreTrash(item: TrashItem) {
        val root = trashRoot()
        val dir = File(root, item.id)
        val inner = dir.listFiles()?.firstOrNull() ?: throw IOException("Item is missing from the recycle bin")
        val target = File(item.origPath)
        val parent = target.parentFile ?: throw IOException("Invalid original location")
        if (!parent.exists() && !parent.mkdirs()) throw IOException("Cannot recreate ${parent.path}")
        val dst = unique(parent, target.name)
        if (!inner.renameTo(dst)) {
            copyRec(inner, parent)
            deleteRec(inner)
        }
        deleteRec(dir)
        File(root, item.id + ".meta").delete()
    }

    fun deleteTrash(item: TrashItem) {
        val root = trashRoot()
        deleteRec(File(root, item.id))
        File(root, item.id + ".meta").delete()
    }

    fun emptyTrash() {
        val root = trashRoot()
        root.listFiles()?.forEach { deleteRec(it) }
    }

    /** Permanently removes bin entries older than the given number of days. */
    fun purgeOldTrash(days: Int) {
        val cutoff = System.currentTimeMillis() - days * 86_400_000L
        for (t in listTrash()) if (t.deletedAt < cutoff) deleteTrash(t)
    }

    // -------------------------------------------------------- duplicate finder

    private suspend fun hashFile(f: File, limit: Long): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(256 * 1024)
        var total = 0L
        FileInputStream(f).use { input ->
            while (total < limit) {
                currentCoroutineContext().ensureActive()
                val want = minOf(buf.size.toLong(), limit - total).toInt()
                val r = input.read(buf, 0, want)
                if (r <= 0) break
                md.update(buf, 0, r)
                total += r
            }
        }
        return md.digest().joinToString("") { b -> "%02x".format(b) }
    }

    /**
     * Finds byte-identical files. Group by size, then by a hash of the first 64 KB,
     * then (for larger files) by a full SHA-256, so most files are never read completely.
     */
    suspend fun findDuplicates(root: File, minSize: Long): List<DupGroup> {
        val bySize = HashMap<Long, MutableList<File>>()
        val stack = ArrayDeque<File>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val d = stack.removeLast()
            val kids = d.listFiles() ?: continue
            for (k in kids) {
                if (k.isDirectory) {
                    if (k.name != TRASH_NAME && !Files.isSymbolicLink(k.toPath())) stack.addLast(k)
                } else {
                    val len = k.length()
                    if (len >= minSize) bySize.getOrPut(len) { mutableListOf() }.add(k)
                }
            }
        }

        val groups = ArrayList<DupGroup>()
        for ((size, files) in bySize) {
            if (files.size < 2) continue
            val quick = HashMap<String, MutableList<File>>()
            for (f in files) {
                val h: String? = try { hashFile(f, 65536L) } catch (e: IOException) { null }
                if (h != null) quick.getOrPut(h) { mutableListOf() }.add(f)
            }
            for (same in quick.values) {
                if (same.size < 2) continue
                val sets: List<List<File>>
                if (size <= 65536L) {
                    sets = listOf(same)
                } else {
                    val full = HashMap<String, MutableList<File>>()
                    for (f in same) {
                        val h: String? = try { hashFile(f, Long.MAX_VALUE) } catch (e: IOException) { null }
                        if (h != null) full.getOrPut(h) { mutableListOf() }.add(f)
                    }
                    sets = full.values.filter { it.size > 1 }
                }
                for (g in sets) {
                    groups.add(DupGroup(size, g.map { item(it) }.sortedBy { it.mtime }))
                }
            }
        }
        groups.sortByDescending { it.wasted }
        return groups
    }

    // ------------------------------------------------- receiving shared files

    /** Display name and size (-1 if unknown) of a shared content Uri. */
    fun queryIncoming(ctx: Context, uri: Uri): Pair<String, Long> {
        var name: String? = null
        var size = -1L
        try {
            val cursor = ctx.contentResolver.query(uri, null, null, null, null)
            if (cursor != null) {
                cursor.use { c ->
                    if (c.moveToFirst()) {
                        val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val si = c.getColumnIndex(OpenableColumns.SIZE)
                        if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni)
                        if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                    }
                }
            }
        } catch (e: Exception) {
            // fall back to the Uri itself below
        }
        var n: String = name ?: uri.lastPathSegment ?: "shared_file"
        n = n.substringAfterLast('/').replace(Regex("[\\\\:*?\"<>|]"), "_").trim()
        if (n.isEmpty()) n = "shared_file"
        if (!n.contains('.')) {
            val mime = ctx.contentResolver.getType(uri)
            val ext = if (mime != null) MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) else null
            if (ext != null) n = "$n.$ext"
        }
        return n to size
    }

    fun saveStream(ctx: Context, uri: Uri, destDir: File, name: String): File {
        if (!destDir.isDirectory) throw IOException("Destination folder not found")
        val out = unique(destDir, name)
        val input = ctx.contentResolver.openInputStream(uri) ?: throw IOException("Cannot read the shared file")
        input.use { ins ->
            BufferedOutputStream(FileOutputStream(out)).use { os -> ins.copyTo(os) }
        }
        return out
    }

    fun saveText(destDir: File, name: String, text: String): File {
        if (!destDir.isDirectory) throw IOException("Destination folder not found")
        val out = unique(destDir, name)
        out.writeText(text, Charsets.UTF_8)
        return out
    }
}
