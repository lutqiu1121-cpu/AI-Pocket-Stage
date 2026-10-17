package com.lutqiu.lumiread.ui.bookshelf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.lutqiu.lumiread.services.ParserService
import kotlinx.coroutines.launch

private const val TAG = "BookshelfScreen"

private val CanvasGray = Color(0xFFF7F8FA)
private val Ink = Color(0xFF1A1B1E)
private val SubInk = Color(0xFF6B7078)
private val Accent = Color(0xFF3B82F6)

/** SAF 里只用这两种 MIME 过滤：纯文本 + EPUB。 */
private val PICKER_MIME = arrayOf("text/plain", "application/epub+zip")

@Composable
fun BookshelfScreen(
    onOpenBook: (Book) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var books by remember { mutableStateOf<List<Book>>(emptyList()) }
    var importing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    // 恢复上次导入的书架
    LaunchedEffect(Unit) {
        books = BookStore.load(context)
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
                .background(CanvasGray)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "书架",
                        style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink),
                    )
                    Text(
                        text = "支持 TXT / EPUB",
                        style = TextStyle(fontSize = 12.sp, color = SubInk),
                    )
                }

                ImportButton(
                    importing = importing,
                    onClick = {
                        // OpenDocument 需要 mime 数组；系统文件选择器会自动过滤 txt / epub
                        runCatching { picker.launch(PICKER_MIME) }
                            .onFailure { message = "无法打开文件选择器：${it.message}" }
                    },
                )
            }

            Spacer(Modifier.height(14.dp))

            message?.let {
                Text(
                    text = it,
                    style = TextStyle(fontSize = 12.sp, color = SubInk),
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }

            if (books.isEmpty() && !importing) {
                EmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(items = books, key = { it.id }) { book ->
                        BookRow(book = book, onClick = { onOpenBook(book) })
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
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "书架还是空的",
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Ink),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "点右上角「导入书籍」，选一本 TXT 或 EPUB",
                style = TextStyle(fontSize = 12.sp, color = SubInk),
            )
        }
    }
}

@Composable
private fun BookRow(book: Book, onClick: () -> Unit) {
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

            Text(
                text = "›",
                style = TextStyle(fontSize = 20.sp, color = SubInk),
            )
        }
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
        chapters = chapters,
    )
}

private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
}.getOrNull()
