package com.lutqiu.lumiread.services

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 全局单队列。
 *
 * 规则（硬约束）：
 * 1. **并发恒为 1** —— 整个进程只有一个 worker 协程消费同一个 [Channel]；
 * 2. 每个任务结束后 `delay(3000)` 再做下一个（节流，避免打爆第三方限流）；
 * 3. 任务抛 [HttpRateLimitException]（HTTP 429）时：**清空 Channel** + 打警告日志；
 * 4. 队列随首个任务自动启动，不需要显式初始化。
 */
object RequestQueue {

    private const val TAG = "RequestQueue"

    /** 任务之间的固定间隔。 */
    private const val THROTTLE_MS = 3_000L

    /** testQueue 里模拟一次网络请求的耗时。 */
    private const val SIMULATED_NETWORK_MS = 500L

    /** 队列里的一个任务：名字只用于日志，[body] 是真正要做的事。 */
    data class QueueTask(
        val name: String,
        val body: suspend () -> Unit,
    )

    /** HTTP 429：由任务实现方抛出，队列据此熔断清空。 */
    class HttpRateLimitException(message: String = "HTTP 429 Too Many Requests") : Exception(message)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val channel = Channel<QueueTask>(capacity = Channel.UNLIMITED)

    private var worker: Job? = null

    /**
     * 日志出口。默认写 Logcat；单元测试可替换成收集器，从而在 JVM 上验证串行时序。
     */
    internal var logger: (String) -> Unit = { message -> Log.i(TAG, message) }

    /** 幂等启动 worker。 */
    @Synchronized
    fun start() {
        if (worker?.isActive == true) return
        worker = scope.launch { processLoop() }
    }

    /** 非挂起入队（满了不等待，[Channel.UNLIMITED] 实际不会满）。 */
    fun enqueue(task: QueueTask) {
        start()
        channel.trySend(task)
    }

    /** 挂起入队，背压友好。 */
    suspend fun enqueueAndWait(task: QueueTask) {
        start()
        channel.send(task)
    }

    private suspend fun processLoop() {
        for (task in channel) {
            try {
                task.body()
            } catch (e: HttpRateLimitException) {
                logger("[Queue] 命中 HTTP 429，触发熔断：清空队列并告警")
                drainQueue()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                logger("[Queue] 任务 ${task.name} 执行失败：${t.message}")
            }
            // 无论成功失败，固定间隔后再取下一个 —— 全局恒 1 并发 + 3s 节流。
            delay(THROTTLE_MS)
        }
    }

    /** 清空排队中的任务，返回丢弃数量。 */
    internal fun drainQueue(): Int {
        var dropped = 0
        while (channel.tryReceive().isSuccess) dropped++
        if (dropped > 0) {
            logger("[Queue] 已丢弃 $dropped 个排队任务")
        }
        return dropped
    }

    /**
     * 提交一个有返回值的任务并挂起等待结果。
     * 队列仍然是「全局单队列 + 并发 1 + 任务间 3s」，只是把结果用 [CompletableDeferred] 带回调用方。
     */
    suspend fun <T> submit(name: String, block: suspend () -> T): T {
        start()
        val deferred = CompletableDeferred<Result<T>>()
        channel.send(
            QueueTask(name = name) {
                if (!deferred.isCompleted) deferred.complete(runCatching { block() })
            },
        )
        return deferred.await().getOrThrow()
    }

    /**
     * 自检：入队 5 个模拟任务（每个 delay(500)），等全部跑完后返回。
     * 预期日志：任务 N 开始 → 500ms → 任务 N 结束 → 3000ms → 任务 N+1 开始 …
     */
    suspend fun testQueue() {
        start()
        val allDone = CompletableDeferred<Unit>()

        repeat(5) { i ->
            val n = i + 1
            channel.send(
                QueueTask(name = "任务 $n") {
                    logger("[Queue] 任务 $n 开始")
                    delay(SIMULATED_NETWORK_MS)
                    logger("[Queue] 任务 $n 结束")
                },
            )
        }
        // 内部屏障：它排在 5 个任务之后，只为让 testQueue 能 await 到队列尾部。
        channel.send(QueueTask(name = "test-barrier") { allDone.complete(Unit) })

        allDone.await()
    }
}
