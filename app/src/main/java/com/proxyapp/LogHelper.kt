package com.proxyapp

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 统一日志：所有运行信息写入日志文件，可导出到"下载"文件夹，也可直接分享。
 */
object LogHelper {
    private const val FILE_NAME = "app.log"

    @Volatile
    private var appContext: Context? = null

    private val lock = Any()

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun log(message: String) {
        val ctx = appContext ?: return
        val line = "${SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date())} $message"
        synchronized(lock) {
            try {
                val dir = File(ctx.filesDir, "logs").apply { mkdirs() }
                File(dir, FILE_NAME).appendText(line + "\n")
            } catch (_: Exception) {
            }
            // 镜像到外部目录（手机文件管理器/电脑可通过 USB 找到）
            try {
                val extDir = File(ctx.getExternalFilesDir(null), "logs").apply { mkdirs() }
                File(extDir, FILE_NAME).appendText(line + "\n")
            } catch (_: Exception) {
            }
        }
    }

    fun logFile(context: Context): File = File(File(context.filesDir, "logs"), FILE_NAME)

    fun externalLogDir(context: Context): File = File(context.getExternalFilesDir(null), "logs")

    /** 合并 App 日志与内核日志，导出到"下载/老习VPN"目录。返回可读路径。 */
    fun exportToDownloads(context: Context): String? {
        return try {
            val combined = buildCombinedLog(context) ?: return null
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "老习VPN日志.txt")
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        "${Environment.DIRECTORY_DOWNLOADS}/老习VPN"
                    )
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values
                ) ?: return null
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    combined.inputStream().use { it.copyTo(out) }
                }
                "下载/老习VPN/老习VPN日志.txt"
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "老习VPN"
                )
                dir.mkdirs()
                combined.copyTo(File(dir, "老习VPN日志.txt"), overwrite = true)
                "下载/老习VPN/老习VPN日志.txt"
            }
        } catch (e: Exception) {
            null
        }
    }

    /** 通过系统分享（微信/QQ 等）发送日志文件。 */
    fun shareLog(context: Context) {
        try {
            val file = buildCombinedLog(context) ?: return
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "分享日志"))
        } catch (e: Exception) {
        }
    }

    private fun buildCombinedLog(context: Context): File? {
        val src = logFile(context)
        val mihomo = File(context.filesDir, "mihomo.log")
        if (!src.exists() && !mihomo.exists()) return null
        val logsDir = File(context.filesDir, "logs").apply { mkdirs() }
        val combined = File(logsDir, "export.log")
        val sb = StringBuilder()
        sb.append("===== 老习VPN 运行日志 =====\n")
        sb.append("时间：${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n")
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "-"
        sb.append("版本：$version\n\n")
        if (src.exists()) {
            sb.append("【App 日志】\n")
            sb.append(src.readText())
            sb.append("\n")
        }
        if (mihomo.exists()) {
            sb.append("\n【mihomo 内核日志（尾部 200 行）】\n")
            sb.append(mihomo.readLines().takeLast(200).joinToString("\n"))
            sb.append("\n")
        }
        combined.writeText(sb.toString())
        return combined
    }
}
