package com.proxyapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import hev.sockstun.TProxyService
import java.io.File
import kotlin.concurrent.thread

/**
 * VPN 服务：创建虚拟网卡接管流量，交给 tun2socks 转发到本地代理内核。
 */
class ProxyVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.proxyapp.action.START"
        const val ACTION_STOP = "com.proxyapp.action.STOP"
        const val ACTION_STATUS = "com.proxyapp.action.STATUS"
        private const val CHANNEL_ID = "proxy_vpn"
        private const val NOTIFICATION_ID = 1
        private const val TUN_ADDRESS = "198.18.0.1"
        private const val DNS_ADDRESS = "198.18.0.2"

        @Volatile
        var isRunning = false
            private set
    }

    private var tunFd: ParcelFileDescriptor? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return try {
            when (intent?.action) {
            ACTION_STOP -> {
                LogHelper.log("服务停止请求")
                stopAll()
                sendStatus(false, null)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                    START_NOT_STICKY
                }
                else -> {
                    if (!isRunning) {
                        LogHelper.log("服务启动请求")
                        try {
                            startForeground(NOTIFICATION_ID, buildNotification())
                        } catch (e: Throwable) {
                            CoreManager.lastError = "前台服务启动失败：${e.message}"
                            stopSelf()
                            return START_NOT_STICKY
                        }
                        thread(name = "proxy-start") {
                            try {
                                CoreManager.log(this@ProxyVpnService, "开始启动内核")
                                val coreOk = CoreManager.start(this@ProxyVpnService)
                                val tunOk = coreOk && startTunnel()
                                if (tunOk) {
                                    CoreManager.log(this@ProxyVpnService, "VPN 隧道已建立，代理运行中")
                                    isRunning = true
                                    LogHelper.log("连接成功，代理运行中")
                                    sendStatus(true, null)
                                } else {
                                    CoreManager.stop()
                                    CoreManager.log(this@ProxyVpnService, "失败：启动未完成 ${CoreManager.lastError}")
                                    LogHelper.log("连接失败：${CoreManager.lastError}")
                                    sendStatus(false, CoreManager.lastError)
                                    stopSelf()
                                }
                            } catch (e: Throwable) {
                                CoreManager.lastError = "启动异常：${e.message}"
                                CoreManager.log(this@ProxyVpnService, "异常：${e.message}")
                                LogHelper.log("连接异常：${e.message}")
                                sendStatus(false, CoreManager.lastError)
                                CoreManager.stop()
                                stopSelf()
                            }
                        }
                    }
                    START_STICKY
                }
            }
        } catch (e: Throwable) {
            CoreManager.lastError = "服务异常：${e.message}"
            runCatching {
                stopAll()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            START_NOT_STICKY
        }
    }

    private fun startTunnel(): Boolean {
        return try {
            CoreManager.log(this, "正在建立 VPN 隧道")
            // 提前在主线程之外加载原生库，避免界面线程做原生初始化
            runCatching { TProxyService.TProxyGetStats() }
            val builder = Builder()
            builder.setSession(getString(R.string.app_name))
                .setMtu(1500)
                .addAddress(TUN_ADDRESS, 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer(DNS_ADDRESS)
            // 排除本应用自身，避免内核的上游连接被 VPN 再次接管造成环路
            runCatching { builder.addDisallowedApplication(packageName) }

            val fd = builder.establish() ?: return false
            tunFd = fd

            val conf = """
                misc:
                  task-stack-size: 81920
                tunnel:
                  mtu: 1500
                  icmp: 'reply'
                socks5:
                  port: ${CoreManager.mixedPort}
                  address: '127.0.0.1'
                  udp: 'udp'
                mapdns:
                  address: 198.18.0.2
                  port: 53
                  network: 240.0.0.0
                  netmask: 240.0.0.0
                  cache-size: 10000
            """.trimIndent()
            val confFile = File(cacheDir, "tproxy.conf")
            confFile.writeText(conf)

            runCatching { TProxyService.TProxyStartService(confFile.absolutePath, fd.fd) }
            CoreManager.log(this, "隧道启动完成")
            true
        } catch (e: Throwable) {
            CoreManager.lastError = "隧道启动失败：${e.message}"
            CoreManager.log(this, "隧道异常：${e.message}")
            false
        }
    }

    private fun stopAll() {
        runCatching { TProxyService.TProxyStopService() }
        tunFd?.close()
        tunFd = null
        CoreManager.stop()
        isRunning = false
    }

    /** 把启动结果广播给界面，避免界面显示虚假的连接状态。 */
    private fun sendStatus(running: Boolean, error: String?) {
        val i = Intent(ACTION_STATUS)
            .setPackage(packageName)
            .putExtra("running", running)
            .putExtra("error", error)
        sendBroadcast(i)
    }

    override fun onRevoke() {
        stopAll()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopAll()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "代理服务",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("正在为设备提供代理服务")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }
}
