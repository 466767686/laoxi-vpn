package com.proxyapp

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.proxyapp.ui.theme.ProxyTheme
import hev.sockstun.TProxyService
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var isRunning by mutableStateOf(false)
    private var connecting by mutableStateOf(false)
    private var globalMode by mutableStateOf(false)
    private var nodeCount by mutableStateOf(0)
    private var groupName by mutableStateOf("")
    private var currentNode by mutableStateOf("")
    private var lastError by mutableStateOf<String?>(null)
    private var diagnostics by mutableStateOf<String?>(null)
    private var subscriptions by mutableStateOf(listOf<String>())
    private var subscriptionStatus by mutableStateOf<String?>(null)
    private var txBytes by mutableStateOf(0L)
    private var rxBytes by mutableStateOf(0L)
    private var txRate by mutableStateOf(0L)
    private var rxRate by mutableStateOf(0L)
    private var lastTx = 0L
    private var lastRx = 0L
    private var lastSampleTime = 0L
    private var nodeFetching = false
    private var nodeFetchFailed by mutableStateOf(false)
    private var fetchFailCount = 0

    // 当前页面：home / proxy / subscription / settings
    private var currentPage by mutableStateOf("home")

    // 节点页面状态
    private var groups by mutableStateOf(listOf<ProxyGroup>())
    private var currentGroup by mutableStateOf("")
    private var nodes by mutableStateOf(listOf<String>())
    private var delays by mutableStateOf(mapOf<String, Int>())
    private var testing by mutableStateOf(setOf<String>())
    private var panelMsg by mutableStateOf<String?>(null)

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                requestNotificationPermission()
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            startProxyService()
        }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val running = intent?.getBooleanExtra("running", false) ?: false
            val err = intent?.getStringExtra("error")
            connecting = false
            isRunning = running
            if (!running && !err.isNullOrBlank()) {
                lastError = err
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isRunning = ProxyVpnService.isRunning
        globalMode = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("global_mode", false)
        val info = CoreManager.loadConfigInfo(this)
        nodeCount = info.nodeCount
        groupName = info.groupName
        lastError = readDiagnostics()
        diagnostics = lastError
        subscriptions = CoreManager.getSubscriptions(this)

        setContent {
            ProxyTheme {
                ProxyScreen(
                    currentPage = currentPage,
                    onNavigate = ::navigateTo,
                    isRunning = isRunning,
                    connecting = connecting,
                    globalMode = globalMode,
                    nodeCount = nodeCount,
                    groupName = groupName,
                    currentNode = currentNode,
                    nodeFetchFailed = nodeFetchFailed,
                    txRate = txRate,
                    rxRate = rxRate,
                    txBytes = txBytes,
                    rxBytes = rxBytes,
                    error = lastError,
                    versionName = runCatching {
                        packageManager.getPackageInfo(packageName, 0).versionName
                    }.getOrNull() ?: "-",
                    diagnostics = diagnostics,
                    subscriptions = subscriptions,
                    subscriptionStatus = subscriptionStatus,
                    groups = groups,
                    currentGroup = currentGroup,
                    nodes = nodes,
                    delays = delays,
                    testing = testing,
                    panelMsg = panelMsg,
                    onToggle = ::toggleProxy,
                    onToggleGlobalMode = ::toggleGlobalMode,
                    onRetryNode = {
                        fetchFailCount = 0
                        nodeFetchFailed = false
                    },
                    onExportLog = ::exportLog,
                    onShareLog = ::shareLog,
                    onAddSubscription = ::addSubscription,
                    onUpdateSubscription = ::updateSubscription,
                    onDeleteSubscription = ::deleteSubscription,
                    onRestoreSubscription = ::restoreSubscription,
                    onSelectGroup = { group ->
                        currentGroup = group
                        panelMsg = null
                        loadGroupNodes()
                    },
                    onSelectNode = ::selectNode,
                    onRetest = ::refreshDelays
                )
                if (isRunning) {
                    LaunchedEffect(Unit) {
                        while (isActive) {
                            if (ProxyVpnService.isRunning) {
                                val now = System.currentTimeMillis()
                                if (currentNode.isEmpty() && !nodeFetching) {
                                    nodeFetching = true
                                    lifecycleScope.launch {
                                        val gs = withContext(Dispatchers.IO) { CoreManager.fetchGroups() }
                                        if (!gs.isNullOrEmpty()) {
                                            groups = gs
                                            val main = gs.firstOrNull { it.type == "Selector" } ?: gs.first()
                                            currentNode = main.now
                                            // 记忆的全局模式在连接后自动应用
                                            if (globalMode) {
                                                withContext(Dispatchers.IO) {
                                                    CoreManager.setMode("global")
                                                }
                                                LogHelper.log("连接后自动应用全局模式")
                                            }
                                            fetchFailCount = 0
                                            nodeFetchFailed = false
                                        } else {
                                            fetchFailCount++
                                            if (fetchFailCount == 8) {
                                                nodeFetchFailed = true
                                                CoreManager.log(this@MainActivity, "节点信息获取失败：内核控制端口不可达")
                                            }
                                        }
                                        nodeFetching = false
                                    }
                                }
                                runCatching { TProxyService.TProxyGetStats() }.getOrNull()?.let { stats ->
                                    if (stats.size >= 4) {
                                        txBytes = stats[1]
                                        rxBytes = stats[3]
                                        if (lastSampleTime > 0 && now > lastSampleTime) {
                                            val dt = (now - lastSampleTime) / 1000f
                                            if (dt > 0f) {
                                                txRate = ((stats[1] - lastTx).toFloat() / dt).toLong().coerceAtLeast(0)
                                                rxRate = ((stats[3] - lastRx).toFloat() / dt).toLong().coerceAtLeast(0)
                                            }
                                        }
                                        lastTx = stats[1]
                                        lastRx = stats[3]
                                        lastSampleTime = now
                                    }
                                }
                            }
                            delay(1000)
                        }
                    }
                }
            }
        }
    }

    private fun toggleProxy() {
        if (isRunning) {
            stopProxyService()
        } else {
            lastError = null
            val prepareIntent = VpnService.prepare(this)
            if (prepareIntent != null) {
                vpnPermissionLauncher.launch(prepareIntent)
            } else {
                requestNotificationPermission()
            }
        }
    }

    private fun toggleGlobalMode(enabled: Boolean) {
        globalMode = enabled
        getSharedPreferences("settings", MODE_PRIVATE)
            .edit()
            .putBoolean("global_mode", enabled)
            .apply()
        if (ProxyVpnService.isRunning) {
            lifecycleScope.launch {
                val ok = withContext(Dispatchers.IO) {
                    CoreManager.setMode(if (enabled) "global" else "rule")
                }
                if (!ok) {
                    Toast.makeText(this@MainActivity, "切换模式失败", Toast.LENGTH_SHORT).show()
                } else {
                    LogHelper.log(if (enabled) "已切换到全局模式" else "已切换到规则模式")
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startProxyService()
        }
    }

    private fun startProxyService() {
        val intent = Intent(this, ProxyVpnService::class.java)
            .setAction(ProxyVpnService.ACTION_START)
        ContextCompat.startForegroundService(this, intent)
        connecting = true
        isRunning = false
        lastError = CoreManager.lastError
    }

    private fun stopProxyService() {
        val intent = Intent(this, ProxyVpnService::class.java)
            .setAction(ProxyVpnService.ACTION_STOP)
        startService(intent)
        connecting = false
        isRunning = false
        txBytes = 0
        rxBytes = 0
        txRate = 0
        rxRate = 0
        lastSampleTime = 0
        currentNode = ""
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                statusReceiver,
                IntentFilter(ProxyVpnService.ACTION_STATUS),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(statusReceiver, IntentFilter(ProxyVpnService.ACTION_STATUS))
        }
        // 校准：界面认为已连接但服务实际已停止时，纠正状态
        if (isRunning && !connecting && !ProxyVpnService.isRunning) {
            isRunning = false
        }
    }

    override fun onPause() {
        unregisterReceiver(statusReceiver)
        super.onPause()
    }

    private fun openNodePanel() {
        currentPage = "proxy"
        panelMsg = null
        if (!ProxyVpnService.isRunning) {
            nodes = emptyList()
            panelMsg = "请先连接代理，再切换节点"
            return
        }
        lifecycleScope.launch {
            val gs = withContext(Dispatchers.IO) { CoreManager.fetchGroups() }
            if (gs.isNullOrEmpty()) {
                panelMsg = "无法读取节点列表，请重试"
                return@launch
            }
            groups = gs
            val main = gs.firstOrNull { it.type == "Selector" } ?: gs.first()
            currentGroup = main.name
            currentNode = main.now
            loadGroupNodes()
        }
    }

    private fun navigateTo(page: String) {
        when (page) {
            "proxy" -> openNodePanel()
            "subscription" -> {
                currentPage = "subscription"
                if (ProxyVpnService.isRunning) refreshAll(quiet = true)
            }
            "settings" -> currentPage = "settings"
            else -> currentPage = "home"
        }
    }

    private fun exportLog() {
        runCatching {
            val path = LogHelper.exportToDownloads(this)
            Toast.makeText(
                this,
                if (path != null) "已导出到：$path" else "导出失败（没有日志）",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun shareLog() {
        LogHelper.shareLog(this)
    }

    private fun addSubscription(url: String) {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            subscriptionStatus = "请输入有效的订阅链接（以 http:// 或 https:// 开头）"
            return
        }
        if (subscriptions.contains(trimmed)) {
            subscriptionStatus = "该订阅已在列表中，可直接点更新"
            return
        }
        lifecycleScope.launch {
            subscriptionStatus = "正在下载订阅…"
            val ok = withContext(Dispatchers.IO) {
                CoreManager.downloadSubscription(this@MainActivity, trimmed)
            }
            if (ok) {
                subscriptions = subscriptions + trimmed
                CoreManager.saveSubscriptions(this@MainActivity, subscriptions)
                subscriptionStatus = "订阅添加并更新成功"
                restartForNewConfig()
            } else {
                subscriptionStatus = CoreManager.lastError
            }
        }
    }

    private fun updateSubscription(url: String) {
        lifecycleScope.launch {
            subscriptionStatus = "正在更新订阅…"
            val ok = withContext(Dispatchers.IO) {
                CoreManager.downloadSubscription(this@MainActivity, url)
            }
            subscriptionStatus = if (ok) "订阅更新成功" else CoreManager.lastError
            if (ok) restartForNewConfig()
        }
    }

    private fun deleteSubscription(url: String) {
        subscriptions = subscriptions - url
        CoreManager.saveSubscriptions(this@MainActivity, subscriptions)
        if (subscriptions.isEmpty()) {
            CoreManager.restoreEmbeddedSubscription(this)
            subscriptionStatus = "已删除订阅"
            restartForNewConfig()
        } else {
            subscriptionStatus = "已删除订阅"
        }
    }

    private fun restoreSubscription() {
        subscriptions = emptyList()
        CoreManager.saveSubscriptions(this@MainActivity, emptyList())
        CoreManager.restoreEmbeddedSubscription(this)
        subscriptionStatus = "已清空订阅，请添加订阅链接"
        restartForNewConfig()
    }

    /** 订阅配置变更后重启连接以生效。 */
    private fun restartForNewConfig() {
        if (!ProxyVpnService.isRunning) return
        lifecycleScope.launch {
            stopProxyService()
            delay(1500)
            startProxyService()
        }
    }

    private fun loadGroupNodes() {
        val g = groups.firstOrNull { it.name == currentGroup } ?: return
        nodes = g.all
        currentNode = g.now
        refreshDelays()
    }

    private fun refreshDelays() {
        val targets = nodes.filter { it != "REJECT" }
        if (targets.isEmpty()) return
        testing = targets.toSet()
        lifecycleScope.launch {
            targets.chunked(6).forEach { batch ->
                val results = batch.map { node ->
                    async(Dispatchers.IO) { node to CoreManager.testDelay(node, 3000) }
                }.awaitAll()
                results.forEach { (node, d) ->
                    if (d != null) {
                        delays = delays + (node to d)
                    }
                    testing = testing - node
                }
            }
        }
    }

    private fun selectNode(node: String) {
        panelMsg = null
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { CoreManager.switchNode(currentGroup, node) }
            if (ok) {
                groups = groups.map { if (it.name == currentGroup) it.copy(now = node) else it }
                currentNode = node
            } else {
                panelMsg = "切换失败，请重试"
            }
        }
    }

    private fun refreshAll(quiet: Boolean = false) {
        if (!ProxyVpnService.isRunning) {
            if (!quiet) Toast.makeText(this, "请先连接代理", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val gs = withContext(Dispatchers.IO) { CoreManager.fetchGroups() }
            if (gs.isNullOrEmpty()) {
                if (!quiet) Toast.makeText(this@MainActivity, "刷新失败", Toast.LENGTH_SHORT).show()
                return@launch
            }
            groups = gs
            val main = gs.firstOrNull { it.type == "Selector" } ?: gs.first()
            currentNode = main.now
            if (!quiet) Toast.makeText(this@MainActivity, "已刷新", Toast.LENGTH_SHORT).show()
        }
    }

    /** 汇总上次运行留下的诊断信息，用于排查问题。 */
    private fun readDiagnostics(): String? {
        val parts = mutableListOf<String>()
        CrashCatcher.readCrash(this)?.let { parts.add("上次闪退：\n$it") }
        val startup = File(filesDir, "startup.log")
        if (startup.exists() && startup.readText().isNotBlank()) {
            parts.add("启动过程：\n" + startup.readLines().takeLast(6).joinToString("\n"))
        }
        val mihomoLog = File(filesDir, "mihomo.log")
        if (mihomoLog.exists() && mihomoLog.readText().isNotBlank()) {
            parts.add("内核日志：\n" + mihomoLog.readLines().takeLast(8).joinToString("\n"))
        }
        val logcat = File(filesDir, "logcat.log")
        if (logcat.exists() && logcat.readText().isNotBlank()) {
            parts.add("系统日志（崩溃记录）：\n" + logcat.readLines().takeLast(25).joinToString("\n"))
        }
        return parts.joinToString("\n\n").ifBlank { CoreManager.lastError }
    }
}
