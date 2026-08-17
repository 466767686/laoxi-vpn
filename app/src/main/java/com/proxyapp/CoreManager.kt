package com.proxyapp

import android.content.Context
import java.io.File
import java.io.InputStream
import java.net.URLEncoder
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class ConfigInfo(val nodeCount: Int, val groupName: String)

data class ProxyGroup(
    val name: String,
    val type: String,
    val now: String,
    val all: List<String>
)

/**
 * 代理内核（mihomo）管理：负责把内核二进制和订阅配置准备好、启动/停止内核进程，
 * 并通过内核的控制接口确认它已经就绪。
 */
object CoreManager {
    private const val TAG = "CoreManager"
    private const val CONTROLLER_URL = "http://127.0.0.1:19090"
    private const val SUBSCRIPTION_FILE = "subscription.yaml"
    private const val PREFS_NAME = "settings"
    private const val KEY_SUBSCRIPTIONS = "subscriptions"
    private val PROXY_TYPES = Regex("type: (anytls|ss|ssr|vmess|vless|trojan|hysteria2|tuic|socks5|http|shadowtls)")
    private val GROUP_RE = Regex("\\{ name:\\s*'?([^,{]+?)'?\\s*,\\s*type: (select|url-test|fallback)")

    @Volatile
    var process: Process? = null
        private set

    private var logcatProcess: Process? = null

    @Volatile
    var lastError: String? = null

    @Volatile
    var mixedPort: Int = 7890
        private set

    @Volatile
    var progress: String = "就绪"

    /** 记录启动过程到 startup.log，闪退后可在界面上查看最后停在哪一步。 */
    fun log(context: Context, msg: String) {
        progress = msg
        LogHelper.log(msg)
        try {
            val f = File(context.filesDir, "startup.log")
            val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            val lines = (if (f.exists()) f.readLines() else emptyList()) + "$time $msg"
            f.writeText(lines.takeLast(50).joinToString("\n"))
        } catch (_: Exception) {
        }
    }

    /** 从当前订阅配置中解析节点数与主策略组名（只用于界面展示）。 */
    fun loadConfigInfo(context: Context): ConfigInfo {
        val subFile = File(context.filesDir, SUBSCRIPTION_FILE)
        if (!subFile.exists()) return ConfigInfo(0, "")
        return try {
            val text = subFile.readText()
            var nodes = 0
            var group = ""
            text.lineSequence().forEach { line ->
                if (PROXY_TYPES.containsMatchIn(line)) nodes++
                if (group.isEmpty()) {
                    GROUP_RE.find(line)?.let { group = it.groupValues[1].trim() }
                }
            }
            ConfigInfo(nodes, group)
        } catch (e: Exception) {
            ConfigInfo(0, "")
        }
    }

