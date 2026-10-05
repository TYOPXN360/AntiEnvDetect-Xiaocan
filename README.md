# AntiEnvDetect

一个 Xposed 模块，用于在 Android 应用中拦截广告 / 风控 SDK 的设备环境检测，并把相关结果改写为"未检测到异常"，同时阻断对应上报通道。

## 针对的应用

| 项 | 值 |
|---|---|
| 应用名 | **小蚕** |
| 包名 | **`com.realtech.xiaocan`** |
| 版本 | `3.21.2` |
| 技术形态 | Flutter 应用，Java 层经 **V库加固壳**（`com.appsec` + `com.Proxy`，`libxloader.so` v1.5.2.23）保护，dex 在运行时解密到内存加载 |
| 模块作用域 | `com.realtech.xiaocan` |

模块的作用对象是该应用内集成的下列 SDK，而非应用自身逻辑。

## 针对谁

被集成在 App 内、会对设备做 root / 模拟器 / 注入框架检测并据此限制功能的第三方 SDK。本仓库的目标样本是集成在 `com.realtech.xiaocan` 中的这批 SDK：

- 快手风控 `com.kuaishou.weapon`（`1007002` / `1007010` 上报类型）
- 白帽广告 `com.baihemob.ad`
- 融合 SDK `com.beizi.fusion`
- 广告配置 `com.fl.saas.adx`
- 腾讯 Bugly
- `com.qm.advlib` / `com.octopus.ad` / `com.mdad.sdk` / `com.aggmoread` / `com.czhj.sdk` / `com.jd.android.sdk`

## 解决什么问题

这些 SDK 会在启动后短时间内完成一轮设备环境采集，把 root、Xposed / LSPosed / Magisk、模拟器、多开、代理、抓包证书等状态写入一个上报包交给服务端，由服务端决定是否把该设备标记为异常环境。

模块做两件事：

1. **改写检测结果** —— 让这批检测项返回"未命中"
2. **阻断上报通道** —— 环境采集入口与实际发送出口一并拦下

## 日志

日志通过 libxposed API-102 的 `XposedInterface.log()` 输出，由框架转发，不在目标进程写文件。

框架日志路径：`/data/adb/lspd/log/modules_*.log`（需 root 读取）

| tag | 内容 |
|---|---|
| `AntiEnv` | 模块加载、hook 安装统计（`ok=N miss=M`）、未命中目标清单、上报阻断记录 |
| `AntiEnvNet` | 网络层观测：上报请求体（明文与加密）、服务器响应 |

```bash
# 安装与拦截概况
adb shell su -c "grep -E 'ok=|missed targets|blocked' /data/adb/lspd/log/modules_*.log" | tail -20

# 网络观测
adb shell su -c "tail -f /data/adb/lspd/log/modules_*.log" | grep AntiEnvNet
```

日志示例：

```
loaded in com.realtech.xiaocan (systemServer=false, framework=LSPosed 2.2.0-it)
packer loader acquired: dalvik.system.PathClassLoader[...]
pass=packer packer: ok=94 miss=1 (loaders=1)
missed targets: [android.os.SystemProperties#get1]
blocked com.baihemob.ad.base.k0#a0 (环境指纹落盘上报)
REQ  logId=1007002 z=false enc=true len=1024
REQ  plain={"0":1,"1":0,...}
RESP ok  {"code":0,...}
```

## 配置

配置存放在框架侧（`getRemotePreferences("anti_env_config")`），在被 hook 进程中只读。

| key | 默认 | 说明 |
|---|---|---|
| `enabled` | `true` | 模块总开关 |
| `block_report` | `true` | 阻断环境上报 |
| `block_shell_probe` | `true` | 过滤 `which su` / `id` / `busybox df` 等 shell 探针 |
| `block_sysprop` | `false` | 归一化 `ro.secure` 等系统属性（影响面较大，默认关闭） |
| `dump_network` | `true` | 打印上报内容与响应；`false` 则打印后吞掉 |
| `verbose` | `false` | 打印每一次拦截的"原值 => 新值" |
| `debug` | `false` | 打印每条 hook 的安装 / 跳过明细 |

## 适配版本

| 项 | 版本 |
|---|---|
| Xposed API | libxposed **API-102** |
| 框架 | LSPosed **2.x**（实测 2.2.0-it） |
| LSPosed Manager | 2.2.0-it |
| Android | minSdk **26** / 8.0 起 |
| 目标应用 | 小蚕 / `com.realtech.xiaocan` **3.21.2**（V库加固，Flutter） |
| 构建 | AGP 8.7.3 / Gradle 8.9 / Kotlin 1.9.24 / JDK 17 / compileSdk 35 |

目标应用 `com.realtech.xiaocan` 使用了加固壳，dex 在运行时解密加载，模块会自行识别 `com.Proxy.ShellApplication` 并在解密完成后安装 hook，无需额外配置。

## 预期效果

在 `192.168.195.93:5555`（Redmi K70 Ultra，Android 15，Magisk + LSPosed 已安装但未注入目标 App）实测：

```
pass=packer packer: ok=94 miss=1
```

- 目标 App 不再返回环境异常
- 日志中出现 `blocked ...` 表示上报被拦下
- 网络观测可看到完整的上报字段与响应

## 构建

```bash
export JAVA_HOME=/path/to/jdk17
./gradlew :app:assembleRelease

# 签名（Xposed 模块必须签名才能安装）
apksigner sign --ks your.keystore \
  --ks-pass pass:... --key-pass pass:... \
  --ks-key-alias your-alias \
  --out anti-env-detect.apk app/build/outputs/apk/release/app-release-unsigned.apk
```

`local.properties` 中需配置 `sdk.dir` 指向 Android SDK。

## 安装

```bash
adb install -r anti-env-detect.apk
```

随后在 LSPosed 中启用模块，并确认作用域包含目标包。

## 扩展

检测项集中在 `app/src/main/kotlin/dev/antienv/targets/Targets.kt`，每条是一个 `Target`，写明类名、方法名、参数签名与替换结果。新增或调整目标只需修改该文件，无需改动 hook 引擎。

若目标 App 升级后出现大量 `class absent`，说明混淆类名发生变化，按日志中的类名更新 `Targets.kt` 重新构建即可。
