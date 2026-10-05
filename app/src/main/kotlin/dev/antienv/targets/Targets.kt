package dev.antienv.targets

import android.content.Context
import dev.antienv.core.Feature
import dev.antienv.core.Result
import dev.antienv.core.Target
import org.json.JSONObject

/**
 * 目标清单。
 *
 * 全部条目来自对 com.realtech.xiaocan（V库加固壳 + 16 个 dex）的静态分析结论，
 * 类名/方法名与反编译源码一一对应；混淆后的类名在目标包不存在时会自动跳过。
 *
 * 分组：
 *  - [kuaishouWeapon]  快手风控 com.kuaishou.weapon.p0（字段 0/1/2/24/65-68/82-86/99/103/128/131-151/161/185）
 *  - [systemLayer]      系统层通用探针（SystemProperties、Runtime.exec）
 *  - [vendorSdks]       宿主广告 SDK（白帽、融合、adx、bugly、qm、octopus、czhj、jd、mdad、aggmore）
 */
object Targets {

    private val CTX: Class<*> = Context::class.java
    private val STRING: Class<*> = String::class.java
    private val INT: Class<*> = Int::class.javaPrimitiveType ?: Int::class.java
    private val BOOLEAN: Class<*> = java.lang.Boolean.TYPE

    /** 空 JSONObject：SDK 里普遍用 null 或空对象表示"无可疑项"。 */
    private fun emptyJson(): Any? = try {
        JSONObject()
    } catch (t: Throwable) {
        null
    }

    // ---------------------------------------------------------------------
    // 快手风控 SDK
    // ---------------------------------------------------------------------

    /** aj：root 检测主类 */
    private fun aj(cl: String, m: String, v: Any?, note: String, params: List<Class<*>> = emptyList()) =
        Target(cl, m, params, Result.Of(v), Feature.DETECT, note = note)

