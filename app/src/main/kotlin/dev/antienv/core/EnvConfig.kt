package dev.antienv.core

import android.content.SharedPreferences
import io.github.libxposed.api.XposedInterface

/**
 * 模块配置。
 *
 * 遵守 API-102 规范：配置存放在框架侧，通过 [XposedInterface.getRemotePreferences] 读取。
 * 在被 hook 的进程中该接口是只读的（框架保证），因此模块 app 侧写入、target 进程侧只读。
 */
class EnvConfig(private val module: XposedInterface) {

    companion object {
        const val GROUP = "anti_env_config"

        const val KEY_ENABLED = "enabled"
        const val KEY_BLOCK_REPORT = "block_report"
        const val KEY_BLOCK_SHELL_PROBE = "block_shell_probe"
        const val KEY_BLOCK_SYSPROP = "block_sysprop"
        const val KEY_DUMP_NETWORK = "dump_network"
        const val KEY_VERBOSE = "verbose"
        const val KEY_DEBUG = "debug"
    }

    private val prefs: SharedPreferences by lazy {
        try {
            module.getRemotePreferences(GROUP)
        } catch (t: Throwable) {
            // 框架为嵌入式实现时不支持 remote preferences，降级为全开
            FallbackPrefs()
        }
    }

    val enabled: Boolean get() = prefs.getBoolean(KEY_ENABLED, true)
    val blockReport: Boolean get() = prefs.getBoolean(KEY_BLOCK_REPORT, true)
    val blockShellProbe: Boolean get() = prefs.getBoolean(KEY_BLOCK_SHELL_PROBE, true)
    val blockSysProp: Boolean get() = prefs.getBoolean(KEY_BLOCK_SYSPROP, false)
    /** true=放行并打印；false=打印后吞掉。 */
    val dumpNetwork: Boolean get() = prefs.getBoolean(KEY_DUMP_NETWORK, true)
    val verbose: Boolean get() = prefs.getBoolean(KEY_VERBOSE, false)
    val debug: Boolean get() = prefs.getBoolean(KEY_DEBUG, false)

    /** 进程内缓存，避免每次 hook 回调都读 SP。 */
    private class FallbackPrefs : SharedPreferences {
        override fun getAll(): MutableMap<String, *> = mutableMapOf<String, Any>()
        override fun getString(key: String?, defValue: String?): String? = defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = defValue
        override fun getLong(key: String?, defValue: Long): Long = defValue
        override fun getFloat(key: String?, defValue: Float): Float = defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = defValue
        override fun contains(key: String?): Boolean = false
        override fun edit(): SharedPreferences.Editor = throw UnsupportedOperationException()
        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) = Unit
    }
}
