# QuickJS iOS (SwiftUI + Clang Bridge) 集成指南

## 1. 项目目录架构

```text
ios/
├── project.yml                            // XcodeGen 工程描述文件
├── QuickJsLatexDemo.xcodeproj             // Xcode 工程
└── QuickJsLatexDemo/
    ├── App/
    │   └── QuickJsLatexApp.swift          // App 入口 (@main)
    ├── Bridge/
    │   ├── QuickJsBridge.h                // 暴露给 Swift 的 Obj-C 接口
    │   ├── QuickJsBridge.m                // 核心 C/QuickJS 胶水层（管理 Runtime/Context/反调）
    │   └── QuickJsLatexDemo-Bridging-Header.h // Xcode 桥接头文件
    ├── Engine/
    │   └── QuickJsEngine.swift            // Swift 门面：加载 Katex 资产与双向通信管理
    ├── ViewModel/
    │   └── LatexConverterViewModel.swift  // 业务状态流与后台并发调度 (Swift 6 Concurrency)
    ├── Views/
    │   └── ContentView.swift              // SwiftUI 界面（输入、触发、指标、输出）
    ├── Resources/
    │   └── katex.min.js                   // KaTeX 离线 JS 包（Copy Bundle Resources）
    └── QuickJS/                           // QuickJS 官方 C 源码
        ├── quickjs.h / quickjs.c
        ├── cutils.h / cutils.c
        ├── dtoa.h / dtoa.c
        ├── libregexp.h / libregexp.c
        └── libunicode.h / libunicode.c
```

---

## 2. 新机克隆后配置 VS Code / SourceKit-LSP

由于 `buildServer.json` 包含本机绝对路径与 DerivedData 哈希（已加入 `.gitignore`），新电脑拉取仓库后，执行以下命令即可恢复完整的 LSP 代码跳转与补全：

```bash
# 1. 安装构建服务工具（若未安装）
brew install xcode-build-server

# 2. 在项目根目录下为 LSP 生成当前主机的 buildServer.json
xcode-build-server config -project ios/QuickJsLatexDemo.xcodeproj -scheme QuickJsLatexDemo

# 3. 执行一次预编译生成索引数据库 (IndexStore)
xcodebuild -project ios/QuickJsLatexDemo.xcodeproj \
  -scheme QuickJsLatexDemo \
  -destination 'generic/platform=iOS Simulator' \
  build

# 4. 在 VS Code 中按下 Cmd + Shift + P，执行: Developer: Reload Window
```

---

## 3. 编译与运行

### 重新生成 Xcode 工程（可选）
本项目支持使用 [XcodeGen](https://github.com/yonaskolb/XcodeGen) 维护工程配置：
```bash
cd ios
xcodegen generate
```

### 模拟器编译构建
```bash
xcodebuild -project QuickJsLatexDemo.xcodeproj \
  -scheme QuickJsLatexDemo \
  -destination 'generic/platform=iOS Simulator' \
  build
```

---

## 4. 导出 IPA 产物

iOS `.ipa` 本质为包含 `Payload/<AppName>.app` 的 ZIP 压缩包。

### 命令行快速出包（离线测试 / 侧载）
无需签名直接打包真机二进制产物：

```bash
cd ios

# 1. 编译 Release arm64 .app
xcodebuild -project QuickJsLatexDemo.xcodeproj \
  -scheme QuickJsLatexDemo \
  -configuration Release \
  -destination 'generic/platform=iOS' \
  -derivedDataPath build/DerivedData \
  build \
  CODE_SIGN_IDENTITY="" \
  CODE_SIGNING_REQUIRED=NO \
  CODE_SIGNING_ALLOWED=NO

# 2. 封装为 IPA 包
mkdir -p build/Payload
cp -r build/DerivedData/Build/Products/Release-iphoneos/QuickJsLatexDemo.app build/Payload/
(cd build && zip -r -q QuickJsLatexDemo.ipa Payload)
rm -rf build/Payload

# 产物输出路径: ios/build/QuickJsLatexDemo.ipa
```

### 官方签名流水线（Archive + Export）
```bash
# 1. 生成归档包
xcodebuild -project QuickJsLatexDemo.xcodeproj \
  -scheme QuickJsLatexDemo \
  -configuration Release \
  -destination 'generic/platform=iOS' \
  -archivePath build/QuickJsLatexDemo.xcarchive \
  archive

# 2. 依据 ExportOptions.plist 导出正式 IPA
xcodebuild -exportArchive \
  -archivePath build/QuickJsLatexDemo.xcarchive \
  -exportPath build/out \
  -exportOptionsPlist build/ExportOptions.plist
```

---

## 5. 跨语言调用链说明

```text
SwiftUI (ContentView)
   ↓ (Task.detached 异步派发)
Swift Engine (QuickJsEngine.swift)
   ↓ (调用 Obj-C 方法 evaluateScript)
Obj-C / C 桥接层 (QuickJsBridge.m)
   ↓ (JS_Eval / JSContext)
QuickJS 内核 (quickjs.c 执行 KaTeX)
   ↓ (JS 调用 NativeBridge.onMetric)
C 回调函数 (JsNativeBridge_onMetric)
   ↓ (通过 JS_GetContextOpaque 拿回 QuickJsBridge 指针)
派发代理 (QuickJsMetricDelegate)
   ↓ (dispatch_async 主线程更新)
SwiftUI 状态响应渲染
```
