package dev.antienv.core

import android.util.Log
import io.github.libxposed.api.XposedInterface

/**
 * 日志接口。
 *
 * 遵守 libxposed API-102 规范：所有日志统一走 [XposedInterface.log]，
 * 由框架转发到模块日志通道（不自行写文件、不使用 android.util.Log 直连 target 进程）。
 */
class EnvLogger(
    private val module: XposedInterface,
    private val tag: String,
) {

    companion object { const val NET_TAG = "AntiEnvNet" }
    /** 由配置控制；关闭后仅保留错误日志。 */
    @Volatile
    var verbose: Boolean = false

    @Volatile
    var debug: Boolean = false

    fun v(message: String) {
        if (!verbose) return
        module.log(Log.INFO, tag, message)
    }

    fun d(message: String) {
        if (!debug) return
        module.log(Log.DEBUG, tag, message)
    }

    fun i(message: String) {
        module.log(Log.INFO, tag, message)
    }

    fun w(message: String, tr: Throwable? = null) {
        module.log(Log.WARN, tag, message, tr)
    }

    fun e(message: String, tr: Throwable? = null) {
        module.log(Log.ERROR, tag, message, tr)
    }

    /** 记录一次 hook 安装结果。 */
    fun hooked(target: String, result: String) {
        d("hooked $target -> $result")
    }

    /** 记录一次拦截（检测被改写）。 */
    fun intercepted(target: String, original: Any?, replacement: Any?) {
        v("intercept $target: $original => $replacement")
    }

    /** 网络层观测日志（上报请求/服务器响应）。 */
    fun net(message: String) {
        module.log(Log.INFO, NET_TAG, message)
    }

    /** 记录一次上报阻断。 */
    fun blocked(target: String, reason: String) {
        i("blocked $target ($reason)")
    }
}
