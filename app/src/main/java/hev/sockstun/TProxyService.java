package hev.sockstun;

/**
 * tun2socks 原生库的 JNI 入口（与官方 sockstun 应用完全一致）。
 * 库加载时通过 JNI_OnLoad 注册下面这些方法。
 */
public final class TProxyService {
    static {
        System.loadLibrary("hev-socks5-tunnel");
    }

    private TProxyService() {
    }

    /** 启动隧道：把 VPN 网卡的数据包转发到本地 SOCKS5 代理。 */
    public static native void TProxyStartService(String configPath, int fd);

    /** 停止隧道。 */
    public static native void TProxyStopService();

    /** 获取流量统计：[上传包数, 上传字节, 下载包数, 下载字节]。 */
    public static native long[] TProxyGetStats();
}
