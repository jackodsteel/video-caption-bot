package nz.co.jacksteel.videocaptionerbot.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal const val MINIMUM_BLOCK_SIZE: Int = 512

suspend fun File.forEachBlockIndexed(blockSize: Int, action: suspend (buffer: ByteArray, index: Int) -> Unit) {
    var index = 0
    val arr = ByteArray(blockSize.coerceAtLeast(MINIMUM_BLOCK_SIZE))

    inputStream().use { input ->
        do {
            val size = withContext(Dispatchers.IO) { input.read(arr) }
            if (size <= 0) {
                break
            } else {
                action(arr, checkIndexOverflow(index++))
            }
        } while (true)
    }
}

internal fun checkIndexOverflow(index: Int): Int {
    if (index < 0) {
        throw ArithmeticException("Index overflow has happened.")
    }
    return index
}