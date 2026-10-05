# libxposed api 102 官方规则（见 Howard20181/api README）
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}

# 入口类必须保持原名：java_init.list 里写的是源码全名，
# 若 R8 重命名而未同步重写资源，框架会因找不到类而识别不到模块。
-keep class dev.antienv.AntiEnvModule { *; }
-keep class dev.antienv.core.** { *; }
-keep class dev.antienv.targets.** { *; }
