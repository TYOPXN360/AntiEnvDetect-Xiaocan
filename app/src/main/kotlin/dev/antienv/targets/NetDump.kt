package dev.antienv.targets

import android.content.Context
import dev.antienv.core.EnvConfig
import dev.antienv.core.EnvLogger
import dev.antienv.core.Feature
import dev.antienv.core.Result
import dev.antienv.core.Target
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * HTTP 层观测。
 *
 * 快手风控的发送器 com.kuaishou.weapon.p0.l 使用原生 HttpURLConnection：
 *   l.a(m request, j callback)              → 组装并发送（method 49）
 *   l.a(m request, j callback, String)      → 带 header 的变体（method 94）
 *   l.b(m request, j callback)              → method 238，实际出口
 *   l.a(m request)                          → method 249，同步发送，返回响应体
 *   l.a(String url, String x)               → method 53，创建 HttpURLConnection
 *
 * 同时 hook 系统 HttpURLConnection 的响应码与输入流，这样不依赖 SDK 内部结构，
 * 任何走 HttpURLConnection 的上报都能被抓到。
 */
object NetDump {

    private val CTX: Class<*> = Context::class.java
    private val STRING: Class<*> = String::class.java

    private const val MAX_DUMP = 6000

    /** 已被本模块消费过的响应体，避免二次读取。 */
    private val dumped = java.util.Collections.synchronizedSet(HashSet<String>())

    fun targets(logger: EnvLogger, dump: Boolean): List<Target> = listOf(
        // ---- 快手 SDK 内部发送器：打印 URL 与响应 ----
        Target(
            "com.kuaishou.weapon.p0.l", "a", listOf(null, null),
            Result.Dynamic { args ->
                val req = args.getOrNull(0)
                logger.net("SDK  send url=${urlOf(req)}")
                bodyOf(req)?.let { logger.net("SDK  body=${clip(it)}") }
                if (dump) null else Result.PASS
            },
            Feature.NET, id = "l#a(m,j):dump", note = "SDK 发送器（dump）",
        ),
        Target(
            "com.kuaishou.weapon.p0.l", "b", listOf(null, null),
            Result.Dynamic { args ->
                val req = args.getOrNull(0)
                logger.net("SDK  sendB url=${urlOf(req)}")
                bodyOf(req)?.let { logger.net("SDK  bodyB=${clip(it)}") }
                if (dump) null else Result.PASS
            },
            Feature.NET, id = "l#b(m,j):dump", note = "SDK 发送器 b（dump）",
        ),
        Target(
            "com.kuaishou.weapon.p0.l", "a", listOf(null),
            Result.Dynamic { args ->
                val req = args.getOrNull(0)
                logger.net("SDK  sync url=${urlOf(req)}")
                bodyOf(req)?.let { logger.net("SDK  bodySync=${clip(it)}") }
                if (dump) null else Result.PASS
            },
            Feature.NET, id = "l#a(m):dump", note = "SDK 同步发送（dump）",
        ),

        // ---- 系统 HttpURLConnection：通用抓取 ----
        Target(
            "java.net.HttpURLConnection", "connect", emptyList(),
            Result.Dynamic {
                val conn = it.firstOrNull() as? java.net.HttpURLConnection
                conn?.let { logger.net("HTTP connect ${it.url} (${it.requestMethod})") }
                if (dump) null else Result.PASS
            },
            Feature.NET, id = "HttpURLConnection#connect", note = "HTTP 连接观测",
        ),
        Target(
            "java.net.HttpURLConnection", "getResponseCode", emptyList(),
            Result.Dynamic { args ->
                val conn = args.firstOrNull() as? java.net.HttpURLConnection
                // 放行拿真实状态码之前，先打印请求信息
                conn?.let { logger.net("HTTP req ${it.url} method=${it.requestMethod}") }
                if (dump) null else Result.PASS
            },
            Feature.NET, id = "HttpURLConnection#getResponseCode", note = "HTTP 状态码观测",
        ),
        Target(
            "java.net.HttpURLConnection", "getInputStream", emptyList(),
            Result.Dynamic { args ->
                val conn = args.firstOrNull() as? java.net.HttpURLConnection
                if (conn != null) {
                    logger.net("HTTP ${conn.url} -> reading response")
                    if (dump) null else Result.PASS
                } else {
                    Result.PASS
                }
            },
            Feature.NET, id = "HttpURLConnection#getInputStream", note = "HTTP 响应读取",
        ),
        // ---- okhttp3（14 个 dex 均内置，各 SDK 独立打包，类可能不在同一 loader）----
        Target(
            "okhttp3.OkHttpClient", "newCall", listOf(null, java.lang.Boolean.TYPE),
            Result.Dynamic { args ->
                args.getOrNull(0)?.let { r ->
                    logger.net("OK  ${describeRequest(r)}")
                }
                if (dump) null else Result.PASS
            },
            Feature.NET, id = "okhttp3#newCall2", note = "okhttp 请求观测",
        ),
        Target(
            "okhttp3.OkHttpClient", "newCall", listOf(null),
            Result.Dynamic { args ->
                args.getOrNull(0)?.let { r ->
                    logger.net("OK  ${describeRequest(r)}")
                }
                if (dump) null else Result.PASS
            },
            Feature.NET, id = "okhttp3#newCall1", note = "okhttp 请求观测",
        ),
        Target(
            "okhttp3.ResponseBody", "string", emptyList(),
            Result.Dynamic { args ->
                logger.net("OK  resp ${clip(args.firstOrNull() as? String)}")
                if (dump) null else Result.PASS
            },
            Feature.NET, id = "okhttp3.ResponseBody#string", note = "okhttp 响应观测",
        ),

        // ---- java.net.URLConnection（HttpURLConnection 的父类，抽象方法在此）----
        Target(
            "java.net.URLConnection", "getInputStream", emptyList(),
            Result.Dynamic { args ->
                args.firstOrNull()?.let { c ->
                    runCatching { c.javaClass.getMethod("getURL").invoke(c) }
                        .getOrNull()?.let { logger.net("URL $it -> response") }
                }
                if (dump) null else Result.PASS
            },
            Feature.NET, id = "URLConnection#getInputStream", note = "URLConnection 响应观测",
        ),
        Target(
            "java.net.URL", "openConnection", emptyList(),
            Result.Dynamic { args ->
                args.firstOrNull()?.let { logger.net("OPEN $it") }
                if (dump) null else Result.PASS
            },
            Feature.NET, id = "URL#openConnection", note = "URL 打开连接观测",
        ),
    )

