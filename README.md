# 通知收纳

独立、开源的本地 Android 通知管理工具。使用 Java、`NotificationListenerService` 和公开 SDK API，无第三方依赖，不申请互联网权限。

[下载最新版](https://github.com/Sokya-Yip/notification-pocket/releases/latest) · [隐私政策](https://github.com/Sokya-Yip/notification-pocket/blob/main/PRIVACY.md) · [问题反馈](https://github.com/Sokya-Yip/notification-pocket/issues)

## 下载

正式 APK 和 SHA-256 校验文件发布在本仓库的 **GitHub Releases**。当前版本为 **v1.4.3（versionCode 8）**，支持 Android 8.1（API 27）及以上。

- 发布文件：`notification-pocket-v1.4.3.apk`、`SHA256.txt`
- 包名：`com.notificationpocket`
- minSdk 27；targetSdk / compileSdk 36
- [隐私政策](PRIVACY.md) · [更新日志](CHANGELOG.md) · [验证范围](docs/验证范围.md)

> 请从本仓库 Releases 下载并核对校验值。旧测试包与正式包的签名不同，不能直接覆盖安装；迁移前先恢复仍需立即接收的通知。

## 功能与交互

- “当前通知”与“已隐藏”两个列表直接读取系统返回的通知快照。
- 所有通知采用同一套手动管理路径，不按应用、标题或标签设置专用规则，也不会自动隐藏新通知。
- 单击“隐藏”：默认长期隐藏 **约 10 年**，不弹菜单，也不自动切换标签页。
- 长按“隐藏”：选择 **长期（约 10 年）、24 小时、1 小时**。
- 点击“已隐藏”条目上的“恢复”：请求系统立即重新发布，并核实活动通知状态；失败时明确提示，不显示虚假成功。
- 日常管理不需要 Root、Shizuku、电脑或 ADB 命令。
- 自定义按钮统一使用至少 48dp 的触控高度、圆角边界 Ripple，并禁用与背景冲突的系统着色及默认高度动画。

“长期”不是永久：到期、系统更新、厂商策略或通知键变化都可能影响结果。部分受系统或厂商保护的通知可能无法隐藏或恢复。

## 安装与授权

1. 从 Releases 下载 APK，按 Android 系统提示确认安装。
2. 打开 App，点击“去授权”，在系统设置中手动允许“通知使用权 / 通知访问权限”。
3. Android 11 及以上优先直达本 App 的授权详情页；不支持直达时进入系统授权列表。
4. 部分侧载机型可能提示“受限设置”。只有系统提供该选项时，才按系统页面说明操作；App 不会绕过系统确认。
5. 返回 App。顶部显示“已授权，可管理通知”后即可使用。

通知使用权可能让 App 接触通知所属应用、标题、正文、图标和系统通知键。请只授予你信任的应用，也可以随时在系统设置中撤销。

同包名、同签名的正常覆盖升级通常保留授权；卸载重装或更换 Debug/Release 签名可能需要重新授权。

## 连接恢复

授权与系统连接分开判断。进程启动、返回 App、监听断开和覆盖更新后会请求系统重连，并采用 0、1、3、8 秒的有限重试；连接成功或授权撤销即停止。断连触发的自动恢复另有 60 秒冷却，避免反复断连形成紧凑后台重试。

界面分别显示正在恢复和需要手动重连，不依赖常驻后台、忽略电池优化或新增权限。只有系统实际连接和活动列表状态才用于确认成功，不把 API 调用正常返回冒充为操作成功。

## 自愿支持

核心功能完整免费，无广告、订阅、强制登录或功能付费墙。

首页底部保留“支持作者 · 微信赞赏”，采用随内容宽度居中的透明细边框和绿色小爱心，作为次级轻量入口；“关于”中也有同一入口。弹窗只展示一条主路径：

1. 长按二维码或点击“发送赞赏码到微信”。
2. App 通过系统分享把内置原图交给微信，优先直达微信公开导出的发送聊天图片接收页；版本不兼容时回退普通分享。
3. 你自行选择“文件传输助手”并发送，再在微信聊天中长按图片识别。另一台手机也可直接扫码。

App 不选择联系人、不自动发送或识别，不能直接跳转付款确认，不打开网页，也不增加互联网权限或支付 SDK。是否支持及金额完全自愿，核心功能没有差别。

## 构建

环境：JDK 17、Android SDK、Gradle 9.3.1、AGP 8.12.0。

Windows：

```powershell
.\gradlew.bat :app:clean :app:lintDebug :app:assembleDebug
```

Debug 输出：`app/build/outputs/apk/debug/app-debug.apk`。

Release 需要你自己的私密签名配置。复制 `keystore.properties.example` 为 `keystore.properties` 并填入本机配置后运行：

```powershell
.\gradlew.bat :app:assembleRelease
```

真实私钥与密码均被 `.gitignore` 排除；缺少配置时，Release 打包会明确失败，避免误发未签名包。签名保管与公开证书指纹见[发布签名说明](docs/发布签名.md)。

## 核心逻辑自测

```powershell
javac -encoding UTF-8 -d build/selftest `
  app/src/main/java/com/notificationpocket/Durations.java `
  app/src/main/java/com/notificationpocket/RebindRequest.java `
  tools/com/notificationpocket/DurationsSelfTest.java `
  tools/com/notificationpocket/RebindRequestSelfTest.java
java -cp build/selftest com.notificationpocket.DurationsSelfTest
java -cp build/selftest com.notificationpocket.RebindRequestSelfTest
```

预期输出：两个 SelfTest 均为 `4 groups passed`。时长测试覆盖三种文档化时长、严格递增关系、正数约束和默认长期使用 `LONG_TERM_MS`；重连测试覆盖自动恢复不解绑、手动重置仅首轮生效、取消清理和后续自动恢复不继承手动重置。

## 隐私与权限

App 不建立通知数据库，不把通知标题、正文或列表保存到首选项、文件、云端或剪贴板。APK 不声明普通或危险 `uses-permission`，包括互联网、定位、通讯录、存储、发布通知、开机和忽略电池优化权限。

清单中的 `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` 只用于保护系统绑定，不代表 App 自动获得通知使用权。

完整说明见[隐私政策](PRIVACY.md)。公开反馈时请隐去通知正文、联系人、订单号等个人信息。

## 许可证

[Apache License 2.0](LICENSE)。
