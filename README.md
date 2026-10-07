# QuickJS Mobile Cross-Platform Reference Architecture
> **Android & iOS 双端引入 QuickJS 轻量级 JavaScript 引擎的生产级接入、双向互操作与公式渲染实战项目**

本项目是一个完整的跨平台工程参考实现，旨在探索与演示如何在 **Android (Kotlin + Jetpack Compose + JNI/NDK)** 与 **iOS (Swift + SwiftUI + Clang/Obj-C Bridge)** 原生应用中，优雅、高性能地集成嵌入式 JavaScript 引擎 **[QuickJS](https://bellard.org/quickjs/)**。

以 **“LaTeX 公式解析转 MathML 纯文本”** 为真实业务落地场景，贯穿了：
1. **两端 Native 调 JS**：加载离线大型 JS 运行时（KaTeX 266KB），执行动态脚本与正则 AST 解析；
2. **两端 JS 反调 Native**：注册全局 `NativeBridge.onMetric()`，在 JS 运行时内捕获执行耗时与公式命中数量，穿透底层 C/JNI/Obj-C 逆向通知原生 UI 状态流；
3. **生产级工程规范**：包含 macOS APFS 大小写头文件碰撞防御、Kotlin 字符串插值陷阱规避、Swift 6 严格并发与现代结构化并发重构、以及无签名命令行一键打出 iOS `.ipa` 产物。

---

## 目录索引

* [一、跨平台架构概览](#一跨平台架构概览)
* [二、核心业务场景：LaTeX 转 MathML 纯文本二级流水线](#二核心业务场景latex-转-mathml-纯文本二级流水线)
* [三、仓库目录规范](#三仓库目录规范)
* [四、Android 架构与接入细节](#四android-架构与接入细节)
  * [1. JNI 四级严格编译与系统调用链](#1-jni-四级严格编译与系统调用链)
  * [2. JNI 核心：Opaque 句柄模式与跨线程 JVM 绑定](#2-jni-核心opaque-句柄模式与跨线程-jvm-绑定)
  * [3. 快速编译与运行](#3-快速编译与运行)
* [五、iOS 架构与接入细节](#五ios-架构与接入细节)
  * [1. Clang 原生互操作与 Obj-C 胶水层设计](#1-clang-原生互操作与-objc-胶水层设计)
  * [2. 现代结构化并发改造 (Swift 6 Ready)](#2-现代结构化并发改造-swift-6-ready)
  * [3. 快速编译与一键导出 IPA](#3-快速编译与一键导出-ipa)
  * [4. 新机克隆配置 VS Code / SourceKit-LSP](#4-新机克隆配置-vs-code--sourcekit-lsp)
* [六、双端集成踩坑与防御性编程指南](#六双端集成踩坑与防御性编程指南)

---

## 一、跨平台架构概览

```
                            +-----------------------------------+
                            |  原始输入文本 (包含 $...$ / $$...$$) |
                            +-----------------+-----------------+
                                              │
                      ┌───────────────────────┴───────────────────────┐
                      ▼                                               ▼
         【Android 原生环境】                                【iOS 原生环境】
         Jetpack Compose + StateFlow                         SwiftUI + @Observable
                      │                                               │
             QuickJsEngine.kt                                QuickJsEngine.swift
                      │ (JNI 句柄调用)                                 │ (Obj-C 消息派发)
         quickjs_wrapper.cpp (JNI)                           QuickJsBridge.m (Clang)
                      │                                               │
                      └───────────────────────┬───────────────────────┘
                                              ▼
                                 【QuickJS C 引擎沙箱内核】
                          JSRuntime (堆与GC) / JSContext (独立Realm)
                                              │
                              1. 加载并求值 katex.min.js
                              2. 正则捕获公式: katex.renderToString(..., {output: 'mathml'})
                              3. 耗时度量: NativeBridge.onMetric("RenderLatex", duration)
                                              │
                      ┌───────────────────────┴───────────────────────┐
                      ▼ (JNI 反射 CallVoidMethod)                     ▼ (dispatch_async 主线程)
         Kotlin 监听器回调 (onMetric)                        Swift 代理闭包回调 (onMetric)
                      │                                               │
         MathMlPlainTextConverter (Jsoup)                   纯文本 DOM 映射输出
                      │                                               │
                      └───────────────────────┬───────────────────────┘
                                              ▼
                             +---------------------------------+
                             | 终态输出: 易读 Unicode 纯文本      |
                             | 例: x = (-b ± √(b² - 4ac))/(2a) |
                             +---------------------------------+
```

---

## 二、核心业务场景：LaTeX 转 MathML 纯文本二级流水线

移动端原生渲染 LaTeX（尤其是无网络环境下的 TTS 朗读、富文本预览）通常面临两难：直接读原始 LaTeX 语法体验极差；WebView 渲染又过重。本项目采用与生产级系统一致的 **“JS AST 解析 + 原生 DOM 递归转写”** 二级架构：

```text
[原始文本 + LaTeX]
       ↓ (1. QuickJS + KaTeX：将公式解析为 MathML XML 结构)
[文本 + MathML DOM 树]
       ↓ (2. 原生递归 AST 处理器：映射分数/根号/上标为 Unicode)
[终态输出：Unicode 纯文本（如 E = mc²，(-b ± √(b² - 4ac))/(2a)）]
```

* **输入样例**：
  ```text
  已知 $E = mc^2$，求根公式：
  $$x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a}$$
  ```
* **中间态 MathML（KaTeX 输出）**：
  ```xml
  <span class="katex"><math xmlns="http://www.w3.org/1998/Math/MathML"><semantics><mrow><mi>E</mi><mo>=</mo><mi>m</mi><msup><mi>c</mi><mn>2</mn></msup></mrow>...
  ```
* **输出纯文本**：
  ```text
  已知 E = mc²，求根公式：
  x = (-b ± √(b² - 4ac))/(2a)
  ```

---

## 三、仓库目录规范

```text
.
├── README.md                              // 整体项目架构与跨端集成总览 (本文档)
├── .gitignore                             // 完备的双端构建产物、LSP缓存与密钥忽略规则
├── .vscode/settings.json                  // 双端 C/C++/Swift 语言服务配置
├── android/                               // Android 独立工程
│   ├── build.gradle.kts                   // 根构建脚本
│   ├── settings.gradle.kts                // 模块声明
│   ├── readme.md                          // Android 专用流向与规范文档
│   └── app/
│       ├── build.gradle.kts               // 启用 Compose、CMake NDK 接入
│       └── src/main/
│           ├── AndroidManifest.xml        // 清单配置
│           ├── assets/katex.min.js        // KaTeX 运行时资产 (266KB)
│           ├── cpp/
│           │   ├── CMakeLists.txt         // NDK 编译规则与宏定义
│           │   ├── quickjs_wrapper.cpp    // JNI 双向胶水层与 Handle 管理
│           │   └── quickjs/               // QuickJS 完整 C 源码 (C11)
│           └── java/com/example/latex/
│               ├── MainActivity.kt        // Compose 容器入口
│               ├── engine/QuickJsEngine.kt// JNI 生命周期包装与 JS 注入
│               ├── converter/             // MathML 纯文本递归解析器
│               └── ui/                    // ViewModel (StateFlow) 与 Compose 界面
└── ios/                                   // iOS 独立工程
    ├── project.yml                        // 声明式 XcodeGen 配置文件
    ├── QuickJsLatexDemo.xcodeproj         // Xcode 工程
    ├── readme.md                          // iOS 专用流向、LSP 与打包文档
    └── QuickJsLatexDemo/
        ├── App/QuickJsLatexApp.swift      // SwiftUI App 入口
        ├── Bridge/
        │   ├── QuickJsBridge.h / .m       // Obj-C/C 胶水层 (ContextOpaque 派发)
        │   └── QuickJsLatexDemo-Bridging-Header.h
        ├── Engine/QuickJsEngine.swift     // 现代结构化并发 Swift 引擎包装
        ├── ViewModel/                     // @MainActor @Observable 状态驱动
        ├── Views/ContentView.swift        // SwiftUI 界面
        ├── Resources/katex.min.js         // Bundle 内嵌资产
        └── QuickJS/                       // QuickJS 完整 C 源码 (Clang 直接编译)
```

---

## 四、Android 架构与接入细节

### 1. JNI 四级严格编译与系统调用链

```text
======================= 编译期 (Compile Time) =======================
app/build.gradle.kts
   │ (externalNativeBuild: 指定 CMakeLists.txt 路径与 NDK 工具链)
   ▼
CMakeLists.txt
   │ (add_library quickjs_bridge SHARED: 编译 quickjs_wrapper.cpp 等 C 代码)
   ▼
libquickjs_bridge.so  ──> 打包至 APK 内部目录 (/lib/arm64-v8a/)

======================= 运行期 (Runtime) ===========================
Kotlin: System.loadLibrary("quickjs_bridge")
   │ (系统底层触发 dlopen("libquickjs_bridge.so")，将共享库映射进进程虚拟内存)
   ▼
Kotlin: external fun nativeInit(...)
   │ (初次执行触发 ART 虚拟机 dlsym() 符号动态解析)
   ▼
C++: Java_com_example_latex_engine_QuickJsEngine_nativeInit
   │ (extern "C" 保持纯字面量符号，成功绑定函数指针并执行)
   ▼
QuickJS C 运行时初始化 & 句柄指针 (jlong) 返回 Kotlin
```

### 2. JNI 核心：Opaque 句柄模式与跨线程 JVM 绑定

在 `quickjs_wrapper.cpp` 中，定义了核心宿主结构体 `EngineContext`：
```cpp
struct EngineContext {
    JavaVM* jvm;               // 全局唯一的 Java 虚拟机接口指针 (线程安全)
    jobject listenerGlobalRef; // Kotlin 监听器的跨生命周期全局强引用 (防止被 Java GC 误杀)
    JSRuntime* rt;             // QuickJS 物理堆与引用计数 GC 管理器
    JSContext* ctx;            // QuickJS 沙箱 Realm (拥有专属的 globalThis)
};
```

* **双向反查机制**：初始化时通过 `JS_SetContextOpaque(engine->ctx, engine)` 将宿主指针注入上下文；
* **JS 调用 Native**：在静态 C 回调 `JsNativeBridge_onMetric` 触发时，通过 `JS_GetContextOpaque(ctx)` 瞬间找回宿主实例；
* **跨线程获取环境**：通过 `engine->jvm->GetEnv((void**)&env, JNI_VERSION_1_6)` 动态拿取当前线程私有的 `JNIEnv*`，安全调用 Kotlin 接口：
  ```cpp
  env->CallVoidMethod(engine->listenerGlobalRef, method, jTag, duration);
  ```

### 3. 快速编译与运行

在 `android/` 目录下执行：
```bash
# 编译 Debug APK
./gradlew assembleDebug

# 安装至已连接设备/模拟器
./gradlew installDebug
```

---

## 五、iOS 架构与接入细节

### 1. Clang 原生互操作与 Obj-C 胶水层设计

与 Android 必须编写复杂 JNI 反射不同，iOS 的 Swift / Objective-C 具备与 C 语言直接进行 **ABI 级零开销调用** 的能力。

为规避 Swift 无法直接展开 C 语言宏（如 `JS_UNDEFINED`、`JS_MKVAL`）以及操作非托管裸指针的风险，项目设计了轻量级 Objective-C 桥接类 `QuickJsBridge`：
1. `initQuickJs` 内部创建 `JSRuntime` 与 `JSContext`；
2. 执行 `JS_SetContextOpaque(_ctx, (__bridge void *)self)`，使静态 C 回调可秒级反查回 `QuickJsBridge` 实例；
3. JS 触发 `NativeBridge.onMetric()` 时，通过 GCD `dispatch_async(dispatch_get_main_queue(), ...)` 顺畅切回主线程通知 Swift。

### 2. 现代结构化并发改造 (Swift 6 Ready)

彻底淘汰传统 `Task.detached` 混合 `[weak self]` 与到处手动切回 `MainActor.run` 的高嵌套屎山写法，全面改用线性 `async/await`：

```swift
@MainActor
@Observable
final class LatexConverterViewModel {
    // 状态天然由 MainActor 保护，无并发数据竞争
    var inputText: String = ...
    var outputText: String = ""
    var isConverting: Bool = false

    func convert() {
        guard let engine, !inputText.isEmpty, isEngineReady else { return }
        isConverting = true

        let rawInput = inputText
        Task {
            // 线性等待后台完成，随后自动在主线程安全恢复赋值
            self.outputText = await engine.convertAsync(text: rawInput)
            self.isConverting = false
        }
    }
}
```

### 3. 快速编译与一键导出 IPA

在 `ios/` 目录下执行：

#### 模拟器编译构建
```bash
xcodebuild -project QuickJsLatexDemo.xcodeproj \
  -scheme QuickJsLatexDemo \
  -destination 'generic/platform=iOS Simulator' \
  build
```

#### 命令行一键打包真机测试 IPA (无需付费证书)
```bash
# 1. 编译 Release arm64 .app
xcodebuild -project QuickJsLatexDemo.xcodeproj \
  -scheme QuickJsLatexDemo \
  -configuration Release \
  -destination 'generic/platform=iOS' \
  -derivedDataPath build/DerivedData \
  build CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO CODE_SIGNING_ALLOWED=NO

# 2. 封装为标准 IPA 包 (Payload 压缩)
mkdir -p build/Payload
cp -r build/DerivedData/Build/Products/Release-iphoneos/QuickJsLatexDemo.app build/Payload/
(cd build && zip -r -q QuickJsLatexDemo.ipa Payload)
rm -rf build/Payload

# 产物输出于: ios/build/QuickJsLatexDemo.ipa (包体仅 ~474 KB)
```

### 4. 新机克隆配置 VS Code / SourceKit-LSP

由于 `buildServer.json` 包含开发机绝对路径与 DerivedData 动态哈希（已在 `.gitignore` 中全局忽略），新机拉取本仓库后，只需在项目根目录运行一行指令即可恢复完整的代码补全与跳转：

```bash
# 1. 为 LSP 重新生成当前主机的构建绑定
xcode-build-server config -project ios/QuickJsLatexDemo.xcodeproj -scheme QuickJsLatexDemo

# 2. 执行一次预编译建立索引库
xcodebuild -project ios/QuickJsLatexDemo.xcodeproj -scheme QuickJsLatexDemo -destination 'generic/platform=iOS Simulator' build

# 3. 在 VS Code 中按下 Cmd + Shift + P，执行: Developer: Reload Window
```

---

## 六、双端集成踩坑与防御性编程指南

| 常见陷阱 | 现象与报错 | 根因与规避规范 |
| :--- | :--- | :--- |
| **macOS 大小写不敏感碰撞** | `quickjs/version:1:1: error: expected unqualified-id: 2024-01-13` | APFS 文件系统不区分大小写，C++ 标准库 `#include <version>` 误匹配为 `quickjs/VERSION`。**规避**：严禁将 `quickjs` 源码根目录加入全局 Header Search Paths，统一通过带目录的 `#include "quickjs/quickjs.h"` 引用。 |
| **Kotlin 原始字符串插值陷阱** | `Unresolved reference 'E'` / `Unresolved reference 'x'` | Kotlin 多行原始字符串 `"""` 中紧随字母的 `$E` 会被解析为变量插值，且不支持 `\$` 转义。**规避**：一律使用 `${'$'}E` 进行显式转义。 |
| **QuickJS C 宏缺失** | `quickjs.c: error: 'CONFIG_VERSION' undeclared` | QuickJS 依赖构建系统显式传入版本号宏。**规避**：必须在 CMakeLists.txt 与 Xcode 中配置预定义宏 `CONFIG_VERSION="2024-01-13"` 与 `_GNU_SOURCE=1`。 |
| **嵌套 Git 仓库导致的源码丢失** | `git status` 仅显示 `quickjs/`，克隆后为空文件夹 | 使用 `git clone` 下载 QuickJS 后残留了内部 `.git` 文件夹，Git 判定其为 Gitlink 子模块。**规避**：作为 Vendor 内嵌源码时，必须清除内部的 `.git` 目录，确保全部 C 文件受外层 Git 直接管控。 |
| **JNIEnv 跨线程使用崩溃** | Crash: `JNIEnv used across threads` | `JNIEnv*` 是线程私有的句柄表，不可跨线程缓存。**规避**：C++ 结构体只允许长期持有进程级的 `JavaVM*`，在每次回调发生时动态调用 `jvm->GetEnv()` 获取当前线程专属的 `JNIEnv*`。 |