    val kuaishouWeapon: List<Target> = listOf(
        // --- aj.java：root / 属性 / 命令探测 ---
        aj("com.kuaishou.weapon.p0.aj", "a", 0, "PATH 遍历扫 su（字段 1）"),
        aj("com.kuaishou.weapon.p0.aj", "c", "", "su -v（字段 2）"),
        aj("com.kuaishou.weapon.p0.aj", "d", 0, "ro.secure（字段 0）"),
        aj("com.kuaishou.weapon.p0.aj", "e", 0, "ro.debuggable（字段 24）"),
        aj("com.kuaishou.weapon.p0.aj", "f", 0, "ro.adb.secure（字段 3）"),
        aj("com.kuaishou.weapon.p0.aj", "g", null, "which su / id uid=0 / busybox df（字段 133）"),
        aj("com.kuaishou.weapon.p0.aj", "h", 0, "Superuser.apk（字段 82）"),
        aj("com.kuaishou.weapon.p0.aj", "i", "", "magisk 文件路径（字段 83）"),
        aj("com.kuaishou.weapon.p0.aj", "j", "", "su 文件路径（字段 84）"),
        aj("com.kuaishou.weapon.p0.aj", "k", "", "栈帧注入检测（字段 86）"),
        aj("com.kuaishou.weapon.p0.aj", "b", false, "flyme ROM 识别（静态）"),

        // --- af.java：Magisk 挂载表（现代 Magisk 专用）---
        aj("com.kuaishou.weapon.p0.af", "a", 0, "/proc/<pid>/mounts 扫 .magisk/.core（字段 99）"),

        // --- ao.java：Xposed / EdXposed ---
        aj("com.kuaishou.weapon.p0.ao", "a", false, "置 XposedBridge.disableHooks（字段 67）"),
        aj("com.kuaishou.weapon.p0.ao", "b", false, "Xposed 栈帧（字段 66）"),
        aj("com.kuaishou.weapon.p0.ao", "c", false, "EdXposed 配置类（字段 101）"),
        aj("com.kuaishou.weapon.p0.ao", "d", false, "Xposed 类加载（字段 65）"),
        aj("com.kuaishou.weapon.p0.ao", "e", null, "vxp 系统属性（字段 PPSLauncher）"),
        aj("com.kuaishou.weapon.p0.ao", "f", false, "CLASSPATH/XposedBridge（字段 68）"),
        aj("com.kuaishou.weapon.p0.ao", "g", false, "getStackTrace 是否 native（字段 102）"),

        // --- ah.java：ActivityManager 单例是否动态代理（system_server 被 hook）---
        aj("com.kuaishou.weapon.p0.ah", "a", null, "AMS 代理检测（字段 131/145）", emptyList()),
        aj("com.kuaishou.weapon.p0.ah", "a", null, "AMS 代理检测（字段 131/145）", listOf(CTX)),

        // --- an.java：hook 方法清单 / 被 hook 的 framework 方法 ---
        aj("com.kuaishou.weapon.p0.an", "a", null, "substrate/XposedBridge 映射（字段 34）"),
        aj("com.kuaishou.weapon.p0.an", "b", null, "XposedBridge.sHookedMethodCallbacks（字段 33）"),
        aj("com.kuaishou.weapon.p0.an", "c", null, "XposedHelpers.methodCache（字段 32）"),
        aj("com.kuaishou.weapon.p0.an", "d", null, "TelephonyManager 等方法是否 native（字段 60）"),
        aj("com.kuaishou.weapon.p0.an", "e", null, "MediaRecorder/Camera 方法（字段 155）"),
        aj("com.kuaishou.weapon.p0.an", "f", null, "Cipher/MessageDigest 方法（字段 170）"),
        aj("com.kuaishou.weapon.p0.an", "g", null, "JSONObject/HttpURLConnection 方法（字段 190）"),

        // --- z.java：maps 扫描（lineageos / hook / mokee / 加密 hook 框架）---
        aj("com.kuaishou.weapon.p0.z", "a", null, "maps 通用可疑集（字段 5）", listOf(CTX)),
        aj("com.kuaishou.weapon.p0.z", "b", null, "maps lineageos/hook 集（字段 161）"),
        aj("com.kuaishou.weapon.p0.z", "c", null, "maps art.so 集（字段 191）"),
        aj("com.kuaishou.weapon.p0.z", "d", null, "maps 加密 hook 框架集（字段 185）"),
        aj("com.kuaishou.weapon.p0.z", "e", null, "maps dex 提取（字段 146）"),
        aj("com.kuaishou.weapon.p0.z", "a", 0, "mokee 集计数（字段 154）"),

        // --- ac.java：多开 / 沙箱 / 注入 so ---
        aj("com.kuaishou.weapon.p0.ac", "a", false, "uid/100000 多开判定"),
        aj("com.kuaishou.weapon.p0.ac", "a", false, "/data 可读（字段 8）", listOf(CTX)),
        aj("com.kuaishou.weapon.p0.ac", "b", "", "dataDir 父目录（字段 61）", listOf(CTX)),
        aj("com.kuaishou.weapon.p0.ac", "c", null, "mPackages 遍历可写包（字段 11）", listOf(CTX)),
        aj("com.kuaishou.weapon.p0.ac", "d", null, "maps 非本包 so/dex（字段 9）", listOf(CTX)),
        aj("com.kuaishou.weapon.p0.ac", "e", 0, "dataDir 写权限试探（字段 10）", listOf(CTX)),
        aj("com.kuaishou.weapon.p0.ac", "f", false, "多开辅助判定（字段 181）", listOf(CTX)),

        // --- ad.java / ae.java / al.java：模拟器与可写性 ---
        aj("com.kuaishou.weapon.p0.ad", "a", false, "goldfish 模拟器（字段 103）"),
        aj("com.kuaishou.weapon.p0.ad", "b", false, "x86/amd cpuinfo（字段 128）"),
        aj("com.kuaishou.weapon.p0.ae", "a", null, "分区可写性 /data /system（字段 96）"),
        aj("com.kuaishou.weapon.p0.ae", "b", null, "分区可写性 /sys /sbin（字段 134）"),
        aj("com.kuaishou.weapon.p0.ae", "a", false, "/proc/<x> canWrite（字段 26/27）", listOf(STRING)),
        aj("com.kuaishou.weapon.p0.ae", "b", false, "/proc 写入测试（字段 28/29）", listOf(STRING)),
        aj("com.kuaishou.weapon.p0.ae", "a", false, "写入测试", listOf(STRING, BOOLEAN)),
        aj("com.kuaishou.weapon.p0.al", "a", false, "虚拟摄像头文件", listOf(CTX)),
        aj("com.kuaishou.weapon.p0.al", "a", null, "虚拟摄像头属性/MockCamera/群控痕迹（静态）"),

        // --- am.java / di.java / dg.java / cq.java / ak.java ---
        aj("com.kuaishou.weapon.p0.am", "d", null, "CA 证书抓包检测（字段 77）"),
        aj("com.kuaishou.weapon.p0.am", "c", 0, "CA 证书计数（字段 104）"),
        aj("com.kuaishou.weapon.p0.di", "a", null, "LD_PRELOAD/LD_LIBRARY_PATH（字段 132）"),
        aj("com.kuaishou.weapon.p0.di", "b", 0, "CPU 架构（字段 151）"),
        aj("com.kuaishou.weapon.p0.dg", "a", null, "lsof 非 443 端口（字段 147）"),
        aj("com.kuaishou.weapon.p0.dg", "b", null, "pidof adbd（字段 148）"),
        aj("com.kuaishou.weapon.p0.dg", "c", null, "调试端口（字段 149）"),
        aj("com.kuaishou.weapon.p0.dg", "e", null, "网络探测（字段 97）"),
        aj("com.kuaishou.weapon.p0.cq", "a", null, "反射取设备标识（字段 98）"),
        aj("com.kuaishou.weapon.p0.cq", "b", null, "反射取设备标识（字段 98）"),
        aj("com.kuaishou.weapon.p0.ak", "a", "00000000000000000000", "模拟器指纹 20 位串"),

        // --- ab.java / ac 等辅助 ---
        aj("com.kuaishou.weapon.p0.ab", "b", false, "Debug.isDebuggerConnected（字段 35）"),
        aj("com.kuaishou.weapon.p0.ab", "a", null, "TracerPid（字段 22）"),
        aj("com.kuaishou.weapon.p0.ab", "a", false, "FLAG_DEBUGGABLE（字段 36）", listOf(CTX)),
    )

