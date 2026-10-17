package com.lutqiu.lumiread.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.runBlocking

/**
 * 队列时序自检（JVM 单测）。
 *
 * 用的就是线上那一个 [RequestQueue]（同一个 object、同一个 Channel、同一个 worker），
 * 只是把 logger 从 `android.util.Log` 换成带时间戳的收集器，从而在没有真机 Logcat 的环境里
 * 也能验证「5 个任务严格串行、任务间 3 秒」。
 */
class RequestQueueTest {

    private data class Entry(val atMillis: Long, val text: String)

    @Test
    fun fiveTasksRunStrictlySeriallyWithThreeSecondGap() = runBlocking {
        val format = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        val entries = Collections.synchronizedList(mutableListOf<Entry>())

        RequestQueue.logger = { message ->
            val entry = Entry(System.currentTimeMillis(), message)
            entries.add(entry)
            println("${format.format(Date(entry.atMillis))}  I/RequestQueue: $message")
        }

        val startedAt = System.currentTimeMillis()
        RequestQueue.testQueue()
        val elapsed = System.currentTimeMillis() - startedAt

        // ① 5 个任务 = 10 条日志（开始 + 结束各 5）
        assertEquals("任务日志条数应为 10", 10, entries.size)

        // ② 严格串行：开始/结束成对相邻，且任务号递增
        entries.forEachIndexed { i, entry ->
            val taskNo = i / 2 + 1
            val expected = if (i % 2 == 0) "[Queue] 任务 $taskNo 开始" else "[Queue] 任务 $taskNo 结束"
            assertEquals("第 $i 条日志顺序不对", expected, entry.text)
        }

        // ③ 每个任务耗时 ≈ 500ms，任务之间间隔 ≈ 3000ms
        val taskDurations = mutableListOf<Long>()
        val gaps = mutableListOf<Long>()
        for (i in 0 until 5) {
            val start = entries[i * 2].atMillis
            val end = entries[i * 2 + 1].atMillis
            taskDurations += end - start
            if (i < 4) gaps += entries[(i + 1) * 2].atMillis - end
        }

        println()
        println("──── 时序汇总 ────")
        taskDurations.forEachIndexed { i, d -> println("任务 ${i + 1} 耗时: ${d}ms") }
        gaps.forEachIndexed { i, g -> println("任务 ${i + 1} → 任务 ${i + 2} 间隔: ${g}ms") }
        println("总耗时: ${elapsed}ms")
        println("──────────────────")
        println()

        taskDurations.forEach { assertTrue("单任务耗时应 ≥ 450ms，实际 ${it}ms", it >= 450) }
        gaps.forEach { assertTrue("任务间隔应 ≥ 2900ms，实际 ${it}ms", it >= 2_900) }
        assertTrue("整轮应 ≥ 17s，实际 ${elapsed}ms", elapsed >= 17_000)
    }
}
