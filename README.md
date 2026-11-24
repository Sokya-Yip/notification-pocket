# 通知收纳

独立、开源的本地 Android 通知管理工具：逐条隐藏与恢复通知，不联网、无广告、无第三方依赖。

[下载最新版](https://github.com/Sokya-Yip/notification-pocket/releases/latest) · [隐私政策](PRIVACY.md) · [问题反馈](https://github.com/Sokya-Yip/notification-pocket/issues)

## 下载

正式 APK 和 SHA-256 校验值发布在 [GitHub Releases](https://github.com/Sokya-Yip/notification-pocket/releases/latest)。当前版本 **v1.4.3（versionCode 8）**，支持 Android 8.1（API 27）及以上，包名 `com.notificationpocket`。

> 旧测试包与正式包签名不同，不能直接覆盖安装；升级前先恢复仍需立即接收的通知。

[更新日志](CHANGELOG.md) · [验证范围](docs/验证范围.md)

## 功能

- “当前通知”和“已隐藏”两个列表，直接读取系统返回的通知快照。
- 逐条手动管理，不按应用或标题设规则，也不会自动隐藏新通知。
- 单击“隐藏”长期隐藏（约 10 年），长按可选 24 小时或 1 小时。
- 点“恢复”会请求系统重新发布并核实结果，失败会明确提示，不报假成功。
- 不需要 Root、Shizuku、电脑或 ADB。

“长期”不是永久：到期、系统更新、厂商策略或通知键变化都可能影响结果，部分受保护的通知可能无法隐藏或恢复。

## 安装与授权

1. 从 Releases 下载 APK 并安装。
2. 打开 App，点“去授权”，在系统设置里允许“通知使用权”。
3. Android 11+ 优先直达本 App 的授权详情页；不支持时进入系统授权列表。
4. 部分侧载机型提示“受限设置”时，按系统页面说明操作，App 不会绕过系统确认。
5. 返回 App，顶部显示“已授权，可管理通知”即可用。

通知使用权可能让 App 读到通知所属应用、标题、正文等信息，请只授予信任的应用，可随时在系统设置里撤销。同签名覆盖升级通常保留授权，卸载重装或换签名则需重新授权。

## 连接恢复

授权与系统连接分开判断。进程启动、返回 App、监听断开、覆盖更新后会有限次重连，成功或撤销授权即停止；断连后的自动恢复有冷却间隔，避免频繁重试。界面区分“正在恢复”和“需要手动重连”，不依赖常驻后台或忽略电池优化。是否成功只看系统实际连接和列表状态，不看 API 返回值。

## 隐私与权限

App 不建通知数据库，也不把通知内容保存到任何地方。**APK 不声明任何权限**（含互联网、定位、存储、开机、电池优化等）。清单里的 `BIND_NOTIFICATION_LISTENER_SERVICE` 只用于系统绑定，不代表自动获得通知使用权。

详见[隐私政策](PRIVACY.md)。公开反馈时请隐去通知正文、联系人等个人信息。

## 支持作者

核心功能完全免费，无广告、订阅或登录。首页底部和“关于”里有微信赞赏入口，通过系统分享把赞赏码交给微信，由你自行发送——App 不自动发送、不跳付款、不开网页、不加任何权限。

## 构建

环境：JDK 17、Android SDK、Gradle 9.3.1、AGP 8.12.0。

```powershell
.\gradlew.bat :app:clean :app:lintDebug :app:assembleDebug
```

Debug 输出在 `app/build/outputs/apk/debug/`。Release 需自备签名：复制 `keystore.properties.example` 为 `keystore.properties` 填好配置后运行 `.\gradlew.bat :app:assembleRelease`。私钥不入库，缺配置会明确失败。`tools/` 下有核心逻辑自测，预期均 `4 groups passed`。签名说明见[发布签名](docs/发布签名.md)。

## 许可证

[Apache License 2.0](LICENSE)。
