package com.lutqiu.lumiread.ui.bookshelf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.lutqiu.lumiread.BuildConfig
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.lutqiu.lumiread.ui.components.PageHeader
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lutqiu.lumiread.data.Book
import com.lutqiu.lumiread.data.BookFormat
import com.lutqiu.lumiread.data.BookStore
import com.lutqiu.lumiread.data.ReadingMode
import com.lutqiu.lumiread.services.ParserService
import kotlinx.coroutines.launch

private const val TAG = "BookshelfScreen"

private val CanvasGray = Color(0xFFF7F8FA)
private val Ink = Color(0xFF1A1B1E)
private val SubInk = Color(0xFF6B7078)
private val Accent = Color(0xFF3B82F6)
private val DeepBlue = Color(0xFF1E3A8A)
private val DeepBlueBorder = Color(0xFFBFD3FF)

/** SAF 里只用这两种 MIME 过滤：纯文本 + EPUB。 */
private val PICKER_MIME = arrayOf("text/plain", "application/epub+zip")

@Composable
fun BookshelfScreen(
    onOpenBook: (Book, ReadingMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var books by remember { mutableStateOf<List<Book>>(emptyList()) }
    // 每本书进入方式（文本阅读 / 剧场 VN）；默认普通阅读
    val modes = remember { mutableStateMapOf<String, ReadingMode>() }
    var importing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    // 恢复上次导入的书架；旧解析器切出来的 EPUB 自动按新逻辑重解析（修复历史缓存里的目录页/乱码）
    LaunchedEffect(Unit) {
        var loaded = BookStore.load(context)

        // Debug 通道：adb push 到私有目录后用 run-as 放进 filesDir/debug_import.txt，
        // 启动即自动解析，绕开虚拟屏无法打开 SAF 文件选择器的问题。
        if (BuildConfig.DEBUG) {
            val debugFile = listOf("debug_import.txt", "debug_import.epub")
                .map { File(context.filesDir, it) }
                .firstOrNull { it.exists() }
            if (debugFile != null) {
                val id = "debug://${debugFile.name}"
                if (loaded.none { it.id == id }) {
                    val isEpub = debugFile.extension.equals("epub", true)
                    val chapters = runCatching {
                        if (isEpub) {
                            ParserService.parseEpub(context, Uri.fromFile(debugFile))
                        } else {
                            ParserService.parseTxt(context, Uri.fromFile(debugFile))
                        }
                    }.getOrDefault(emptyList())
                    if (chapters.isNotEmpty()) {
                        loaded = loaded + Book(
                            id = id,
                            title = debugFile.nameWithoutExtension,
                            format = if (isEpub) BookFormat.EPUB else BookFormat.TXT,
                            importedAt = System.currentTimeMillis(),
                            parsedWith = ParserService.PARSER_VERSION,
                            chapters = chapters,
                        )
                        BookStore.save(context, loaded)
                    }
                }
                debugFile.delete()
            }
        }

        books = loaded

        // 任何被旧解析器切过的书（TXT / EPUB 都算）都重新解析一遍
        val stale = loaded.filter {
            it.parsedWith < ParserService.PARSER_VERSION && !it.id.startsWith("debug://")
        }
        if (stale.isEmpty()) return@LaunchedEffect

        importing = true
        var updated = loaded
        stale.forEach { oldBook ->
            val uri = runCatching { Uri.parse(oldBook.id) }.getOrNull() ?: return@forEach
            val chapters = runCatching {
                if (oldBook.format == BookFormat.EPUB) {
                    ParserService.parseEpub(context, uri)
                } else {
                    ParserService.parseTxt(context, uri)
                }
            }.getOrDefault(emptyList())
            if (chapters.isNotEmpty()) {
                val title = if (oldBook.format == BookFormat.EPUB) {
                    runCatching { ParserService.readEpubTitle(context, uri) }.getOrNull() ?: oldBook.title
                } else {
                    oldBook.title
                }
                updated = updated.map {
                    if (it.id == oldBook.id) {
                        it.copy(title = title, chapters = chapters, parsedWith = ParserService.PARSER_VERSION)
                    } else {
                        it
                    }
                }
            }
        }
        books = updated
        BookStore.save(context, updated)
        importing = false
        message = "已按新解析器重新切分 ${stale.size} 本 EPUB"
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            importing = true
            message = null
            try {
                val imported = importBook(context, uri)
                if (imported == null) {
                    message = "解析失败：文件为空或格式不受支持"
                } else {
                    val next = books.filterNot { it.id == imported.id } + imported
                    books = next
                    BookStore.save(context, next)
                    message = "已导入《${imported.title}》，共 ${imported.chapters.size} 章"
                }
            } catch (t: Throwable) {
                Log.w(TAG, "导入失败", t)
                message = "导入失败：${t.message ?: t.javaClass.simpleName}"
            } finally {
                importing = false
            }
        }
    }

    MaterialTheme {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(CanvasGray),
        ) {
            // 统一 PageHeader：24dp 边距 / 28sp 加粗，右侧放导入按钮
            PageHeader(
                title = "书架",
                action = {
                    ImportButton(
                        importing = importing,
                        onClick = {
                            runCatching { picker.launch(PICKER_MIME) }
                                .onFailure { message = "无法打开文件选择器：${it.message}" }
                        },
                    )
                },
            )

            if (books.isNotEmpty() || importing) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
                ) {
                    items(items = books, key = { it.id }) { book ->
                        val mode = modes[book.id] ?: ReadingMode.READER
                        BookRow(
                            book = book,
                            mode = mode,
                            onSelectMode = { modes[book.id] = it },
                            onClick = { onOpenBook(book, mode) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportButton(importing: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Accent)
            .clickable(enabled = !importing, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (importing) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = Color.White,
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = if (importing) "解析中…" else "导入书籍",
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White),
        )
    }
}

@Composable
private fun BookRow(
    book: Book,
    mode: ReadingMode,
    onSelectMode: (ReadingMode) -> Unit,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 58.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (book.format == BookFormat.EPUB) "EPUB" else "TXT",
                    style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Accent),
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink),
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${book.format.name} · ${book.chapters.size} 章",
                    style = TextStyle(fontSize = 11.sp, color = SubInk),
                )
            }

            ModeChip(mode = mode, onSelect = onSelectMode)

            Spacer(Modifier.width(6.dp))

            Text(
                text = "›",
                style = TextStyle(fontSize = 20.sp, color = SubInk),
            )
        }
    }
}

