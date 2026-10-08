import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.20"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "com.bytedance.tools"
version = "2.0.6-cw"

repositories {
    mavenCentral()
    google()
    intellijPlatform {
        defaultRepositories()
    }
}

// IPGP 2.x 不再像 1.x 那样给所有工程注入仓库，子工程（CodeLocatorModel）需要自己声明
subprojects {
    repositories {
        mavenCentral()
        google()
    }
}

dependencies {
    implementation("com.google.code.gson:gson:2.9.0")
    // kotlin-stdlib 由 IDE 提供（平台自带），打进插件会与平台版本打架，故 compileOnly
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("com.squareup.okhttp3:okhttp:3.14.9")
    implementation("com.hankcs:hanlp:portable-1.8.6")
    implementation("javazoom:jlayer:1.0.1")
    implementation(project(":CodeLocatorModel"))
    implementation("io.reactivex.rxjava3:rxjava:3.1.7")
    implementation("com.google.zxing:core:3.5.2")
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    intellijPlatform {
        // 目标平台直接用本机安装的 Android Studio（AI-262 / 2026.2），不再下载 IC
        local("/Applications/Android Studio.app")
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.kotlin")
        bundledPlugin("org.jetbrains.android")
        bundledPlugin("Git4Idea")
    }
}

// 目标平台 AS 2026.2 自身就是 Java 25 字节码，Java/Kotlin 目标版本统一交给 JDK 25 默认值
// （显式写 17 会触发 "Inconsistent JVM-target compatibility"）

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "262"
            untilBuild = "262.*"
        }
    }
}

// 打包进插件根目录的运行时资源（代码里按插件目录路径读取，见 FileUtils.sCodeLocatorPluginDir）
// 注意：imgcopy 不在此列 —— 它是运行期由 MacHelper 用 gcc 从 imgcopy.m 编译出来的
val extraPluginFiles = listOf(
    "imgcopy.m", "JarModuleTemplate.zip",
    "AndroidModuleTemplate.zip", "codelocatorhelper.apk", "restartAndroidStudio"
)

// IPGP 2.x 的沙箱布局未必还是 build/idea-sandbox/plugins/CodeLocatorPlugin，运行时动态定位
fun sandboxPluginDir(): File? {
    val sandbox = layout.buildDirectory.dir("idea-sandbox").get().asFile
    if (!sandbox.exists()) return null
    return sandbox.walkTopDown().firstOrNull {
        it.isDirectory && it.name == "CodeLocatorPlugin" && it.parentFile?.name == "plugins"
    }
}

fun copyExtraPluginFiles() {
    val target = sandboxPluginDir() ?: return
    extraPluginFiles.forEach { name -> copyFile(File(name), File(target, name)) }
}

tasks.named("prepareSandbox") {
    doLast { copyExtraPluginFiles() }
}

// IPGP 2.x 的 buildPlugin 直接产出 zip（不走沙箱），资源只能挂到 zip 任务上
// zip 内已有 CodeLocatorPlugin/ 顶层目录，故这里不再 into
tasks.named<org.gradle.api.tasks.bundling.Zip>("buildPlugin") {
    extraPluginFiles.forEach { name -> from(name) }
}

fun copyFile(sourceFile: File, targetFile: File) {
    var inputStream: InputStream? = null
    var outputStream: OutputStream? = null
    try {
        inputStream = FileInputStream(sourceFile)
        outputStream = FileOutputStream(targetFile)
        val buffer = ByteArray(8192)
        var len = inputStream.read(buffer)
        while (len > 0) {
            outputStream.write(buffer, 0, len)
            len = inputStream.read(buffer)
        }
        inputStream.close()
        outputStream.close()
    } catch (e: Exception) {
        System.out.println("Copy Error " + e)
    }
}