    // ---------------------------------------------------------------------
    // 环境上报入口（re_po_rt / ci.d=1007002）
    // ---------------------------------------------------------------------

    val reportBlockers: List<Target> = listOf(
        // ---- 快手风控：环境采集与发送的全部入口 ----
        Target(
            "com.kuaishou.weapon.p0.cz", "a", listOf(INT, INT), Result.VOID,
            Feature.REPORT, note = "re_po_rt 环境采集入口（开关 plc001_r_s）",
        ),
        Target(
            "com.kuaishou.weapon.p0.dc", "a", listOf(INT), Result.VOID,
            Feature.REPORT, id = "com.kuaishou.weapon.p0.dc#a(int)", note = "1007010 上报入口",
        ),
        Target(
            "com.kuaishou.weapon.p0.cn", "a", listOf(CTX, STRING), Result.VOID,
            Feature.REPORT, id = "cn#a(ctx,str)", note = "通用发送器 2 参",
        ),
        Target(
            "com.kuaishou.weapon.p0.cn", "a", listOf(CTX, STRING, STRING, BOOLEAN, BOOLEAN), Result.VOID,
            Feature.REPORT, id = "cn#a(ctx,str,str,b,b)", note = "通用发送器 5 参（实际 HTTP 出口）",
        ),
        // JNI 通道：pqr/eopq 是 native 上报，ax/az/aq/av/bd/bc 都经由它
        Target(
            "com.kuaishou.weapon.p0.jni.Engine", "pqr", listOf(INT, INT, INT, STRING), Result.Of(""),
            Feature.REPORT, note = "native 上报 pqr（ci.d=1007002）",
        ),
        Target(
            "com.kuaishou.weapon.p0.jni.Engine", "eopq", listOf(INT, INT, INT, STRING), Result.Of(""),
            Feature.REPORT, note = "native 上报 eopq",
        ),

        // ---- 白帽广告：环境指纹落盘 ----
        Target(
            "com.baihemob.ad.base.k0", "a", emptyList(), Result.VOID, Feature.REPORT,
            note = "环境指纹落盘上报（isProxy/hasSign/mobileRoot…）",
        ),
    )

    // ---------------------------------------------------------------------
    // 系统层探针
    // ---------------------------------------------------------------------

    val systemLayer: List<Target> = listOf(
        // 只在 sysprop 开关打开时才安装；未匹配的 key 返回 Result.PASS 表示放行
        Target("android.os.SystemProperties", "get", listOf(STRING),
            Result.Dynamic { args ->
                when (args.firstOrNull() as? String) {
                    "ro.secure" -> "1"
                    "ro.debuggable" -> "0"
                    "ro.adb.secure" -> "1"
                    "ro.build.display.id" -> ""
                    else -> Result.PASS
                }
            }, Feature.SYSPROP, note = "关键系统属性归一化"),
        Target("java.lang.Runtime", "exec", listOf(STRING),
            Result.Dynamic { args ->
                val cmd = args.firstOrNull() as? String
                if (cmd != null && isRootProbeCommand(cmd)) "" else Result.PASS
            }, Feature.SHELL_PROBE, id = "java.lang.Runtime#exec:shell", note = "shell 探针过滤"),
    )

