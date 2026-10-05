package dev.antienv

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import dev.antienv.core.EnvConfig
import dev.antienv.core.EnvLogger
import dev.antienv.core.HookInstaller
import dev.antienv.targets.Targets
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * 模块入口。
 *
 * 遵守 libxposed API-102 规范：
 * - 继承 [XposedModule]，由框架实例化后调用 attachFramework
 * - 在 [onPackageReady] 拿到默认 ClassLoader 后安装 hook
 * - 热重载实现 [onHotReloading] / [onHotReloaded]
 * - 日志一律通过 [io.github.libxposed.api.XposedInterface.log]
 *
 * 针对 V库加固（com.appsec + com.Proxy）：
 * 真实 dex 在 attachBaseContext 内解密并替换 ClassLoader，
 * 因此 onPackageReady 时类尚不可见。策略是
 *   1) onPackageReady 先用默认 loader 试一轮（能命中系统类与已就绪的类）
 *   2) hook 壳的 attachBaseContext / onCreate，在其之后用加固 loader 再试一轮
 *   3) 仍未命中的目标，隔一段时间重试（最多 N 轮）
 */
class AntiEnvModule : XposedModule() {

    private val logger = EnvLogger(this, TAG)
    private val config = EnvConfig(this)
    private val installer = HookInstaller(this, logger, config)

    private val handler = Handler(Looper.getMainLooper())

    private var defaultLoader: ClassLoader? = null
    private var packerLoader: ClassLoader? = null
    private var retryCount = 0

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        logger.verbose = config.verbose
        logger.debug = config.debug
        logger.i(
            "loaded in ${param.processName} " +
                "(systemServer=${param.isSystemServer}, framework=${getFrameworkName()} ${getFrameworkVersion()})"
        )
    }

    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        logger.d("systemServer starting")
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        if (!config.enabled) {
            logger.i("disabled by config, skip ${param.packageName}")
            return
        }
        defaultLoader = param.classLoader

        // 第一轮：默认 classloader（系统类、宿主类）
        runPass(param.packageName, "default")

        // 壳生命周期挂钩：等加固解密完成、loader 就绪后再装
        hookPackerLifecycle(param.classLoader)

        // 兜底轮询：加固可能在 onCreate 之后才完成注入
        scheduleRetry()
    }

    /**
     * hook V库壳（com.Proxy.ShellApplication）的生命周期方法，
     * 在其执行之后再用加固后的 loader 安装目标 hook。
     */
    private fun hookPackerLifecycle(loader: ClassLoader) {
        val shell = try {
            Class.forName("com.Proxy.ShellApplication", false, loader)
        } catch (t: Throwable) {
            logger.d("packer shell class absent (非加固应用?)")
            return
        }
        logger.i("packer shell found, hooking lifecycle")

        listOf("attachBaseContext", "onCreate").forEach { name ->
            val m = shell.declaredMethods.firstOrNull { it.name == name && it.parameterCount == 1 }
                ?: shell.declaredMethods.firstOrNull { it.name == name }
                ?: return@forEach
            try {
                hook(m).setId("packer#$name")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept { chain ->
                        val r = chain.proceed()
                        // 壳执行完毕，此时解密后的 dex 与 loader 已就绪
                        afterPackerReady()
                        r
                    }
                logger.hooked("packer#$name", m.name)
            } catch (t: Throwable) {
                logger.w("hook packer#$name failed", t)
            }
        }
    }

    private fun afterPackerReady() {
        val app = try {
            // 壳的 Application 实例持有真实 loader；取不到时退回线程上下文
            val current = currentApplication()
            current?.javaClass?.classLoader
        } catch (t: Throwable) {
            null
        } ?: Thread.currentThread().contextClassLoader

        packerLoader = app
        logger.i("packer loader acquired: $app")
        runPass("packer", "packer")
    }

    private fun scheduleRetry() {
        if (retryCount >= MAX_RETRY) return
        handler.postDelayed({
            retryCount++
            val before = installer.installedCount()
            runPass("retry#$retryCount", "retry")
            val after = installer.installedCount()
            if (after > before) {
                logger.i("retry#$retryCount progress: $before -> $after")
                scheduleRetry()
            } else {
                logger.i("retry#$retryCount no progress, stop")
            }
        }, RETRY_DELAY_MS)
    }

    private fun runPass(tag: String, pass: String) {
        val loaders = listOfNotNull(packerLoader, defaultLoader)
            .distinct()
        val all = Targets.all() + dev.antienv.targets.NetDump.targets(logger, config.dumpNetwork)
        val (ok, miss) = installer.installAll(loaders, all)
        logger.i("pass=$pass $tag: ok=$ok miss=$miss (loaders=${loaders.size})")
        if (miss > 0) logger.i("missed targets: ${installer.lastMissed}")
    }

    private fun currentApplication(): ClassLoader? = null

    companion object {
        const val TAG = "AntiEnv"
        private const val MAX_RETRY = 6
        private const val RETRY_DELAY_MS = 1500L
    }
}
