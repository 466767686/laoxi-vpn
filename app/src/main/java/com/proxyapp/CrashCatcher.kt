package com.proxyapp

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * 记录未捕获的崩溃到应用私有目录，下次打开时可在界面上看到原因。
 */
object CrashCatcher {
    private const val FILE_NAME = "crash.log"

    fun init(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                File(context.filesDir, FILE_NAME).writeText(
                    "线程: ${thread.name}\n${sw.toString()}"
                )
                LogHelper.log("崩溃: ${thread.name} ${throwable::class.java.simpleName}: ${throwable.message}")
            } catch (_: Throwable) {
                // 记录失败也不能影响崩溃流程
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun readCrash(context: Context): String? {
        val file = File(context.filesDir, FILE_NAME)
        return if (file.exists()) file.readText().take(1500) else null
    }

    fun clear(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
    }
}
