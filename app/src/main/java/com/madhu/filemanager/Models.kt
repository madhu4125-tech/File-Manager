package com.madhu.filemanager

import androidx.compose.ui.graphics.Color
import java.text.DateFormat
import java.util.Date

enum class Kind(val icon: String) {
    IMAGE("🖼️"), VIDEO("🎬"), AUDIO("🎵"), PDF("📕"), ARCHIVE("🗜️"),
    APK("🤖"), TEXT("📝"), DOC("📊"), OTHER("📄")
}

enum class NavTab { FILES, CATS, STORAGE }
enum class SortKey { NAME, DATE, SIZE, TYPE }
enum class ResFrom { SEARCH, CATS }

private val KIND_EXT: Map<Kind, Set<String>> = mapOf(
    Kind.IMAGE to setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg", "avif"),
    Kind.VIDEO to setOf("mp4", "mkv", "webm", "3gp", "avi", "mov", "m4v"),
    Kind.AUDIO to setOf("mp3", "wav", "ogg", "m4a", "aac", "flac", "opus", "amr"),
    Kind.PDF to setOf("pdf"),
    Kind.ARCHIVE to setOf("zip", "rar", "7z", "tar", "gz"),
    Kind.APK to setOf("apk"),
    Kind.TEXT to setOf(
        "txt", "md", "log", "json", "xml", "csv", "tsv", "html", "htm", "css", "js", "ts", "java", "kt",
        "py", "sh", "yml", "yaml", "ini", "conf", "sql", "c", "cpp", "h", "properties", "gradle", "bat"
    ),
    Kind.DOC to setOf("doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "rtf")
)

/** size = bytes for files, number of children for folders (except inside analyzer results, where it is bytes). */
data class FileItem(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long,
    val mtime: Long
) {
    val hidden: Boolean get() = name.startsWith(".")
    val ext: String get() = if (name.lastIndexOf('.') > 0) name.substringAfterLast('.').lowercase() else ""
    val kind: Kind
        get() {
            if (isDir) return Kind.OTHER
            val e = ext
            for ((k, set) in KIND_EXT) if (e in set) return k
            return Kind.OTHER
        }
    val icon: String get() = if (isDir) "📁" else kind.icon
    val isRasterImage: Boolean get() = !isDir && kind == Kind.IMAGE && ext != "svg"
}

data class Volume(val name: String, val path: String, val total: Long, val free: Long)

data class FileProps(
    val totalSize: Long,
    val files: Long,
    val folders: Long,
    val canRead: Boolean,
    val canWrite: Boolean,
    val mtime: Long
)

data class CatStat(val name: String, val bytes: Long, val count: Long)

data class AnalyzeResult(
    val total: Long,
    val categories: List<CatStat>,
    val children: List<FileItem>,
    val largest: List<FileItem>
)

class An(
    val path: String,
    val loading: Boolean = false,
    val data: AnalyzeResult? = null,
    val error: String? = null
)

data class Results(val title: String, val items: List<FileItem>, val from: ResFrom, val truncated: Boolean)
data class Clip(val cut: Boolean, val paths: List<String>)

class MenuEntry(val icon: String, val label: String, val danger: Boolean = false, val action: () -> Unit)

sealed interface Dlg {
    class Menu(val title: String, val entries: List<MenuEntry>) : Dlg
    class Prompt(
        val title: String,
        val initial: String,
        val label: String,
        val selectBase: Boolean,
        val onOk: (String) -> Unit
    ) : Dlg

    class Confirm(
        val title: String,
        val msg: String,
        val label: String,
        val danger: Boolean,
        val onOk: () -> Unit
    ) : Dlg

    class Props(val item: FileItem, val props: FileProps) : Dlg
}

sealed interface Viewer {
    data class Images(val items: List<FileItem>, val index: Int) : Viewer
    data class Media(val item: FileItem, val video: Boolean) : Viewer
    data class TextDoc(val item: FileItem, val text: String, val truncated: Boolean) : Viewer
}

class Cat(val id: String, val label: String, val icon: String, val color: Color, val exts: Set<String>)

val CATS: List<Cat> = listOf(
    Cat("images", "Images", "🖼️", Color(0xFFE91E63), KIND_EXT.getValue(Kind.IMAGE)),
    Cat("videos", "Videos", "🎬", Color(0xFF9C27B0), KIND_EXT.getValue(Kind.VIDEO)),
    Cat("audio", "Audio", "🎵", Color(0xFFFB8C00), KIND_EXT.getValue(Kind.AUDIO)),
    Cat(
        "docs", "Documents", "📄", Color(0xFF1E88E5),
        KIND_EXT.getValue(Kind.PDF) + KIND_EXT.getValue(Kind.DOC) + setOf("txt", "csv", "md")
    ),
    Cat("archives", "Archives", "🗜️", Color(0xFF795548), KIND_EXT.getValue(Kind.ARCHIVE)),
    Cat("apks", "APKs", "🤖", Color(0xFF43A047), setOf("apk"))
)

val CAT_COLORS: Map<String, Color> = mapOf(
    "Images" to Color(0xFFE91E63),
    "Videos" to Color(0xFF9C27B0),
    "Audio" to Color(0xFFFB8C00),
    "Documents" to Color(0xFF1E88E5),
    "Archives" to Color(0xFF795548),
    "APKs" to Color(0xFF43A047),
    "Other" to Color(0xFF9AA5B8)
)

fun fmtSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var i = -1
    do {
        v /= 1024
        i++
    } while (v >= 1024 && i < units.size - 1)
    return (if (v >= 100) "%.0f".format(v) else "%.1f".format(v)) + " " + units[i]
}

fun fmtDate(ms: Long): String =
    if (ms <= 0) "" else DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms))

fun fmtDateTime(ms: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))
