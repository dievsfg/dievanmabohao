# 暗码启动器 (DievMabohao)

> 🤖 **该项目完全由 AI (OpenCode) 生成**

一款基于 [YukiHookAPI](https://github.com/HighCapable/YukiHookAPI) 开发的 LSPosed 模块，允许用户通过在拨号盘输入自定义暗码快速启动指定应用。

> ⚠️ **注意**：本模块仅针对小米澎湃OS 3 (HyperOS 3) 的通讯录与拨号应用进行适配。

## 功能特性

- 🚀 **快速启动**：通过拨号盘输入暗码即可启动应用
- ⚙️ **灵活配置**：支持自定义暗码前缀和后缀
- 📱 **应用管理**：支持手动输入包名或从已安装应用列表选择
- 🔧 **调试控制**：支持开启/关闭日志记录和 Toast 弹窗
- 📤 **导入导出**：支持规则的 JSON 格式导入导出
- 🎨 **Material Design**：简洁美观的用户界面

## 兼容性

- **目标系统**：小米澎湃OS 3 (HyperOS 3)
- **目标应用**：通讯录与拨号 (`com.android.contacts`)
- **最低 Android 版本**：Android 7.0 (API 24)
- **目标 Android 版本**：Android 16 (API 36)
- **Xposed 框架**：LSPosed
- **作用域**：`com.android.contacts`

## 使用方法

### 1. 安装模块

1. 下载最新版本的 APK 文件
2. 安装到设备上
3. 在 LSPosed 管理器中激活模块
4. 选择作用域为 `com.android.contacts`
5. **重启「通讯录与拨号」应用**（首次激活需要重启应用，无需重启设备）

### 2. 配置规则

1. 打开「暗码启动器」应用
2. 点击右下角的浮动按钮添加规则
3. 输入暗码数字（如 `123`）
4. 选择或输入目标应用包名
5. 保存规则

### 3. 使用暗码

在拨号盘输入暗码，默认格式为：

```
*#*#暗码数字#*#*
```

例如：`*#*#123#*#*`

## 设置选项

点击主界面右上角的齿轮图标进入设置：

- **全局前缀**：暗码的前缀，默认 `*#*#`
- **全局后缀**：暗码的后缀，默认 `#*#*`
- **日志记录**：开启后输出详细调试日志
- **Toast 弹窗**：开启后显示暗码检测和应用启动提示

## 调试方法

开启日志记录后，可以通过以下命令查看日志：

```bash
adb logcat -s DievMabohao
```

## 技术实现

### 核心 Hook

模块通过 Hook `com.android.contacts.SpecialCharSequenceMgr` 类中的方法实现功能。

使用方法签名定位目标方法（而非混淆后的方法名），确保跨版本兼容：

```kotlin
val targetMethod = targetClass.declaredMethods.firstOrNull { method ->
    method.modifiers and java.lang.reflect.Modifier.PUBLIC != 0 &&
    method.modifiers and java.lang.reflect.Modifier.STATIC != 0 &&
    method.returnType == Boolean::class.javaPrimitiveType &&
    method.parameterTypes.size == 2 &&
    method.parameterTypes[0] == Context::class.java &&
    method.parameterTypes[1] == String::class.java
}
```

### 数据存储

使用 YukiHookAPI 的 `prefs()` 扩展函数存储规则数据，确保在模块应用和 Hook 进程间正确共享。

## 项目结构

```
dievanmabohao/
├── app/
│   ├── src/main/
│   │   ├── java/com/diev/mabohao/
│   │   │   ├── data/           # 数据层
│   │   │   │   ├── Rule.kt     # 规则数据类
│   │   │   │   └── RuleRepository.kt  # 规则存储
│   │   │   ├── hook/           # Hook 入口
│   │   │   │   └── HookEntry.kt
│   │   │   ├── ui/             # 用户界面
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── SettingsActivity.kt
│   │   │   │   ├── RuleEditActivity.kt
│   │   │   │   ├── AppSelectorActivity.kt
│   │   │   │   └── RuleAdapter.kt
│   │   │   └── util/           # 工具类
│   │   │       ├── ImportExportUtil.kt
│   │   │       └── LSPosedUtil.kt
│   │   ├── res/                # 资源文件
│   │   └── AndroidManifest.xml
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── gradle/
│   └── libs.versions.toml      # 版本目录
├── build.gradle.kts
├── settings.gradle.kts
└── gradlew.bat
```

## 构建方法

### 环境要求

- JDK 17
- Android SDK (compileSdk 36)

### 构建命令

```bash
# 构建 Release 版本
./gradlew.bat assembleRelease

# 构建 Debug 版本
./gradlew.bat assembleDebug
```

构建产物位于 `app/build/outputs/apk/release/` 目录。

## 依赖库

| 库 | 版本 | 用途 |
|---|---|---|
| [YukiHookAPI](https://github.com/HighCapable/YukiHookAPI) | 1.3.1 | Xposed Hook 框架 |
| [KavaRef](https://github.com/HighCapable/KavaRef) | 1.0.2 | 反射工具库 |
| [Gson](https://github.com/google/gson) | 2.11.0 | JSON 解析 |
| Material Components | 1.11.0 | UI 组件 |

## 常见问题

### Q: 为什么需要重启应用？

A: LSPosed 模块激活后需要重启目标应用（通讯录与拨号）才能生效。只需强制停止该应用后重新打开即可，无需重启整个设备。

### Q: 模块显示未激活怎么办？

A: 请确认：
1. 已在 LSPosed 管理器中激活模块
2. 已选择 `com.android.contacts` 作用域
3. 已重启「通讯录与拨号」应用

### Q: 暗码输入后没有反应？

A: 请确认：
1. 规则已启用（开关处于开启状态）
2. 暗码格式正确（默认 `*#*#数字#*#*`）
3. 目标应用已安装

### Q: 不同 ROM 兼容性问题？

A: 模块使用方法签名定位 Hook 目标，而非混淆后的方法名，提高了跨 ROM 兼容性。如果仍然不兼容，请提交 Issue 并附上日志。

## 许可证

本项目仅供学习交流使用，请勿用于商业用途。

## 致谢

- [YukiHookAPI](https://github.com/HighCapable/YukiHookAPI) - 优秀的 Xposed Hook 框架
- [KavaRef](https://github.com/HighCapable/KavaRef) - 强大的反射工具库
- [LSPosed](https://github.com/LSPosed/LSPosed) - Xposed 框架
