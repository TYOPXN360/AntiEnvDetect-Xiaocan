package dev.antienv.core

import io.github.libxposed.api.XposedInterface

/**
 * 一个待 hook 的目标方法。
 *
 * @param clazz        目标类全名
 * @param method       方法名
 * @param params       参数类型；空列表表示无参。用于在重载方法中精确选中
 * @param result       返回值替换策略
 * @param feature      所属功能分组，对应 [EnvConfig] 中的开关
 * @param id           hook id，用于 API-102 的原子替换与热重载
 * @param note         备注，写进日志便于排查
 */
data class Target(
    val clazz: String,
    val method: String,
    val params: List<Class<*>?> = emptyList(),
    val result: Result,
    val feature: Feature = Feature.DETECT,
    val id: String = "$clazz#$method${params.size}",
    val note: String = "",
)

/** 返回值替换策略。 */
sealed class Result {
    /** 放行原始调用。 */
    data object PASS : Result()

    /** 固定返回值。 */
    data class Of(val value: Any?) : Result()

    /** 依据入参计算返回值。 */
    data class Dynamic(val fn: (List<Any?>) -> Any?) : Result()

    /** void 方法：吞掉调用，不执行原始实现（用于阻断上报）。 */
    data object VOID : Result()
}

/** 功能分组，对应 [EnvConfig] 开关。 */
enum class Feature { DETECT, REPORT, SHELL_PROBE, SYSPROP, NET }

/**
 * hook 安装器。
 *
 * 遵守 libxposed API-102 规范：
 * - [XposedInterface.hook] 取得 [XposedInterface.HookBuilder]
 * - [XposedInterface.HookBuilder.setId] 设定稳定 id，使同 id 的新 hook 原子替换旧 hook
 * - [XposedInterface.HookBuilder.setExceptionMode] 选择异常模式
 * - [XposedInterface.HookBuilder.intercept] 注册 [XposedInterface.Hooker]
 * - [XposedInterface.Hooker.intercept] 中用 [XposedInterface.Chain.proceed] 放行
 */
class HookInstaller(
    private val module: XposedInterface,
    private val logger: EnvLogger,
    private val config: EnvConfig,
) {
    private val installed = mutableSetOf<String>()

    private val PASS = Any()

    fun enabled(feature: Feature): Boolean = when (feature) {
        Feature.DETECT -> true
        Feature.REPORT -> config.blockReport
        Feature.SHELL_PROBE -> config.blockShellProbe
        Feature.SYSPROP -> config.blockSysProp
        Feature.NET -> config.dumpNetwork
    }

    /**
     * 在候选 classloader 链上依次尝试安装。
     *
     * V库加固会在 attachBaseContext 里解密 dex 并替换 ClassLoader，
     * 因此需要把系统默认 loader 与加固后的 loader 一起考虑。
     */
    fun install(loaders: List<ClassLoader>, target: Target): Boolean {
        if (!enabled(target.feature)) {
            logger.d("feature disabled, skip ${target.id}")
            return false
        }
        if (target.id in installed) return true

        var cls: Class<*>? = null
        for (cl in loaders) {
            cls = try {
                Class.forName(target.clazz, false, cl)
            } catch (t: Throwable) {
                null
            }
            if (cls != null) break
        }
        if (cls == null) {
            logger.d("class absent: ${target.clazz}")
            return false
        }

        val method = findMethod(cls, target) ?: run {
            logger.d("signature absent: ${target.id}")
            return false
        }

        return try {
            val handle = module.hook(method)
                .setId(target.id)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val out = compute(target, chain)
                    if (out === PASS) chain.proceed() else out
                }
            installed += target.id
            logger.hooked(target.id, handle.getExecutable().name)
            true
        } catch (t: Throwable) {
            logger.w("hook failed: ${target.id}", t)
            false
        }
    }

    fun installedCount(): Int = installed.size

    /** 本轮未命中的目标 id，供日志排查。 */
    @Volatile
    var lastMissed: List<String> = emptyList()
        private set

    fun installAll(loaders: List<ClassLoader>, targets: List<Target>): Pair<Int, Int> {
        var ok = 0
        var miss = 0
        val missed = mutableListOf<String>()
        for (t in targets) {
            if (install(loaders, t)) ok++ else {
                miss++
                missed += t.id
            }
        }
        lastMissed = missed
        return ok to miss
    }

    private fun findMethod(cls: Class<*>, target: Target): java.lang.reflect.Method? =
        cls.declaredMethods.firstOrNull { m ->
            m.name == target.method &&
                m.parameterTypes.size == target.params.size &&
                // params 中的 null 表示"任意类型"，用于避开类加载顺序问题
                m.parameterTypes.zip(target.params).all { (a, b) -> b == null || a == b }
        }

    private fun compute(target: Target, chain: XposedInterface.Chain): Any? =
        when (val r = target.result) {
            is Result.PASS -> PASS
            is Result.Of -> {
                logger.intercepted(target.id, null, r.value)
                r.value
            }
            is Result.Dynamic -> {
                val out = try {
                    r.fn(chain.args)
                } catch (t: Throwable) {
                    logger.w("dynamic failed: ${target.id}", t)
                    null
                }
                // Dynamic 约定：返回 Result.PASS 表示本次放行原始调用
                if (out === Result.PASS) {
                    chain.proceed()
                } else {
                    logger.intercepted(target.id, null, out)
                    out
                }
            }
            is Result.VOID -> {
                logger.blocked(target.id, target.note.ifEmpty { "void" })
                null
            }
        }
}
