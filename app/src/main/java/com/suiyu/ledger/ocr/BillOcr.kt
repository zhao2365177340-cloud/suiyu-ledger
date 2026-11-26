package com.suiyu.ledger.ocr

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 单张截图的识别结果；失败时 [failed] 为 true 并带上错误原因，不中断整批 */
data class RecognizedText(
    val uri: Uri,
    val text: String,
    val lineCount: Int = 0,
    val failed: Boolean = false,
    val error: String? = null,
)

/**
 * 端侧 OCR 封装：ML Kit 中文识别（内置模型，离线，不上传任何图片）。
 *
 * 设计要点：
 *  - 一次初始化、批量复用识别器，避免每张图都新建 client（ML Kit 初始化较慢）；
 *  - `Task` 用 [suspendCancellableCoroutine] 包成挂起函数，调用方可以顺序 `for` 循环 + 进度回调；
 *  - 单张图片失败（文件损坏、不支持的格式、内存不足）只记录失败序号，整批继续，
 *    导入页可以用 [lastFailedIndexes] 提示「第 N 张没识别出来，请重试或手动补录」；
 *  - 用完调用 [close]，释放底层模型。
 */
class BillOcr(context: Context) {

    private val appContext: Context = context.applicationContext
    private val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    private val failedIndexes = ArrayList<Int>()

    /** 最近一次 [recognize] 中失败的图片序号（从 0 开始） */
    val lastFailedIndexes: List<Int>
        get() = failedIndexes.toList()

    /**
     * 逐张识别，顺序执行；每张完成（成功或失败）后回调 [onProgress]。
     *
     * @return 与 [uris] 等长的结果列表，失败项 [RecognizedText.failed] 为 true
     */
    suspend fun recognize(
        uris: List<Uri>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): List<RecognizedText> {
        failuresClear()
        val total = uris.size
        if (total == 0) return emptyList()
        val results = ArrayList<RecognizedText>(total)
        uris.forEachIndexed { index, uri ->
            val result = try {
                val image = InputImage.fromFilePath(appContext, uri)
                val recognized = recognizer.process(image).awaitText()
                RecognizedText(
                    uri = uri,
                    text = reconstructRows(recognized),
                    lineCount = recognized.textBlocks.sumOf { block -> block.lines.size },
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                failuresAdd(index)
                RecognizedText(
                    uri = uri,
                    text = "",
                    failed = true,
                    error = describeError(error),
                )
            }
            results += result
            onProgress(index + 1, total)
        }
        return results
    }

    /** 释放识别器；重复调用安全 */
    fun close() {
        runCatching { recognizer.close() }
    }

    private fun failuresClear() = failedIndexes.clear()

    private fun failuresAdd(index: Int) {
        failedIndexes += index
    }

    private suspend fun Task<Text>.awaitText(): Text = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result -> if (continuation.isActive) continuation.resume(result) }
        addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
        addOnCanceledListener { if (continuation.isActive) continuation.cancel() }
    }

    /**
     * 按「行」重建文本：账单详情截图是两列表格（左侧标签、右侧值），
     * ML Kit 常把同一行的标签与内容拆成两个文本行，直接按默认阅读顺序拼接会导致
     * 「商户全称」和「星巴克咖啡」分家，解析器只能拿到标签而拿不到值。
     *
     * 这里按几何位置重组：把垂直中心接近的文本片段视为同一行，按 x 从左到右拼接。
     * 这样输出接近人眼看到的一行行账单，解析器的标签取值逻辑可以原样工作。
     */
    private fun reconstructRows(text: Text): String {
        data class Segment(val content: String, val left: Int, val centerY: Int, val height: Int)

        val segments = ArrayList<Segment>()
        text.textBlocks.forEach { block ->
            block.lines.forEach { line ->
                val box = line.boundingBox ?: return@forEach
                val content = line.text.trim()
                if (content.isEmpty()) return@forEach
                segments += Segment(content, box.left, box.centerY(), box.height())
            }
        }
        if (segments.isEmpty()) return text.text
        val averageHeight = segments.map { it.height }.average().toInt().coerceAtLeast(1)
        val rowTolerance = (averageHeight * 0.6f).toInt().coerceAtLeast(8)

        val rows = ArrayList<MutableList<Segment>>()
        segments.sortedBy { it.centerY }.forEach { segment ->
            val current = rows.lastOrNull()
            val anchor = current?.firstOrNull()
            if (current != null && anchor != null && kotlin.math.abs(anchor.centerY - segment.centerY) <= rowTolerance) {
                current += segment
            } else {
                rows += mutableListOf(segment)
            }
        }
        return rows.joinToString("\n") { row ->
            row.sortedBy { it.left }.joinToString("  ") { it.content }
        }
    }

    private fun describeError(error: Throwable): String {
        val message = error.message?.trim()
        return if (!message.isNullOrEmpty()) message else error::class.java.simpleName
    }
}