/**
 * 「文本 / 剧场」切换 Chip（v0.4.0）：
 * 选中态 = 深蓝底 + 白字 + 浅蓝外边框，极其明显；未选中为透明底灰字。
 */
@Composable
private fun ModeChip(mode: ReadingMode, onSelect: (ReadingMode) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFFF3F4F6))
            .border(1.dp, Color(0xFFE2E5EA), RoundedCornerShape(50))
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeSegment("文本", selected = mode == ReadingMode.READER) { onSelect(ReadingMode.READER) }
        ModeSegment("剧场", selected = mode == ReadingMode.VN) { onSelect(ReadingMode.VN) }
    }
}

@Composable
private fun ModeSegment(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) DeepBlue else Color.Transparent)
            .then(
                if (selected) {
                    Modifier.border(2.dp, DeepBlueBorder, RoundedCornerShape(50))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color.White else Color(0xFF8A9099),
            ),
        )
    }
}

/**
 * 读 Uri → Book。
 *
 * 书名：EPUB 优先取 OPF 里的 dc:title；TXT 取文件名去后缀。
 * 章节：交给 [ParserService]。
 */
private suspend fun importBook(context: Context, uri: Uri): Book? {
    // 让 Uri 在重启后仍可读（否则下次冷启动重读书架会拿到失效 Uri）
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }

    val displayName = queryDisplayName(context, uri) ?: uri.lastPathSegment.orEmpty()
    val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()
    val isEpub = displayName.endsWith(".epub", ignoreCase = true) ||
        mime.contains("epub", ignoreCase = true)

    val format = if (isEpub) BookFormat.EPUB else BookFormat.TXT
    val chapters = if (isEpub) {
        ParserService.parseEpub(context, uri)
    } else {
        ParserService.parseTxt(context, uri)
    }
    if (chapters.isEmpty()) return null

    val title = if (isEpub) {
        ParserService.readEpubTitle(context, uri)
            ?: displayName.substringBeforeLast('.')
    } else {
        displayName.substringBeforeLast('.')
    }

    return Book(
        id = uri.toString(),
        title = title.ifBlank { "未命名" },
        format = format,
        importedAt = System.currentTimeMillis(),
        parsedWith = ParserService.PARSER_VERSION,
        chapters = chapters,
    )
}

private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
}.getOrNull()