    private fun isRootProbeCommand(cmd: String): Boolean {
        val c = cmd.trim().lowercase()
        return c == "which su" || c == "id" || c == "busybox df" || c == "su -v" ||
            c.startsWith("which su") || c.startsWith("busybox df")
    }

    // ---------------------------------------------------------------------
    // 宿主广告 SDK
    // ---------------------------------------------------------------------

    val vendorSdks: List<Target> = listOf(
        // com.baihemob.ad：白帽广告（四段式级联 + 业务短路 + 上报）
        Target("com.baihemob.ad.base.n0", "b", emptyList(), Result.Of(false), Feature.DETECT,
            note = "su 包名/mount/getprop/路径 四段式级联（mobileRoot）"),
        Target("com.baihemob.ad.base.n0", "a", emptyList(), Result.Of(null), Feature.DETECT,
            id = "com.baihemob.ad.base.n0#a:noarg", note = "root 管理器包名枚举"),
        Target("com.baihemob.ad.base.n0", "a", listOf(CTX), Result.Of(emptyList<Any>()), Feature.DETECT,
            id = "com.baihemob.ad.base.n0#a:ctx", note = "root 管理器包名枚举(ctx)"),
        Target("com.baihemob.ad.base.n0", "a", listOf(STRING), Result.Of(""), Feature.DETECT,
            id = "com.baihemob.ad.base.n0#a:str", note = "getprop 执行"),

        // com.beizi.fusion：su 路径 + ls -l 权限位
        Target("com.beizi.fusion.to", "b", emptyList(), Result.Of("no"), Feature.DETECT,
            note = "su 存在性 + 权限位（DevInfo.root）"),
        Target("com.beizi.fusion.uo", "b", emptyList(), Result.Of("no"), Feature.DETECT,
            note = "同款实现"),

        // com.fl.saas.adx
        Target("com.fl.saas.adx.util.DeviceStateUtil", "isDeviceRooted", emptyList(), Result.Of(false),
            Feature.DETECT, note = "rootEnabled 上报"),

        // com.tencent.bugly
        Target("com.tencent.bugly.proguard.ab", "q", emptyList(), Result.Of(false), Feature.DETECT,
            note = "su 路径 + test-keys"),

        // com.qm.advlib（180+ 黑名单 + 模拟器路径）
        Target("com.qm.advlib.trdparty.identifier.utils.AppInfomation", "g", emptyList(),
            Result.Of(false), Feature.DETECT, note = "Build.TAGS test-keys"),
        Target("com.qm.advlib.trdparty.identifier.utils.AppInfomation", "h", emptyList(),
            Result.Of(false), Feature.DETECT, note = "Superuser.apk"),
        Target("com.qm.advlib.trdparty.identifier.utils.AppInfomation", "i", emptyList(),
            Result.Of(false), Feature.DETECT, note = "su 存在性"),
        Target("com.qm.advlib.trdparty.identifier.utils.AppInfomation", "j", emptyList(),
            Result.Of(null), Feature.DETECT, note = "maps 扫描"),

        // com.octopus.ad
        Target("com.octopus.ad.d.b.i", "b", emptyList(), Result.Of("no"), Feature.DETECT,
            note = "su 存在性 + 权限位"),

        // com.mdad.sdk
        Target("com.mdad.sdk.mduisdk.t.f", "g", emptyList(), Result.Of(false), Feature.DETECT,
            note = "su 存在性"),

        // com.aggmoread（10 条 su 路径）
        Target("com.aggmoread.sdk.z.c.a.a.e.c", "b", emptyList(), Result.Of(false), Feature.DETECT,
            note = "10 条 su 路径"),

        // com.czhj.sdk
        Target("com.czhj.sdk.common.utils.DeviceUtils", "isRoot", emptyList(), Result.Of(false),
            Feature.DETECT, note = "su 存在性 + 权限位"),

        // com.jd.android.sdk
        Target("com.jd.android.sdk.coreinfo.a", "a", emptyList(), Result.Of(false), Feature.DETECT,
            note = "/proc/tty/drivers goldfish"),
        Target("com.jd.android.sdk.coreinfo.a", "b", emptyList(), Result.Of(false), Feature.DETECT,
            id = "com.jd.android.sdk.coreinfo.a#b:noarg", note = "qemud/qemu_pipe"),

        // com.baihemob ShellUtils：主动 su shell 探测
        Target("com.baihemob.ad.util.ShellUtils", "checkRootPermission", emptyList(), Result.Of(false),
            Feature.DETECT, note = "Runtime.exec(\"su\") 探测"),
    )

    /** 全部目标。 */
    fun all(): List<Target> = kuaishouWeapon + reportBlockers + systemLayer + vendorSdks
}