    /** 启动内核，等待控制接口就绪后返回 true。 */
    fun start(context: Context): Boolean {
        lastError = null
        log(context, "开始启动")
        startLogcatCapture(context)
        killStaleCores(context)
        val subFile = File(context.filesDir, SUBSCRIPTION_FILE)
        val subs = getSubscriptions(context)
        if (!subFile.exists() && subs.isNotEmpty()) {
            log(context, "首次使用订阅链接，尝试下载…")
            downloadSubscription(context, subs.first())
        }
        if (!extractBinaries(context)) {
            log(context, "失败：准备内核文件 $lastError")
            return false
        }
        log(context, "内核文件就绪")
        if (!writeConfig(context)) {
            log(context, "失败：生成配置 $lastError")
            return false
        }
        log(context, "配置就绪")
        stop()

        val filesDir = context.filesDir
        val mihomo = File(filesDir, "mihomo")
        val logFile = File(filesDir, "mihomo.log")
        val primary = startCore(mihomo.absolutePath, filesDir, logFile)
        val p: Process?
        val usedPrimary: Boolean
        if (primary != null) {
            p = primary
            usedPrimary = true
        } else {
            p = startCore(
                File(context.applicationInfo.nativeLibraryDir, "libmihomo.so").absolutePath,
                filesDir,
                logFile
            )
            usedPrimary = false
        }
        if (p == null) {
            log(context, "失败：启动内核进程 $lastError")
            return false
        }
        log(context, "内核执行路径：${if (usedPrimary) "应用目录" else "安装目录原生库"}")
        process = p
        log(context, "内核进程已启动，等待就绪")

        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            if (!p.isAlive) {
                lastError = "内核启动后立即退出：\n${tail(logFile)}"
                log(context, "失败：内核启动后立即退出 $lastError")
                process = null
                return false
            }
            if (controllerReady()) {
                log(context, "内核就绪")
                return true
            }
            Thread.sleep(300)
        }
        lastError = "内核启动超时（端口被占用或配置有误）：\n${tail(logFile)}"
        log(context, "失败：等待内核就绪超时 $lastError")
        stop()
        return false
    }

    private fun startCore(path: String, filesDir: File, logFile: File): Process? {
        return try {
            val pb = ProcessBuilder(
                path,
                "-d", filesDir.absolutePath,
                "-f", File(filesDir, "config.yaml").absolutePath
            )
            pb.redirectErrorStream(true)
            pb.redirectOutput(logFile)
            pb.start()
        } catch (e: Exception) {
            lastError = "内核进程启动失败：${e.message}"
            null
        }
    }

    /** 清理上一次崩溃后残留的内核进程（它们会占用代理端口）。 */
    private fun killStaleCores(context: Context) {
        try {
            val marker = File(context.filesDir, "mihomo").absolutePath
            ProcessBuilder("pkill", "-f", marker).start()
            Thread.sleep(300)
        } catch (_: Exception) {
        }
    }

    /** 停止内核进程。 */
    fun stop() {
        logcatProcess?.destroy()
        logcatProcess = null
        val p = process ?: return
        process = null
        p.destroy()
        try {
            if (!p.waitFor(3, TimeUnit.SECONDS)) p.destroyForcibly()
        } catch (e: InterruptedException) {
            p.destroyForcibly()
        }
    }

    /** 从内核控制接口读取策略组与节点列表。 */
    fun fetchGroups(): List<ProxyGroup>? {
        return try {
            val conn = URL("$CONTROLLER_URL/proxies").openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            val code = conn.responseCode
            if (code != 200) {
                conn.disconnect()
                return null
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val root = org.json.JSONObject(body)
            val proxies = root.getJSONObject("proxies")
            val groups = mutableListOf<ProxyGroup>()
            val keys = proxies.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val obj = proxies.getJSONObject(key)
                val type = obj.optString("type")
                if (type == "Selector" || type == "URLTest" || type == "Fallback") {
                    val arr = obj.getJSONArray("all")
                    val all = (0 until arr.length()).map { arr.getString(it) }
                    groups.add(ProxyGroup(key, type, obj.optString("now"), all))
                }
            }
            groups.sortedBy { if (it.type == "Selector") 0 else 1 }
        } catch (e: Exception) {
            null
        }
    }

    /** 切换策略组里的当前节点。 */
    fun switchNode(group: String, node: String): Boolean {
        return try {
            val conn = URL("$CONTROLLER_URL/proxies/${enc(group)}").openConnection() as HttpURLConnection
            conn.requestMethod = "PUT"
            conn.doOutput = true
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.setRequestProperty("Content-Type", "application/json")
            val body = "{\"name\":\"${node.replace("\\", "\\\\").replace("\"", "\\\"")}\"}"
            conn.outputStream.use { it.write(body.toByteArray()) }
            val ok = conn.responseCode == 204 || conn.responseCode == 200
            conn.disconnect()
            ok
        } catch (e: Exception) {
            false
        }
    }

    /** 切换代理模式：rule = 规则分流，global = 全局代理，direct = 直连。 */
    fun setMode(mode: String): Boolean {
        return try {
            val conn = URL("$CONTROLLER_URL/configs").openConnection() as HttpURLConnection
            conn.requestMethod = "PATCH"
            conn.doOutput = true
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.setRequestProperty("Content-Type", "application/json")
            val body = "{\"mode\":\"$mode\"}"
            conn.outputStream.use { it.write(body.toByteArray()) }
            val ok = conn.responseCode == 204 || conn.responseCode == 200
            conn.disconnect()
            ok
        } catch (e: Exception) {
            false
        }
    }

    /** 测试某个节点的连通延迟，返回毫秒；失败返回 null。 */
    fun testDelay(node: String, timeoutMs: Int = 3000): Int? {
        return try {
            val testUrl = "http://www.gstatic.com/generate_204"
            val url = "$CONTROLLER_URL/proxies/${enc(node)}/delay" +
                "?url=${enc(testUrl)}&timeout=$timeoutMs"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            val code = conn.responseCode
            if (code == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val delay = org.json.JSONObject(body).optInt("delay", -1)
                conn.disconnect()
                if (delay >= 0) delay else null
            } else {
                conn.disconnect()
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 持续记录系统日志（仅本应用进程），用于捕获原生层崩溃的"Fatal signal"记录。
     */
    private fun startLogcatCapture(context: Context) {
        if (logcatProcess != null) return
        try {
            val pb = ProcessBuilder("logcat", "-v", "threadtime")
            pb.redirectErrorStream(true)
            pb.redirectOutput(File(context.filesDir, "logcat.log"))
            logcatProcess = pb.start()
        } catch (_: Exception) {
        }
    }

    /** 从安装包中取出内核与地理数据库，放到应用私有目录。 */
    private fun extractBinaries(context: Context): Boolean {
        return try {
            val filesDir = context.filesDir
            val src = File(context.applicationInfo.nativeLibraryDir, "libmihomo.so")
            if (!src.exists()) {
                lastError = "当前设备架构 ${android.os.Build.SUPPORTED_ABIS.firstOrNull()} 不受支持"
                return false
            }
            val dst = File(filesDir, "mihomo")
            if (!dst.exists() || dst.length() != src.length()) {
                src.copyTo(dst, overwrite = true)
            }
            // W^X 安全机制：可执行文件不能可写，否则部分机型会拒绝执行
            dst.setReadable(true, false)
            dst.setWritable(false, false)
            dst.setExecutable(true, false)
            copyAssetIfNeeded(context, "geoip.metadb", File(filesDir, "geoip.metadb"))
            true
        } catch (e: Exception) {
            lastError = "内核文件准备失败：${e.message}"
            false
        }
    }

    /**
     * 生成运行用配置：订阅文件基础上做必要调整——
     * 关闭局域网共享、只监听本机、去掉 DNS/TUN 段（DNS 由隧道层负责）。
     */
    private fun writeConfig(context: Context): Boolean {
        return try {
            val subFile = File(context.filesDir, SUBSCRIPTION_FILE)
            if (!subFile.exists()) {
                lastError = "请先在订阅页面添加订阅链接"
                LogHelper.log("配置缺失：请先添加订阅链接")
                return false
            }
            val raw = subFile.readText()

            val out = StringBuilder()
            var skipBlock: String? = null
            for (line in raw.split("\n")) {
                val t = line.trim()
                val isTopLevel = t.isNotEmpty() &&
                    !t.startsWith("-") &&
                    !t.startsWith("#") &&
                    line == t &&
                    t.contains(":")
                if (skipBlock != null) {
                    if (isTopLevel) skipBlock = null else continue
                }
                when {
                    t == "dns:" || t.startsWith("dns: ") -> skipBlock = "dns"
                    t == "tun:" || t.startsWith("tun: ") -> skipBlock = "tun"
                    t.startsWith("allow-lan:") -> out.append("allow-lan: false\n")
                    t.startsWith("bind-address:") -> out.append("bind-address: '127.0.0.1'\n")
                    t.startsWith("external-controller:") -> out.append("external-controller: '127.0.0.1:19090'\n")
                    t.startsWith("mixed-port:") -> {
                        // 改用不常用端口，避免与其他代理软件冲突
                        mixedPort = 17890
                        out.append("mixed-port: 17890\n")
                    }
                    else -> out.append(line).append('\n')
                }
            }
            File(context.filesDir, "config.yaml").writeText(out.toString())
            true
        } catch (e: Exception) {
            lastError = "配置处理失败：${e.message}"
            false
        }
    }

    /** 订阅链接列表（用户自行添加）。 */
    fun getSubscriptions(context: Context): List<String> {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_SUBSCRIPTIONS, emptySet())
            ?.toList()
            ?: emptyList()
    }

    fun saveSubscriptions(context: Context, urls: List<String>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_SUBSCRIPTIONS, urls.toSet())
            .apply()
    }

    /** 从订阅链接下载配置并保存。 */
    fun downloadSubscription(context: Context, url: String): Boolean {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 20_000
            conn.readTimeout = 20_000
            conn.instanceFollowRedirects = true
            // 很多机场订阅要求特定 User-Agent，否则返回网页而非配置
            conn.setRequestProperty("User-Agent", "clash-verge/v2.0.0")
            conn.setRequestProperty("Accept", "text/yaml, text/plain, application/octet-stream, */*")
            val code = conn.responseCode
            if (code !in 200..299) {
                lastError = "订阅下载失败：HTTP $code"
                LogHelper.log("订阅下载失败：HTTP $code")
                conn.disconnect()
                return false
            }
            var input: InputStream = conn.inputStream
            when (conn.contentEncoding?.lowercase()) {
                "gzip" -> input = GZIPInputStream(input)
                "deflate" -> input = InflaterInputStream(input)
            }
            val body = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
            conn.disconnect()

            var yaml = body
            if (!yaml.contains("proxies:")) {
                // 部分订阅返回 base64 编码
                tryBase64Decode(yaml)?.let { yaml = it }
            }
            if (!yaml.contains("proxies:")) {
                lastError = "订阅内容不是有效的 Clash 配置（缺少 proxies 字段）。" +
                    "服务器返回内容开头：${yaml.trim().take(120)}"
                LogHelper.log("订阅校验失败：$lastError")
                return false
            }
            File(context.filesDir, SUBSCRIPTION_FILE).writeText(yaml)
            LogHelper.log("订阅下载成功：$url（${yaml.length} 字符）")
            true
        } catch (e: Exception) {
            lastError = "订阅下载失败：${e.message}"
            LogHelper.log("订阅下载失败：${e.message}")
            false
        }
    }

    private fun tryBase64Decode(text: String): String? {
        val cleaned = text.trim()
        if (cleaned.length < 20) return null
        if (!Regex("^[A-Za-z0-9+/=\\s]+$").matches(cleaned)) return null
        return try {
            val decoded = String(
                android.util.Base64.decode(cleaned, android.util.Base64.DEFAULT),
                Charsets.UTF_8
            )
            if (decoded.contains("proxies:")) decoded else null
        } catch (e: Exception) {
            null
        }
    }

    /** 清空订阅（删除下载的订阅文件）。 */
    fun restoreEmbeddedSubscription(context: Context) {
        File(context.filesDir, SUBSCRIPTION_FILE).delete()
        LogHelper.log("已清空订阅")
    }

    private fun copyAssetIfNeeded(context: Context, asset: String, dst: File) {
        val len = context.assets.open(asset).use { it.available().toLong() }
        if (!dst.exists() || dst.length() != len) {
            context.assets.open(asset).use { input ->
                dst.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    private fun controllerReady(): Boolean {
        return try {
            val conn = URL("$CONTROLLER_URL/version").openConnection() as HttpURLConnection
            conn.connectTimeout = 500
            conn.readTimeout = 500
            conn.requestMethod = "GET"
            val ok = conn.responseCode == 200
            conn.disconnect()
            ok
        } catch (e: Exception) {
            false
        }
    }

    private fun enc(s: String): String =
        URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun tail(file: File): String {
        return try {
            file.readLines().takeLast(15).joinToString("\n")
        } catch (e: Exception) {
            "(无法读取日志)"
        }
    }
}