    private fun describeRequest(req: Any?): String = try {
        val m = req?.javaClass?.getMethod("method")?.invoke(req)
        val u = req?.javaClass?.getMethod("url")?.invoke(req)
        val b = req?.javaClass?.getMethod("body")?.invoke(req)
        "$m $u body=${clip(b?.javaClass?.simpleName)}"
    } catch (t: Throwable) {
        "?"
    }

    private fun urlOf(req: Any?): String = try {
        req?.javaClass?.getField("a")?.get(req)?.toString() ?: "?"
    } catch (t: Throwable) {
        "?"
    }

    private fun bodyOf(req: Any?): String? = try {
        val m = req?.javaClass?.getMethod("c")?.invoke(req) as? String
        m?.takeIf { it.isNotEmpty() }
    } catch (t: Throwable) {
        null
    }

    /** 读取流内容并重置为可读（供 SDK 继续解析）。 */
    fun drain(stream: InputStream): String? = try {
        val bos = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (true) {
            val n = stream.read(buf)
            if (n <= 0) break
            bos.write(buf, 0, n)
        }
        bos.toString("UTF-8")
    } catch (t: Throwable) {
        null
    }

    fun clip(s: String?): String {
        if (s == null) return "<null>"
        val one = s.replace("\n", "\\n")
        return if (one.length <= MAX_DUMP) one else one.take(MAX_DUMP) + "...[truncated ${one.length}]"
    }
}
